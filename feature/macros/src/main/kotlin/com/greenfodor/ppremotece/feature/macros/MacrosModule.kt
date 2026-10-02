package com.greenfodor.ppremotece.feature.macros

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val macrosModule = module {
    viewModelOf(::MacrosViewModel)
}
