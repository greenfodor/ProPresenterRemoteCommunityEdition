package com.greenfodor.ppremotece.feature.remote

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val remoteModule = module {
    viewModelOf(::RemoteViewModel)
}
