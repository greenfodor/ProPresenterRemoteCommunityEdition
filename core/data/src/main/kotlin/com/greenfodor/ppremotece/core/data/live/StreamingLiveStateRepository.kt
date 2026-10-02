package com.greenfodor.ppremotece.core.data.live

import android.util.Log
import com.greenfodor.ppremotece.core.data.network.KtorProPresenterClient
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.live.map
import com.greenfodor.ppremotece.core.domain.looks.LooksRepository
import com.greenfodor.ppremotece.core.domain.macros.MacrosRepository
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.Look
import com.greenfodor.ppremotece.core.domain.model.MacroCollection
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.Timer
import com.greenfodor.ppremotece.core.domain.model.TimerReading
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.status.StatusEvent
import com.greenfodor.ppremotece.core.domain.status.StatusFrameParser
import com.greenfodor.ppremotece.core.domain.status.rejectedUrl
import com.greenfodor.ppremotece.core.domain.status.withoutRejected
import com.greenfodor.ppremotece.core.domain.timers.LiveTimer
import com.greenfodor.ppremotece.core.domain.timers.TimersRepository
import com.greenfodor.ppremotece.core.domain.timers.joinTimers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.timeout
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val FIRST_RECONNECT_DELAY = 500.milliseconds
private val MAX_RECONNECT_DELAY = 10.seconds

/** 0.5 s doubled per failed attempt, capped at 10 s. */
fun defaultReconnectDelay(attempt: Int): Duration =
    (FIRST_RECONNECT_DELAY * 2.0.pow(attempt)).coerceAtMost(MAX_RECONNECT_DELAY)

/**
 * Keeps one `status/updates` stream open while [liveState] has subscribers and starts from
 * [LiveState.Initial] each time it is subscribed again. Within each chunk, `playlist/active` sets
 * the live item first; then, if the chunk held a `status/slide` frame or changed the item, or an
 * earlier read failed, `slide_index` and `playlist/active` are read once, in parallel, and that
 * pair, when both reads succeed, sets the live slide and item. Any chunk resets the watchdog. When no chunk arrives for
 * [watchdogTimeout], or the stream ends or fails, the stream is closed and reopened after
 * [reconnectDelay] with the same subscriptions, and the pair is read again. [onReconnected] is
 * called when the first chunk of a reopened stream arrives. Each `status/slide` frame sets the
 * slide text; each pair read naming a slide of the live item's presentation, or a slide live
 * without a playlist item, sets [lastLive], which outlives clears, reconnects and resubscriptions.
 * `timers` and `timers/current` frames set [timers], each timer joined with its latest reading, and
 * `macro_collections` frames set [collections], `looks` frames [looks] and `look/current` frames
 * [currentLook]; the lists are [Loadable.NotLoaded] until their first frame and keep their content
 * across reconnects. An error frame naming a subscribed url ([rejectedUrl])
 * removes that url from the subscriptions for the reopened streams of this connection
 * ([withoutRejected]), keeps the content it feeds [Loadable.Unavailable] and is passed to [log] once.
 */
class StreamingLiveStateRepository(
    private val client: KtorProPresenterClient,
    scope: CoroutineScope,
    private val watchdogTimeout: Duration = 10.seconds,
    private val reconnectDelay: (attempt: Int) -> Duration = ::defaultReconnectDelay,
    private val onReconnected: () -> Unit = {},
    private val log: (String) -> Unit = { Log.w(TAG, it) }
) : LiveStateRepository,
    TimersRepository,
    MacrosRepository,
    LooksRepository {
    private val _lastLive = MutableStateFlow<LiveCue?>(null)
    override val lastLive: StateFlow<LiveCue?> = _lastLive.asStateFlow()

    private val timerList = MutableStateFlow<Loadable<List<Timer>>>(Loadable.NotLoaded)
    private val timerReadings = MutableStateFlow<List<TimerReading>>(emptyList())
    override val timers: StateFlow<Loadable<List<LiveTimer>>> =
        combine(timerList, timerReadings) { list, readings -> list.map { joinTimers(it, readings) } }
            .stateIn(scope, SharingStarted.Eagerly, Loadable.NotLoaded)

    private val macroCollections = MutableStateFlow<Loadable<List<MacroCollection>>>(Loadable.NotLoaded)
    override val collections: StateFlow<Loadable<List<MacroCollection>>> = macroCollections.asStateFlow()

    private val lookList = MutableStateFlow<Loadable<List<Look>>>(Loadable.NotLoaded)
    override val looks: StateFlow<Loadable<List<Look>>> = lookList.asStateFlow()

    private val liveLook = MutableStateFlow<Look?>(null)
    override val currentLook: StateFlow<Look?> = liveLook.asStateFlow()

    @Volatile
    private var subscriptions = SUBSCRIPTIONS

    @Volatile
    private var timersRejected = false

    @Volatile
    private var macrosRejected = false

    @Volatile
    private var looksRejected = false

    override val liveState: StateFlow<LiveState> =
        channelFlow {
            var state = LiveState.Initial
            var itemPresentation: String? = null
            send(state)

            var attempt = 0
            while (true) {
                val parser = StatusFrameParser()
                var slideReadNeeded = true
                val failure = runCatching {
                    streamChunks().collect { chunk ->
                        attempt = 0
                        if (state.connection == ConnectionStatus.RECONNECTING) onReconnected()
                        var next = state.copy(connection = ConnectionStatus.CONNECTED)
                        for (event in parser.events(chunk)) {
                            when (event) {
                                is StatusEvent.SlideChanged -> {
                                    next = next.copy(slideText = event.text)
                                    slideReadNeeded = true
                                }
                                is StatusEvent.PlaylistActive -> {
                                    itemPresentation = event.presentationUuid
                                    if (event.item != next.item) {
                                        next = next.copy(item = event.item)
                                        slideReadNeeded = true
                                    }
                                }
                                is StatusEvent.Layers -> next = next.copy(layers = event.active)
                                is StatusEvent.Timers ->
                                    if (!timersRejected) timerList.value = Loadable.Loaded(event.timers)
                                is StatusEvent.TimerReadings -> timerReadings.value = event.readings
                                is StatusEvent.MacroCollections ->
                                    if (!macrosRejected) macroCollections.value = Loadable.Loaded(event.collections)
                                is StatusEvent.Looks -> if (!looksRejected) {
                                    lookList.value =
                                        Loadable.Loaded(event.looks)
                                }
                                is StatusEvent.CurrentLook -> liveLook.value = event.look
                                is StatusEvent.Rejected -> event.messages.forEach(::reject)
                                else -> Unit
                            }
                        }
                        if (slideReadNeeded) {
                            val (slideRead, activeRead) = coroutineScope {
                                val slide = async { client.slideIndex() }
                                val active = async { client.activePlaylistItem() }
                                slide.await() to active.await()
                            }
                            if (slideRead is Result.Success && activeRead is Result.Success) {
                                next = next.copy(item = activeRead.data.item, slide = slideRead.data)
                                itemPresentation = activeRead.data.presentationUuid
                                slideReadNeeded = false
                                remember(next.item, itemPresentation, slideRead.data)
                            }
                        }
                        state = next
                        send(state)
                    }
                }.exceptionOrNull()
                if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
                state = state.copy(connection = ConnectionStatus.RECONNECTING)
                send(state)
                delay(reconnectDelay(attempt++))
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), LiveState.Initial)

    /**
     * Sets [lastLive] when [slide] is a slide of the presentation that the live [item] plays, or a
     * slide live without a playlist item.
     */
    private fun remember(item: PlaylistItemKey?, itemPresentation: String?, slide: LiveSlide?) {
        slide ?: return
        val source = when {
            item == null -> CueSource.Presentation(slide.presentationUuid)
            slide.presentationUuid == itemPresentation -> CueSource.PlaylistItem(item)
            else -> return
        }
        _lastLive.value = LiveCue(source, slide.presentationUuid, slide.index)
    }

    /**
     * Drops the url [message] names from [subscriptions] and marks the content it feeds unavailable
     * for the rest of this connection.
     */
    private fun reject(message: String) {
        if (rejectedUrl(message) !in subscriptions) return
        subscriptions = withoutRejected(subscriptions, message)
        when (rejectedUrl(message)) {
            "timers", "timers/current" -> {
                timersRejected = true
                timerList.value = Loadable.Unavailable
            }
            "macro_collections" -> {
                macrosRejected = true
                macroCollections.value = Loadable.Unavailable
            }
            "looks" -> {
                looksRejected = true
                lookList.value = Loadable.Unavailable
            }
        }
        log(message)
    }

    @OptIn(FlowPreview::class)
    private fun streamChunks() = client.statusUpdates(subscriptions).timeout(watchdogTimeout)

    private companion object {
        const val TAG = "LiveStream"
        val SUBSCRIPTIONS =
            listOf(
                "status/slide",
                "timer/system_time",
                "playlist/active",
                "status/layers",
                "timers",
                "timers/current",
                "macro_collections",
                "looks",
                "look/current"
            )
    }
}
