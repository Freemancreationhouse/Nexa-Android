package com.studiokinematics.nexa

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NexaApp(applicationContext) }
    }
}

enum class Source { RADIO, AUDIUS, YOUTUBE }

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val language: String,
    val genre: String,
    val streamUrl: String = "",
    val source: Source = Source.RADIO,
    val videoId: String = "",
    val thumbnailUrl: String = "",
    val countryCode: String = "",
    val homepage: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val durationMs: Long = 0L
)

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    EXPLORE("Search", Icons.Default.Search),
    RADIO("Radio", Icons.Default.Radio),
    LIBRARY("Library", Icons.Default.LibraryMusic)
}

enum class SearchMode(val label: String) { ALL("All"), SONGS("Songs"), ARTISTS("Artists"), ALBUMS("Albums"), MOVIES("Movies") }

private val Bg = Color(0xFF070A12)
private val Panel = Color(0xFF111725)
private val Panel2 = Color(0xFF171E31)
private val Purple = Color(0xFFA855F7)
private val Cyan = Color(0xFF22D3EE)
private val Pink = Color(0xFFEC4899)
private val Bright = Color(0xFFF8FAFC)
private val Soft = Color(0xFFD8DEEA)
private val Muted = Color(0xFFAEB7C7)
private val Danger = Color(0xFFFF7B8B)

private class PlayerController(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepJob: Job? = null

    var current by mutableStateOf<Track?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var liveTitle by mutableStateOf<String?>(null)
        private set
    var liveArtist by mutableStateOf<String?>(null)
        private set
    var playbackError by mutableStateOf<String?>(null)
        private set
    var sleepMinutes by mutableStateOf<Int?>(null)
        private set

    init {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(), true
        )
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) { isPlaying = value }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val title = mediaMetadata.title?.toString()?.trim().orEmpty()
                val artist = mediaMetadata.artist?.toString()?.trim().orEmpty()
                if (title.isNotBlank()) liveTitle = title
                if (artist.isNotBlank()) liveArtist = artist
            }
            override fun onPlayerError(error: PlaybackException) {
                playbackError = "This audio stream is unavailable. Try another result."
            }
        })
    }

    val displayTitle: String get() = liveTitle?.takeIf { it.isNotBlank() } ?: current?.title.orEmpty()
    val displayArtist: String get() = liveArtist?.takeIf { it.isNotBlank() } ?: current?.artist.orEmpty()

    fun play(track: Track) {
        current = track
        liveTitle = null
        liveArtist = null
        playbackError = null
        if (track.source == Source.YOUTUBE) return
        val metadata = MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build()
        val item = MediaItem.Builder().setUri(track.streamUrl).setMediaMetadata(metadata).build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    fun toggle() { if (player.isPlaying) player.pause() else player.play() }

    fun seekTo(ms: Long) = player.seekTo(ms)

    fun cycleSleepTimer() {
        val next = when (sleepMinutes) { null -> 15; 15 -> 30; 30 -> 60; else -> null }
        sleepJob?.cancel()
        sleepMinutes = next
        if (next != null) sleepJob = scope.launch { delay(next * 60_000L); player.pause(); sleepMinutes = null }
    }

    fun release() { scope.cancel(); player.release() }
}

private class RadioBrowserRepository {
    private val bootstrap = "https://all.api.radio-browser.info"
    private val fallbackServers = listOf("https://de1.api.radio-browser.info", "https://nl1.api.radio-browser.info")
    @Volatile private var cachedServer: String? = null

    suspend fun home(): List<Track> = withContext(Dispatchers.IO) {
        val india = safeRequest("/json/stations/bycountrycodeexact/IN?hidebroken=true&order=votes&reverse=true&limit=70")
        val global = safeRequest("/json/stations/topvote/40?hidebroken=true")
        (india + global).distinctBy { it.id }.take(90)
    }
    suspend fun byLanguage(language: String): List<Track> = withContext(Dispatchers.IO) { safeRequest("/json/stations/search?language=${enc(language)}&hidebroken=true&order=votes&reverse=true&limit=80") }
    suspend fun byTag(tag: String): List<Track> = withContext(Dispatchers.IO) { safeRequest("/json/stations/search?tag=${enc(tag)}&hidebroken=true&order=votes&reverse=true&limit=80") }
    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val q = enc(query.trim())
        (safeRequest("/json/stations/search?name=$q&hidebroken=true&order=votes&reverse=true&limit=35") +
                safeRequest("/json/stations/search?language=$q&hidebroken=true&order=votes&reverse=true&limit=35") +
                safeRequest("/json/stations/search?tag=$q&hidebroken=true&order=votes&reverse=true&limit=35"))
            .distinctBy { it.id }.take(80)
    }
    suspend fun registerClick(stationUuid: String) = withContext(Dispatchers.IO) { runCatching { fetchText("${server()}/json/url/${enc(stationUuid)}") }; Unit }

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
                if (name.isNotBlank()) { val base = "https://$name"; cachedServer = base; return@runCatching base }
            }
            fallbackServers.first()
        }.getOrElse { fallbackServers.first() }
    }
    private fun fetchText(url: String): String {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 9_000; readTimeout = 13_000
            setRequestProperty("User-Agent", "NEXA/0.3 Android (Studio Kinematics)")
            setRequestProperty("Accept", "application/json")
        }
        return try { if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}"); conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
    }
    private fun parseStations(json: String): List<Track> {
        val arr = JSONArray(json); val out = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("stationuuid").trim(); val name = o.optString("name").trim().replace(Regex("\\s+"), " ")
            val stream = o.optString("url_resolved").trim().ifBlank { o.optString("url").trim() }
            if (id.isBlank() || name.isBlank() || !(stream.startsWith("https://") || stream.startsWith("http://"))) continue
            val language = o.optString("language").trim().ifBlank { "Music" }
            val tags = o.optString("tags").split(',').map { it.trim() }.filter { it.isNotBlank() }
            val genre = tags.firstOrNull()?.replaceFirstChar { c -> c.uppercase() } ?: "Live"
            val countryCode = o.optString("countrycode").trim().uppercase(); val country = o.optString("country").trim()
            out += Track(id, name, listOf("Live radio", country.ifBlank { countryCode }).filter { it.isNotBlank() }.joinToString(" • "), language, genre, stream, Source.RADIO, countryCode = countryCode, homepage = o.optString("homepage").trim(), codec = o.optString("codec").trim(), bitrate = o.optInt("bitrate", 0))
        }
        return out.distinctBy { it.id }
    }
    private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}

private class AudiusRepository {
    private val base = "https://api.audius.co/v1"
    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val url = "$base/tracks/search?query=${enc(query)}&limit=30&sort_method=relevance"
        runCatching { parse(fetch(url)) }.getOrDefault(emptyList())
    }
    private fun fetch(url: String): String {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 9_000; readTimeout = 13_000
            setRequestProperty("User-Agent", "NEXA/0.3 Android (Studio Kinematics)")
            setRequestProperty("Accept", "application/json")
        }
        return try { if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}"); conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
    }
    private fun parse(json: String): List<Track> {
        val root = JSONObject(json); val arr = root.optJSONArray("data") ?: return emptyList(); val out = ArrayList<Track>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").trim(); val title = o.optString("title").trim(); val artist = o.optJSONObject("user")?.optString("name").orEmpty().trim()
            if (id.isBlank() || title.isBlank()) continue
            val duration = o.optLong("duration", 0L) * 1000L
            val stream = "https://api.audius.co/v1/tracks/$id/stream"
            out += Track("audius:$id", title, artist.ifBlank { "Audius artist" }, o.optString("mood").ifBlank { "Open music" }, o.optString("genre").ifBlank { "Music" }, stream, Source.AUDIUS, thumbnailUrl = o.optString("artwork").let { if (it.isNotBlank()) it else "" }, homepage = "https://audius.co", durationMs = duration)
        }
        return out
    }
    private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}

private class YouTubeRepository(private val context: Context) {
    suspend fun search(query: String, mode: SearchMode): List<Track> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("nexa", Context.MODE_PRIVATE)
        val key = (prefs.getString("youtube_api_key", "")?.trim().orEmpty()).ifBlank { BuildConfig.YOUTUBE_API_KEY.trim() }
        if (key.isBlank()) throw IllegalStateException("YouTube search is not configured. Open Settings and add a YouTube Data API key.")
        val q = when (mode) {
            SearchMode.SONGS -> "$query song"
            SearchMode.ARTISTS -> "$query official songs"
            SearchMode.ALBUMS -> "$query album songs"
            SearchMode.MOVIES -> "$query movie soundtrack songs"
            SearchMode.ALL -> query
        }
        val url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&videoCategoryId=10&maxResults=25&q=${enc(q)}&regionCode=IN&relevanceLanguage=en"
        parse(fetch(url, key))
    }

    private fun fetch(url: String, key: String): String {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("User-Agent", "NEXA/0.3.2 Android (Studio Kinematics)")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-goog-api-key", key)
            setRequestProperty("X-Android-Package", context.packageName)
            signingCertificateSha1()?.let { setRequestProperty("X-Android-Cert", it) }
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val body = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val apiMessage = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
                }.getOrDefault("")
                throw IllegalStateException(if (apiMessage.isBlank()) "YouTube search failed (HTTP $code)." else "YouTube: $apiMessage")
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }

    @Suppress("DEPRECATION")
    private fun signingCertificateSha1(): String? = runCatching {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            packageInfo.signatures?.firstOrNull()
        } ?: return@runCatching null
        MessageDigest.getInstance("SHA-1").digest(signature.toByteArray()).joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    }.getOrNull()

    private fun parse(json: String): List<Track> {
        val arr = JSONObject(json).optJSONArray("items") ?: return emptyList(); val out = ArrayList<Track>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue; val id = o.optJSONObject("id")?.optString("videoId").orEmpty().trim(); val s = o.optJSONObject("snippet") ?: continue
            val title = clean(s.optString("title")); val channel = clean(s.optString("channelTitle")); val thumb = s.optJSONObject("thumbnails")?.optJSONObject("high")?.optString("url").orEmpty()
            if (id.isBlank() || title.isBlank()) continue
            out += Track("youtube:$id", title, channel.ifBlank { "YouTube" }, "Online", "YouTube Music", source = Source.YOUTUBE, videoId = id, thumbnailUrl = thumb, homepage = "https://www.youtube.com/watch?v=$id")
        }
        return out
    }
    private fun clean(value: String) = value.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace(Regex("\\s+"), " ").trim()
    private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}

@Composable
fun NexaApp(context: Context) {
    val controller = remember { PlayerController(context) }
    val radio = remember { RadioBrowserRepository() }
    val audius = remember { AudiusRepository() }
    val youtube = remember(context) { YouTubeRepository(context) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { controller.release() } }

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showPlayer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showYoutube by remember { mutableStateOf<Track?>(null) }
    val prefs = remember { context.getSharedPreferences("nexa", Context.MODE_PRIVATE) }
    var youtubeConsent by rememberSaveable { mutableStateOf(prefs.getBoolean("youtube_consent", false)) }
    var favorites by remember { mutableStateOf(loadTracks(prefs.getString("favorite_tracks", null))) }
    var recent by remember { mutableStateOf(loadTracks(prefs.getString("recent_tracks", null))) }
    var catalog by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true; loadError = null
        try { catalog = radio.home(); if (catalog.isEmpty()) loadError = "No live stations were returned. Tap retry." }
        catch (_: Throwable) { loadError = "Could not reach the live station directory. Check internet and retry." }
        finally { loading = false }
    }

    fun play(track: Track) {
        recent = (listOf(track) + recent.filterNot { it.id == track.id }).take(30)
        prefs.edit().putString("recent_tracks", saveTracks(recent)).apply()
        if (track.source == Source.YOUTUBE) { showYoutube = track; showPlayer = false }
        else { showYoutube = null; controller.play(track); showPlayer = true; if (track.source == Source.RADIO) scope.launch { radio.registerClick(track.id) } }
    }
    fun toggleFavorite(track: Track) {
        favorites = if (favorites.any { it.id == track.id }) favorites.filterNot { it.id == track.id } else listOf(track) + favorites
        prefs.edit().putString("favorite_tracks", saveTracks(favorites)).apply()
    }

    val favoriteIds = favorites.mapTo(mutableSetOf()) { it.id }
    val queue = (catalog + favorites + recent).distinctBy { it.id }

    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Panel, primary = Purple, secondary = Cyan, tertiary = Pink, onBackground = Bright, onSurface = Bright, onPrimary = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Bright) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            Tab.HOME -> HomeScreen(catalog, loading, loadError, { refreshKey++ }, { tab = Tab.EXPLORE }, ::play, favoriteIds, ::toggleFavorite, { showSettings = true })
                            Tab.EXPLORE -> ExploreScreen(radio, audius, youtube, ::play, favoriteIds, ::toggleFavorite, { showSettings = true })
                            Tab.RADIO -> RadioScreen(catalog, loading, loadError, { refreshKey++ }, ::play, favoriteIds, ::toggleFavorite)
                            Tab.LIBRARY -> LibraryScreen(favorites, recent, ::play, ::toggleFavorite)
                        }
                    }
                    AnimatedVisibility(controller.current != null && showPlayer && showYoutube == null) {
                        controller.current?.let { MiniPlayer(controller.displayTitle, controller.displayArtist, controller.isPlaying, controller::toggle, { showPlayer = true }) }
                    }
                    BottomBar(tab) { tab = it }
                }

                if (showPlayer && controller.current != null && showYoutube == null) {
                    val current = controller.current!!
                    NowPlaying(current, controller, current.id in favoriteIds, { toggleFavorite(current) }, { showPlayer = false }, {
                        val index = queue.indexOfFirst { it.id == current.id }; if (queue.isNotEmpty()) play(queue[if (index <= 0) queue.lastIndex else index - 1])
                    }, {
                        val index = queue.indexOfFirst { it.id == current.id }; if (queue.isNotEmpty()) play(queue[if (index < 0 || index >= queue.lastIndex) 0 else index + 1])
                    })
                }

                showYoutube?.let { track -> YouTubePlayerScreen(track, { showYoutube = null }, { toggleFavorite(track) }, track.id in favoriteIds, context) }
                if (!youtubeConsent) {
                    YouTubeConsentDialog(
                        onAccept = {
                            prefs.edit().putBoolean("youtube_consent", true).apply()
                            youtubeConsent = true
                        }
                    )
                }
                if (showSettings) SettingsDialog(prefs, { showSettings = false })
            }
        }
    }
}

@Composable
private fun TopBrand(subtitle: String, onSearch: (() -> Unit)? = null, onSettings: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Purple, Pink, Cyan))), contentAlignment = Alignment.Center) { Text("N", fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color.White) }
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("NEXA", fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = Bright); Text(subtitle, fontSize = 12.sp, color = Muted) }
        if (onSearch != null) IconButton(onClick = onSearch) { Icon(Icons.Default.Search, null, tint = Bright) }
        if (onSettings != null) IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, null, tint = Bright) }
    }
}

@Composable
private fun HomeScreen(stations: List<Track>, loading: Boolean, error: String?, onRetry: () -> Unit, onSearch: () -> Unit, onPlay: (Track) -> Unit, favorites: Set<String>, onFav: (Track) -> Unit, onSettings: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { TopBrand("Music beyond borders", onSearch, onSettings) }
        item {
            Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(180.dp).clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF35105F), Color(0xFF0D4F6B), Color(0xFF8E164D)))).padding(22.dp)) {
                Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(.78f)) {
                    Surface(color = Color.White.copy(.13f), shape = RoundedCornerShape(20.dp)) { Text("SEARCH • STREAM • DISCOVER", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Bright, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                    Spacer(Modifier.height(10.dp)); Text("Find your song.\nFind your sound.", fontSize = 30.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black, color = Bright)
                    Text("Songs, artists, movies + live radio.", color = Soft, fontSize = 13.sp)
                }
                FilledIconButton(onClick = onSearch, modifier = Modifier.align(Alignment.BottomEnd).size(54.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)) { Icon(Icons.Default.Search, null, modifier = Modifier.size(30.dp)) }
            }
        }
        if (loading) item { LoadingBlock("Finding live stations…") }
        if (error != null) item { ErrorBlock(error, onRetry) }
        item { SectionTitle("Live now", "Radio is still here when you want it") }
        items(stations.take(12), key = { it.id }) { TrackRow(it, it.id in favorites, { onPlay(it) }, { onFav(it) }) }
        if (stations.isNotEmpty()) item { SourceFooter() }
    }
}

@Composable
private fun ExploreScreen(radio: RadioBrowserRepository, audius: AudiusRepository, youtube: YouTubeRepository, onPlay: (Track) -> Unit, favorites: Set<String>, onFav: (Track) -> Unit, onSettings: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(SearchMode.ALL) }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var providerNotes by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val languages = listOf("Hindi", "English", "Punjabi", "Tamil", "Telugu", "Malayalam", "Bengali", "Marathi", "Gujarati", "Kannada")

    fun searchNow(value: String = query) {
        val q = value.trim(); if (q.isBlank()) return
        loading = true; error = null; providerNotes = emptyList()
        // A provider failure must never cancel the whole search coroutine or close the app.
        scope.launch {
            try {
                val providerResults = supervisorScope {
                    val yt = async { runCatching { youtube.search(q, mode) } }
                    val au = async { runCatching { audius.search(q) } }
                    val rb = async { runCatching { radio.search(q) } }
                    Triple(yt.await(), au.await(), rb.await())
                }
                val ytResult = providerResults.first
                val auResult = providerResults.second
                val rbResult = providerResults.third
                val combined = (
                    ytResult.getOrDefault(emptyList()) +
                    auResult.getOrDefault(emptyList()) +
                    rbResult.getOrDefault(emptyList())
                ).distinctBy { it.id }
                results = combined

                providerNotes = buildList {
                    ytResult.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }?.let { add(it) }
                    auResult.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }?.let { add("Audius: $it") }
                    rbResult.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }?.let { add("Radio: $it") }
                }.distinct()

                error = when {
                    combined.isNotEmpty() -> null
                    providerNotes.any { it.startsWith("YouTube search is not configured") } ->
                        "Mainstream song search needs the YouTube Data API key. Tap the settings icon, add the key once, then search again. Radio and open-catalog sources remain independent."
                    providerNotes.isNotEmpty() -> "No provider returned a result. ${providerNotes.joinToString(" • ")}"
                    else -> "No results. Try the song title, artist, movie or a shorter search."
                }
            } catch (t: Throwable) {
                // Final UI boundary: unexpected search errors are shown instead of escaping the Main coroutine.
                results = emptyList()
                error = "Search could not finish: ${t.message ?: "unknown error"}. Please retry."
            } finally { loading = false }
        }
    }


    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { TopBrand("Search songs, artists, movies", onSettings = onSettings) }
        item {
            Row(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp)).background(Panel2).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = Cyan); Spacer(Modifier.width(10.dp))
                BasicTextField(value = query, onValueChange = { query = it }, singleLine = true, textStyle = TextStyle(color = Bright, fontSize = 15.sp), cursorBrush = Brush.verticalGradient(listOf(Cyan, Cyan)), modifier = Modifier.weight(1f), decorationBox = { inner -> if (query.isEmpty()) Text("Song, artist, movie, album…", color = Muted); inner() })
                IconButton(onClick = { searchNow() }) { Icon(Icons.Default.ArrowForward, null, tint = Bright) }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SearchMode.values().toList()) { m -> FilterPill(m.label, m == mode) { mode = m; if (query.isNotBlank()) searchNow() } }
            }
        }
        item { SectionTitle("Search examples", "Try a name exactly like you would in a music app") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Arijit Singh", "Tum Hi Ho", "Kabir Singh soundtrack", "A R Rahman", "Punjabi hits", "Tamil songs")) { example -> FilterPill(example, false) { query = example; searchNow(example) } }
            }
        }
        item { SectionTitle(if (query.isBlank()) "Languages" else "Results", if (query.isBlank()) "Browse live + open music" else "${results.size} matches") }
        if (query.isBlank()) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(languages) { label -> Box(Modifier.width(108.dp).height(66.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Purple.copy(.55f), Cyan.copy(.25f)))).clickable { query = "$label songs"; searchNow() }.padding(12.dp), contentAlignment = Alignment.BottomStart) { Text(label, fontWeight = FontWeight.Bold, color = Bright) } }
                }
            }
        }
        if (loading) item { LoadingBlock("Searching music sources…") }
        error?.let { msg -> item { ErrorBlock(msg, null) } }
        if (providerNotes.any { it.startsWith("YouTube search is not configured") }) {
            item {
                Button(
                    onClick = onSettings,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp).fillMaxWidth()
                ) { Icon(Icons.Default.Settings, null); Spacer(Modifier.width(8.dp)); Text("Configure mainstream music search") }
            }
        }
        if (providerNotes.isNotEmpty() && results.isNotEmpty()) {
            item { ProviderStatusBlock(providerNotes) }
        }
        items(results, key = { it.id }) { track -> SearchResultRow(track, track.id in favorites, { onPlay(track) }, { onFav(track) }) }
        if (results.isNotEmpty()) item { Text("YouTube results play in the official embedded YouTube player. Audius results use native NEXA playback.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(18.dp)) }
    }
}


@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) { Surface(color = if (selected) Purple.copy(.55f) else Panel2, shape = RoundedCornerShape(50), modifier = Modifier.clickable(onClick = onClick)) { Text(label, color = Bright, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) } }

@Composable
private fun SearchResultRow(track: Track, favorite: Boolean, onPlay: () -> Unit, onFav: () -> Unit) {
    Row(Modifier.padding(horizontal = 18.dp, vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).clickable(onClick = onPlay).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Purple, Pink, Cyan))), contentAlignment = Alignment.Center) { Icon(if (track.source == Source.YOUTUBE) Icons.Default.PlayCircle else if (track.source == Source.AUDIUS) Icons.Default.MusicNote else Icons.Default.Radio, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
            Text(track.title, fontWeight = FontWeight.ExtraBold, color = Bright, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
            Text(track.artist, color = Soft, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            Text(when (track.source) { Source.YOUTUBE -> "YouTube • video player"; Source.AUDIUS -> "Audius • direct audio"; Source.RADIO -> "Radio • live" }, color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onFav) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorite) Pink else Muted) }
        Icon(Icons.Default.PlayArrow, null, tint = Bright)
    }
}

@Composable
private fun RadioScreen(stations: List<Track>, loading: Boolean, error: String?, onRetry: () -> Unit, onPlay: (Track) -> Unit, favorites: Set<String>, onFav: (Track) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { TopBrand("Live & endless") }
        item { Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Color(0xFF22154F), Color(0xFF0D5061)))).padding(20.dp)) { Column { Text("NEXA RADIO", color = Cyan, fontWeight = FontWeight.Bold, letterSpacing = 2.sp); Text("Always on.", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Bright); Text("Real live streams. No demo tracks, no login wall.", color = Soft) } } }
        item { SectionTitle("Live stations", "Tap any station to start") }
        if (loading) item { LoadingBlock("Tuning the live directory…") }
        if (error != null) item { ErrorBlock(error, onRetry) }
        items(stations, key = { it.id }) { track -> RadioRow(track, track.id in favorites, { onPlay(track) }, { onFav(track) }) }
    }
}

@Composable
private fun LibraryScreen(favorites: List<Track>, recent: List<Track>, onPlay: (Track) -> Unit, onToggleFavorite: (Track) -> Unit) {
    val favIds = favorites.mapTo(mutableSetOf()) { it.id }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { TopBrand("Your music, on this device") }
        item { Row(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { StatCard("${favorites.size}", "Liked", Icons.Default.Favorite, Modifier.weight(1f)); StatCard("${recent.size}", "Recent", Icons.Default.History, Modifier.weight(1f)) } }
        item { SectionTitle("Liked music", if (favorites.isEmpty()) "Tap the heart on any result" else "Saved locally") }
        if (favorites.isEmpty()) item { EmptyState("Nothing liked yet", "Favorites stay on this phone — no account needed.") }
        items(favorites, key = { it.id }) { TrackRow(it, true, { onPlay(it) }, { onToggleFavorite(it) }) }
        item { SectionTitle("Recently played", "Saved locally") }
        if (recent.isEmpty()) item { EmptyState("Nothing played yet", "Search a song or start a station.") }
        items(recent, key = { it.id }) { TrackRow(it, it.id in favIds, { onPlay(it) }, { onToggleFavorite(it) }) }
    }
}

@Composable
private fun TrackRow(track: Track, favorite: Boolean, onPlay: () -> Unit, onFav: () -> Unit) {
    Row(Modifier.padding(horizontal = 18.dp, vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).clickable(onClick = onPlay).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Purple, Pink))), contentAlignment = Alignment.Center) { Icon(if (track.source == Source.RADIO) Icons.Default.Radio else Icons.Default.MusicNote, null, tint = Color.White) }
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(track.title, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Bright, fontSize = 15.sp); Text(track.artist, color = Soft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(sourceLabel(track), color = Cyan, fontSize = 10.sp) }
        IconButton(onClick = onFav) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorite) Pink else Muted) }; Icon(Icons.Default.PlayArrow, null, tint = Bright)
    }
}

@Composable
private fun RadioRow(track: Track, favorite: Boolean, onPlay: () -> Unit, onFav: () -> Unit) {
    Row(Modifier.padding(horizontal = 18.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).clickable(onClick = onPlay).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(Purple.copy(.25f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Radio, null, tint = Cyan) }
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(track.title, fontWeight = FontWeight.ExtraBold, color = Bright, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(buildString { append(track.language); if (track.codec.isNotBlank()) append(" • ${track.codec}"); if (track.bitrate > 0) append(" • ${track.bitrate} kbps") }, color = Soft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        IconButton(onClick = onFav) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorite) Pink else Muted) }
        FilledIconButton(onClick = onPlay, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(.12f), contentColor = Bright)) { Icon(Icons.Default.PlayArrow, null) }
    }
}

@Composable
private fun MiniPlayer(title: String, subtitle: String, playing: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Panel2).clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Purple, Pink, Cyan))), contentAlignment = Alignment.Center) { Icon(Icons.Default.GraphicEq, null, tint = Color.White) }
        Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(title, color = Bright, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(subtitle, color = Soft, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        IconButton(onClick = onToggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Bright) }
    }
}

@Composable
private fun NowPlaying(track: Track, controller: PlayerController, isFavorite: Boolean, onFavorite: () -> Unit, onClose: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    var position by remember { mutableLongStateOf(0L) }; var duration by remember { mutableLongStateOf(0L) }; var repeat by remember { mutableStateOf(false) }; var shuffle by remember { mutableStateOf(false) }; val context = LocalContext.current
    LaunchedEffect(track.id) { while (true) { position = controller.player.currentPosition.coerceAtLeast(0); duration = controller.player.duration.takeIf { it > 0 && it < 86_400_000L } ?: 0L; delay(500) } }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1C0D2E), Bg, Color(0xFF071724)))).padding(22.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Bright) }; Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text(sourceLabel(track).uppercase(), color = Muted, fontSize = 10.sp); Text(track.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Bright, maxLines = 1, overflow = TextOverflow.Ellipsis) }; IconButton(onClick = { share(context, track.homepage.ifBlank { track.title }, "Share on NEXA") }) { Icon(Icons.Default.Share, null, tint = Bright) } }
            Spacer(Modifier.height(28.dp)); Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(36.dp)).background(Brush.linearGradient(listOf(Purple, Color(0xFF47216B), Pink, Color(0xFF0D6571)))), contentAlignment = Alignment.Center) { Icon(Icons.Default.GraphicEq, null, modifier = Modifier.size(120.dp), tint = Color.White.copy(.92f)) }
            Spacer(Modifier.height(26.dp)); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(controller.displayTitle, fontSize = 25.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Bright); Text(controller.displayArtist, color = Soft, maxLines = 1, overflow = TextOverflow.Ellipsis) }; IconButton(onClick = onFavorite) { Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (isFavorite) Pink else Bright) } }
            controller.playbackError?.let { Text(it, color = Danger, fontSize = 12.sp) }; Spacer(Modifier.height(12.dp))
            if (duration > 0L) { Slider(value = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f), onValueChange = { controller.seekTo((duration * it).toLong()) }); Row(Modifier.fillMaxWidth()) { Text(formatTime(position), color = Muted, fontSize = 11.sp); Spacer(Modifier.weight(1f)); Text(formatTime(duration), color = Muted, fontSize = 11.sp) } } else { Surface(color = Pink.copy(.18f), shape = RoundedCornerShape(20.dp)) { Text("● LIVE", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Pink, fontWeight = FontWeight.Bold, fontSize = 11.sp) } }
            Spacer(Modifier.height(18.dp)); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) { IconButton(onClick = { shuffle = !shuffle; controller.player.shuffleModeEnabled = shuffle }) { Icon(Icons.Default.Shuffle, null, tint = if (shuffle) Cyan else Muted) }; IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(34.dp), tint = Bright) }; FilledIconButton(onClick = controller::toggle, modifier = Modifier.size(72.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)) { Icon(if (controller.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(38.dp)) }; IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(34.dp), tint = Bright) }; IconButton(onClick = { repeat = !repeat; controller.player.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }) { Icon(Icons.Default.Repeat, null, tint = if (repeat) Pink else Muted) } }
            Spacer(Modifier.height(24.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) { PlayerAction(Icons.Default.Timer, controller.sleepMinutes?.let { "$it min" } ?: "Timer") { controller.cycleSleepTimer() }; PlayerAction(Icons.Default.Equalizer, "EQ") { runCatching { context.startActivity(Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply { putExtra(AudioEffect.EXTRA_AUDIO_SESSION, controller.player.audioSessionId) }) } }; PlayerAction(Icons.Default.Share, "Share") { share(context, track.homepage.ifBlank { track.title }, "Share") } }
        }
    }
}

@Composable
private fun YouTubePlayerScreen(track: Track, onClose: () -> Unit, onFavorite: () -> Unit, favorite: Boolean, context: Context) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Bright) }; Column(Modifier.weight(1f)) { Text(track.title, color = Bright, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(track.artist, color = Soft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }; IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorite) Pink else Bright) } }
            AndroidView(factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true; settings.domStorageEnabled = true; settings.mediaPlaybackRequiresUserGesture = false; settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webChromeClient = WebChromeClient(); webViewClient = WebViewClient()
                    setBackgroundColor(android.graphics.Color.BLACK)
                    val destination = "https://www.youtube.com/embed/${track.videoId}?autoplay=1&playsinline=1&controls=1&rel=0&enablejsapi=1"
                    loadUrl(destination, mapOf("Referer" to "https://com.studiokinematics.nexa"))
                }
            }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Column(Modifier.padding(20.dp)) {
                Text(track.title, color = Bright, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Soft, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Text("YouTube playback", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("The official YouTube player and its controls remain visible. Background/audio-only playback is not enabled.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track.homepage))) }) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Open in YouTube") }
            }
        }
    }
}

@Composable
private fun YouTubeConsentDialog(onAccept: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text("NEXA + YouTube", color = Bright) },
        text = {
            Column {
                Text("NEXA can search mainstream music through YouTube and play the selected video using the official embedded YouTube player.", color = Soft)
                Spacer(Modifier.height(10.dp))
                Text("No NEXA login is required. YouTube playback keeps its own player, controls and ads where applicable.", color = Bright, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("NEXA stores favorites and recently played items locally on this device. Search requests are sent to the selected music source only when you search.", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/t/terms"))) }) { Text("YouTube Terms") }
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://policies.google.com/privacy"))) }) { Text("Google Privacy") }
                }
            }
        },
        confirmButton = { Button(onClick = onAccept) { Text("I agree") } }
    )
}

@Composable
private fun SettingsDialog(prefs: android.content.SharedPreferences, onClose: () -> Unit) {
    var key by remember { mutableStateOf(prefs.getString("youtube_api_key", "").orEmpty()) }
    val hasBuildKey = BuildConfig.YOUTUBE_API_KEY.isNotBlank()
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("NEXA Search Sources", color = Bright) },
        text = {
            Column {
                Text("Search works across YouTube, Audius and Radio Browser. YouTube provides the large mainstream song/movie/artist catalog; Audius provides direct open-catalog audio; Radio Browser provides live stations.", color = Soft)
                Spacer(Modifier.height(12.dp))
                Text("YouTube Data API key", color = Bright, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    singleLine = true,
                    placeholder = { Text("Paste your restricted Google API key") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (key.isNotBlank() || hasBuildKey) "YouTube search is configured."
                    else "Add a YouTube Data API key to enable mainstream song, artist, album and movie searches. No listener login is required.",
                    color = if (key.isNotBlank() || hasBuildKey) Cyan else Muted,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { openUrl(context = context, url = "https://console.cloud.google.com/apis/library/youtube.googleapis.com") }) { Text("Get API") }
                    TextButton(onClick = { openUrl(context = context, url = "https://www.youtube.com/t/terms") }) { Text("YouTube Terms") }
                    TextButton(onClick = { openUrl(context = context, url = "https://policies.google.com/privacy") }) { Text("Privacy") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
        confirmButton = {
            Button(onClick = {
                prefs.edit().putString("youtube_api_key", key.trim()).apply()
                onClose()
            }) { Text("Save") }
        }
    )
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Bright)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(22.dp)).background(Panel).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Purple.copy(.2f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Pink)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Bright)
            Text(label, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.AutoAwesome, null, tint = Purple, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.Bold, color = Bright)
        Text(body, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun LoadingBlock(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Cyan)
        Spacer(Modifier.width(12.dp))
        Text(text, color = Soft)
    }
}

@Composable
private fun ProviderStatusBlock(notes: List<String>) {
    Column(
        Modifier.padding(horizontal = 18.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF122033)).padding(12.dp)
    ) {
        Text("Some sources are unavailable", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        notes.take(3).forEach { note -> Text("• $note", color = Soft, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)) }
    }
}

@Composable
private fun ErrorBlock(text: String, onRetry: (() -> Unit)?) {
    Row(
        Modifier.padding(horizontal = 18.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF2A1420)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.WifiOff, null, tint = Danger)
        Spacer(Modifier.width(10.dp))
        Text(text, modifier = Modifier.weight(1f), color = Soft, fontSize = 13.sp)
        if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry", color = Cyan) }
    }
}

@Composable
private fun SourceFooter() {
    Text(
        "Music search: YouTube + Audius • Live directory: Radio Browser.",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        color = Muted,
        fontSize = 10.sp
    )
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = Color(0xFF090E18), tonalElevation = 0.dp) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, null) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Pink,
                    selectedTextColor = Pink,
                    indicatorColor = Purple.copy(.18f),
                    unselectedIconColor = Muted,
                    unselectedTextColor = Muted
                )
            )
        }
    }
}

@Composable
private fun PlayerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Soft)
        Spacer(Modifier.height(5.dp))
        Text(label, color = Soft, fontSize = 11.sp)
    }
}

private fun sourceLabel(track: Track): String = when (track.source) {
    Source.YOUTUBE -> "YouTube • video"
    Source.AUDIUS -> "Audius • direct audio"
    Source.RADIO -> "Radio • live"
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

private fun share(context: Context, text: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun saveTracks(tracks: List<Track>): String {
    val arr = JSONArray()
    tracks.forEach { t ->
        arr.put(JSONObject().apply {
            put("id", t.id)
            put("title", t.title)
            put("artist", t.artist)
            put("language", t.language)
            put("genre", t.genre)
            put("streamUrl", t.streamUrl)
            put("source", t.source.name)
            put("videoId", t.videoId)
            put("thumbnailUrl", t.thumbnailUrl)
            put("countryCode", t.countryCode)
            put("homepage", t.homepage)
            put("codec", t.codec)
            put("bitrate", t.bitrate)
            put("durationMs", t.durationMs)
        })
    }
    return arr.toString()
}

private fun loadTracks(json: String?): List<Track> {
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
                val streamUrl = o.optString("streamUrl").trim()
                val videoId = o.optString("videoId").trim()
                val playable = when (source) {
                    Source.YOUTUBE -> videoId.isNotBlank()
                    Source.AUDIUS, Source.RADIO -> streamUrl.isNotBlank()
                }
                if (!playable) continue

                add(
                    Track(
                        id = id,
                        title = title,
                        artist = o.optString("artist", if (source == Source.RADIO) "Live radio" else "Artist"),
                        language = o.optString("language", "Music"),
                        genre = o.optString("genre", "Music"),
                        streamUrl = streamUrl,
                        source = source,
                        videoId = videoId,
                        thumbnailUrl = o.optString("thumbnailUrl"),
                        countryCode = o.optString("countryCode"),
                        homepage = o.optString("homepage"),
                        codec = o.optString("codec"),
                        bitrate = o.optInt("bitrate", 0),
                        durationMs = o.optLong("durationMs", 0L)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}
