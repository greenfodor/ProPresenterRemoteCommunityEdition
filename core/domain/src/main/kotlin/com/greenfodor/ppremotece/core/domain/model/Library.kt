package com.greenfodor.ppremotece.core.domain.model

/** A ProPresenter library; [index] is its position in the library list. */
data class Library(
    val uuid: String,
    val name: String,
    val index: Int
)

/** A presentation in a library; [index] is its position in the library. */
data class LibraryEntry(
    val uuid: String,
    val name: String,
    val index: Int
)

/** A library with its presentations. */
data class LibraryContents(
    val library: Library,
    val entries: List<LibraryEntry>
)
