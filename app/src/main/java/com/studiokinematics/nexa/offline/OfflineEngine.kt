package com.studiokinematics.nexa.offline

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import java.io.File
import java.util.concurrent.Executors

@UnstableApi
object OfflineEngine {
    @Volatile private var database:StandaloneDatabaseProvider?=null
    @Volatile private var cacheInstance:SimpleCache?=null
    @Volatile private var managerInstance:DownloadManager?=null
    private val executor by lazy{Executors.newFixedThreadPool(3)}

    @Synchronized private fun db(context:Context):StandaloneDatabaseProvider =
        database?:StandaloneDatabaseProvider(context.applicationContext).also{database=it}

    @Synchronized fun cache(context:Context):Cache =
        cacheInstance?:SimpleCache(
            File(context.filesDir,"nexa_offline_media"),
            NoOpCacheEvictor(),
            db(context)
        ).also{cacheInstance=it}

    @Synchronized fun manager(context:Context):DownloadManager =
        managerInstance?:DownloadManager(
            context.applicationContext,db(context),cache(context),
            DefaultHttpDataSource.Factory(),executor
        ).apply{
            maxParallelDownloads=2
            resumeDownloads()
        }.also{managerInstance=it}
}
