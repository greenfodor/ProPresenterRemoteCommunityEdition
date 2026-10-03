package com.greenfodor.ppremotece

import com.greenfodor.ppremotece.core.data.session.ProPresenterSession
import com.greenfodor.ppremotece.core.designsystem.ui.UiMessages
import com.greenfodor.ppremotece.navigation.ShellViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { UiMessages() }
    viewModel { ShellViewModel(get(), get<ProPresenterSession>()::restore, get()) }
}
