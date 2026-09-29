package com.greenfodor.ppremotece

import com.greenfodor.ppremotece.navigation.ShellViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    viewModelOf(::ShellViewModel)
}
