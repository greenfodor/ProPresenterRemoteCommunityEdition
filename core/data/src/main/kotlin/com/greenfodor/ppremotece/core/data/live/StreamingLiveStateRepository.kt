package com.greenfodor.ppremotece.core.data.live

import android.util.Log
import com.greenfodor.ppremotece.core.data.network.KtorProPresenterClient
import com.greenfodor.ppremotece.core.domain.audio.AudioRepository
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.live.map
import com.greenfodor.ppremotece.core.domain.live.orNull
import com.greenfodor.ppremotece.core.domain.looks.LooksRepository
import com.greenfodor.ppremotece.core.domain.macros.MacrosRepository
import com.greenfodor.ppremotece.core.domain.model.ActiveAudio
import com.greenfodor.ppremotece.core.domain.model.AudioNode
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.LiveCue
import com.greenfodor.ppremotece.core.domain.model.LiveSlide
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.model.Look
import com.greenfodor.ppremotece.core.domain.model.MacroCollection
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.core.domain.model.PropCollection
import com.greenfodor.ppremotece.core.domain.model.Timer
import com.greenfodor.ppremotece.core.domain.model.TimerReading
import com.greenfodor.ppremotece.core.domain.model.Transport
import com.greenfodor.ppremotece.core.domain.props.PropsRepository
import com.greenfodor.ppremotece.core.domain.result.Result
import com.greenfodor.ppremotece.core.domain.status.StatusEvent
import com.greenfodor.ppremotece.core.domain.status.StatusFrameParser
import com.greenfodor.ppremotece.core.domain.status.rejectedUrl
import com.greenfodor.ppremotece.core.domain.status.withoutRejected
import com.greenfodor.ppremotece.core.domain.timers.LiveTimer
import com.greenfodor.ppremotece.core.domain.timers.TimersRepository
import com.greenfodor.ppremotece.core.domain.timers.joinTimers
import com.greenfodor.ppremotece.core.domain.transport.TransportRepository
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
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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
 * [currentLook], and `prop_collections` frames [propCollections], and `transport/presentation/current`
 * and `transport/audio/current` frames [presentationTransport] and [audioTransport], and
 * `audio/playlists`, `audio/playlist/active` and `transport/audio/time` frames [audioPlaylists]
 * (and [audioPlaylistsRepeats] when the first such frame of a stream opened again repeats the tree
 * held),
 * [activeAudio] and [audioPosition], the position starting again as unknown whenever the audio
 * transport changes what it has loaded; the lists are
 * [Loadable.NotLoaded] until their first frame and keep their content across reconnects. An error
 * frame naming a subscribed url ([rejectedUrl]) removes that url from the subscriptions for the
 * reopened streams of this connection ([withoutRejected]), keeps the content it feeds
 * [Loadable.Unavailable] and is passed to [log] once; a rejected transport url leaves that transport
 * unavailable (the audio one without a position), and a rejected `audio/playlist/active` leaves no
 * active track. [requestLiveRead] makes the next chunk read `slide_index` and `playlist/active` again.
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
    LooksRepository,
    PropsRepository,
    TransportRepository,
    AudioRepository {
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

    private val propList = MutableStateFlow<Loadable<List<PropCollection>>>(Loadable.NotLoaded)
    override val propCollections: StateFlow<Loadable<List<PropCollection>>> = propList.asStateFlow()

    private val presentationLoaded = MutableStateFlow<Loadable<Transport>>(Loadable.NotLoaded)
    override val presentationTransport: StateFlow<Loadable<Transport>> = presentationLoaded.asStateFlow()

    private val audioLoaded = MutableStateFlow<Loadable<Transport>>(Loadable.NotLoaded)
    override val audioTransport: StateFlow<Loadable<Transport>> = audioLoaded.asStateFlow()

    private val audioPlaylistList = MutableStateFlow<Loadable<List<AudioNode>>>(Loadable.NotLoaded)
    override val audioPlaylists: StateFlow<Loadable<List<AudioNode>>> = audioPlaylistList.asStateFlow()

    private val audioRepeats = MutableStateFlow(0)
    override val audioPlaylistsRepeats: StateFlow<Int> = audioRepeats.asStateFlow()

    private val activeTrack = MutableStateFlow<ActiveAudio?>(null)
    override val activeAudio: StateFlow<ActiveAudio?> = activeTrack.asStateFlow()

    private val audioSeconds = MutableStateFlow<Double?>(null)
    override val audioPosition: StateFlow<Double?> = audioSeconds.asStateFlow()

    @Volatile
    private var subscriptions = SUBSCRIPTIONS

    private val liveReadRequested = AtomicBoolean()

    /** Reads `slide_index` and `playlist/active` again when the stream's next chunk arrives. */
    fun requestLiveRead() {
        liveReadRequested.set(true)
    }

    private val rejected: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override val liveState: StateFlow<LiveState> =
        channelFlow {
            var state = LiveState.Initial
            var itemPresentation: String? = null
            send(state)

            var attempt = 0
            while (true) {
                val parser = StatusFrameParser()
                var slideReadNeeded = true
                var audioTreeAwaited = audioPlaylistList.value is Loadable.Loaded
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
                                is StatusEvent.Timers -> timerList.load(event.timers, "timers", "timers/current")
                                is StatusEvent.TimerReadings -> timerReadings.value = event.readings
                                is StatusEvent.MacroCollections ->
                                    macroCollections.load(event.collections, "macro_collections")
                                is StatusEvent.Looks -> lookList.load(event.looks, "looks")
                                is StatusEvent.CurrentLook -> liveLook.value = event.look
                                is StatusEvent.PropCollections ->
                                    propList.load(event.collections, "prop_collections")
                                is StatusEvent.PresentationTransport ->
                                    presentationLoaded.load(event.transport, "transport/presentation/current")
                                is StatusEvent.AudioTransport -> {
                                    val loaded = audioLoaded.value.orNull()
                                    if (loaded != null && event.transport.uuid != loaded.uuid) audioSeconds.value = null
                                    audioLoaded.load(event.transport, *AUDIO_TRANSPORT_URLS)
                                }
                                is StatusEvent.AudioTime ->
                                    if (AUDIO_TRANSPORT_URLS.none { it in rejected }) audioSeconds.value = event.seconds
                                is StatusEvent.AudioPlaylists -> {
                                    val repeated = audioPlaylistList.value == Loadable.Loaded(event.nodes)
                                    if (audioTreeAwaited && repeated) audioRepeats.update { it + 1 }
                                    audioTreeAwaited = false
                                    audioPlaylistList.load(event.nodes, "audio/playlists")
                                }
                                is StatusEvent.ActiveAudioChanged -> activeTrack.value = event.active
                                is StatusEvent.Rejected -> event.messages.forEach(::reject)
                                else -> Unit
                            }
                        }
                        if (liveReadRequested.getAndSet(false)) slideReadNeeded = true
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
        val url = rejectedUrl(message) ?: return
        if (url !in subscriptions) return
        subscriptions = withoutRejected(subscriptions, message)
        rejected += url
        when (url) {
            "timers", "timers/current" -> timerList.value = Loadable.Unavailable
            "macro_collections" -> macroCollections.value = Loadable.Unavailable
            "looks" -> lookList.value = Loadable.Unavailable
            "prop_collections" -> propList.value = Loadable.Unavailable
            "audio/playlists" -> audioPlaylistList.value = Loadable.Unavailable
            "transport/presentation/current" -> presentationLoaded.value = Loadable.Unavailable
            in AUDIO_TRANSPORT_URLS -> {
                audioSeconds.value = null
                audioLoaded.value = Loadable.Unavailable
            }
            "audio/playlist/active" -> activeTrack.value = null
        }
        log(message)
    }

    /** Sets this list to [value] unless ProPresenter rejected one of the [urls] that feed it. */
    private fun <T> MutableStateFlow<Loadable<T>>.load(value: T, vararg urls: String) {
        if (urls.none { it in rejected }) this.value = Loadable.Loaded(value)
    }

    @OptIn(FlowPreview::class)
    private fun streamChunks() = client.statusUpdates(subscriptions).timeout(watchdogTimeout)

    private companion object {
        const val TAG = "LiveStream"
        val AUDIO_TRANSPORT_URLS = arrayOf("transport/audio/current", "transport/audio/time")
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
                "look/current",
                "prop_collections",
                "transport/presentation/current",
                "transport/audio/current",
                "transport/audio/time",
                "audio/playlists",
                "audio/playlist/active"
            )
    }
}
