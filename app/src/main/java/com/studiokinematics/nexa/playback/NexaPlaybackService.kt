package com.studiokinematics.nexa.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.common.util.UnstableApi
import com.studiokinematics.nexa.offline.OfflineEngine
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@OptIn(UnstableApi::class)
class NexaPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val upstream=DefaultDataSource.Factory(this,DefaultHttpDataSource.Factory())
        val cacheFactory=CacheDataSource.Factory()
            .setCache(OfflineEngine.cache(this))
            .setUpstreamDataSourceFactory(upstream)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val mediaSourceFactory=DefaultMediaSourceFactory(this).setDataSourceFactory(cacheFactory)
        val player = ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
        }
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
