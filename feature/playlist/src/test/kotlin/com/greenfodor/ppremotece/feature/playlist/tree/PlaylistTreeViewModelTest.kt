package com.greenfodor.ppremotece.feature.playlist.tree

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import com.greenfodor.ppremotece.core.domain.model.PlaylistFolder
import com.greenfodor.ppremotece.core.domain.model.PlaylistLeaf
import com.greenfodor.ppremotece.core.domain.result.DataError
import com.greenfodor.ppremotece.feature.playlist.FakeContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistTreeViewModelTest {
    private val content = FakeContentRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        content.tree = listOf(
            PlaylistFolder("f-1", "Folder A", listOf(PlaylistLeaf("pl-1", "Arrangement Test"))),
            PlaylistLeaf("pl-2", "Service Playlist")
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the tree lists folders collapsed and playlists`() = runTest {
        val viewModel = PlaylistTreeViewModel(content)

        assertThat(viewModel.state.value.isLoading).isFalse()
        assertThat(viewModel.state.value.rows).containsExactly(
            TreeRowUi.Folder("f-1", 0, "Folder A", expanded = false),
            TreeRowUi.Playlist("pl-2", 0, "Service Playlist")
        )
    }

    @Test
    fun `a folder tap expands it into its playlists and a second tap collapses it`() = runTest {
        val viewModel = PlaylistTreeViewModel(content)

        viewModel.onAction(PlaylistTreeAction.OnFolderClick("f-1"))
        assertThat(viewModel.state.value.rows).containsExactly(
            TreeRowUi.Folder("f-1", 0, "Folder A", expanded = true),
            TreeRowUi.Playlist("pl-1", 1, "Arrangement Test"),
            TreeRowUi.Playlist("pl-2", 0, "Service Playlist")
        )

        viewModel.onAction(PlaylistTreeAction.OnFolderClick("f-1"))
        assertThat(viewModel.state.value.rows.map { it.id }).containsExactly("f-1", "pl-2")
    }

    @Test
    fun `a playlist tap opens the playlist and reads none of its items`() = runTest {
        val viewModel = PlaylistTreeViewModel(content)

        viewModel.events.test {
            viewModel.onAction(PlaylistTreeAction.OnPlaylistClick("pl-2"))
            assertThat(awaitItem()).isEqualTo(PlaylistTreeEvent.OpenPlaylist("pl-2"))
        }

        assertThat(viewModel.state.value.rows.map { it.id }).containsExactly("f-1", "pl-2")
        assertThat(content.playlistReads).isEmpty()
    }

    @Test
    fun `a refresh reads the tree again`() = runTest {
        val viewModel = PlaylistTreeViewModel(content)

        viewModel.onAction(PlaylistTreeAction.OnRefresh)

        assertThat(content.refreshed).containsExactly("tree")
        assertThat(viewModel.state.value.isRefreshing).isFalse()
    }

    @Test
    fun `a tree that cannot be read shows its error`() = runTest {
        content.failWith = DataError.Network.TIMEOUT

        val viewModel = PlaylistTreeViewModel(content)

        assertThat(viewModel.state.value.error).isNotNull()
        assertThat(viewModel.state.value.isLoading).isFalse()
    }
}
