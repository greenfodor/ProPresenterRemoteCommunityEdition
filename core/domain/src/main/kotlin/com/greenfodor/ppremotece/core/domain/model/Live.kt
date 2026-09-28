package com.greenfodor.ppremotece.core.domain.model

/** The cue ProPresenter is showing: its index within the live item's cue list. */
data class LiveSlide(
    val presentationUuid: String,
    val index: Int,
    val totalCues: Int
)

data class LiveState(
    val connection: ConnectionStatus,
    val item: PlaylistItemKey?,
    val slide: LiveSlide?
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
