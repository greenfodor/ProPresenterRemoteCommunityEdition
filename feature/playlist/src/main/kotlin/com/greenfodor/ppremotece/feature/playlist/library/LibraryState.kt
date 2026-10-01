package com.greenfodor.ppremotece.feature.playlist.library

import com.greenfodor.ppremotece.core.designsystem.ui.UiText

/**
 * Library mode: the [query] in the search field and the [rows] shown for it, the libraries with
 * the expanded ones' presentations while the query is blank, else the matching presentations under
 * their library names; [noResults] when a query matches nothing.
 */
data class LibraryState(
    val query: String = "",
    val rows: List<LibraryRowUi> = emptyList(),
    val noResults: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: UiText? = null
)

sealed interface LibraryRowUi {
    val id: String

    data class Library(
        override val id: String,
        val name: String,
        val expanded: Boolean,
        val isLoading: Boolean
    ) : LibraryRowUi

    /** A library name above its search results. */
    data class Section(
        override val id: String,
        val name: String
    ) : LibraryRowUi

    data class Presentation(
        override val id: String,
        val uuid: String,
        val name: String
    ) : LibraryRowUi
}

sealed interface LibraryAction {
    data class OnQueryChange(
        val query: String
    ) : LibraryAction

    data class OnLibraryClick(
        val uuid: String
    ) : LibraryAction

    data class OnPresentationClick(
        val uuid: String
    ) : LibraryAction

    data object OnRetryClick : LibraryAction

    data object OnRefresh : LibraryAction
}

sealed interface LibraryEvent {
    data class OpenPresentation(
        val uuid: String
    ) : LibraryEvent

    data class ShowError(
        val message: UiText
    ) : LibraryEvent
}
