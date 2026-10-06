package com.greenfodor.ppremotece.feature.audio

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val audioModule = module {
    viewModelOf(::AudioViewModel)
}
