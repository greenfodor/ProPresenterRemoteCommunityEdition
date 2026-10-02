package com.greenfodor.ppremotece.feature.timers

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val timersModule = module {
    viewModelOf(::TimersViewModel)
}
