package com.greenfodor.ppremotece.feature.stage

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val stageModule = module {
    viewModelOf(::StageViewModel)
    viewModel { (screenUuid: String) -> StageLayoutsViewModel(screenUuid, get(), get(), get()) }
}
