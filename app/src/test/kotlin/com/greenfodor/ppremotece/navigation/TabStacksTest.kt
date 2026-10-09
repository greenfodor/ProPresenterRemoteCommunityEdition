package com.greenfodor.ppremotece.navigation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.domain.layout.ShellTab
import com.greenfodor.ppremotece.feature.playlist.LibraryGridRoute
import com.greenfodor.ppremotece.feature.playlist.PlaylistItemRoute
import com.greenfodor.ppremotece.feature.playlist.PlaylistRoute
import com.greenfodor.ppremotece.feature.playlist.PlaylistsRoute
import com.greenfodor.ppremotece.feature.playlist.SlideGridRoute
import com.greenfodor.ppremotece.feature.remote.RemoteRoute
import com.greenfodor.ppremotece.feature.settings.MoreRoute
import com.greenfodor.ppremotece.feature.settings.SettingsRoute
import com.greenfodor.ppremotece.feature.stage.StageLayoutsRoute
import com.greenfodor.ppremotece.feature.stage.StageRoute
import org.junit.jupiter.api.Test

class TabStacksTest {
    private val grid1 = SlideGridRoute(playlistUuid = "p", itemIndex = 1)
    private val grid6 = SlideGridRoute(playlistUuid = "p", itemIndex = 6)
    private val playlist = PlaylistRoute(playlistUuid = "p")
    private val library = LibraryGridRoute(presentationUuid = "pres")
    private val initial = TabStacks.initial(
        mapOf(
            ShellTab.PRESENTATION to PlaylistsRoute,
            ShellTab.REMOTE to RemoteRoute,
            ShellTab.STAGE to StageRoute,
            ShellTab.SETTINGS to SettingsRoute,
            ShellTab.MORE to MoreRoute
        )
    )
    private val layouts = StageLayoutsRoute(screenUuid = "s-0")
    private val stageInMore = setOf(ShellTab.STAGE, ShellTab.SETTINGS)
    private val noneInMore = emptySet<ShellTab>()
    private val remoteInMore = setOf(ShellTab.REMOTE, ShellTab.SETTINGS)
    private val settingsInMore = setOf(ShellTab.SETTINGS)

    @Test
    fun `opens on the presentation root`() {
        assertThat(initial.current).isEqualTo(ShellTab.PRESENTATION)
        assertThat(initial.displayed(noneInMore)).containsExactly(PlaylistsRoute)
    }

    @Test
    fun `opening a detail replaces the open one`() {
        val stacks = initial.openDetail(grid1).openDetail(grid6)

        assertThat(stacks.stack(ShellTab.PRESENTATION)).containsExactly(PlaylistsRoute, grid6)
        assertThat(stacks.current).isEqualTo(ShellTab.PRESENTATION)
    }

    @Test
    fun `a library grid replaces an open slide grid and the other way round`() {
        assertThat(initial.openDetail(grid1).openDetail(library).stack(ShellTab.PRESENTATION))
            .containsExactly(PlaylistsRoute, library)
        assertThat(initial.openDetail(library).openDetail(grid6).stack(ShellTab.PRESENTATION))
            .containsExactly(PlaylistsRoute, grid6)
        assertThat(initial.openDetail(library).back(noneInMore).stack(ShellTab.PRESENTATION))
            .containsExactly(PlaylistsRoute)
    }

    @Test
    fun `opening a playlist puts its screen on the tree and closes any open detail`() {
        assertThat(initial.openPlaylist(playlist).stack(ShellTab.PRESENTATION))
            .containsExactly(PlaylistsRoute, playlist)
        assertThat(initial.openDetail(library).openPlaylist(playlist).stack(ShellTab.PRESENTATION))
            .containsExactly(PlaylistsRoute, playlist)
    }

    @Test
    fun `a detail opened from a playlist sits on the playlist screen`() {
        val stacks = initial.openPlaylist(playlist).openDetail(grid1)

        assertThat(stacks.stack(ShellTab.PRESENTATION)).containsExactly(PlaylistsRoute, playlist, grid1)
        assertThat(stacks.detail).isEqualTo(grid1)
    }

    @Test
    fun `another detail opened from a playlist replaces the open one and keeps the playlist`() {
        val item = PlaylistItemRoute(playlistUuid = "p", itemIndex = 4)
        val stacks = initial.openPlaylist(playlist).openDetail(grid1).openDetail(item)

        assertThat(stacks.stack(ShellTab.PRESENTATION)).containsExactly(PlaylistsRoute, playlist, item)
        assertThat(stacks.openDetail(grid6).stack(ShellTab.PRESENTATION))
            .containsExactly(PlaylistsRoute, playlist, grid6)
    }

    @Test
    fun `back walks from the detail to the playlist to the tree and stops there`() {
        val detail = initial.openPlaylist(playlist).openDetail(grid1)
        val onPlaylist = detail.back(noneInMore)
        val onTree = onPlaylist.back(noneInMore)

        assertThat(onPlaylist.stack(ShellTab.PRESENTATION)).containsExactly(PlaylistsRoute, playlist)
        assertThat(onPlaylist.detail).isEqualTo(null)
        assertThat(onTree.stack(ShellTab.PRESENTATION)).containsExactly(PlaylistsRoute)
        assertThat(onTree.back(noneInMore)).isEqualTo(onTree)
    }

    @Test
    fun `re-selecting presentation with a playlist and a detail open trims it to the tree`() {
        val stacks = initial.openPlaylist(playlist).openDetail(grid1).select(ShellTab.PRESENTATION)

        assertThat(stacks.displayed(noneInMore)).containsExactly(PlaylistsRoute)
    }

    @Test
    fun `no detail is open on the tree or on a playlist screen`() {
        assertThat(initial.detail).isEqualTo(null)
        assertThat(initial.openPlaylist(playlist).detail).isEqualTo(null)
        assertThat(initial.openDetail(library).detail).isEqualTo(library)
    }

    @Test
    fun `remote shows the presentation stack under the remote stack`() {
        val stacks = initial.openDetail(grid1).select(ShellTab.REMOTE)

        assertThat(stacks.current).isEqualTo(ShellTab.REMOTE)
        assertThat(stacks.displayed(noneInMore)).containsExactly(PlaylistsRoute, grid1, RemoteRoute)
    }

    @Test
    fun `back from the remote root returns to presentation with its stack kept`() {
        val stacks = initial.openDetail(grid1).select(ShellTab.REMOTE).back(noneInMore)

        assertThat(stacks.current).isEqualTo(ShellTab.PRESENTATION)
        assertThat(stacks.displayed(noneInMore)).containsExactly(PlaylistsRoute, grid1)
    }

    @Test
    fun `back on presentation pops its stack and stops at the root`() {
        val once = initial.openDetail(grid1).back(noneInMore)

        assertThat(once.displayed(noneInMore)).containsExactly(PlaylistsRoute)
        assertThat(once.back(noneInMore)).isEqualTo(once)
    }

    @Test
    fun `switching tabs keeps each stack`() {
        val stacks = initial.openDetail(grid1).select(ShellTab.REMOTE).select(ShellTab.PRESENTATION)

        assertThat(stacks.displayed(noneInMore)).containsExactly(PlaylistsRoute, grid1)
    }

    @Test
    fun `re-selecting presentation trims it to its root`() {
        val stacks = initial.openDetail(grid1).select(ShellTab.PRESENTATION)

        assertThat(stacks.current).isEqualTo(ShellTab.PRESENTATION)
        assertThat(stacks.displayed(noneInMore)).containsExactly(PlaylistsRoute)
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

        assertThat(stacks.displayed(noneInMore)).containsExactly(PlaylistsRoute, grid1, SettingsRoute)
        assertThat(stacks.back(noneInMore).current).isEqualTo(ShellTab.PRESENTATION)
        assertThat(stacks.back(noneInMore).displayed(noneInMore)).containsExactly(PlaylistsRoute, grid1)
    }

    @Test
    fun `a more row selects that destination's own tab and more stays highlighted`() {
        val stacks = initial.openDetail(grid1).select(ShellTab.MORE).select(ShellTab.REMOTE)

        assertThat(stacks.current).isEqualTo(ShellTab.REMOTE)
        assertThat(stacks.displayed(remoteInMore)).containsExactly(PlaylistsRoute, grid1, MoreRoute, RemoteRoute)
        assertThat(stacks.highlighted(remoteInMore)).isEqualTo(ShellTab.MORE)
        assertThat(stacks.highlighted(noneInMore)).isEqualTo(ShellTab.REMOTE)
    }

    @Test
    fun `back from an overflowed tab's root shows the more list and back from it returns to presentation`() {
        val remote = initial.select(ShellTab.MORE).select(ShellTab.REMOTE)

        val moreList = remote.back(remoteInMore)
        assertThat(moreList.current).isEqualTo(ShellTab.MORE)
        assertThat(moreList.displayed(remoteInMore)).containsExactly(PlaylistsRoute, MoreRoute)

        assertThat(moreList.back(remoteInMore).current).isEqualTo(ShellTab.PRESENTATION)
    }

    @Test
    fun `a resize moves no stacks, only the highlight`() {
        val remote = initial.openDetail(grid1).select(ShellTab.MORE).select(ShellTab.REMOTE)

        assertThat(remote.highlighted(remoteInMore)).isEqualTo(ShellTab.MORE)
        assertThat(remote.highlighted(noneInMore)).isEqualTo(ShellTab.REMOTE)
        assertThat(remote.displayed(noneInMore)).containsExactly(PlaylistsRoute, grid1, RemoteRoute)
        assertThat(remote.displayed(remoteInMore)).containsExactly(PlaylistsRoute, grid1, MoreRoute, RemoteRoute)
        assertThat(remote.back(noneInMore).current).isEqualTo(ShellTab.PRESENTATION)
        assertThat(remote.back(remoteInMore).current).isEqualTo(ShellTab.MORE)
    }

    @Test
    fun `presentation under more is highlighted as more and opens with its stack`() {
        val everything = setOf(ShellTab.PRESENTATION, ShellTab.REMOTE, ShellTab.SETTINGS)
        val moreList = initial.openDetail(grid1).select(ShellTab.MORE)

        assertThat(moreList.displayed(everything)).containsExactly(PlaylistsRoute, grid1, MoreRoute)

        val presentation = moreList.select(ShellTab.PRESENTATION)
        assertThat(presentation.current).isEqualTo(ShellTab.PRESENTATION)
        assertThat(presentation.highlighted(everything)).isEqualTo(ShellTab.MORE)
        assertThat(presentation.displayed(everything)).containsExactly(PlaylistsRoute, grid1)
        assertThat(presentation.back(everything).displayed(everything)).containsExactly(PlaylistsRoute)
    }

    @Test
    fun `re-selecting more trims nothing but the more list`() {
        val stacks = initial.openDetail(grid1).select(ShellTab.MORE).select(ShellTab.MORE)

        assertThat(stacks.current).isEqualTo(ShellTab.MORE)
        assertThat(stacks.displayed(noneInMore)).containsExactly(PlaylistsRoute, grid1, MoreRoute)
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

    @Test
    fun `with two panes back from a playlist under an open detail removes the playlist and keeps the detail`() {
        val stacks = initial.openPlaylist(playlist).openDetail(grid1)

        val onTree = stacks.back(noneInMore, twoPanes = true)

        assertThat(onTree.stack(ShellTab.PRESENTATION)).containsExactly(PlaylistsRoute, grid1)
        assertThat(onTree.detail).isEqualTo(grid1)
    }

    @Test
    fun `with two panes back at the tree closes the open detail and then stops`() {
        val onTree = initial.openPlaylist(playlist).openDetail(grid1).back(noneInMore, twoPanes = true)

        val closed = onTree.back(noneInMore, twoPanes = true)

        assertThat(closed.stack(ShellTab.PRESENTATION)).containsExactly(PlaylistsRoute)
        assertThat(closed.back(noneInMore, twoPanes = true)).isEqualTo(closed)
    }

    @Test
    fun `with two panes back from a playlist without a detail returns to the tree`() {
        val onTree = initial.openPlaylist(playlist).back(noneInMore, twoPanes = true)

        assertThat(onTree.stack(ShellTab.PRESENTATION)).containsExactly(PlaylistsRoute)
    }

    @Test
    fun `with two panes opening a playlist keeps the open detail`() {
        val other = PlaylistRoute(playlistUuid = "q")
        val fromTree = initial.openDetail(library).openPlaylist(playlist, twoPanes = true)
        val fromPlaylist = initial.openPlaylist(playlist).openDetail(grid1).openPlaylist(other, twoPanes = true)

        assertThat(fromTree.stack(ShellTab.PRESENTATION)).containsExactly(PlaylistsRoute, playlist, library)
        assertThat(fromPlaylist.stack(ShellTab.PRESENTATION)).containsExactly(PlaylistsRoute, other, grid1)
    }

    @Test
    fun `closing the detail removes only the detail`() {
        val stacks = initial.openPlaylist(playlist).openDetail(grid1)

        assertThat(stacks.closeDetail().stack(ShellTab.PRESENTATION)).containsExactly(PlaylistsRoute, playlist)
        assertThat(initial.openDetail(grid1).closeDetail().stack(ShellTab.PRESENTATION)).containsExactly(PlaylistsRoute)
        assertThat(initial.openPlaylist(playlist).closeDetail()).isEqualTo(initial.openPlaylist(playlist))
    }

    @Test
    fun `back on another tab is the same with two panes`() {
        val remote = initial.openPlaylist(playlist).openDetail(grid1).select(ShellTab.REMOTE)

        assertThat(remote.back(noneInMore, twoPanes = true)).isEqualTo(remote.back(noneInMore))
    }

    @Test
    fun `the more list is kept while nothing fits under more for a moment`() {
        val moreList = initial.select(ShellTab.MORE)

        assertThat(moreList.fitting(noneInMore).current).isEqualTo(ShellTab.PRESENTATION)
        assertThat(moreList.fitting(noneInMore).stacks).isEqualTo(moreList.stacks)
        assertThat(moreList.fitting(remoteInMore)).isEqualTo(moreList)
        assertThat(moreList.current).isEqualTo(ShellTab.MORE)
    }

    @Test
    fun `a tab that is not the more list is shown whatever is under more`() {
        val remote = initial.select(ShellTab.REMOTE)

        assertThat(remote.fitting(noneInMore)).isEqualTo(remote)
        assertThat(remote.fitting(remoteInMore)).isEqualTo(remote)
    }

    @Test
    fun `a tab under more shows the more list between the presentation stack and its own stack`() {
        val settings = initial.openPlaylist(playlist).openDetail(grid1).select(ShellTab.MORE).select(ShellTab.SETTINGS)

        assertThat(settings.displayed(settingsInMore))
            .containsExactly(PlaylistsRoute, playlist, grid1, MoreRoute, SettingsRoute)
    }

    @Test
    fun `back from the root of a tab under more selects more and the more list ends what is displayed`() {
        val settings = initial.openDetail(grid1).select(ShellTab.MORE).select(ShellTab.SETTINGS)

        val moreList = settings.back(settingsInMore)

        assertThat(moreList.current).isEqualTo(ShellTab.MORE)
        assertThat(moreList.displayed(settingsInMore)).containsExactly(PlaylistsRoute, grid1, MoreRoute)
    }

    @Test
    fun `a tab that is not under more is displayed without the more list`() {
        val remote = initial.openDetail(grid1).select(ShellTab.REMOTE)

        assertThat(remote.displayed(settingsInMore)).containsExactly(PlaylistsRoute, grid1, RemoteRoute)
        assertThat(remote.displayed(settingsInMore)).isEqualTo(remote.displayed(noneInMore))
    }

    @Test
    fun `presentation is displayed as its own stack whatever is under more`() {
        val presentation = initial.openPlaylist(playlist).openDetail(grid1)

        assertThat(presentation.displayed(remoteInMore)).containsExactly(PlaylistsRoute, playlist, grid1)
        assertThat(presentation.displayed(remoteInMore)).isEqualTo(presentation.displayed(noneInMore))
    }

    @Test
    fun `with two panes back on presentation is the same while tabs are under more`() {
        val stacks = initial.openPlaylist(playlist).openDetail(grid1)

        assertThat(stacks.back(remoteInMore, twoPanes = true)).isEqualTo(stacks.back(noneInMore, twoPanes = true))
        assertThat(stacks.back(remoteInMore, twoPanes = true).displayed(remoteInMore))
            .containsExactly(PlaylistsRoute, grid1)
    }

    @Test
    fun `a stage screen's layouts are shown on the stage stack over the more list`() {
        val stage = initial.openDetail(grid1).select(ShellTab.MORE).select(ShellTab.STAGE)

        val open = stage.open(ShellTab.STAGE, layouts)

        assertThat(open.current).isEqualTo(ShellTab.STAGE)
        assertThat(open.stack(ShellTab.STAGE)).containsExactly(StageRoute, layouts)
        assertThat(open.displayed(stageInMore)).containsExactly(PlaylistsRoute, grid1, MoreRoute, StageRoute, layouts)
        assertThat(open.displayed(noneInMore)).containsExactly(PlaylistsRoute, grid1, StageRoute, layouts)
    }

    @Test
    fun `back walks from a stage screen's layouts to the stage root and then to the more list`() {
        val open = initial.select(ShellTab.MORE).select(ShellTab.STAGE).open(ShellTab.STAGE, layouts)

        val root = open.back(stageInMore)
        assertThat(root.current).isEqualTo(ShellTab.STAGE)
        assertThat(root.stack(ShellTab.STAGE)).containsExactly(StageRoute)

        val moreList = root.back(stageInMore)
        assertThat(moreList.current).isEqualTo(ShellTab.MORE)
        assertThat(moreList.displayed(stageInMore)).containsExactly(PlaylistsRoute, MoreRoute)
        assertThat(root.back(noneInMore).current).isEqualTo(ShellTab.PRESENTATION)
    }

    @Test
    fun `opening another stage screen's layouts replaces the open ones and re-selecting stage trims to its root`() {
        val other = StageLayoutsRoute(screenUuid = "s-1")
        val open = initial.select(ShellTab.STAGE).open(ShellTab.STAGE, layouts).open(ShellTab.STAGE, other)

        assertThat(open.stack(ShellTab.STAGE)).containsExactly(StageRoute, other)
        assertThat(open.select(ShellTab.STAGE).stack(ShellTab.STAGE)).containsExactly(StageRoute)
        assertThat(open.select(ShellTab.REMOTE).select(ShellTab.STAGE).stack(ShellTab.STAGE))
            .containsExactly(StageRoute, other)
    }

    @Test
    fun `a stage screen's layouts opened while another tab is selected go on the stage stack only`() {
        val remote = initial.openPlaylist(playlist).openDetail(grid1).select(ShellTab.REMOTE)

        val open = remote.open(ShellTab.STAGE, layouts)

        assertThat(open.current).isEqualTo(ShellTab.REMOTE)
        assertThat(open.stack(ShellTab.STAGE)).containsExactly(StageRoute, layouts)
        assertThat(open.stack(ShellTab.REMOTE)).containsExactly(RemoteRoute)
        assertThat(open.stack(ShellTab.PRESENTATION)).containsExactly(PlaylistsRoute, playlist, grid1)
    }

    @Test
    fun `closing a stage screen's layouts removes them from the stage stack and nothing else`() {
        val open = initial.openDetail(grid1).select(ShellTab.STAGE).open(ShellTab.STAGE, layouts)

        val closed = open.close(ShellTab.STAGE, layouts)

        assertThat(closed.current).isEqualTo(ShellTab.STAGE)
        assertThat(closed.stack(ShellTab.STAGE)).containsExactly(StageRoute)
        assertThat(closed.close(ShellTab.STAGE, layouts)).isEqualTo(closed)
        assertThat(open.select(ShellTab.REMOTE).close(ShellTab.STAGE, layouts).stack(ShellTab.REMOTE))
            .containsExactly(RemoteRoute)
        assertThat(open.close(ShellTab.STAGE, StageLayoutsRoute("s-9"))).isEqualTo(open)
        assertThat(closed.close(ShellTab.STAGE, StageRoute)).isEqualTo(closed)
    }
}
