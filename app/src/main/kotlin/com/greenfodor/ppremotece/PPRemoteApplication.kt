package com.greenfodor.ppremotece

import android.app.Application
import com.greenfodor.ppremotece.core.data.di.coreDataModule
import com.greenfodor.ppremotece.feature.connect.connectModule
import com.greenfodor.ppremotece.feature.playlist.playlistModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PPRemoteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PPRemoteApplication)
            modules(coreDataModule, connectModule, playlistModule)
        }
    }
}
