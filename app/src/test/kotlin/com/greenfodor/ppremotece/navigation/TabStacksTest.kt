package com.greenfodor.ppremotece.navigation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.domain.layout.ShellTab
import com.greenfodor.ppremotece.feature.playlist.LibraryGridRoute
import com.greenfodor.ppremotece.feature.playlist.PlaylistsRoute
import com.greenfodor.ppremotece.feature.playlist.SlideGridRoute
import com.greenfodor.ppremotece.feature.remote.RemoteRoute
import com.greenfodor.ppremotece.feature.settings.MoreRoute
import com.greenfodor.ppremotece.feature.settings.SettingsRoute
import org.junit.jupiter.api.Test

class TabStacksTest {
    private val grid1 = SlideGridRoute(playlistUuid = "p", itemIndex = 1)
    private val grid6 = SlideGridRoute(playlistUuid = "p", itemIndex = 6)
    private val initial = TabStacks.initial(
        mapOf(
            ShellTab.PRESENTATION to PlaylistsRoute,
            ShellTab.REMOTE to RemoteRoute,
            ShellTab.SETTINGS to SettingsRoute,
            ShellTab.MORE to MoreRoute
        )
    )
    private val noneInMore = emptySet<ShellTab>()
    private val remoteInMore = setOf(ShellTab.REMOTE, ShellTab.SETTINGS)

    @Test
    fun `opens on the presentation root`() {
        assertThat(initial.current).isEqualTo(ShellTab.PRESENTATION)
        assertThat(initial.displayed).containsExactly(PlaylistsRoute)
    }

    @Test
    fun `opening a detail replaces the open one`() {
        val stacks = initial.openDetail(grid1).openDetail(grid6)

        assertThat(stacks.stack(ShellTab.PRESENTATION)).containsExactly(PlaylistsRoute, grid6)
        assertThat(stacks.current).isEqualTo(ShellTab.PRESENTATION)
    }

    @Test
    fun `a library grid replaces an open slide grid and the other way round`() {
        val library = LibraryGridRoute(presentationUuid = "pres")

        assertThat(initial.openDetail(grid1).openDetail(library).stack(ShellTab.PRESENTATION))
            .containsExactly(PlaylistsRoute, library)
        assertThat(initial.openDetail(library).openDetail(grid6).stack(ShellTab.PRESENTATION))
            .containsExactly(PlaylistsRoute, grid6)
        assertThat(initial.openDetail(library).back(noneInMore).stack(ShellTab.PRESENTATION))
            .containsExactly(PlaylistsRoute)
    }

    @Test
    fun `remote shows the presentation stack under the remote stack`() {
        val stacks = initial.openDetail(grid1).select(ShellTab.REMOTE)

        assertThat(stacks.current).isEqualTo(ShellTab.REMOTE)
        assertThat(stacks.displayed).containsExactly(PlaylistsRoute, grid1, RemoteRoute)
    }

    @Test
    fun `back from the remote root returns to presentation with its stack kept`() {
        val stacks = initial.openDetail(grid1).select(ShellTab.REMOTE).back(noneInMore)

        assertThat(stacks.current).isEqualTo(ShellTab.PRESENTATION)
        assertThat(stacks.displayed).containsExactly(PlaylistsRoute, grid1)
    }

    @Test
    fun `back on presentation pops its stack and stops at the root`() {
        val once = initial.openDetail(grid1).back(noneInMore)

        assertThat(once.displayed).containsExactly(PlaylistsRoute)
        assertThat(once.back(noneInMore)).isEqualTo(once)
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

        assertThat(stacks.stack(ShellTab.REMOTE)).containsExactly(RemoteRoute)
        assertThat(stacks.stack(ShellTab.PRESENTATION)).containsExactly(PlaylistsRoute, grid1)
    }

    @Test
    fun `settings shows the presentation stack under the settings stack and back returns to presentation`() {
        val stacks = initial.openDetail(grid1).select(ShellTab.SETTINGS)

        assertThat(stacks.displayed).containsExactly(PlaylistsRoute, grid1, SettingsRoute)
        assertThat(stacks.back(noneInMore).current).isEqualTo(ShellTab.PRESENTATION)
        assertThat(stacks.back(noneInMore).displayed).containsExactly(PlaylistsRoute, grid1)
    }

    @Test
    fun `a more row selects that destination's own tab and more stays highlighted`() {
        val stacks = initial.openDetail(grid1).select(ShellTab.MORE).select(ShellTab.REMOTE)

        assertThat(stacks.current).isEqualTo(ShellTab.REMOTE)
        assertThat(stacks.displayed).containsExactly(PlaylistsRoute, grid1, RemoteRoute)
        assertThat(stacks.highlighted(remoteInMore)).isEqualTo(ShellTab.MORE)
        assertThat(stacks.highlighted(noneInMore)).isEqualTo(ShellTab.REMOTE)
    }

    @Test
    fun `back from an overflowed tab's root shows the more list and back from it returns to presentation`() {
        val remote = initial.select(ShellTab.MORE).select(ShellTab.REMOTE)

        val moreList = remote.back(remoteInMore)
        assertThat(moreList.current).isEqualTo(ShellTab.MORE)
        assertThat(moreList.displayed).containsExactly(PlaylistsRoute, MoreRoute)

        assertThat(moreList.back(remoteInMore).current).isEqualTo(ShellTab.PRESENTATION)
    }

    @Test
    fun `a resize moves no stacks, only the highlight`() {
        val remote = initial.openDetail(grid1).select(ShellTab.MORE).select(ShellTab.REMOTE)

        assertThat(remote.highlighted(remoteInMore)).isEqualTo(ShellTab.MORE)
        assertThat(remote.highlighted(noneInMore)).isEqualTo(ShellTab.REMOTE)
        assertThat(remote.displayed).containsExactly(PlaylistsRoute, grid1, RemoteRoute)
        assertThat(remote.back(noneInMore).current).isEqualTo(ShellTab.PRESENTATION)
        assertThat(remote.back(remoteInMore).current).isEqualTo(ShellTab.MORE)
    }

    @Test
    fun `presentation under more is highlighted as more and opens with its stack`() {
        val everything = setOf(ShellTab.PRESENTATION, ShellTab.REMOTE, ShellTab.SETTINGS)
        val moreList = initial.openDetail(grid1).select(ShellTab.MORE)

        assertThat(moreList.displayed).containsExactly(PlaylistsRoute, grid1, MoreRoute)

        val presentation = moreList.select(ShellTab.PRESENTATION)
        assertThat(presentation.current).isEqualTo(ShellTab.PRESENTATION)
        assertThat(presentation.highlighted(everything)).isEqualTo(ShellTab.MORE)
        assertThat(presentation.displayed).containsExactly(PlaylistsRoute, grid1)
        assertThat(presentation.back(everything).displayed).containsExactly(PlaylistsRoute)
    }

    @Test
    fun `re-selecting more trims nothing but the more list`() {
        val stacks = initial.openDetail(grid1).select(ShellTab.MORE).select(ShellTab.MORE)

        assertThat(stacks.current).isEqualTo(ShellTab.MORE)
        assertThat(stacks.displayed).containsExactly(PlaylistsRoute, grid1, MoreRoute)
    }

    @Test
    fun `the more list returns to presentation once nothing is under more, keeping every stack`() {
        val moreList = initial.openDetail(grid1).select(ShellTab.REMOTE).select(ShellTab.MORE)

        val fits = moreList.withoutMore()

        assertThat(fits.current).isEqualTo(ShellTab.PRESENTATION)
        assertThat(fits.stacks).isEqualTo(moreList.stacks)
    }

    @Test
    fun `another tab stays selected once nothing is under more`() {
        val remote = initial.select(ShellTab.MORE).select(ShellTab.REMOTE)

        assertThat(remote.withoutMore()).isEqualTo(remote)
    }
}
