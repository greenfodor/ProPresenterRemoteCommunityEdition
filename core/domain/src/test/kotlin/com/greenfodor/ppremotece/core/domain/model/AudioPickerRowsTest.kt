package com.greenfodor.ppremotece.core.domain.model

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class AudioPickerRowsTest {
    private val first = AudioPlaylist("p-0", "Audio Playlist 01")
    private val second = AudioPlaylist("p-1", "Audio Playlist 02")
    private val third = AudioPlaylist("p-2", "Audio Playlist 03")

    @Test
    fun `root playlists are rows at depth zero`() {
        assertThat(listOf(first, second).pickerRows()).isEqualTo(
            listOf(AudioPickerRow.Playlist(first, depth = 0), AudioPickerRow.Playlist(second, depth = 0))
        )
    }

    @Test
    fun `a folder is a heading and its playlists sit one level deeper`() {
        val tree = listOf(first, AudioFolder("f-0", "Folder A", listOf(second, third)))

        assertThat(tree.pickerRows()).isEqualTo(
            listOf(
                AudioPickerRow.Playlist(first, depth = 0),
                AudioPickerRow.Heading("f-0", "Folder A", depth = 0),
                AudioPickerRow.Playlist(second, depth = 1),
                AudioPickerRow.Playlist(third, depth = 1)
            )
        )
    }

    @Test
    fun `a nested folder is a heading one level deeper and its playlists deeper again`() {
        val nested = AudioFolder("f-1", "Folder B", listOf(second))
        val tree = listOf(AudioFolder("f-0", "Folder A", listOf(first, nested, third)))

        assertThat(tree.pickerRows()).isEqualTo(
            listOf(
                AudioPickerRow.Heading("f-0", "Folder A", depth = 0),
                AudioPickerRow.Playlist(first, depth = 1),
                AudioPickerRow.Heading("f-1", "Folder B", depth = 1),
                AudioPickerRow.Playlist(second, depth = 2),
                AudioPickerRow.Playlist(third, depth = 1)
            )
        )
    }

    @Test
    fun `a folder with no playlist anywhere beneath it is dropped`() {
        val empty = AudioFolder("f-0", "Folder A", emptyList())
        val holdingAnEmptyFolder = AudioFolder("f-1", "Folder B", listOf(AudioFolder("f-2", "Folder A", emptyList())))

        assertThat(listOf(empty, first, holdingAnEmptyFolder).pickerRows())
            .isEqualTo(listOf(AudioPickerRow.Playlist(first, depth = 0)))
        assertThat(listOf(empty).pickerRows()).isEmpty()
    }

    @Test
    fun `a folder holding only a nested folder with a playlist keeps both headings`() {
        val tree = listOf(AudioFolder("f-0", "Folder A", listOf(AudioFolder("f-1", "Folder B", listOf(first)))))

        assertThat(tree.pickerRows()).isEqualTo(
            listOf(
                AudioPickerRow.Heading("f-0", "Folder A", depth = 0),
                AudioPickerRow.Heading("f-1", "Folder B", depth = 1),
                AudioPickerRow.Playlist(first, depth = 2)
            )
        )
    }

    @Test
    fun `the playlists of a tree are listed in tree order`() {
        val nested = AudioFolder("f-1", "Folder B", listOf(second))
        val tree = listOf(AudioFolder("f-0", "Folder A", listOf(first, nested)), third)

        assertThat(tree.playlists()).isEqualTo(listOf(first, second, third))
    }
}
