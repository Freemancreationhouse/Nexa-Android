package com.studiokinematics.nexa.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.studiokinematics.nexa.model.Source
import com.studiokinematics.nexa.model.Track

private const val EXTRA_SOURCE = "nexa.source"
private const val EXTRA_LANGUAGE = "nexa.language"
private const val EXTRA_GENRE = "nexa.genre"
private const val EXTRA_STREAM = "nexa.stream"
private const val EXTRA_CONTENT = "nexa.content"
private const val EXTRA_HOMEPAGE = "nexa.homepage"
private const val EXTRA_ALBUM = "nexa.album"
private const val EXTRA_DOWNLOAD = "nexa.download"
private const val EXTRA_DOWNLOADABLE = "nexa.downloadable"

@OptIn(UnstableApi::class)
fun Track.toMediaItem(): MediaItem {
    require(source != Source.YOUTUBE) { "YouTube must use the official embedded player." }
    val uri = when (source) {
        Source.LOCAL -> contentUri
        Source.AUDIUS, Source.RADIO -> streamUrl
        Source.YOUTUBE -> ""
    }
    require(uri.isNotBlank()) { "Native media requires a playable URI." }

    val extras = Bundle().apply {
        putString(EXTRA_SOURCE, source.name); putString(EXTRA_LANGUAGE, language); putString(EXTRA_GENRE, genre)
        putString(EXTRA_STREAM, streamUrl); putString(EXTRA_CONTENT, contentUri); putString(EXTRA_HOMEPAGE, homepage)
        putString(EXTRA_ALBUM, album); putString(EXTRA_DOWNLOAD, downloadUri); putBoolean(EXTRA_DOWNLOADABLE, isDownloadable)
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title).setArtist(artist).setAlbumTitle(album.takeIf { it.isNotBlank() })
        .setArtworkUri(effectiveArtworkUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
        .setExtras(extras).build()

    return MediaItem.Builder().setMediaId(id).setUri(uri)
        .apply { if(source==Source.AUDIUS && isDownloadable) setCustomCacheKey(id) }
        .setMediaMetadata(metadata).build()
}

fun MediaItem.toNexaTrack(): Track {
    val x = mediaMetadata.extras
    val source = runCatching { Source.valueOf(x?.getString(EXTRA_SOURCE).orEmpty()) }.getOrDefault(Source.AUDIUS)
    return Track(
        id=mediaId, title=mediaMetadata.title?.toString().orEmpty(), artist=mediaMetadata.artist?.toString().orEmpty(),
        language=x?.getString(EXTRA_LANGUAGE).orEmpty(), genre=x?.getString(EXTRA_GENRE).orEmpty(),
        streamUrl=x?.getString(EXTRA_STREAM).orEmpty(), contentUri=x?.getString(EXTRA_CONTENT).orEmpty(),
        source=source, artworkUrl=mediaMetadata.artworkUri?.toString().orEmpty(),
        homepage=x?.getString(EXTRA_HOMEPAGE).orEmpty(), album=x?.getString(EXTRA_ALBUM).orEmpty(),
        downloadUri=x?.getString(EXTRA_DOWNLOAD).orEmpty(), isDownloadable=x?.getBoolean(EXTRA_DOWNLOADABLE,false) ?: false
    )
}
