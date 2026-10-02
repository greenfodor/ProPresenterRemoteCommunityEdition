package com.greenfodor.ppremotece.feature.looks

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val looksModule = module {
    viewModelOf(::LooksViewModel)
}
