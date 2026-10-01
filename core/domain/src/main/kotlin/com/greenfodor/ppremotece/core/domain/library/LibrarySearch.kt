package com.greenfodor.ppremotece.core.domain.library

import com.greenfodor.ppremotece.core.domain.model.LibraryContents
import java.text.Normalizer
import java.util.Locale

private val CombiningMarks = Regex("\\p{Mn}+")

/** [text] lower-cased with its diacritics removed, for matching search queries. */
fun foldForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(CombiningMarks, "").lowercase(Locale.ROOT)

/**
 * The presentations of [libraries] whose names contain [query], compared with [foldForSearch];
 * grouped by library in library order, each with its entries in their order, leaving out libraries
 * with no match. A blank query has no results.
 */
fun searchLibraries(libraries: List<LibraryContents>, query: String): List<LibraryContents> {
    val folded = foldForSearch(query.trim())
    if (folded.isEmpty()) return emptyList()
    return libraries
        .sortedBy { it.library.index }
        .map { contents -> contents.copy(entries = contents.entries.filter { folded in foldForSearch(it.name) }) }
        .filter { it.entries.isNotEmpty() }
}
