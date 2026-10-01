package com.studiokinematics.nexa

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Bundle
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NexaApp(applicationContext) }
    }
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val language: String,
    val genre: String,
    val streamUrl: String,
    val countryCode: String = "",
    val homepage: String = "",
    val codec: String = "",
    val bitrate: Int = 0
)

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    EXPLORE("Explore", Icons.Default.Explore),
    RADIO("Radio", Icons.Default.Radio),
    LIBRARY("Library", Icons.Default.LibraryMusic)
}

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
                .build(),
            true
        )
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val title = mediaMetadata.title?.toString()?.trim().orEmpty()
                val artist = mediaMetadata.artist?.toString()?.trim().orEmpty()
                if (title.isNotBlank() && title != current?.title) liveTitle = title
                if (artist.isNotBlank() && artist != current?.artist) liveArtist = artist
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = "This stream is temporarily unavailable. Try another station."
            }
        })
    }

    val displayTitle: String
        get() = liveTitle?.takeIf { it.isNotBlank() } ?: current?.title.orEmpty()

    val displayArtist: String
        get() = liveArtist?.takeIf { it.isNotBlank() } ?: current?.artist.orEmpty()

    fun play(track: Track) {
        current = track
        liveTitle = null
        liveArtist = null
        playbackError = null
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .build()
        val item = MediaItem.Builder()
            .setUri(track.streamUrl)
            .setMediaMetadata(metadata)
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    fun toggle() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(ms: Long) = player.seekTo(ms)

    fun cycleSleepTimer() {
        val next = when (sleepMinutes) {
            null -> 15
            15 -> 30
            30 -> 60
            else -> null
        }
        sleepJob?.cancel()
        sleepMinutes = next
        if (next != null) {
            sleepJob = scope.launch {
                delay(next * 60_000L)
                player.pause()
                sleepMinutes = null
            }
        }
    }

    fun release() {
        scope.cancel()
        player.release()
    }
}

private class RadioBrowserRepository {
    private val bootstrap = "https://all.api.radio-browser.info"
    private val fallbackServers = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info"
    )
    @Volatile private var cachedServer: String? = null

    suspend fun home(): List<Track> = withContext(Dispatchers.IO) {
        val india = safeRequest("/json/stations/bycountrycodeexact/IN?hidebroken=true&order=votes&reverse=true&limit=70")
        val global = safeRequest("/json/stations/topvote/40?hidebroken=true")
        (india + global).distinctBy { it.id }.take(90)
    }

    suspend fun byLanguage(language: String): List<Track> = withContext(Dispatchers.IO) {
        safeRequest("/json/stations/search?language=${enc(language)}&hidebroken=true&order=votes&reverse=true&limit=80")
    }

    suspend fun byTag(tag: String): List<Track> = withContext(Dispatchers.IO) {
        safeRequest("/json/stations/search?tag=${enc(tag)}&hidebroken=true&order=votes&reverse=true&limit=80")
    }

    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val q = enc(query.trim())
        val byName = safeRequest("/json/stations/search?name=$q&hidebroken=true&order=votes&reverse=true&limit=35")
        val byLanguage = safeRequest("/json/stations/search?language=$q&hidebroken=true&order=votes&reverse=true&limit=35")
        val byTag = safeRequest("/json/stations/search?tag=$q&hidebroken=true&order=votes&reverse=true&limit=35")
        (byName + byLanguage + byTag).distinctBy { it.id }.take(80)
    }

    suspend fun registerClick(stationUuid: String) = withContext(Dispatchers.IO) {
        runCatching { fetchText("${server()}/json/url/${enc(stationUuid)}") }
        Unit
    }

    private fun safeRequest(path: String): List<Track> {
        val bases = buildList {
            runCatching { add(server()) }
            fallbackServers.forEach { if (it !in this) add(it) }
        }
        var last: Throwable? = null
        for (base in bases) {
            try {
                val result = parseStations(fetchText(base + path))
                if (result.isNotEmpty()) {
                    cachedServer = base
                    return result
                }
            } catch (t: Throwable) {
                last = t
            }
        }
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
                    val base = "https://$name"
                    cachedServer = base
                    return@runCatching base
                }
            }
            fallbackServers.first()
        }.getOrElse { fallbackServers.first() }
    }

    private fun fetchText(url: String): String {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 9_000
            readTimeout = 13_000
            setRequestProperty("User-Agent", "NEXA/0.2 Android (Studio Kinematics)")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseStations(json: String): List<Track> {
        val arr = JSONArray(json)
        val out = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("stationuuid").trim()
            val name = o.optString("name").trim().replace(Regex("\\s+"), " ")
            val resolved = o.optString("url_resolved").trim()
            val raw = o.optString("url").trim()
            val stream = (resolved.ifBlank { raw }).trim()
            if (id.isBlank() || name.isBlank() || !(stream.startsWith("https://") || stream.startsWith("http://"))) continue
            val language = o.optString("language").trim().ifBlank { "Music" }
            val tags = o.optString("tags").split(',').map { it.trim() }.filter { it.isNotBlank() }
            val genre = tags.firstOrNull()?.replaceFirstChar { c -> c.uppercase() } ?: "Live"
            val countryCode = o.optString("countrycode").trim().uppercase()
            val country = o.optString("country").trim()
            val stationArtist = listOf("Live radio", country.ifBlank { countryCode }).filter { it.isNotBlank() }.joinToString(" • ")
            out += Track(
                id = id,
                title = name,
                artist = stationArtist,
                language = language,
                genre = genre,
                streamUrl = stream,
                countryCode = countryCode,
                homepage = o.optString("homepage").trim(),
                codec = o.optString("codec").trim(),
                bitrate = o.optInt("bitrate", 0)
            )
        }
        return out.distinctBy { it.id }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}

@Composable
fun NexaApp(context: Context) {
    val controller = remember { PlayerController(context) }
    val repository = remember { RadioBrowserRepository() }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { controller.release() } }

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showPlayer by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("nexa", Context.MODE_PRIVATE) }
    var favorites by remember { mutableStateOf(loadTracks(prefs.getString("favorite_tracks", null))) }
    var recent by remember { mutableStateOf(loadTracks(prefs.getString("recent_tracks", null))) }
    var catalog by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        loadError = null
        try {
            catalog = repository.home()
            if (catalog.isEmpty()) loadError = "No live stations were returned. Tap retry."
        } catch (_: Throwable) {
            loadError = "Could not reach the live music directory. Check internet and retry."
        } finally {
            loading = false
        }
    }

    fun play(track: Track) {
        controller.play(track)
        recent = (listOf(track) + recent.filterNot { it.id == track.id }).take(20)
        prefs.edit().putString("recent_tracks", saveTracks(recent)).apply()
        scope.launch { repository.registerClick(track.id) }
    }

    fun toggleFavorite(track: Track) {
        favorites = if (favorites.any { it.id == track.id }) {
            favorites.filterNot { it.id == track.id }
        } else {
            listOf(track) + favorites
        }
        prefs.edit().putString("favorite_tracks", saveTracks(favorites)).apply()
    }

    val favoriteIds = favorites.mapTo(mutableSetOf()) { it.id }
    val queue = (catalog + favorites + recent).distinctBy { it.id }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg,
            surface = Panel,
            primary = Purple,
            secondary = Cyan,
            tertiary = Pink,
            onBackground = Bright,
            onSurface = Bright,
            onPrimary = Color.White
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Bright) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            Tab.HOME -> HomeScreen(
                                stations = catalog,
                                loading = loading,
                                error = loadError,
                                onRetry = { refreshKey++ },
                                onSearch = { tab = Tab.EXPLORE },
                                onPlay = ::play,
                                favorites = favoriteIds,
                                onFav = ::toggleFavorite
                            )
                            Tab.EXPLORE -> ExploreScreen(
                                seedStations = catalog,
                                repository = repository,
                                onPlay = ::play,
                                favorites = favoriteIds,
                                onFav = ::toggleFavorite
                            )
                            Tab.RADIO -> RadioScreen(
                                stations = catalog,
                                loading = loading,
                                error = loadError,
                                onRetry = { refreshKey++ },
                                onPlay = ::play,
                                favorites = favoriteIds,
                                onFav = ::toggleFavorite
                            )
                            Tab.LIBRARY -> LibraryScreen(
                                favorites = favorites,
                                recent = recent,
                                onPlay = ::play,
                                onToggleFavorite = ::toggleFavorite
                            )
                        }
                    }

                    AnimatedVisibility(controller.current != null) {
                        controller.current?.let { track ->
                            MiniPlayer(
                                title = controller.displayTitle,
                                subtitle = controller.playbackError ?: controller.displayArtist,
                                playing = controller.isPlaying,
                                onToggle = controller::toggle,
                                onOpen = { showPlayer = true }
                            )
                        }
                    }
                    BottomBar(tab) { tab = it }
                }

                if (showPlayer && controller.current != null) {
                    val current = controller.current!!
                    NowPlaying(
                        track = current,
                        controller = controller,
                        isFavorite = current.id in favoriteIds,
                        onFavorite = { toggleFavorite(current) },
                        onClose = { showPlayer = false },
                        onPrevious = {
                            val index = queue.indexOfFirst { it.id == current.id }
                            if (queue.isNotEmpty()) play(queue[(if (index <= 0) queue.lastIndex else index - 1)])
                        },
                        onNext = {
                            val index = queue.indexOfFirst { it.id == current.id }
                            if (queue.isNotEmpty()) play(queue[(if (index < 0 || index >= queue.lastIndex) 0 else index + 1)])
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBrand(subtitle: String, onSearch: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(
                Brush.linearGradient(listOf(Purple, Pink, Cyan))
            ), contentAlignment = Alignment.Center
        ) {
            Text("N", fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("NEXA", fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = Bright)
            Text(subtitle, fontSize = 12.sp, color = Muted)
        }
        if (onSearch != null) {
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, null, tint = Bright) }
        }
    }
}

@Composable
private fun HomeScreen(
    stations: List<Track>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onSearch: () -> Unit,
    onPlay: (Track) -> Unit,
    favorites: Set<String>,
    onFav: (Track) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { TopBrand("Music beyond borders", onSearch) }
        item {
            Box(
                Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(180.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF35105F), Color(0xFF0D4F6B), Color(0xFF8E164D))))
                    .padding(22.dp)
            ) {
                Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(.76f)) {
                    Surface(color = Color.White.copy(.13f), shape = RoundedCornerShape(20.dp)) {
                        Text("LIVE • NO LOGIN", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Bright, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Your world.\nYour sound.", fontSize = 32.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black, color = Bright)
                    Text("Real online music stations, ready to play.", color = Soft, fontSize = 13.sp)
                }
                FilledIconButton(
                    onClick = { stations.firstOrNull()?.let(onPlay) },
                    enabled = stations.isNotEmpty(),
                    modifier = Modifier.align(Alignment.BottomEnd).size(54.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)
                ) { Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(30.dp)) }
            }
        }

        if (loading) item { LoadingBlock("Finding live stations…") }
        if (error != null) item { ErrorBlock(error, onRetry) }

        if (stations.isNotEmpty()) {
            item { SectionTitle("Quick picks", "Live now") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(stations.take(10), key = { it.id }) { track -> QuickCard(track) { onPlay(track) } }
                }
            }
            item { SectionTitle("Trending stations", "India + global discovery") }
            items(stations.drop(10).take(45), key = { it.id }) { track ->
                TrackRow(track, track.id in favorites, { onPlay(track) }, { onFav(track) })
            }
            item { SourceFooter() }
        }
    }
}

@Composable
private fun ExploreScreen(
    seedStations: List<Track>,
    repository: RadioBrowserRepository,
    onPlay: (Track) -> Unit,
    favorites: Set<String>,
    onFav: (Track) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf(seedStations) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val languages = listOf("Hindi", "English", "Punjabi", "Tamil", "Telugu", "Malayalam", "Bengali", "Marathi", "Gujarati", "Kannada")
    val genres = listOf("Bollywood", "Pop", "Hip Hop", "Lo-Fi", "Rock", "Devotional", "Classical", "Electronic", "Romance", "Retro")

    LaunchedEffect(seedStations, filter) {
        if (query.isBlank() && filter == null) results = seedStations
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            if (filter == null) results = seedStations
            return@LaunchedEffect
        }
        delay(350)
        loading = true
        error = null
        try {
            results = repository.search(query)
            if (results.isEmpty()) error = "No matching live stations found. Try a language, genre or station name."
        } catch (_: Throwable) {
            error = "Search could not reach the live directory."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(filter) {
        val f = filter ?: return@LaunchedEffect
        loading = true
        error = null
        try {
            results = when {
                f.startsWith("lang:") -> repository.byLanguage(f.removePrefix("lang:"))
                f.startsWith("tag:") -> repository.byTag(f.removePrefix("tag:"))
                else -> seedStations
            }
            if (results.isEmpty()) error = "Nothing found in this category right now."
        } catch (_: Throwable) {
            error = "Could not load this category."
        } finally {
            loading = false
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { TopBrand("Explore every sound") }
        item {
            Row(
                Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp)).background(Panel2).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, tint = Muted)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        if (it.isNotBlank()) filter = null
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = Bright, fontSize = 15.sp),
                    cursorBrush = Brush.verticalGradient(listOf(Cyan, Cyan)),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Search stations, languages, genres…", color = Muted)
                        inner()
                    }
                )
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null, tint = Soft) }
                }
            }
        }
        item { SectionTitle("Languages", "Live stations from around the world") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(languages) { label ->
                    val selected = filter == "lang:$label"
                    Box(
                        Modifier.width(108.dp).height(66.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (selected) Brush.linearGradient(listOf(Purple, Pink)) else Brush.linearGradient(listOf(Purple.copy(.45f), Cyan.copy(.22f))))
                            .clickable { query = ""; filter = "lang:$label" }
                            .padding(12.dp),
                        contentAlignment = Alignment.BottomStart
                    ) { Text(label, fontWeight = FontWeight.Bold, color = Bright) }
                }
            }
        }
        item { SectionTitle("Genres & moods", "Tap to load real stations") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(genres) { genre ->
                    val selected = filter == "tag:$genre"
                    SuggestionChip(
                        onClick = { query = ""; filter = "tag:$genre" },
                        label = { Text(genre, color = if (selected) Color.White else Bright) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (selected) Purple.copy(.55f) else Panel2,
                            labelColor = Bright
                        )
                    )
                }
            }
        }
        item { SectionTitle(if (query.isNotBlank()) "Search results" else "Live discovery", "${results.size} stations") }
        if (loading) item { LoadingBlock("Loading live results…") }
        if (error != null) item { ErrorBlock(error!!, null) }
        items(results, key = { it.id }) { track -> TrackRow(track, track.id in favorites, { onPlay(track) }, { onFav(track) }) }
        if (results.isNotEmpty()) item { SourceFooter() }
    }
}

@Composable
private fun RadioScreen(
    stations: List<Track>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onPlay: (Track) -> Unit,
    favorites: Set<String>,
    onFav: (Track) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { TopBrand("Live & endless") }
        item {
            Box(
                Modifier.padding(horizontal = 18.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF22154F), Color(0xFF0D5061)))).padding(20.dp)
            ) {
                Column {
                    Text("NEXA RADIO", color = Cyan, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Always on.", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Bright)
                    Text("Real live streams. No demo tracks, no login wall.", color = Soft)
                }
            }
        }
        item { SectionTitle("Live stations", "Tap any station to start") }
        if (loading) item { LoadingBlock("Tuning the live directory…") }
        if (error != null) item { ErrorBlock(error, onRetry) }
        items(stations, key = { it.id }) { track ->
            RadioRow(track, track.id in favorites, { onPlay(track) }, { onFav(track) })
        }
        if (stations.isNotEmpty()) item { SourceFooter() }
    }
}

@Composable
private fun LibraryScreen(
    favorites: List<Track>,
    recent: List<Track>,
    onPlay: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit
) {
    val favIds = favorites.mapTo(mutableSetOf()) { it.id }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { TopBrand("Your music, on your device") }
        item {
            Row(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("${favorites.size}", "Liked", Icons.Default.Favorite, Modifier.weight(1f))
                StatCard("${recent.size}", "Recent", Icons.Default.History, Modifier.weight(1f))
            }
        }
        item { SectionTitle("Liked stations", if (favorites.isEmpty()) "Tap the heart on any station" else "Saved locally") }
        if (favorites.isEmpty()) item { EmptyState("Nothing liked yet", "Favorites stay on this phone — no account needed.") }
        items(favorites, key = { it.id }) { track -> TrackRow(track, true, { onPlay(track) }, { onToggleFavorite(track) }) }
        item { SectionTitle("Recently played", "Saved locally") }
        if (recent.isEmpty()) item { EmptyState("Nothing played yet", "Start from Home, Explore or Radio.") }
        items(recent, key = { it.id }) { track -> TrackRow(track, track.id in favIds, { onPlay(track) }, { onToggleFavorite(track) }) }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Bright)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun QuickCard(track: Track, onClick: () -> Unit) {
    Column(Modifier.width(138.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.size(138.dp).clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Purple.copy(.75f), Pink.copy(.55f), Cyan.copy(.45f)))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.GraphicEq, null, modifier = Modifier.size(52.dp), tint = Color.White.copy(.94f)) }
        Spacer(Modifier.height(8.dp))
        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, color = Bright)
        Text(track.language, maxLines = 1, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun TrackRow(track: Track, favorite: Boolean, onPlay: () -> Unit, onFav: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 18.dp, vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).clickable(onClick = onPlay).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Purple, Pink))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.MusicNote, null, tint = Color.White) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Bright, fontSize = 15.sp)
            Text("${track.language} • ${track.genre}", color = Soft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onFav) {
            Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorite) Pink else Muted)
        }
        Icon(Icons.Default.PlayArrow, null, tint = Bright)
    }
}

@Composable
private fun RadioRow(track: Track, favorite: Boolean, onPlay: () -> Unit, onFav: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 18.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).clickable(onClick = onPlay).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(Purple.copy(.25f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Radio, null, tint = Cyan)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontWeight = FontWeight.ExtraBold, color = Bright, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = buildString {
                append(track.language)
                if (track.codec.isNotBlank()) append(" • ${track.codec}")
                if (track.bitrate > 0) append(" • ${track.bitrate} kbps")
            }
            Text(detail, color = Soft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onFav) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorite) Pink else Muted) }
        FilledIconButton(onClick = onPlay, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(.12f), contentColor = Bright)) {
            Icon(Icons.Default.PlayArrow, null)
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(22.dp)).background(Panel).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Purple.copy(.2f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Pink) }
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
    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Cyan)
        Spacer(Modifier.width(12.dp))
        Text(text, color = Soft)
    }
}

@Composable
private fun ErrorBlock(text: String, onRetry: (() -> Unit)?) {
    Row(
        Modifier.padding(horizontal = 18.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF2A1420)).padding(14.dp),
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
        "Live directory data: Radio Browser • Stream availability is controlled by each broadcaster.",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        color = Muted,
        fontSize = 10.sp
    )
}

@Composable
private fun MiniPlayer(title: String, subtitle: String, playing: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 10.dp, vertical = 4.dp).fillMaxWidth().height(64.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF24103F), Color(0xFF10263A)))).clickable(onClick = onOpen).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Purple), contentAlignment = Alignment.Center) { Icon(Icons.Default.GraphicEq, null, tint = Color.White) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Bright)
            Text(subtitle, color = Soft, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onToggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Bright) }
        Icon(Icons.Default.QueueMusic, null, tint = Muted)
    }
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
private fun NowPlaying(
    track: Track,
    controller: PlayerController,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var repeat by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(track.id) {
        while (true) {
            position = controller.player.currentPosition.coerceAtLeast(0)
            duration = controller.player.duration.takeIf { it > 0 && it < 86_400_000L } ?: 0L
            delay(500)
        }
    }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1C0D2E), Bg, Color(0xFF071724)))).padding(22.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Bright) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LIVE FROM", color = Muted, fontSize = 10.sp)
                    Text(track.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Bright, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = {
                    val shareText = buildString {
                        append("Listening to ${track.title} on NEXA")
                        if (track.homepage.isNotBlank()) append("\n${track.homepage}")
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share station").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Icon(Icons.Default.Share, null, tint = Bright) }
            }
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(36.dp))
                    .background(Brush.linearGradient(listOf(Purple, Color(0xFF47216B), Pink, Color(0xFF0D6571)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.GraphicEq, null, modifier = Modifier.size(120.dp), tint = Color.White.copy(.92f))
            }
            Spacer(Modifier.height(26.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(controller.displayTitle, fontSize = 25.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Bright)
                    Text(controller.displayArtist, color = Soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onFavorite) {
                    Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (isFavorite) Pink else Bright)
                }
            }

            if (controller.playbackError != null) {
                Spacer(Modifier.height(8.dp))
                Text(controller.playbackError!!, color = Danger, fontSize = 12.sp)
            }

            Spacer(Modifier.height(12.dp))
            if (duration > 0L) {
                Slider(
                    value = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                    onValueChange = { controller.seekTo((duration * it).toLong()) }
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(formatTime(position), color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(duration), color = Muted, fontSize = 11.sp)
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Pink.copy(.18f), shape = RoundedCornerShape(20.dp)) {
                        Text("● LIVE", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Pink, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(listOf(track.codec, track.bitrate.takeIf { it > 0 }?.let { "$it kbps" }).filterNotNull().filter { it.toString().isNotBlank() }.joinToString(" • "), color = Muted, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { shuffle = !shuffle; controller.player.shuffleModeEnabled = shuffle }) { Icon(Icons.Default.Shuffle, null, tint = if (shuffle) Cyan else Muted) }
                IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(34.dp), tint = Bright) }
                FilledIconButton(
                    onClick = controller::toggle,
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)
                ) { Icon(if (controller.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(38.dp)) }
                IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(34.dp), tint = Bright) }
                IconButton(onClick = {
                    repeat = !repeat
                    controller.player.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                }) { Icon(Icons.Default.Repeat, null, tint = if (repeat) Pink else Muted) }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                PlayerAction(Icons.Default.Timer, controller.sleepMinutes?.let { "$it min" } ?: "Timer") { controller.cycleSleepTimer() }
                PlayerAction(Icons.Default.Equalizer, "EQ") {
                    runCatching {
                        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, controller.player.audioSessionId)
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
                PlayerAction(Icons.Default.Share, "Share") {
                    val text = "${controller.displayTitle} — ${track.title}"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
}

@Composable
private fun PlayerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Soft)
        Spacer(Modifier.height(5.dp))
        Text(label, color = Soft, fontSize = 11.sp)
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
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
            put("countryCode", t.countryCode)
            put("homepage", t.homepage)
            put("codec", t.codec)
            put("bitrate", t.bitrate)
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
                val id = o.optString("id")
                val title = o.optString("title")
                val streamUrl = o.optString("streamUrl")
                if (id.isBlank() || title.isBlank() || streamUrl.isBlank()) continue
                add(
                    Track(
                        id = id,
                        title = title,
                        artist = o.optString("artist", "Live radio"),
                        language = o.optString("language", "Music"),
                        genre = o.optString("genre", "Live"),
                        streamUrl = streamUrl,
                        countryCode = o.optString("countryCode"),
                        homepage = o.optString("homepage"),
                        codec = o.optString("codec"),
                        bitrate = o.optInt("bitrate", 0)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}
