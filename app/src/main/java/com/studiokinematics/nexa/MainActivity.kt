package com.studiokinematics.nexa

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.studiokinematics.nexa.data.AudiusRepository
import com.studiokinematics.nexa.data.LibraryStore
import com.studiokinematics.nexa.data.LyricsRepository
import com.studiokinematics.nexa.data.RadioBrowserRepository
import com.studiokinematics.nexa.data.YouTubeRepository
import com.studiokinematics.nexa.local.LocalMediaPermission
import com.studiokinematics.nexa.local.LocalMusicRepository
import com.studiokinematics.nexa.local.LocalMusicResult
import com.studiokinematics.nexa.model.Source
import com.studiokinematics.nexa.model.Track
import com.studiokinematics.nexa.model.LyricsState
import com.studiokinematics.nexa.playback.PlaybackConnection
import com.studiokinematics.nexa.offline.DownloadRepository
import com.studiokinematics.nexa.offline.EnqueueResult
import com.studiokinematics.nexa.offline.OfflineEntitlement
import com.studiokinematics.nexa.billing.BillingRepository
import com.studiokinematics.nexa.billing.NexaPlusState
import com.studiokinematics.nexa.ui.components.*
import com.studiokinematics.nexa.ui.screens.*
import com.studiokinematics.nexa.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NexaTheme { NexaApp(this@MainActivity) } }
    }
}

@Composable
fun NexaApp(context: Context) {
    val radio=remember{RadioBrowserRepository()}
    val audius=remember{AudiusRepository()}
    val youtube=remember(context){YouTubeRepository(context)}
    val store=remember(context){LibraryStore(context)}
    val lyricsRepo=remember{LyricsRepository()}
    val localRepo=remember(context){LocalMusicRepository(context)}
    val playback=remember(context){PlaybackConnection(context)}
    val billing=remember(context){BillingRepository(context)}
    val billingState by billing.state.collectAsState()
    val entitlementState=rememberUpdatedState(if(billingState is NexaPlusState.Active)OfflineEntitlement.NEXA_PLUS else OfflineEntitlement.FREE)
    val downloadsRepo=remember(context){DownloadRepository(context){entitlementState.value}}
    val playbackState by playback.state.collectAsState()
    val downloads by downloadsRepo.downloads.collectAsState()
    val scope=rememberCoroutineScope()
    DisposableEffect(playback){onDispose{playback.release();downloadsRepo.close();billing.close()}}

    val prefs=remember{context.getSharedPreferences("nexa",Context.MODE_PRIVATE)}
    var tab by rememberSaveable{mutableStateOf(NexaTab.HOME)}
    var showNowPlaying by remember{mutableStateOf(false)}
    var youtubeTrack by remember{mutableStateOf<Track?>(null)}
    var youtubeMinimized by remember{mutableStateOf(false)}
    var showSettings by remember{mutableStateOf(false)}
    var showLyrics by remember{mutableStateOf(false)}
    var showPlus by remember{mutableStateOf(false)}
    var offlineMessage by remember{mutableStateOf<String?>(null)}
    var pendingOffline by remember{mutableStateOf<Track?>(null)}
    var wifiOnly by remember{mutableStateOf(downloadsRepo.wifiOnly())}
    var youtubeConsent by rememberSaveable{mutableStateOf(prefs.getBoolean("youtube_consent",false))}
    var favorites by remember{mutableStateOf(store.loadFavorites())}
    var recent by remember{mutableStateOf(store.loadRecent())}
    var stations by remember{mutableStateOf<List<Track>>(emptyList())}
    var loading by remember{mutableStateOf(true)}
    var loadError by remember{mutableStateOf<String?>(null)}
    var refresh by remember{mutableIntStateOf(0)}

    val permission=LocalMediaPermission.permissionForSdk(Build.VERSION.SDK_INT)
    var localPermissionGranted by remember{
        mutableStateOf(ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED)
    }
    var localResult by remember{mutableStateOf<LocalMusicResult>(if(localPermissionGranted)LocalMusicResult.Ready(emptyList()) else LocalMusicResult.PermissionRequired)}
    var localRefresh by remember{mutableIntStateOf(0)}
    val localPermissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        localPermissionGranted=granted
        localResult=if(granted)LocalMusicResult.Ready(emptyList()) else LocalMusicResult.PermissionRequired
        localRefresh++
    }
    val notificationLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        val track=pendingOffline;pendingOffline=null
        if(granted&&track!=null){
            when(val result=downloadsRepo.enqueue(track)){
                EnqueueResult.Accepted->offlineMessage="Saving ${track.title} inside NEXA."
                EnqueueResult.AlreadySaved->offlineMessage="This song is already in NEXA Offline."
                EnqueueResult.UpgradeRequired->showPlus=true
                is EnqueueResult.Ineligible->offlineMessage=result.reason
            }
        }else if(!granted)offlineMessage="Notification permission is required for background downloads."
    }

    LaunchedEffect(refresh){
        loading=true;loadError=null
        try{stations=radio.home();if(stations.isEmpty())loadError="No live stations were returned. Tap retry."}
        catch(_:Throwable){loadError="Could not reach the live station directory. Check internet and retry."}
        finally{loading=false}
    }
    LaunchedEffect(localPermissionGranted,localRefresh){
        localResult=localRepo.load(localPermissionGranted)
    }

    fun toggleFavorite(track:Track){
        favorites=if(favorites.any{it.id==track.id})favorites.filterNot{it.id==track.id}else listOf(track)+favorites
        store.saveFavorites(favorites)
    }
    fun play(track:Track,queue:List<Track>){
        recent=(listOf(track)+recent.filterNot{it.id==track.id}).take(40);store.saveRecent(recent)
        if(track.source==Source.YOUTUBE){
            playback.pause();showNowPlaying=false;youtubeTrack=track;youtubeMinimized=false
        }else{
            youtubeTrack=null;youtubeMinimized=false
            playback.play(track,queue);showNowPlaying=true
            if(track.source==Source.RADIO)scope.launch{radio.registerClick(track.id.removePrefix("radio:"))}
        }
    }

    fun saveOffline(track:Track){
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            pendingOffline=track
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        when(val result=downloadsRepo.enqueue(track)){
            EnqueueResult.Accepted->offlineMessage="Saving ${track.title} inside NEXA."
            EnqueueResult.AlreadySaved->offlineMessage="This song is already in NEXA Offline."
            EnqueueResult.UpgradeRequired->showPlus=true
            is EnqueueResult.Ineligible->offlineMessage=result.reason
        }
    }

    val favoriteIds=favorites.mapTo(mutableSetOf()){it.id}
    val localTracks=(localResult as? LocalMusicResult.Ready)?.tracks.orEmpty()

    Surface(Modifier.fillMaxSize(),color=NexaBg){
        Box(Modifier.fillMaxSize()){
            Column(Modifier.fillMaxSize()){
                Box(Modifier.weight(1f)){
                    when(tab){
                        NexaTab.HOME->HomeScreen(stations,recent,loading,loadError,{refresh++},{tab=NexaTab.SEARCH},::play,favoriteIds,::toggleFavorite,{showSettings=true})
                        NexaTab.SEARCH->SearchScreen(radio,audius,youtube,localTracks,::play,favoriteIds,::toggleFavorite,{showSettings=true})
                        NexaTab.RADIO->RadioScreen(stations,loading,loadError,{refresh++},::play,favoriteIds,::toggleFavorite)
                        NexaTab.LIBRARY->LibraryScreen(
                            favorites,recent,localResult,downloads,wifiOnly,
                            {localPermissionLauncher.launch(permission)},::play,::toggleFavorite,
                            downloadsRepo::remove,{enabled->wifiOnly=enabled;downloadsRepo.setWifiOnly(enabled)}
                        )
                    }
                }
                AnimatedVisibility(playbackState.current!=null&&!showNowPlaying&&youtubeTrack==null){
                    PersistentMiniPlayer(playbackState,playback::toggle){showNowPlaying=true}
                }
                NexaBottomBar(tab){tab=it}
            }

            if(showNowPlaying&&playbackState.current!=null&&youtubeTrack==null){
                val current=playbackState.current!!
                NowPlayingScreen(playbackState,playback,current.id in favoriteIds,{toggleFavorite(current)},{showNowPlaying=false},{showLyrics=true},{saveOffline(current)})
            }

            youtubeTrack?.let{track->
                Box(
                    if(youtubeMinimized)Modifier.align(Alignment.BottomCenter).padding(bottom=76.dp)
                    else Modifier.fillMaxSize()
                ){
                    YouTubePlayerOverlay(track,youtubeMinimized,{youtubeMinimized=true},{youtubeMinimized=false},{youtubeTrack=null})
                }
            }

            if(!youtubeConsent)YouTubeConsentDialog{
                prefs.edit().putBoolean("youtube_consent",true).apply();youtubeConsent=true
            }
            if(showSettings)SettingsDialog(prefs){showSettings=false}
            if(showLyrics)playbackState.current?.let{LyricsDialog(it,lyricsRepo){showLyrics=false}}
            if(showPlus)NexaPlusScreen(
                state=billingState,
                onBuy={(context as? android.app.Activity)?.let(billing::launchPurchase)},
                onRestore=billing::refreshPurchases,
                onClose={showPlus=false}
            )
            offlineMessage?.let{message->AlertDialog(
                onDismissRequest={offlineMessage=null},title={Text("NEXA Offline",color=NexaBright)},
                text={Text(message,color=NexaSoft)},confirmButton={TextButton(onClick={offlineMessage=null}){Text("OK")}}
            )}
        }
    }
}

@Composable
private fun YouTubeConsentDialog(onAccept:()->Unit){
    AlertDialog(onDismissRequest={},title={Text("NEXA + YouTube",color=NexaBright)},text={
        Text("NEXA can use YouTube for mainstream discovery and the official visible YouTube player. NEXA native audio, local music, radio, favorites and offline features are separate.",color=NexaSoft)
    },confirmButton={Button(onClick=onAccept){Text("I agree")}})
}

@Composable
private fun SettingsDialog(prefs:android.content.SharedPreferences,onClose:()->Unit){
    var key by remember{mutableStateOf(prefs.getString("youtube_api_key","").orEmpty())}
    AlertDialog(onDismissRequest=onClose,title={Text("NEXA Settings",color=NexaBright)},text={
        Column{
            Text("Mainstream discovery",color=NexaBright,style=MaterialTheme.typography.titleSmall)
            OutlinedTextField(value=key,onValueChange={key=it},singleLine=true,label={Text("YouTube Data API key")},modifier=Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text("Owner / Publisher",color=NexaMuted,style=MaterialTheme.typography.labelSmall)
            Text("Studio Kinematic",color=NexaBright)
            Text("NEXA — Music Beyond Borders",color=NexaCyan,style=MaterialTheme.typography.labelSmall)
        }
    },dismissButton={TextButton(onClick=onClose){Text("Cancel")}},confirmButton={Button(onClick={prefs.edit().putString("youtube_api_key",key.trim()).apply();onClose()}){Text("Save")}})
}

@Composable
private fun LyricsDialog(track:Track,repo:LyricsRepository,onClose:()->Unit){
    var state by remember(track.id){mutableStateOf<LyricsState>(LyricsState.Loading)}
    LaunchedEffect(track.id){
        state=runCatching{repo.getLyrics(track)}.getOrElse{LyricsState.Error(it.message?:"Lyrics provider error")}
    }
    AlertDialog(onDismissRequest=onClose,title={Text("Lyrics",color=NexaBright)},text={
        when(val value=state){
            LyricsState.Loading->CircularProgressIndicator(color=NexaCyan)
            is LyricsState.Synced->Text(value.lines.joinToString("\n"){it.text},color=NexaSoft)
            is LyricsState.Plain->Text(value.text,color=NexaSoft)
            LyricsState.Instrumental->Text("Instrumental track",color=NexaSoft)
            LyricsState.Unavailable->Text("Lyrics are unavailable for this track. NEXA only displays lyrics from a permitted provider.",color=NexaSoft)
            is LyricsState.Error->Text(value.message,color=NexaDanger)
        }
    },confirmButton={TextButton(onClick=onClose){Text("Close")}})
}
