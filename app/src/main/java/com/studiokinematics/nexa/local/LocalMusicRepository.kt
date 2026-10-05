package com.studiokinematics.nexa.local

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.studiokinematics.nexa.model.Source
import com.studiokinematics.nexa.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface LocalMusicResult {
    data class Ready(val tracks: List<Track>) : LocalMusicResult
    data object PermissionRequired : LocalMusicResult
    data class Error(val message: String) : LocalMusicResult
}

class LocalMusicRepository(private val context: Context) {
    suspend fun load(hasPermission: Boolean): LocalMusicResult = withContext(Dispatchers.IO) {
        if (!hasPermission) return@withContext LocalMusicResult.PermissionRequired
        runCatching {
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.MIME_TYPE
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val tracks = buildList {
                context.contentResolver.query(
                    collection, projection, selection, null,
                    "${MediaStore.Audio.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                    while (cursor.moveToNext()) {
                        val mediaId = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol)?.trim().orEmpty()
                        if (mediaId <= 0L || title.isBlank()) continue
                        val contentUri = ContentUris.withAppendedId(collection, mediaId)
                        val albumId = cursor.getLong(albumIdCol)
                        val artwork = if (albumId > 0L) {
                            ContentUris.withAppendedId(
                                android.net.Uri.parse("content://media/external/audio/albumart"),
                                albumId
                            ).toString()
                        } else ""
                        add(
                            Track(
                                id = "local:$mediaId",
                                title = title,
                                artist = cursor.getString(artistCol)?.trim().orEmpty()
                                    .takeUnless { it.isBlank() || it == "<unknown>" } ?: "Unknown artist",
                                album = cursor.getString(albumCol)?.trim().orEmpty()
                                    .takeUnless { it == "<unknown>" } ?: "",
                                language = "On Device",
                                genre = "Local Music",
                                source = Source.LOCAL,
                                contentUri = contentUri.toString(),
                                artworkUrl = artwork,
                                albumId = albumId,
                                durationMs = cursor.getLong(durationCol).coerceAtLeast(0L),
                                codec = cursor.getString(mimeCol).orEmpty()
                            )
                        )
                    }
                }
            }
            LocalMusicResult.Ready(tracks)
        }.getOrElse { LocalMusicResult.Error(it.message ?: "Could not read music on this device.") }
    }
}
