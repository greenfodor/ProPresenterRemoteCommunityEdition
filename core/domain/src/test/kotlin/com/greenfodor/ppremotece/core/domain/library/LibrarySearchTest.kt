package com.greenfodor.ppremotece.core.domain.library

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.greenfodor.ppremotece.core.domain.model.Library
import com.greenfodor.ppremotece.core.domain.model.LibraryContents
import com.greenfodor.ppremotece.core.domain.model.LibraryEntry
import org.junit.jupiter.api.Test

class LibrarySearchTest {
    private val songs = Library("l-1", "Library 01", 0)
    private val hymns = Library("l-2", "Library 02", 1)
    private val empty = Library("l-3", "Library 03", 2)

    private val contents = listOf(
        LibraryContents(
            hymns,
            listOf(LibraryEntry("e-4", "Zorvân Tălmeș", 0), LibraryEntry("e-5", "Brontul Vișinii", 1))
        ),
        LibraryContents(
            songs,
            listOf(
                LibraryEntry("e-1", "MORMÂNTUL Plivic", 0),
                LibraryEntry("e-2", "Țarvelă Şunei", 1),
                LibraryEntry("e-3", "Glaster Ţimbu", 2)
            )
        ),
        LibraryContents(empty, emptyList())
    )

    @Test
    fun `folding lower-cases and removes diacritics`() {
        assertThat(foldForSearch("MORMÂNTUL")).isEqualTo("mormantul")
        assertThat(foldForSearch("âăîșşțţ ÂĂÎȘŞȚŢ")).isEqualTo("aaisstt aaisstt")
        assertThat(foldForSearch("Café Noël")).isEqualTo("cafe noel")
    }

    @Test
    fun `folding maps letters without a combining mark to their base letters`() {
        assertThat(foldForSearch("Kałvørn Đubeß")).isEqualTo("kalvorn dubess")
        assertThat(foldForSearch("ŁØĐẞ")).isEqualTo("lodss")
    }

    @Test
    fun `a query without diacritics matches names with them, case-insensitively`() {
        val results = searchLibraries(contents, "mormantul")

        assertThat(results).containsExactly(LibraryContents(songs, listOf(LibraryEntry("e-1", "MORMÂNTUL Plivic", 0))))
    }

    @Test
    fun `comma-below and cedilla letters match the same query`() {
        val results = searchLibraries(contents, "t")

        assertThat(results.flatMap { result -> result.entries.map { it.uuid } })
            .containsExactly("e-1", "e-2", "e-3", "e-4", "e-5")
        assertThat(searchLibraries(contents, "SUNEI").single().entries.single().uuid).isEqualTo("e-2")
        assertThat(searchLibraries(contents, "timbu").single().entries.single().uuid).isEqualTo("e-3")
    }

    @Test
    fun `results are grouped by library in library order with entries in their order`() {
        val results = searchLibraries(contents, "l")

        assertThat(results.map { it.library }).containsExactly(songs, hymns)
        assertThat(results[0].entries.map { it.uuid }).containsExactly("e-1", "e-2", "e-3")
        assertThat(results[1].entries.map { it.uuid }).containsExactly("e-4", "e-5")
    }

    @Test
    fun `an empty or blank query has no results`() {
        assertThat(searchLibraries(contents, "")).isEmpty()
        assertThat(searchLibraries(contents, "   ")).isEmpty()
    }

    @Test
    fun `a query that matches nothing has no results`() {
        assertThat(searchLibraries(contents, "qqq")).isEmpty()
    }
}
