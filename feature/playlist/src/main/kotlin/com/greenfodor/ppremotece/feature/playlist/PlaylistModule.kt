package com.greenfodor.ppremotece.feature.playlist

import com.greenfodor.ppremotece.core.domain.model.CueSource
import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.feature.playlist.grid.SlideGridViewModel
import com.greenfodor.ppremotece.feature.playlist.items.PlaylistItemViewModel
import com.greenfodor.ppremotece.feature.playlist.items.PlaylistViewModel
import com.greenfodor.ppremotece.feature.playlist.library.LibraryViewModel
import com.greenfodor.ppremotece.feature.playlist.tree.PlaylistTreeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val playlistModule = module {
    viewModelOf(::PlaylistTreeViewModel)
    viewModelOf(::LibraryViewModel)
    viewModel { (source: CueSource) -> SlideGridViewModel(source, get(), get(), get(), get(), get(), get()) }
    viewModel { (playlistUuid: String) -> PlaylistViewModel(playlistUuid, get()) }
    viewModel { (key: PlaylistItemKey) -> PlaylistItemViewModel(key, get(), get(), get(), get()) }
}
