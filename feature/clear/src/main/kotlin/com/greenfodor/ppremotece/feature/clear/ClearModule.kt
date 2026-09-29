package com.greenfodor.ppremotece.feature.clear

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val clearModule = module {
    viewModelOf(::ClearViewModel)
}
