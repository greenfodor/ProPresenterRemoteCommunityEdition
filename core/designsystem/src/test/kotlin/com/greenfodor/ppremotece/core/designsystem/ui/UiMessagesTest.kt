package com.greenfodor.ppremotece.core.designsystem.ui

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
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
    fun `of two messages posted with no collector the next collector receives the second only`() = runBlocking<Unit> {
        val first = UiText.DynamicString("first")
        val second = UiText.DynamicString("second")

        messages.post(first)
        messages.post(second)

        assertThat(messages.messages.first()).isEqualTo(second)
        assertThat(withTimeoutOrNull(100) { messages.messages.first() }).isNull()
    }

    @Test
    fun `a message posted while one collects is delivered`() = runBlocking<Unit> {
        val first = UiText.DynamicString("first")
        val second = UiText.DynamicString("second")

        val received = mutableListOf<UiText>()
        val collector = launch(Dispatchers.Unconfined) { messages.messages.collect { received += it } }

        messages.post(first)
        yield()
        assertThat(received.toList()).containsExactly(first)
        messages.post(second)
        yield()
        assertThat(received.toList()).containsExactly(first, second)
        collector.cancel()
    }
}
