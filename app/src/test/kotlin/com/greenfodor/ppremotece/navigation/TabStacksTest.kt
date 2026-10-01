package com.greenfodor.ppremotece.navigation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
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
        presentationRoot = PlaylistsRoute,
        remoteRoot = RemoteRoute,
        settingsRoot = SettingsRoute,
        moreRoot = MoreRoute
    )

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
    fun `a library grid replaces an open slide grid and the other way round`() {
        val library = LibraryGridRoute(presentationUuid = "pres")

        assertThat(initial.openDetail(grid1).openDetail(library).presentation).containsExactly(PlaylistsRoute, library)
        assertThat(initial.openDetail(library).openDetail(grid6).presentation).containsExactly(PlaylistsRoute, grid6)
        assertThat(initial.openDetail(library).back().presentation).containsExactly(PlaylistsRoute)
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

    @Test
    fun `settings shows the presentation stack under the settings stack and back returns to presentation`() {
        val stacks = initial.openDetail(grid1).select(ShellTab.SETTINGS)

        assertThat(stacks.displayed).containsExactly(PlaylistsRoute, grid1, SettingsRoute)
        assertThat(stacks.back().current).isEqualTo(ShellTab.PRESENTATION)
        assertThat(stacks.back().displayed).containsExactly(PlaylistsRoute, grid1)
    }

    @Test
    fun `a destination opened from more is pushed on the more stack and back pops it`() {
        val stacks = initial.select(ShellTab.MORE).openFromMore(SettingsRoute)

        assertThat(stacks.displayed).containsExactly(PlaylistsRoute, MoreRoute, SettingsRoute)
        assertThat(stacks.back().displayed).containsExactly(PlaylistsRoute, MoreRoute)
        assertThat(stacks.back().back().current).isEqualTo(ShellTab.PRESENTATION)
    }

    @Test
    fun `re-selecting more trims it to its root`() {
        val stacks = initial.select(ShellTab.MORE).openFromMore(SettingsRoute).select(ShellTab.MORE)

        assertThat(stacks.more).containsExactly(MoreRoute)
    }

    @Test
    fun `selected settings moves into more when it overflows and back out when it fits again`() {
        val overflowed = initial.select(ShellTab.SETTINGS).withSettingsInMore(true)

        assertThat(overflowed.current).isEqualTo(ShellTab.MORE)
        assertThat(overflowed.displayed).containsExactly(PlaylistsRoute, MoreRoute, SettingsRoute)

        val fits = overflowed.withSettingsInMore(false)
        assertThat(fits.current).isEqualTo(ShellTab.SETTINGS)
        assertThat(fits.displayed).containsExactly(PlaylistsRoute, SettingsRoute)
        assertThat(fits.more).containsExactly(MoreRoute)
    }

    @Test
    fun `more at its root returns to presentation when settings fits again`() {
        val stacks = initial.select(ShellTab.MORE).withSettingsInMore(false)

        assertThat(stacks.current).isEqualTo(ShellTab.PRESENTATION)
    }

    @Test
    fun `settings in more is dropped from the more stack when it fits again while another tab is selected`() {
        val stacks = initial.select(ShellTab.MORE).openFromMore(SettingsRoute).select(ShellTab.REMOTE)

        assertThat(stacks.withSettingsInMore(true)).isEqualTo(stacks)
        assertThat(stacks.withSettingsInMore(false).more).containsExactly(MoreRoute)
        assertThat(stacks.withSettingsInMore(false).current).isEqualTo(ShellTab.REMOTE)
    }
}
