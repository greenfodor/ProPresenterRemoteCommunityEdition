package com.greenfodor.ppremotece.navigation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.feature.playlist.PlaylistsRoute
import com.greenfodor.ppremotece.feature.playlist.SlideGridRoute
import com.greenfodor.ppremotece.feature.remote.RemoteRoute
import org.junit.jupiter.api.Test

class TabStacksTest {
    private val grid1 = SlideGridRoute(playlistUuid = "p", itemIndex = 1)
    private val grid6 = SlideGridRoute(playlistUuid = "p", itemIndex = 6)
    private val initial = TabStacks.initial(presentationRoot = PlaylistsRoute, remoteRoot = RemoteRoute)

    @Test
    fun `opens on the presentation root`() {
        assertThat(initial.current).isEqualTo(ShellTab.PRESENTATION)
        assertThat(initial.displayed).containsExactly(PlaylistsRoute)
    }

    @Test
    fun `opening a detail replaces the open one`() {
        val stacks = initial.openDetail(grid1).openDetail(grid6)

        assertThat(stacks.presentation).containsExactly(PlaylistsRoute, grid6)
        assertThat(stacks.current).isEqualTo(ShellTab.PRESENTATION)
    }

    @Test
    fun `remote shows the presentation stack under the remote stack`() {
        val stacks = initial.openDetail(grid1).select(ShellTab.REMOTE)

        assertThat(stacks.current).isEqualTo(ShellTab.REMOTE)
        assertThat(stacks.displayed).containsExactly(PlaylistsRoute, grid1, RemoteRoute)
    }

    @Test
    fun `back from the remote root returns to presentation with its stack kept`() {
        val stacks = initial.openDetail(grid1).select(ShellTab.REMOTE).back()

        assertThat(stacks.current).isEqualTo(ShellTab.PRESENTATION)
        assertThat(stacks.displayed).containsExactly(PlaylistsRoute, grid1)
    }

    @Test
    fun `back on presentation pops its stack and stops at the root`() {
        val once = initial.openDetail(grid1).back()

        assertThat(once.displayed).containsExactly(PlaylistsRoute)
        assertThat(once.back()).isEqualTo(once)
    }

    @Test
    fun `switching tabs keeps each stack`() {
        val stacks = initial.openDetail(grid1).select(ShellTab.REMOTE).select(ShellTab.PRESENTATION)

        assertThat(stacks.displayed).containsExactly(PlaylistsRoute, grid1)
    }

    @Test
    fun `re-selecting presentation trims it to its root`() {
        val stacks = initial.openDetail(grid1).select(ShellTab.PRESENTATION)

        assertThat(stacks.current).isEqualTo(ShellTab.PRESENTATION)
        assertThat(stacks.displayed).containsExactly(PlaylistsRoute)
    }

    @Test
    fun `re-selecting remote trims only the remote stack`() {
        val stacks = initial.openDetail(grid1).select(ShellTab.REMOTE).select(ShellTab.REMOTE)

        assertThat(stacks.remote).containsExactly(RemoteRoute)
        assertThat(stacks.presentation).containsExactly(PlaylistsRoute, grid1)
    }
}
