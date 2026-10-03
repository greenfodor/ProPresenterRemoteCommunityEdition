package com.greenfodor.ppremotece.core.designsystem.ui

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The app's failure messages: each posted message is delivered once, in order, to whoever collects
 * [messages]; a message posted while nothing collects waits for the next collector.
 */
class UiMessages {
    private val channel = Channel<UiText>(Channel.UNLIMITED)

    val messages: Flow<UiText> = channel.receiveAsFlow()

    fun post(message: UiText) {
        channel.trySend(message)
    }
}
