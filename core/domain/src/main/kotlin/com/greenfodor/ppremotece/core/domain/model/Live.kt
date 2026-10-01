package com.greenfodor.ppremotece.core.domain.model

/** The cue ProPresenter is showing: its index within the live item's cue list. */
data class LiveSlide(
    val presentationUuid: String,
    val index: Int,
    val totalCues: Int
)

/** The text of the live slide and of the one after it, as `status/slide` reports them. */
data class SlideText(
    val current: String,
    val next: String
)

/** A cue that was live: its playlist item or presentation, the presentation it played and the cue index. */
data class LiveCue(
    val source: CueSource,
    val presentationUuid: String,
    val cueIndex: Int
)

data class LiveState(
    val connection: ConnectionStatus,
    val item: PlaylistItemKey?,
    val slide: LiveSlide?,
    val slideText: SlideText? = null,
    val layers: Set<OutputLayer> = emptySet()
) {
    companion object {
        val Initial = LiveState(connection = ConnectionStatus.CONNECTING, item = null, slide = null)
    }
}

enum class ConnectionStatus {
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

data class ProPresenterVersion(
    val name: String,
    val hostDescription: String,
    val apiVersion: String
)

data class ProPresenterHost(
    val name: String,
    val address: String,
    val port: Int
)

/** The connected [host] and the [version] it reported on connect. */
data class ConnectedHost(
    val host: ProPresenterHost,
    val version: ProPresenterVersion
)
