package com.greenfodor.ppremotece.feature.playlist.library

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.greenfodor.ppremotece.core.domain.model.Library
import com.greenfodor.ppremotece.core.domain.model.LibraryEntry
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
class LibraryViewModelTest {
    private val content = FakeContentRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        content.libraries = listOf(SONGS, HYMNS)
        content.libraryEntries[SONGS.uuid] =
            listOf(LibraryEntry("p-1", "Brăvel Tumin", 0), LibraryEntry("p-2", "Ostral", 1))
        content.libraryEntries[HYMNS.uuid] = listOf(LibraryEntry("p-3", "Tumbrel Vâr", 0))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `opening reads every library and lists them collapsed`() = runTest {
        val viewModel = LibraryViewModel(content)

        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.isLoading).isFalse()
            assertThat(state.rows).containsExactly(
                LibraryRowUi.Library(SONGS.uuid, "Library 01", expanded = false, isLoading = false),
                LibraryRowUi.Library(HYMNS.uuid, "Library 02", expanded = false, isLoading = false)
            )
        }
    }

    @Test
    fun `a library tap expands it into its presentations and a second tap collapses it`() = runTest {
        val viewModel = LibraryViewModel(content)

        viewModel.onAction(LibraryAction.OnLibraryClick(SONGS.uuid))
        assertThat(viewModel.state.value.rows.filterIsInstance<LibraryRowUi.Presentation>().map { it.uuid })
            .containsExactly("p-1", "p-2")

        viewModel.onAction(LibraryAction.OnLibraryClick(SONGS.uuid))
        assertThat(viewModel.state.value.rows.filterIsInstance<LibraryRowUi.Presentation>()).isEqualTo(emptyList())
    }

    @Test
    fun `a query lists the matching presentations of all libraries under their library names`() = runTest {
        val viewModel = LibraryViewModel(content)

        viewModel.onAction(LibraryAction.OnQueryChange("tumbrel var"))
        assertThat(viewModel.state.value.rows).containsExactly(
            LibraryRowUi.Section("section/${HYMNS.uuid}", "Library 02"),
            LibraryRowUi.Presentation("${HYMNS.uuid}/p-3", "p-3", "Tumbrel Vâr")
        )

        viewModel.onAction(LibraryAction.OnQueryChange("TUM"))
        assertThat(viewModel.state.value.rows.map { it.id }).containsExactly(
            "section/${SONGS.uuid}",
            "${SONGS.uuid}/p-1",
            "section/${HYMNS.uuid}",
            "${HYMNS.uuid}/p-3"
        )
    }

    @Test
    fun `a query without matches says so and clearing it shows the libraries again`() = runTest {
        val viewModel = LibraryViewModel(content)

        viewModel.onAction(LibraryAction.OnQueryChange("qqq"))
        assertThat(viewModel.state.value.noResults).isTrue()

        viewModel.onAction(LibraryAction.OnQueryChange(""))
        assertThat(viewModel.state.value.noResults).isFalse()
        assertThat(viewModel.state.value.rows.size).isEqualTo(2)
    }

    @Test
    fun `failed library reads stop loading and are reported once`() = runTest {
        content.libraryEntries.clear()
        val viewModel = LibraryViewModel(content)

        viewModel.events.test {
            assertThat(awaitItem()).isInstanceOf(LibraryEvent.ShowError::class)
            expectNoEvents()
        }
        assertThat(viewModel.state.value.rows.filterIsInstance<LibraryRowUi.Library>().map { it.isLoading })
            .containsExactly(false, false)
    }

    @Test
    fun `a query does not report no results while libraries are still loading`() = runTest {
        content.pendingLibraries += HYMNS.uuid
        val viewModel = LibraryViewModel(content)

        viewModel.onAction(LibraryAction.OnQueryChange("qqq"))

        assertThat(viewModel.state.value.noResults).isFalse()
    }

    @Test
    fun `a presentation tap opens it`() = runTest {
        val viewModel = LibraryViewModel(content)

        viewModel.events.test {
            viewModel.onAction(LibraryAction.OnPresentationClick("p-2"))
            assertThat(awaitItem()).isEqualTo(LibraryEvent.OpenPresentation("p-2"))
        }
    }

    @Test
    fun `pull to refresh reads the library list and every library again`() = runTest {
        val viewModel = LibraryViewModel(content)

        viewModel.onAction(LibraryAction.OnRefresh)

        assertThat(
            content.refreshed
        ).containsExactlyInAnyOrder("libraries", "library/${SONGS.uuid}", "library/${HYMNS.uuid}")
        assertThat(viewModel.state.value.isRefreshing).isFalse()
    }

    private companion object {
        val SONGS = Library("l-1", "Library 01", 0)
        val HYMNS = Library("l-2", "Library 02", 1)
    }
}
