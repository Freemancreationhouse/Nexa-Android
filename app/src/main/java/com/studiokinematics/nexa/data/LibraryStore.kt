package com.studiokinematics.nexa.data

import android.content.Context
import com.studiokinematics.nexa.model.Source
import com.studiokinematics.nexa.model.Track
import org.json.JSONArray
import org.json.JSONObject

class LibraryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nexa", Context.MODE_PRIVATE)

    fun loadFavorites(): List<Track> =
        decode(prefs.getString("favorites", null) ?: prefs.getString("favorite_tracks", null))

    fun loadRecent(): List<Track> =
        decode(prefs.getString("recent", null) ?: prefs.getString("recent_tracks", null))

    fun saveFavorites(tracks: List<Track>) {
        prefs.edit().putString("favorites", encode(tracks)).apply()
    }

    fun saveRecent(tracks: List<Track>) {
        prefs.edit().putString("recent", encode(tracks)).apply()
    }

    companion object {
        fun encode(tracks: List<Track>): String {
            val arr = JSONArray()
            tracks.forEach { t ->
                arr.put(JSONObject().apply {
                    put("id", t.id); put("title", t.title); put("artist", t.artist)
                    put("language", t.language); put("genre", t.genre); put("streamUrl", t.streamUrl)
                    put("source", t.source.name); put("videoId", t.videoId); put("thumbnailUrl", t.thumbnailUrl)
                    put("artworkUrl", t.artworkUrl); put("countryCode", t.countryCode); put("homepage", t.homepage)
                    put("codec", t.codec); put("bitrate", t.bitrate); put("durationMs", t.durationMs)
                    put("album", t.album); put("contentUri", t.contentUri); put("albumId", t.albumId)
                    put("downloadUri", t.downloadUri); put("isDownloadable", t.isDownloadable)
                })
            }
            return arr.toString()
        }

        fun decode(json: String?): List<Track> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optString("id").trim()
                        val title = o.optString("title").trim()
                        if (id.isBlank() || title.isBlank()) continue
                        val source = runCatching { Source.valueOf(o.optString("source", Source.RADIO.name)) }
                            .getOrDefault(Source.RADIO)
                        val track = Track(
                            id=id, title=title, artist=o.optString("artist", "Artist"),
                            language=o.optString("language", "Music"), genre=o.optString("genre", "Music"),
                            streamUrl=o.optString("streamUrl"), source=source, videoId=o.optString("videoId"),
                            thumbnailUrl=o.optString("thumbnailUrl"), artworkUrl=o.optString("artworkUrl"),
                            countryCode=o.optString("countryCode"), homepage=o.optString("homepage"),
                            codec=o.optString("codec"), bitrate=o.optInt("bitrate",0),
                            durationMs=o.optLong("durationMs",0L), album=o.optString("album"),
                            contentUri=o.optString("contentUri"), albumId=o.optLong("albumId",-1L),
                            downloadUri=o.optString("downloadUri"), isDownloadable=o.optBoolean("isDownloadable",false)
                        )
                        val playable = when (source) {
                            Source.YOUTUBE -> track.videoId.isNotBlank()
                            Source.LOCAL -> track.contentUri.isNotBlank()
                            Source.AUDIUS, Source.RADIO -> track.streamUrl.isNotBlank()
                        }
                        if (playable) add(track)
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}
