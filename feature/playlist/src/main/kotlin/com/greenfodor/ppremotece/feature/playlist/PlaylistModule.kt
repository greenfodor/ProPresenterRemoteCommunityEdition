package com.greenfodor.ppremotece.feature.playlist

import com.greenfodor.ppremotece.core.domain.model.PlaylistItemKey
import com.greenfodor.ppremotece.feature.playlist.grid.SlideGridViewModel
import com.greenfodor.ppremotece.feature.playlist.tree.PlaylistTreeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val playlistModule = module {
    viewModelOf(::PlaylistTreeViewModel)
    viewModel { (item: PlaylistItemKey) -> SlideGridViewModel(item, get(), get(), get(), get(), get(), get()) }
}
