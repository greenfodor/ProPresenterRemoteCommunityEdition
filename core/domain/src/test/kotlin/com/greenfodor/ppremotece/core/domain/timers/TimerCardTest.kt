package com.greenfodor.ppremotece.core.domain.timers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.domain.model.Timer
import com.greenfodor.ppremotece.core.domain.model.TimerOperation
import com.greenfodor.ppremotece.core.domain.model.TimerReading
import com.greenfodor.ppremotece.core.domain.model.TimerState
import com.greenfodor.ppremotece.core.domain.model.TimerType
import org.junit.jupiter.api.Test

class TimerCardTest {
    private fun timer(type: TimerType = TimerType.COUNTDOWN) = Timer("t-0", "Timer 01", 0, type, allowsOverrun = true)

    private fun reading(state: TimerState, time: String = "00:05:00") = TimerReading("t-0", time, state)

    @Test
    fun `each type has its icon`() {
        assertThat(timerCard(timer(TimerType.COUNTDOWN), null).icon).isEqualTo(TimerIcon.TIMER)
        assertThat(timerCard(timer(TimerType.COUNTDOWN_TO_TIME), null).icon).isEqualTo(TimerIcon.ALARM)
        assertThat(timerCard(timer(TimerType.ELAPSED), null).icon).isEqualTo(TimerIcon.AVG_PACE)
    }

    @Test
    fun `the readout is the server's time as sent`() {
        assertThat(timerCard(timer(), reading(TimerState.RUNNING, "17:05:22")).readout).isEqualTo("17:05:22")
        assertThat(timerCard(timer(), reading(TimerState.STOPPED, "00:00:01.00")).readout).isEqualTo("00:00:01.00")
        assertThat(timerCard(timer(), null).readout).isEqualTo("")
    }

    @Test
    fun `running and overrunning timers are running`() {
        assertThat(timerCard(timer(), reading(TimerState.RUNNING)).running).isTrue()
        assertThat(timerCard(timer(), reading(TimerState.OVERRUNNING)).running).isTrue()
        listOf(TimerState.STOPPED, TimerState.COMPLETE, TimerState.OVERRAN, TimerState.UNKNOWN).forEach {
            assertThat(timerCard(timer(), reading(it)).running).isFalse()
        }
        assertThat(timerCard(timer(), null).running).isFalse()
    }

    @Test
    fun `overrunning, overran and a negative time are overrun`() {
        assertThat(timerCard(timer(), reading(TimerState.OVERRUNNING)).overrun).isTrue()
        assertThat(timerCard(timer(), reading(TimerState.OVERRAN)).overrun).isTrue()
        assertThat(timerCard(timer(), reading(TimerState.RUNNING, "-00:00:02")).overrun).isTrue()
        assertThat(timerCard(timer(), reading(TimerState.RUNNING, "00:00:02")).overrun).isFalse()
        assertThat(timerCard(timer(), reading(TimerState.COMPLETE, "00:00:00")).overrun).isFalse()
    }

    @Test
    fun `the toggle stops a running timer and starts any other`() {
        assertThat(timerCard(timer(), reading(TimerState.RUNNING)).toggle).isEqualTo(TimerOperation.STOP)
        assertThat(timerCard(timer(), reading(TimerState.OVERRUNNING)).toggle).isEqualTo(TimerOperation.STOP)
        assertThat(timerCard(timer(), reading(TimerState.STOPPED)).toggle).isEqualTo(TimerOperation.START)
        assertThat(timerCard(timer(), reading(TimerState.OVERRAN)).toggle).isEqualTo(TimerOperation.START)
        assertThat(timerCard(timer(), null).toggle).isEqualTo(TimerOperation.START)
    }

    @Test
    fun `a timer listed twice is joined once`() {
        val first = timer()
        val repeated = first.copy(name = "Timer 01 again")

        assertThat(joinTimers(listOf(first, repeated), emptyList())).isEqualTo(listOf(LiveTimer(first, null)))
    }

    @Test
    fun `timers are joined with their readings by uuid in timer order`() {
        val first = timer()
        val second = Timer("t-1", "Timer 02", 1, TimerType.ELAPSED, allowsOverrun = false)
        val readings =
            listOf(TimerReading("t-1", "00:00:03", TimerState.RUNNING), TimerReading("t-9", "", TimerState.STOPPED))

        assertThat(joinTimers(listOf(first, second), readings)).isEqualTo(
            listOf(LiveTimer(first, null), LiveTimer(second, readings[0]))
        )
    }
}
