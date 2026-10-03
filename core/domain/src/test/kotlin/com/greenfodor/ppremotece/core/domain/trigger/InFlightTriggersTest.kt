package com.greenfodor.ppremotece.core.domain.trigger

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class InFlightTriggersTest {
    private val triggers = InFlightTriggers()

    @Test
    fun `a call for a key in flight is ignored`() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        val runs = mutableListOf<String>()
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            triggers.run("a") {
                runs += "first"
                gate.await()
            }
        }

        val second = triggers.run("a") { runs += "second" }
        gate.complete(Unit)

        assertThat(second).isFalse()
        assertThat(first.await()).isTrue()
        assertThat(runs).containsExactly("first")
    }

    @Test
    fun `the key is free again after success`() = runBlocking<Unit> {
        var runs = 0

        triggers.run("a") { runs++ }
        val again = triggers.run("a") { runs++ }

        assertThat(again).isTrue()
        assertThat(runs).isEqualTo(2)
    }

    @Test
    fun `the key is free again after a failure`() = runBlocking<Unit> {
        var runs = 0

        runCatching { triggers.run("a") { error("failed") } }
        val again = triggers.run("a") { runs++ }

        assertThat(again).isTrue()
        assertThat(runs).isEqualTo(1)
    }

    @Test
    fun `different keys run together`() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        val runs = mutableListOf<String>()
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            triggers.run("a") {
                runs += "a"
                gate.await()
            }
        }

        val other = triggers.run("b") { runs += "b" }
        gate.complete(Unit)
        first.join()

        assertThat(other).isTrue()
        assertThat(runs).containsExactly("a", "b")
    }
}
