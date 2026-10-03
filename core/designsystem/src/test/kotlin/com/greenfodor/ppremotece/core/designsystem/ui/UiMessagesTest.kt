package com.greenfodor.ppremotece.core.designsystem.ui

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test

class UiMessagesTest {
    private val messages = UiMessages()

    @Test
    fun `a message posted with no collector is delivered to the next collector once`() = runBlocking<Unit> {
        val message = UiText.DynamicString("Couldn't run Macro 01")

        messages.post(message)

        assertThat(messages.messages.first()).isEqualTo(message)
        assertThat(withTimeoutOrNull(100) { messages.messages.first() }).isNull()
    }

    @Test
    fun `messages are delivered in the order they were posted`() = runBlocking<Unit> {
        val first = UiText.DynamicString("first")
        val second = UiText.DynamicString("second")

        messages.post(first)
        messages.post(second)

        assertThat(messages.messages.take(2).toList()).containsExactly(first, second)
    }
}
