package com.greenfodor.ppremotece.feature.playlist.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenfodor.ppremotece.core.designsystem.ui.toUiText
import com.greenfodor.ppremotece.core.domain.content.ContentRepository
import com.greenfodor.ppremotece.core.domain.library.searchLibraries
import com.greenfodor.ppremotece.core.domain.model.Library
import com.greenfodor.ppremotece.core.domain.model.LibraryContents
import com.greenfodor.ppremotece.core.domain.model.LibraryEntry
import com.greenfodor.ppremotece.core.domain.result.Result
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Library mode: the library list and the presentations of every library, all read in parallel
 * through the [ContentRepository] when it opens and shown again whenever they change. Libraries
 * expand in place; a non-blank query lists the matching presentations of all libraries instead,
 * and says there are none only once every library is read or has failed. Libraries whose read
 * failed stop loading, and a run of failures is reported once. Pull-to-refresh reads them all again.
 */
class LibraryViewModel(
    private val contentRepository: ContentRepository
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state = _state.asStateFlow()

    private val _events = Channel<LibraryEvent>()
    val events = _events.receiveAsFlow()

    private var libraries: List<Library> = emptyList()
    private val entries = mutableMapOf<String, List<LibraryEntry>>()
    private val expanded = mutableSetOf<String>()
    private val failed = mutableSetOf<String>()
    private var listJob: Job? = null
    private val libraryJobs = mutableMapOf<String, Job>()

    init {
        loadLibraries()
    }

    fun onAction(action: LibraryAction) {
        when (action) {
            is LibraryAction.OnQueryChange -> {
                _state.update { it.copy(query = action.query) }
                publish()
            }
            is LibraryAction.OnLibraryClick -> {
                if (!expanded.remove(action.uuid)) expanded += action.uuid
                publish()
            }
            is LibraryAction.OnPresentationClick -> viewModelScope.launch {
                _events.send(LibraryEvent.OpenPresentation(action.uuid))
            }
            LibraryAction.OnRetryClick -> loadLibraries()
            LibraryAction.OnRefresh -> refresh()
        }
    }

    private fun loadLibraries() {
        listJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        listJob = viewModelScope.launch {
            contentRepository.libraries().collect { result ->
                when (result) {
                    is Result.Success -> {
                        libraries = result.data
                        libraries.forEach { observeLibrary(it.uuid) }
                        _state.update { it.copy(isLoading = false, error = null) }
                        publish()
                    }
                    is Result.Failure -> _state.update { it.copy(isLoading = false, error = result.error.toUiText()) }
                }
            }
        }
    }

    private fun observeLibrary(uuid: String) {
        if (uuid in libraryJobs) return
        libraryJobs[uuid] = viewModelScope.launch {
            contentRepository.library(uuid).collect { result ->
                when (result) {
                    is Result.Success -> {
                        failed -= uuid
                        entries[uuid] = result.data
                        publish()
                    }
                    is Result.Failure -> {
                        val firstFailure = failed.isEmpty()
                        failed += uuid
                        publish()
                        if (firstFailure) _events.send(LibraryEvent.ShowError(result.error.toUiText()))
                    }
                }
            }
        }
    }

    private fun refresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val reads = listOf(async { contentRepository.refreshLibraries() }) +
                libraries.map { async { contentRepository.refreshLibrary(it.uuid) } }
            val error = reads.awaitAll().firstNotNullOfOrNull { (it as? Result.Failure)?.error }
            _state.update { it.copy(isRefreshing = false) }
            if (error != null) _events.send(LibraryEvent.ShowError(error.toUiText()))
        }
    }

    private fun publish() {
        val query = _state.value.query
        if (query.isBlank()) {
            _state.update { it.copy(rows = libraryRows(), noResults = false) }
        } else {
            val results = searchLibraries(libraries.map { LibraryContents(it, entries[it.uuid].orEmpty()) }, query)
            val settled = libraries.all { it.uuid in entries || it.uuid in failed }
            _state.update { it.copy(rows = resultRows(results), noResults = settled && results.isEmpty()) }
        }
    }

    private fun libraryRows(): List<LibraryRowUi> =
        libraries.sortedBy { it.index }.flatMap { library ->
            val isExpanded = library.uuid in expanded
            val loaded = entries[library.uuid]
            val isLoading = loaded == null && library.uuid !in failed
            listOf(LibraryRowUi.Library(library.uuid, library.name, isExpanded, isLoading)) +
                if (isExpanded) loaded.orEmpty().map { presentationRow(library, it) } else emptyList()
        }

    private fun resultRows(results: List<LibraryContents>): List<LibraryRowUi> =
        results.flatMap { (library, matches) ->
            listOf(LibraryRowUi.Section("section/${library.uuid}", library.name)) +
                matches.map { presentationRow(library, it) }
        }

    private fun presentationRow(library: Library, entry: LibraryEntry) =
        LibraryRowUi.Presentation("${library.uuid}/${entry.uuid}", entry.uuid, entry.name)
}
