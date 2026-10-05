package com.studiokinematics.nexa.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.studiokinematics.nexa.local.LocalMusicResult
import com.studiokinematics.nexa.model.Track
import com.studiokinematics.nexa.offline.OfflineRecord
import com.studiokinematics.nexa.offline.OfflineStatus
import com.studiokinematics.nexa.ui.components.*
import com.studiokinematics.nexa.ui.theme.*

@Composable
fun LibraryScreen(
    favorites:List<Track>,recent:List<Track>,localResult:LocalMusicResult,
    downloads:List<OfflineRecord>,wifiOnly:Boolean,
    onRequestLocalPermission:()->Unit,onPlay:(Track,List<Track>)->Unit,onFavorite:(Track)->Unit,
    onRemoveOffline:(String)->Unit,onWifiOnly:(Boolean)->Unit
){
    val ids=favorites.mapTo(mutableSetOf()){it.id}
    val localTracks=(localResult as? LocalMusicResult.Ready)?.tracks.orEmpty()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)){
        item{TopBrand("Your music")}

        item{SectionTitle("On Device","Your own saved music • unlimited • no internet required")}
        when(localResult){
            LocalMusicResult.PermissionRequired->item{
                Column(Modifier.padding(horizontal=18.dp)){
                    Text("Allow NEXA to read audio so your saved songs can appear here.",color=NexaSoft)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick=onRequestLocalPermission){Icon(Icons.Default.FolderOpen,null);Spacer(Modifier.width(8.dp));Text("Allow On Device Music")}
                }
            }
            is LocalMusicResult.Error->item{ErrorBlock(localResult.message,onRequestLocalPermission)}
            is LocalMusicResult.Ready->{
                if(localTracks.isEmpty())item{EmptyState("No local music found","Audio files indexed by Android will appear here.")}
                else items(localTracks,key={it.id}){t->TrackRow(t,t.id in ids,{onPlay(t,localTracks)},{onFavorite(t)})}
            }
        }

        item{SectionTitle("NEXA Offline","Eligible online songs saved privately • Free includes 5")}
        item{
            Row(Modifier.padding(horizontal=18.dp).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Column{Text("Wi-Fi only downloads",color=NexaBright);Text("Avoid mobile-data downloads",color=NexaMuted,style=MaterialTheme.typography.labelSmall)}
                Switch(checked=wifiOnly,onCheckedChange=onWifiOnly)
            }
        }
        if(downloads.isEmpty())item{EmptyState("No NEXA offline songs yet","Eligible online audio can be saved inside NEXA. On Device music does not use these five slots.")}
        items(downloads,key={it.track.id}){r->
            TrackRow(r.track,r.track.id in ids,{if(r.status==OfflineStatus.COMPLETED)onPlay(r.track,downloads.map{it.track})},{onFavorite(r.track)}){
                Text(when(r.status){
                    OfflineStatus.COMPLETED->"Saved";OfflineStatus.DOWNLOADING->"${r.percent.toInt()}%"
                    OfflineStatus.QUEUED->"Queued";OfflineStatus.STOPPED->"Waiting";OfflineStatus.FAILED->"Failed"
                },color=NexaCyan,style=MaterialTheme.typography.labelSmall)
                IconButton(onClick={onRemoveOffline(r.track.id)}){Icon(Icons.Default.DeleteOutline,null,tint=NexaMuted)}
            }
        }

        item{SectionTitle("Liked music",if(favorites.isEmpty())"Tap ♥ on any result"else"Saved on this device")}
        if(favorites.isEmpty())item{EmptyState("Nothing liked yet","Your favorites stay local — no account required.")}
        items(favorites,key={it.id}){t->TrackRow(t,true,{onPlay(t,favorites)},{onFavorite(t)})}

        item{SectionTitle("Recently played","Your listening trail")}
        if(recent.isEmpty())item{EmptyState("Nothing played yet","Search for a song or start a station.")}
        items(recent,key={it.id}){t->TrackRow(t,t.id in ids,{onPlay(t,recent)},{onFavorite(t)})}
    }
}
