package com.greenfodor.ppremotece

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.greenfodor.ppremotece.core.data.di.coreDataModule
import com.greenfodor.ppremotece.feature.connect.connectModule
import com.greenfodor.ppremotece.feature.playlist.playlistModule
import com.greenfodor.ppremotece.feature.remote.remoteModule
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PPRemoteApplication :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PPRemoteApplication)
            modules(coreDataModule, appModule, connectModule, playlistModule, remoteModule)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = get()
}
