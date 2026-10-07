package com.greenfodor.ppremotece.core.designsystem.ui

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The app's failure messages: each posted message is delivered once to whoever collects [messages].
 * At most one message waits to be delivered: a newer one replaces it.
 */
class UiMessages {
    private val channel = Channel<UiText>(Channel.CONFLATED)

    val messages: Flow<UiText> = channel.receiveAsFlow()

    fun post(message: UiText) {
        channel.trySend(message)
    }
}
