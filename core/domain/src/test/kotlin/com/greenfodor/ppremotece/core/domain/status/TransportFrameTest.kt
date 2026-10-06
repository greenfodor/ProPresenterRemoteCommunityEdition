package com.greenfodor.ppremotece.core.domain.status

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.domain.model.Transport
import org.junit.jupiter.api.Test

class TransportFrameTest {
    private val parser = StatusFrameParser()

    @Test
    fun `a presentation transport frame decodes to the presentation transport`() {
        val frame = """{"url":"transport/presentation/current","data":{"is_playing":true,""" +
            """"uuid":"2ca780f1-d07f-40c3-b855-5ade46ba2eb9","name":"Media 01","artist":"",""" +
            """"audio_only":false,"duration":20.0333328}}"""

        assertThat(parser.decode(frame)).isEqualTo(
            StatusEvent.PresentationTransport(
                Transport(
                    isPlaying = true,
                    uuid = "2ca780f1-d07f-40c3-b855-5ade46ba2eb9",
                    name = "Media 01",
                    artist = "",
                    audioOnly = false,
                    durationSeconds = 20.0333328
                )
            )
        )
    }

    @Test
    fun `an audio transport frame decodes to the audio transport`() {
        val frame = """{"url":"transport/audio/current","data":{"is_playing":true,""" +
            """"uuid":"28bee335-2a94-4751-b0fd-26592d6dd209","name":"Media 03","artist":"Artist 01",""" +
            """"audio_only":true,"duration":183.5039978}}"""

        assertThat(parser.decode(frame)).isEqualTo(
            StatusEvent.AudioTransport(
                Transport(
                    isPlaying = true,
                    uuid = "28bee335-2a94-4751-b0fd-26592d6dd209",
                    name = "Media 03",
                    artist = "Artist 01",
                    audioOnly = true,
                    durationSeconds = 183.5039978
                )
            )
        )
    }

    @Test
    fun `an empty transport frame decodes to a transport with nothing loaded`() {
        val frame = """{"url":"transport/audio/current","data":{"is_playing":false,"uuid":"","name":"",""" +
            """"artist":"","audio_only":false,"duration":0.0}}"""

        assertThat(parser.decode(frame)).isEqualTo(
            StatusEvent.AudioTransport(Transport(false, "", "", "", audioOnly = false, durationSeconds = 0.0))
        )
    }

    @Test
    fun `a transport frame without an object is unknown`() {
        assertThat(parser.decode("""{"url":"transport/audio/current","data":null}"""))
            .isEqualTo(StatusEvent.Unknown("transport/audio/current"))
    }
}
