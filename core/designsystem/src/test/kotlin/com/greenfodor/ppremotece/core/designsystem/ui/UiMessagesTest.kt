package com.greenfodor.ppremotece.core.designsystem.ui

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

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

        val received = CopyOnWriteArrayList<UiText>()
        val collector = launch { messages.messages.collect { received += it } }
        messages.post(first)
        withTimeout(1_000) { while (received.size < 1) delay(5) }
        messages.post(second)
        withTimeout(1_000) { while (received.size < 2) delay(5) }
        collector.cancel()

        assertThat(received.toList()).containsExactly(first, second)
    }
}
