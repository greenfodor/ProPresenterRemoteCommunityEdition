package com.greenfodor.ppremotece.feature.props

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val propsModule = module {
    viewModelOf(::PropsViewModel)
}
