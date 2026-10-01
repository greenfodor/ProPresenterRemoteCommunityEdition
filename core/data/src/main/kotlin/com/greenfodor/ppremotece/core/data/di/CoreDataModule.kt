package com.greenfodor.ppremotece.core.data.di

import coil3.ImageLoader
import com.greenfodor.ppremotece.core.data.content.CachingContentRepository
import com.greenfodor.ppremotece.core.data.discovery.NsdHostDiscovery
import com.greenfodor.ppremotece.core.data.layout.DataStoreGridPreferences
import com.greenfodor.ppremotece.core.data.layout.gridPreferencesDataStore
import com.greenfodor.ppremotece.core.data.network.HttpClientFactory
import com.greenfodor.ppremotece.core.data.session.ProPresenterSession
import com.greenfodor.ppremotece.core.data.session.SavedHostStore
import com.greenfodor.ppremotece.core.data.thumbnail.CoilThumbnailCache
import com.greenfodor.ppremotece.core.data.thumbnail.thumbnailImageLoader
import com.greenfodor.ppremotece.core.domain.content.ContentRepository
import com.greenfodor.ppremotece.core.domain.layout.GridPreferences
import com.greenfodor.ppremotece.core.domain.live.ConnectionRepository
import com.greenfodor.ppremotece.core.domain.live.HostDiscovery
import com.greenfodor.ppremotece.core.domain.live.LiveStateRepository
import com.greenfodor.ppremotece.core.domain.live.ProPresenterClient
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailCache
import com.greenfodor.ppremotece.core.domain.thumbnail.ThumbnailSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.binds
import org.koin.dsl.module

val coreDataModule = module {
    single { HttpClientFactory.create() }
    single { SavedHostStore(androidContext()) }
    single { ProPresenterSession(get(), get<SavedHostStore>(), get()) } binds
        arrayOf(ConnectionRepository::class, LiveStateRepository::class, ThumbnailSource::class)
    single<ProPresenterClient> { get<ProPresenterSession>().client }
    single<HostDiscovery> { NsdHostDiscovery(androidContext()) }
    single<ImageLoader> { thumbnailImageLoader(androidContext(), get()) }
    single<ThumbnailCache> { CoilThumbnailCache(get()) }
    single<GridPreferences> { DataStoreGridPreferences(androidContext().gridPreferencesDataStore) }
    single<ContentRepository> {
        val session = get<ProPresenterSession>()
        CachingContentRepository(
            client = session.client,
            session = session.sessionKey,
            staleSignals = session.streamReconnects,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            restore = session::restore
        )
    }
}
