package com.studiokinematics.nexa.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.studiokinematics.nexa.BuildConfig
import com.studiokinematics.nexa.model.SearchMode
import com.studiokinematics.nexa.model.Source
import com.studiokinematics.nexa.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class RadioBrowserRepository {
    private val bootstrap = "https://all.api.radio-browser.info"
    private val fallbackServers = listOf("https://de1.api.radio-browser.info", "https://nl1.api.radio-browser.info")
    @Volatile private var cachedServer: String? = null

    suspend fun home(): List<Track> = withContext(Dispatchers.IO) {
        val india = safeRequest("/json/stations/bycountrycodeexact/IN?hidebroken=true&order=votes&reverse=true&limit=70")
        val global = safeRequest("/json/stations/topvote/40?hidebroken=true")
        (india + global).distinctBy { it.id }.take(90)
    }

    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val q = enc(query.trim())
        (safeRequest("/json/stations/search?name=$q&hidebroken=true&order=votes&reverse=true&limit=35") +
            safeRequest("/json/stations/search?language=$q&hidebroken=true&order=votes&reverse=true&limit=35") +
            safeRequest("/json/stations/search?tag=$q&hidebroken=true&order=votes&reverse=true&limit=35"))
            .distinctBy { it.id }.take(80)
    }

    suspend fun registerClick(stationUuid: String) = withContext(Dispatchers.IO) {
        runCatching { fetchText("${server()}/json/url/${enc(stationUuid)}") }; Unit
    }

    private fun safeRequest(path: String): List<Track> {
        val bases = buildList { runCatching { add(server()) }; fallbackServers.forEach { if (it !in this) add(it) } }
        var last: Throwable? = null
        for (base in bases) try {
            val result = parseStations(fetchText(base + path))
            if (result.isNotEmpty()) { cachedServer = base; return result }
        } catch (t: Throwable) { last = t }
        if (last != null) throw last
        return emptyList()
    }

    private fun server(): String {
        cachedServer?.let { return it }
        return runCatching {
            val arr = JSONArray(fetchText("$bootstrap/json/servers"))
            for (i in 0 until arr.length()) {
                val name = arr.optJSONObject(i)?.optString("name").orEmpty()
                if (name.isNotBlank()) {
                    val base = "https://$name"; cachedServer = base; return@runCatching base
                }
            }
            fallbackServers.first()
        }.getOrElse { fallbackServers.first() }
    }

    private fun fetchText(url: String): String {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 9_000; readTimeout = 13_000
            setRequestProperty("User-Agent", "NEXA/0.4 Android (Studio Kinematic)")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }

    private fun parseStations(json: String): List<Track> {
        val arr = JSONArray(json); val out = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("stationuuid").trim()
            val name = o.optString("name").trim().replace(Regex("\\s+"), " ")
            val stream = o.optString("url_resolved").trim().ifBlank { o.optString("url").trim() }
            if (id.isBlank() || name.isBlank() || !(stream.startsWith("https://") || stream.startsWith("http://"))) continue
            val countryCode = o.optString("countrycode").trim().uppercase()
            val country = o.optString("country").trim()
            val tags = o.optString("tags").split(',').map { it.trim() }.filter { it.isNotBlank() }
            out += Track(
                id="radio:$id", title=name,
                artist=listOf("Live radio", country.ifBlank { countryCode }).filter { it.isNotBlank() }.joinToString(" • "),
                language=o.optString("language").trim().ifBlank { "Music" },
                genre=tags.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Live",
                streamUrl=stream, source=Source.RADIO, countryCode=countryCode,
                homepage=o.optString("homepage").trim(), codec=o.optString("codec").trim(),
                bitrate=o.optInt("bitrate",0), artworkUrl=o.optString("favicon").trim()
            )
        }
        return out.distinctBy { it.id }
    }

    private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}

class AudiusRepository {
    private val base = "https://api.audius.co/v1"

    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val url = "$base/tracks/search?query=${enc(query)}&limit=30&sort_method=relevance"
        runCatching { parse(fetch(url)) }.getOrDefault(emptyList())
    }

    private fun fetch(url: String): String {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 9_000; readTimeout = 13_000
            setRequestProperty("User-Agent", "NEXA/0.4 Android (Studio Kinematic)")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }

    private fun parse(json: String): List<Track> {
        val arr = JSONObject(json).optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val rawId=o.optString("id").trim(); val title=o.optString("title").trim()
                if(rawId.isBlank() || title.isBlank() || !o.optBoolean("is_streamable",true)) continue
                val artwork=o.optJSONObject("artwork")
                val image=artwork?.optString("1000x1000").orEmpty()
                    .ifBlank { artwork?.optString("480x480").orEmpty() }
                    .ifBlank { artwork?.optString("150x150").orEmpty() }
                val artist=o.optJSONObject("user")?.optString("name").orEmpty().trim().ifBlank{"Audius artist"}
                val direct="$base/tracks/$rawId/stream"
                val downloadable=o.optBoolean("is_downloadable",o.optBoolean("downloadable",false))
                val download=if(downloadable)"$base/tracks/$rawId/download" else ""
                add(Track(
                    id="audius:$rawId", title=title, artist=artist,
                    language=o.optString("mood").ifBlank{"Open music"}, genre=o.optString("genre").ifBlank{"Music"},
                    streamUrl=direct, source=Source.AUDIUS, artworkUrl=image, thumbnailUrl=image,
                    homepage="https://audius.co", durationMs=o.optLong("duration",0L)*1000L,
                    album=o.optString("album_name"), downloadUri=download, isDownloadable=downloadable
                ))
            }
        }
    }

    private fun enc(value: String)=URLEncoder.encode(value,StandardCharsets.UTF_8.toString())
}

class YouTubeRepository(private val context: Context) {
    suspend fun search(query: String, mode: SearchMode): List<Track> = withContext(Dispatchers.IO) {
        val prefs=context.getSharedPreferences("nexa",Context.MODE_PRIVATE)
        val key=(prefs.getString("youtube_api_key","")?.trim().orEmpty()).ifBlank{BuildConfig.YOUTUBE_API_KEY.trim()}
        if(key.isBlank()) throw IllegalStateException("YouTube search is not configured. Open Settings and add a YouTube Data API key.")
        val q=when(mode){
            SearchMode.SONGS->"$query song"; SearchMode.ARTISTS->"$query official songs"
            SearchMode.ALBUMS->"$query album songs"; SearchMode.MOVIES->"$query movie soundtrack songs"
            SearchMode.ALL->query
        }
        val url="https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&videoCategoryId=10&maxResults=25&q=${enc(q)}&regionCode=IN&relevanceLanguage=en"
        parse(fetch(url,key))
    }

    private fun fetch(url:String,key:String):String{
        val conn=(URI(url).toURL().openConnection() as HttpURLConnection).apply{
            requestMethod="GET";connectTimeout=10_000;readTimeout=15_000
            setRequestProperty("User-Agent","NEXA/0.4 Android (Studio Kinematic)")
            setRequestProperty("Accept","application/json")
            setRequestProperty("x-goog-api-key",key)
            setRequestProperty("X-Android-Package",context.packageName)
            signingCertificateSha1()?.let{setRequestProperty("X-Android-Cert",it)}
        }
        return try{
            val code=conn.responseCode
            if(code !in 200..299){
                val body=conn.errorStream?.bufferedReader()?.use{it.readText()}.orEmpty()
                val msg=runCatching{JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()}.getOrDefault("")
                throw IllegalStateException(if(msg.isBlank())"YouTube search failed (HTTP $code)." else "YouTube: $msg")
            }
            conn.inputStream.bufferedReader().use{it.readText()}
        }finally{conn.disconnect()}
    }

    @Suppress("DEPRECATION")
    private fun signingCertificateSha1():String?=runCatching{
        val info=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P)
            context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
        else context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNATURES)
        val sig=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P)info.signingInfo?.apkContentsSigners?.firstOrNull()
        else info.signatures?.firstOrNull()
        sig?:return@runCatching null
        MessageDigest.getInstance("SHA-1").digest(sig.toByteArray()).joinToString(""){"%02X".format(it.toInt() and 0xFF)}
    }.getOrNull()

    private fun parse(json:String):List<Track>{
        val arr=JSONObject(json).optJSONArray("items")?:return emptyList()
        return buildList{
            for(i in 0 until arr.length()){
                val o=arr.optJSONObject(i)?:continue
                val id=o.optJSONObject("id")?.optString("videoId").orEmpty().trim()
                val sn=o.optJSONObject("snippet")?:continue
                val title=clean(sn.optString("title"));if(id.isBlank()||title.isBlank())continue
                val thumbs=sn.optJSONObject("thumbnails")
                val image=thumbs?.optJSONObject("high")?.optString("url").orEmpty()
                    .ifBlank{thumbs?.optJSONObject("medium")?.optString("url").orEmpty()}
                add(Track(
                    id="youtube:$id",title=title,artist=clean(sn.optString("channelTitle")).ifBlank{"YouTube"},
                    language="Online",genre="YouTube Music",source=Source.YOUTUBE,videoId=id,
                    artworkUrl=image,thumbnailUrl=image,homepage="https://www.youtube.com/watch?v=$id"
                ))
            }
        }
    }

    private fun clean(v:String)=v.replace("&amp;","&").replace("&quot;","\"").replace("&#39;","'").replace(Regex("\\s+")," ").trim()
    private fun enc(v:String)=URLEncoder.encode(v,StandardCharsets.UTF_8.toString())
}
