package com.greenfodor.ppremotece.core.domain.timers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.domain.model.CountDownTarget
import com.greenfodor.ppremotece.core.domain.model.Timer
import com.greenfodor.ppremotece.core.domain.model.TimerReading
import com.greenfodor.ppremotece.core.domain.model.TimerState
import com.greenfodor.ppremotece.core.domain.model.TimerType
import org.junit.jupiter.api.Test

class TimerTargetTest {
    private val evening = CountDownTarget(timeOfDaySeconds = 30600, period = "pm")
    private val noonish = CountDownTarget(timeOfDaySeconds = 42600, period = "is_24_hour")

    private fun toTime(target: CountDownTarget? = evening) =
        Timer("t-0", "Timer 01", 0, TimerType.COUNTDOWN_TO_TIME, allowsOverrun = true, target = target)

    private fun reading(state: TimerState, time: String = "08:23:24") = TimerReading("t-0", time, state)

    @Test
    fun `am and pm targets read on a 12-hour clock`() {
        assertThat(targetLabel(evening)).isEqualTo("8:30 PM")
        assertThat(targetLabel(CountDownTarget(30600, "am"))).isEqualTo("8:30 AM")
        assertThat(targetLabel(CountDownTarget(0, "am"))).isEqualTo("12:00 AM")
        assertThat(targetLabel(CountDownTarget(0, "pm"))).isEqualTo("12:00 PM")
    }

    @Test
    fun `24-hour and unknown periods read on a 24-hour clock`() {
        assertThat(targetLabel(noonish)).isEqualTo("11:50")
        assertThat(targetLabel(CountDownTarget(42600, "24_hour"))).isEqualTo("11:50")
        assertThat(targetLabel(CountDownTarget(32700, "is_24_hour"))).isEqualTo("09:05")
        assertThat(targetLabel(CountDownTarget(75600, "sometime"))).isEqualTo("21:00")
    }

    @Test
    fun `a stopped count-down-to-time shows its target`() {
        assertThat(timerCard(toTime(), reading(TimerState.STOPPED)).target).isEqualTo("8:30 PM")
        assertThat(timerCard(toTime(noonish), reading(TimerState.STOPPED, "11:50:00")).target).isEqualTo("11:50")
    }

    @Test
    fun `running, overrunning and overran count-down-to-time timers show the readout`() {
        listOf(TimerState.RUNNING, TimerState.OVERRUNNING, TimerState.OVERRAN).forEach {
            assertThat(timerCard(toTime(), reading(it)).target).isNull()
        }
    }

    @Test
    fun `a stopped count-down-to-time with a negative readout shows the readout as overrun`() {
        val card = timerCard(toTime(noonish), reading(TimerState.STOPPED, "-00:34:47"))

        assertThat(card.target).isNull()
        assertThat(card.readout).isEqualTo("-00:34:47")
        assertThat(card.overrun).isTrue()
    }

    @Test
    fun `no target is shown without a configured target or a reading`() {
        assertThat(timerCard(toTime(target = null), reading(TimerState.STOPPED)).target).isNull()
        assertThat(timerCard(toTime(), null).target).isNull()
    }

    @Test
    fun `a timer of an unknown type shows the timer icon and its readout`() {
        val unknown = Timer("t-9", "Timer 09", 9, TimerType.UNKNOWN, allowsOverrun = false)

        val card = timerCard(unknown, reading(TimerState.STOPPED, "00:01:00"))

        assertThat(card.icon).isEqualTo(TimerIcon.TIMER)
        assertThat(card.readout).isEqualTo("00:01:00")
        assertThat(card.target).isNull()
        assertThat(card.overrun).isFalse()
    }
}
