package com.greenfodor.ppremotece.core.domain.status

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.domain.model.ActiveAudio
import com.greenfodor.ppremotece.core.domain.model.AudioPlaylist
import org.junit.jupiter.api.Test

class AudioFrameTest {
    private val parser = StatusFrameParser()

    @Test
    fun `an audio time frame carries the position in seconds`() {
        assertThat(parser.decode("""{"url":"transport/audio/time","data":4.305506499963813}"""))
            .isEqualTo(StatusEvent.AudioTime(4.305506499963813))
        assertThat(parser.decode("""{"url":"transport/audio/time","data":0}""")).isEqualTo(StatusEvent.AudioTime(0.0))
    }

    @Test
    fun `an audio time frame without a number is unknown`() {
        assertThat(parser.decode("""{"url":"transport/audio/time","data":"soon"}"""))
            .isEqualTo(StatusEvent.Unknown("transport/audio/time"))
    }

    @Test
    fun `audio playlists are listed in order`() {
        val frame = """{"url":"audio/playlists","data":[""" +
            """{"id":{"uuid":"p-0","name":"Audio Playlist 01","index":0},"type":"playlist","children":[]},""" +
            """{"id":{"uuid":"p-1","name":"Audio Playlist 02","index":1},"type":"playlist","children":[]}]}"""

        assertThat(parser.decode(frame)).isEqualTo(
            StatusEvent.AudioPlaylists(
                listOf(AudioPlaylist("p-0", "Audio Playlist 01", 0), AudioPlaylist("p-1", "Audio Playlist 02", 1))
            )
        )
    }

    @Test
    fun `nested audio playlists are flattened in tree order and folders are left out`() {
        val frame = """{"url":"audio/playlists","data":[""" +
            """{"id":{"uuid":"f-0","name":"Folder 01","index":0},"type":"group","children":[""" +
            """{"id":{"uuid":"p-0","name":"Audio Playlist 01","index":0},"type":"playlist","children":[]},""" +
            """{"id":{"uuid":"f-1","name":"Folder 02","index":1},"type":"group","children":[""" +
            """{"id":{"uuid":"p-1","name":"Audio Playlist 02","index":0},"type":"playlist","children":[]}]}]},""" +
            """{"id":{"uuid":"p-2","name":"Audio Playlist 03","index":1},"type":"playlist","children":[]}]}"""

        val playlists = (parser.decode(frame) as StatusEvent.AudioPlaylists).playlists

        assertThat(playlists.map { it.uuid }).isEqualTo(listOf("p-0", "p-1", "p-2"))
    }

    @Test
    fun `the active audio frame names the playlist and its track`() {
        val frame = """{"url":"audio/playlist/active","data":{""" +
            """"playlist":{"uuid":"p-3","name":"Audio Playlist 04","index":3},""" +
            """"item":{"uuid":"t-2","name":"Track 01","index":2}}}"""

        assertThat(parser.decode(frame)).isEqualTo(StatusEvent.ActiveAudioChanged(ActiveAudio("p-3", "t-2", 2)))
    }

    @Test
    fun `the active audio frame with nothing active carries no track`() {
        assertThat(parser.decode("""{"url":"audio/playlist/active","data":{"playlist":null,"item":null}}"""))
            .isEqualTo(StatusEvent.ActiveAudioChanged(null))
    }
}
