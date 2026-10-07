package com.greenfodor.ppremotece.core.domain.status

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.domain.model.ActiveAudio
import com.greenfodor.ppremotece.core.domain.model.AudioFolder
import com.greenfodor.ppremotece.core.domain.model.AudioPlaylist
import com.greenfodor.ppremotece.core.domain.model.playlists
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
    fun `root audio playlists are listed in order`() {
        val frame = frame(playlist("p-0", "Audio Playlist 01", 0), playlist("p-1", "Audio Playlist 02", 1))

        assertThat(parser.decode(frame)).isEqualTo(
            StatusEvent.AudioPlaylists(
                listOf(AudioPlaylist("p-0", "Audio Playlist 01"), AudioPlaylist("p-1", "Audio Playlist 02"))
            )
        )
    }

    @Test
    fun `a folder keeps its playlists as children`() {
        val frame = frame(
            playlist("p-0", "Audio Playlist 01", 0),
            folder(
                "f-0",
                "Folder A",
                1,
                playlist("p-1", "Audio Playlist 02", 0),
                playlist("p-2", "Audio Playlist 03", 1)
            )
        )

        assertThat(tree(frame)).isEqualTo(
            listOf(
                AudioPlaylist("p-0", "Audio Playlist 01"),
                AudioFolder(
                    "f-0",
                    "Folder A",
                    listOf(AudioPlaylist("p-1", "Audio Playlist 02"), AudioPlaylist("p-2", "Audio Playlist 03"))
                )
            )
        )
    }

    @Test
    fun `a nested folder is kept inside its folder`() {
        val frame =
            frame(folder("f-0", "Folder A", 0, folder("f-1", "Folder B", 0, playlist("p-0", "Audio Playlist 01", 0))))

        assertThat(tree(frame)).isEqualTo(
            listOf(
                AudioFolder(
                    "f-0",
                    "Folder A",
                    listOf(AudioFolder("f-1", "Folder B", listOf(AudioPlaylist("p-0", "Audio Playlist 01"))))
                )
            )
        )
    }

    @Test
    fun `an empty folder is kept without children`() {
        assertThat(
            tree(frame(folder("f-0", "Folder A", 0)))
        ).isEqualTo(listOf(AudioFolder("f-0", "Folder A", emptyList())))
    }

    @Test
    fun `a playlist with no tracks is a playlist like any other`() {
        val frame = frame(folder("f-0", "Folder A", 0, playlist("p-0", "Audio Playlist 01", 0)))

        assertThat(tree(frame).playlists()).isEqualTo(listOf(AudioPlaylist("p-0", "Audio Playlist 01")))
    }

    @Test
    fun `playlists whose indexes repeat across folders stay apart by uuid`() {
        val frame = frame(
            playlist("p-0", "Audio Playlist 01", 0),
            folder(
                "f-0",
                "Folder A",
                1,
                playlist("p-1", "Audio Playlist 02", 0),
                folder("f-1", "Folder B", 1, playlist("p-2", "Audio Playlist 03", 0))
            )
        )

        assertThat(tree(frame).playlists().map { it.uuid }).isEqualTo(listOf("p-0", "p-1", "p-2"))
    }

    private fun tree(frame: String) = (parser.decode(frame) as StatusEvent.AudioPlaylists).nodes

    private fun frame(vararg nodes: String) = """{"url":"audio/playlists","data":[${nodes.joinToString(",")}]}"""

    private fun playlist(uuid: String, name: String, index: Int) =
        """{"id":{"uuid":"$uuid","name":"$name","index":$index},"type":"playlist","children":[]}"""

    private fun folder(uuid: String, name: String, index: Int, vararg children: String) =
        """{"id":{"uuid":"$uuid","name":"$name","index":$index},"type":"group","children":[${children.joinToString(
            ","
        )}]}"""

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
