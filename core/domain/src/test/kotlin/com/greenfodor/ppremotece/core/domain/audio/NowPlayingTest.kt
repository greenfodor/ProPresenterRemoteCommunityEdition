package com.greenfodor.ppremotece.core.domain.audio

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.domain.live.Loadable
import com.greenfodor.ppremotece.core.domain.model.ActiveAudio
import com.greenfodor.ppremotece.core.domain.model.Transport
import org.junit.jupiter.api.Test

class NowPlayingTest {
    private val active = ActiveAudio(playlistUuid = "pl", trackUuid = "t-2", trackIndex = 2)

    private fun transport(isPlaying: Boolean = true, uuid: String = "a-0", duration: Double = 138.4) =
        Loadable.Loaded(
            Transport(
                isPlaying,
                uuid,
                name = "Media 04",
                artist = "Artist 02",
                audioOnly = true,
                durationSeconds = duration
            )
        )

    @Test
    fun `with nothing loaded every button is disabled and nothing is read out`() {
        val unknown = nowPlaying(transport = Loadable.NotLoaded, positionSeconds = null, active = null)
        val cleared = nowPlaying(transport(isPlaying = false, uuid = "", duration = 0.0), positionSeconds = 0.0, active)

        assertThat(unknown).isEqualTo(NowPlaying.Nothing)
        assertThat(cleared).isEqualTo(NowPlaying.Nothing)
        assertThat(unknown.loaded).isFalse()
        assertThat(unknown.playPauseEnabled).isFalse()
        assertThat(unknown.skipEnabled).isFalse()
    }

    @Test
    fun `while playing the button pauses`() {
        val bar = nowPlaying(transport(isPlaying = true), positionSeconds = 41.3, active)

        assertThat(bar.loaded).isTrue()
        assertThat(bar.name).isEqualTo("Media 04")
        assertThat(bar.button).isEqualTo(TransportButton.PAUSE)
        assertThat(bar.playPauseEnabled).isTrue()
    }

    @Test
    fun `while paused the button plays`() {
        val bar = nowPlaying(transport(isPlaying = false), positionSeconds = 41.3, active)

        assertThat(bar.button).isEqualTo(TransportButton.PLAY)
        assertThat(bar.playPauseEnabled).isTrue()
    }

    @Test
    fun `previous and next need an active audio playlist`() {
        assertThat(nowPlaying(transport(), positionSeconds = 1.0, active).skipEnabled).isTrue()
        assertThat(nowPlaying(transport(), positionSeconds = 1.0, active = null).skipEnabled).isFalse()
        assertThat(nowPlaying(transport(), positionSeconds = 1.0, active = null).playPauseEnabled).isTrue()
    }

    @Test
    fun `the readout is the position over the duration`() {
        assertThat(nowPlaying(transport(), positionSeconds = 41.3, active).readout).isEqualTo("0:41 / 2:18")
        assertThat(nowPlaying(transport(), positionSeconds = null, active).readout).isEqualTo("0:00 / 2:18")
    }

    @Test
    fun `the progress is the position over the duration, clamped to the bar`() {
        assertThat(nowPlaying(transport(duration = 200.0), positionSeconds = 50.0, active).progress).isEqualTo(0.25f)
        assertThat(nowPlaying(transport(duration = 200.0), positionSeconds = 250.0, active).progress).isEqualTo(1f)
        assertThat(nowPlaying(transport(duration = 200.0), positionSeconds = -3.0, active).progress).isEqualTo(0f)
        assertThat(nowPlaying(transport(duration = 0.0), positionSeconds = 5.0, active).progress).isEqualTo(0f)
    }

    @Test
    fun `an unavailable transport gives the unavailable bar with every button disabled`() {
        val bar = nowPlaying(Loadable.Unavailable, positionSeconds = 41.3, active)

        assertThat(bar).isEqualTo(NowPlaying.Unavailable)
        assertThat(bar.available).isFalse()
        assertThat(bar.playPauseEnabled).isFalse()
        assertThat(bar.skipEnabled).isFalse()
        assertThat(NowPlaying.Nothing.available).isTrue()
    }
}
