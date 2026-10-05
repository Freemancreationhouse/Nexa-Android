package com.studiokinematics.nexa.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.studiokinematics.nexa.model.Source
import com.studiokinematics.nexa.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackUiState(
    val connected: Boolean = false,
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
    val error: String? = null
)

class PlaybackConnection(context: Context) : Player.Listener {
    private val app = context.applicationContext
    private val token = SessionToken(app, ComponentName(app, NexaPlaybackService::class.java))
    private val future: ListenableFuture<MediaController> = MediaController.Builder(app, token).buildAsync()
    private var controller: MediaController? = null
    private var pendingPlay: Pair<Track,List<Track>>? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepJob: Job? = null

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    var sleepMinutes: Int? = null
        private set

    init {
        future.addListener({
            runCatching { future.get() }.onSuccess {
                controller = it
                it.addListener(this)
                publish()
                pendingPlay?.also { request ->
                    pendingPlay=null
                    play(request.first,request.second)
                }
            }.onFailure {
                _state.value = _state.value.copy(error = "Could not connect to NEXA playback service.")
            }
        }, ContextCompat.getMainExecutor(app))
    }

    fun play(track: Track, queue: List<Track> = listOf(track)) {
        require(track.source != Source.YOUTUBE) { "YouTube uses the embedded player." }
        if(controller==null){
            pendingPlay=track to queue
            return
        }
        val playable = queue.filter { it.source != Source.YOUTUBE && (it.streamUrl.isNotBlank() || it.contentUri.isNotBlank()) }
        if (playable.isEmpty()) return
        val selected = playable.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        controller?.apply {
            setMediaItems(playable.map(Track::toMediaItem), selected, 0L)
            prepare()
            play()
        }
    }

    fun toggle() = controller?.let { if (it.isPlaying) it.pause() else it.play() }
    fun pause() = controller?.pause()
    fun seekTo(ms: Long) = controller?.seekTo(ms)
    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.seekToPreviousMediaItem()
    fun setShuffle(enabled: Boolean) { controller?.shuffleModeEnabled = enabled }
    fun setRepeatOne(enabled: Boolean) { controller?.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }

    fun cycleSleepTimer() {
        val next = when (sleepMinutes) { null -> 15; 15 -> 30; 30 -> 60; else -> null }
        sleepJob?.cancel(); sleepMinutes = next
        if (next != null) sleepJob = scope.launch { delay(next * 60_000L); pause(); sleepMinutes = null }
    }

    fun refreshPosition() {
        val c = controller ?: return
        _state.value = _state.value.copy(
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = c.duration.takeIf { it > 0L && it < 86_400_000L } ?: 0L
        )
    }

    override fun onEvents(player: Player, events: Player.Events) = publish()
    override fun onPlayerError(error: PlaybackException) {
        _state.value = _state.value.copy(error = "This audio stream is unavailable. Try another result.")
    }

    private fun publish() {
        val c = controller ?: return
        val item: MediaItem? = c.currentMediaItem
        _state.value = PlaybackUiState(
            connected=true, current=item?.toNexaTrack(), isPlaying=c.isPlaying,
            positionMs=c.currentPosition.coerceAtLeast(0L),
            durationMs=c.duration.takeIf { it > 0L && it < 86_400_000L } ?: 0L,
            repeatMode=c.repeatMode, shuffle=c.shuffleModeEnabled, error=null
        )
    }

    fun release() {
        controller?.removeListener(this)
        controller = null
        scope.cancel()
        MediaController.releaseFuture(future)
    }
}
