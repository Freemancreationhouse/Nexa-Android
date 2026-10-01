package com.studiokinematics.nexa

import android.content.Context
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

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
    val streamUrl: String
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
private val Muted = Color(0xFF9CA3AF)

private val demoTracks = listOf(
    Track("n1", "Neon Drift", "NEXA Sessions", "Hindi Mix", "Electronic", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"),
    Track("n2", "Midnight Pulse", "NEXA Sessions", "English", "Pop", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"),
    Track("n3", "City Afterglow", "NEXA Sessions", "Punjabi Mix", "Chill", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"),
    Track("n4", "Aurora Run", "NEXA Sessions", "Tamil Mix", "Electronic", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"),
    Track("n5", "Cloudline", "NEXA Sessions", "Telugu Mix", "Lo-Fi", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3")
)

private class PlayerController(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()
    var current by mutableStateOf<Track?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) { isPlaying = value }
        })
    }

    fun play(track: Track) {
        current = track
        player.setMediaItem(MediaItem.fromUri(track.streamUrl))
        player.prepare()
        player.play()
    }

    fun toggle() { if (player.isPlaying) player.pause() else player.play() }
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun release() = player.release()
}

@Composable
fun NexaApp(context: Context) {
    val controller = remember { PlayerController(context) }
    DisposableEffect(Unit) { onDispose { controller.release() } }

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showPlayer by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("nexa", Context.MODE_PRIVATE) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var recent by remember { mutableStateOf<List<String>>(emptyList()) }

    fun play(track: Track) {
        controller.play(track)
        recent = (listOf(track.id) + recent.filterNot { it == track.id }).take(12)
    }

    fun toggleFavorite(track: Track) {
        favorites = if (track.id in favorites) favorites - track.id else favorites + track.id
        prefs.edit().putStringSet("favorites", favorites).apply()
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg,
            surface = Panel,
            primary = Purple,
            secondary = Cyan,
            tertiary = Pink,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Box(Modifier.fillMaxSize().background(Bg)) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        Tab.HOME -> HomeScreen(::play, favorites, ::toggleFavorite)
                        Tab.EXPLORE -> ExploreScreen(::play, favorites, ::toggleFavorite)
                        Tab.RADIO -> RadioScreen(::play)
                        Tab.LIBRARY -> LibraryScreen(
                            favorites = demoTracks.filter { it.id in favorites },
                            recent = recent.mapNotNull { id -> demoTracks.find { it.id == id } },
                            onPlay = ::play,
                            onToggleFavorite = ::toggleFavorite
                        )
                    }
                }

                AnimatedVisibility(controller.current != null) {
                    controller.current?.let { track ->
                        MiniPlayer(track, controller.isPlaying, controller::toggle) { showPlayer = true }
                    }
                }
                BottomBar(tab) { tab = it }
            }

            if (showPlayer && controller.current != null) {
                NowPlaying(
                    track = controller.current!!,
                    controller = controller,
                    isFavorite = controller.current!!.id in favorites,
                    onFavorite = { toggleFavorite(controller.current!!) },
                    onClose = { showPlayer = false },
                    onPrevious = {
                        val index = demoTracks.indexOfFirst { it.id == controller.current?.id }
                        play(demoTracks[(index - 1 + demoTracks.size) % demoTracks.size])
                    },
                    onNext = {
                        val index = demoTracks.indexOfFirst { it.id == controller.current?.id }
                        play(demoTracks[(index + 1) % demoTracks.size])
                    }
                )
            }
        }
    }
}

@Composable
private fun TopBrand(subtitle: String) {
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
            Text("NEXA", fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text(subtitle, fontSize = 12.sp, color = Muted)
        }
        Icon(Icons.Default.Search, null, tint = Color.White)
    }
}

@Composable
private fun HomeScreen(onPlay: (Track) -> Unit, favorites: Set<String>, onFav: (Track) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { TopBrand("Music beyond borders") }
        item {
            Box(
                Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(180.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF35105F), Color(0xFF0D4F6B), Color(0xFF8E164D))))
                    .padding(22.dp)
            ) {
                Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(.72f)) {
                    AssistChip(onClick = {}, label = { Text("FOR YOU") }, colors = AssistChipDefaults.assistChipColors(containerColor = Color.White.copy(.12f)))
                    Spacer(Modifier.height(10.dp))
                    Text("Feel Good\nHits", fontSize = 34.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black)
                    Text("No login. Just press play.", color = Color.White.copy(.75f), fontSize = 13.sp)
                }
                FilledIconButton(
                    onClick = { onPlay(demoTracks.first()) },
                    modifier = Modifier.align(Alignment.BottomEnd).size(54.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)
                ) { Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(30.dp)) }
            }
        }
        item { SectionTitle("Quick picks", "Your vibe, instantly") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(demoTracks) { track -> QuickCard(track) { onPlay(track) } }
            }
        }
        item { SectionTitle("Trending now", "NEXA demo sessions") }
        items(demoTracks) { track -> TrackRow(track, track.id in favorites, { onPlay(track) }, { onFav(track) }) }
    }
}

@Composable
private fun ExploreScreen(onPlay: (Track) -> Unit, favorites: Set<String>, onFav: (Track) -> Unit) {
    var query by remember { mutableStateOf("") }
    val languages = listOf("Hindi", "English", "Punjabi", "Tamil", "Telugu", "Malayalam", "Bengali", "Marathi", "Gujarati", "Bhojpuri", "Kannada", "More")
    val genres = listOf("Bollywood", "Pop", "Hip Hop", "Lo-Fi", "Rock", "Devotional", "Classical", "Electronic", "Romance", "Retro")
    val filtered = demoTracks.filter { t -> query.isBlank() || listOf(t.title, t.artist, t.language, t.genre).any { it.contains(query, true) } }

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
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp), modifier = Modifier.weight(1f),
                    decorationBox = { inner -> if (query.isEmpty()) Text("Search songs, artists, languages…", color = Muted) else Unit; inner() }
                )
                Icon(Icons.Default.Mic, null, tint = Cyan)
            }
        }
        item { SectionTitle("Languages", "Tap into your world") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(languages) { label ->
                    Box(
                        Modifier.width(104.dp).height(66.dp).clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(Purple.copy(.5f), Cyan.copy(.25f)))).padding(12.dp),
                        contentAlignment = Alignment.BottomStart
                    ) { Text(label, fontWeight = FontWeight.Bold) }
                }
            }
        }
        item { SectionTitle("Genres & moods", "Find your frequency") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(genres) { genre -> SuggestionChip(onClick = { query = genre }, label = { Text(genre) }) }
            }
        }
        item { SectionTitle(if (query.isBlank()) "Fresh for you" else "Results", "${filtered.size} tracks") }
        items(filtered) { track -> TrackRow(track, track.id in favorites, { onPlay(track) }, { onFav(track) }) }
    }
}

@Composable
private fun RadioScreen(onPlay: (Track) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { TopBrand("Live & endless") }
        item {
            Box(
                Modifier.padding(18.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF22154F), Color(0xFF0D5061)))).padding(20.dp)
            ) {
                Column {
                    Text("NEXA RADIO", color = Cyan, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Always on.", fontSize = 32.sp, fontWeight = FontWeight.Black)
                    Text("V0.1 validates real internet audio playback. Live station discovery arrives in V0.2.", color = Color.White.copy(.7f))
                }
            }
        }
        item { SectionTitle("Test streams", "Royalty-free playback validation") }
        items(demoTracks) { track -> RadioRow(track) { onPlay(track) } }
    }
}

@Composable
private fun LibraryScreen(
    favorites: List<Track>, recent: List<Track>, onPlay: (Track) -> Unit, onToggleFavorite: (Track) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { TopBrand("Your music, on your device") }
        item {
            Row(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("${favorites.size}", "Liked", Icons.Default.Favorite, Modifier.weight(1f))
                StatCard("${recent.size}", "Recent", Icons.Default.History, Modifier.weight(1f))
            }
        }
        item { SectionTitle("Liked songs", if (favorites.isEmpty()) "Tap the heart on any track" else "Saved locally") }
        if (favorites.isEmpty()) item { EmptyState("No liked tracks yet", "Your favorites stay on this phone — no account needed.") }
        items(favorites) { track -> TrackRow(track, true, { onPlay(track) }, { onToggleFavorite(track) }) }
        item { SectionTitle("Recently played", "This session") }
        if (recent.isEmpty()) item { EmptyState("Nothing played yet", "Start from Home, Explore or Radio.") }
        items(recent) { track -> TrackRow(track, track in favorites, { onPlay(track) }, { onToggleFavorite(track) }) }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Text("See all", color = Cyan, fontSize = 12.sp)
    }
}

@Composable
private fun QuickCard(track: Track, onClick: () -> Unit) {
    Column(Modifier.width(138.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.size(138.dp).clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Purple.copy(.75f), Pink.copy(.55f), Cyan.copy(.45f)))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.GraphicEq, null, modifier = Modifier.size(52.dp), tint = Color.White.copy(.9f)) }
        Spacer(Modifier.height(8.dp))
        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
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
        ) { Icon(Icons.Default.MusicNote, null) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${track.artist} • ${track.language}", color = Muted, fontSize = 12.sp, maxLines = 1)
        }
        IconButton(onClick = onFav) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorite) Pink else Muted) }
        Icon(Icons.Default.MoreVert, null, tint = Muted)
    }
}

@Composable
private fun RadioRow(track: Track, onPlay: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 18.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(Purple.copy(.25f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Radio, null, tint = Cyan) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontWeight = FontWeight.Bold)
            Text("${track.language} • ${track.genre}", color = Muted, fontSize = 12.sp)
        }
        FilledIconButton(onClick = onPlay, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(.1f))) { Icon(Icons.Default.PlayArrow, null) }
    }
}

@Composable
private fun StatCard(value: String, label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(22.dp)).background(Panel).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Purple.copy(.2f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Pink) }
        Spacer(Modifier.width(12.dp))
        Column { Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black); Text(label, color = Muted, fontSize = 12.sp) }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.AutoAwesome, null, tint = Purple, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(8.dp)); Text(title, fontWeight = FontWeight.Bold); Text(body, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun MiniPlayer(track: Track, playing: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 10.dp, vertical = 4.dp).fillMaxWidth().height(62.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF24103F), Color(0xFF10263A)))).clickable(onClick = onOpen).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Purple), contentAlignment = Alignment.Center) { Icon(Icons.Default.GraphicEq, null) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1); Text(track.artist, color = Muted, fontSize = 11.sp) }
        IconButton(onClick = onToggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
        Icon(Icons.Default.QueueMusic, null, tint = Muted)
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = Color(0xFF090E18), tonalElevation = 0.dp) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab, onClick = { onSelect(tab) }, icon = { Icon(tab.icon, null) }, label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = Pink, selectedTextColor = Pink, indicatorColor = Purple.copy(.18f), unselectedIconColor = Muted, unselectedTextColor = Muted)
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
    var duration by remember { mutableLongStateOf(1L) }
    var repeat by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(false) }

    LaunchedEffect(track.id) {
        while (true) {
            position = controller.player.currentPosition.coerceAtLeast(0)
            duration = controller.player.duration.takeIf { it > 0 } ?: 1L
            delay(500)
        }
    }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1C0D2E), Bg, Color(0xFF071724)))).padding(22.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, null) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text("PLAYING FROM", color = Muted, fontSize = 10.sp); Text("NEXA V0.1", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, null) }
            }
            Spacer(Modifier.height(32.dp))
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(36.dp))
                    .background(Brush.linearGradient(listOf(Purple, Color(0xFF47216B), Pink, Color(0xFF0D6571)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.GraphicEq, null, modifier = Modifier.size(120.dp), tint = Color.White.copy(.88f))
            }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("${track.artist} • ${track.language}", color = Muted)
                }
                IconButton(onClick = onFavorite) { Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (isFavorite) Pink else Color.White) }
            }
            Slider(value = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f), onValueChange = { controller.seekTo((duration * it).toLong()) })
            Row(Modifier.fillMaxWidth()) { Text(formatTime(position), color = Muted, fontSize = 11.sp); Spacer(Modifier.weight(1f)); Text(formatTime(duration), color = Muted, fontSize = 11.sp) }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { shuffle = !shuffle; controller.player.shuffleModeEnabled = shuffle }) { Icon(Icons.Default.Shuffle, null, tint = if (shuffle) Cyan else Muted) }
                IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(34.dp)) }
                FilledIconButton(onClick = controller::toggle, modifier = Modifier.size(72.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)) {
                    Icon(if (controller.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(34.dp)) }
                IconButton(onClick = { repeat = !repeat; controller.player.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }) { Icon(Icons.Default.Repeat, null, tint = if (repeat) Pink else Muted) }
            }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                PlayerAction(Icons.Default.Lyrics, "Lyrics")
                PlayerAction(Icons.Default.Timer, "Timer")
                PlayerAction(Icons.Default.Equalizer, "EQ")
                PlayerAction(Icons.Default.QueueMusic, "Queue")
            }
        }
    }
}

@Composable
private fun PlayerAction(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = Muted); Spacer(Modifier.height(5.dp)); Text(label, color = Muted, fontSize = 11.sp) }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
