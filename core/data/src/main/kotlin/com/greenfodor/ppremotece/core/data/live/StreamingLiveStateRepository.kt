package com.greenfodor.ppremotece.core.data.live

import com.greenfodor.ppremotece.core.data.network.KtorProPresenterClient
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.model.ConnectionStatus
import com.greenfodor.ppremotece.core.domain.model.LiveState
import com.greenfodor.ppremotece.core.domain.result.onSuccess
import com.greenfodor.ppremotece.core.domain.status.StatusEvent
import com.greenfodor.ppremotece.core.domain.status.StatusFrameParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
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
 * earlier read failed, `slide_index` is read once. Any chunk resets the watchdog. When no chunk
 * arrives for [watchdogTimeout], or the stream ends or fails, the stream is closed and reopened
 * after [reconnectDelay] with the same subscriptions, and the slide index is read again.
 */
class StreamingLiveStateRepository(
    private val client: KtorProPresenterClient,
    scope: CoroutineScope,
    private val watchdogTimeout: Duration = 10.seconds,
    private val reconnectDelay: (attempt: Int) -> Duration = ::defaultReconnectDelay
) : LiveStateRepository {
    override val liveState: StateFlow<LiveState> =
        channelFlow {
            var state = LiveState.Initial
            send(state)

            var attempt = 0
            while (true) {
                val parser = StatusFrameParser()
                var slideReadNeeded = true
                val failure = runCatching {
                    streamChunks().collect { chunk ->
                        attempt = 0
                        var next = state.copy(connection = ConnectionStatus.CONNECTED)
                        for (event in parser.events(chunk)) {
                            when (event) {
                                StatusEvent.SlideChanged -> slideReadNeeded = true
                                is StatusEvent.PlaylistActive -> if (event.item != next.item) {
                                    next = next.copy(item = event.item)
                                    slideReadNeeded = true
                                }
                                else -> Unit
                            }
                        }
                        if (slideReadNeeded) {
                            client.slideIndex().onSuccess {
                                next = next.copy(slide = it)
                                slideReadNeeded = false
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

    @OptIn(FlowPreview::class)
    private fun streamChunks() = client.statusUpdates(SUBSCRIPTIONS).timeout(watchdogTimeout)

    private companion object {
        val SUBSCRIPTIONS = listOf("status/slide", "timer/system_time", "playlist/active")
    }
}
