package com.studiokinematics.nexa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studiokinematics.nexa.playback.PlaybackConnection
import com.studiokinematics.nexa.model.SourceCapabilities
import com.studiokinematics.nexa.playback.PlaybackUiState
import com.studiokinematics.nexa.ui.components.NexaArtwork
import com.studiokinematics.nexa.ui.components.sourceLabel
import com.studiokinematics.nexa.ui.theme.*
import kotlinx.coroutines.delay

private fun clock(ms:Long):String{val s=(ms/1000).coerceAtLeast(0);return "%d:%02d".format(s/60,s%60)}

@Composable
fun NowPlayingScreen(state:PlaybackUiState,connection:PlaybackConnection,favorite:Boolean,onFavorite:()->Unit,onClose:()->Unit,onLyrics:()->Unit,onOffline:()->Unit){
    val track=state.current?:return
    val capabilities=SourceCapabilities.forTrack(track)
    LaunchedEffect(track.id){while(true){connection.refreshPosition();delay(500)}}
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF28103F),NexaBg,Color(0xFF061B23)))).padding(20.dp)){
        Column(Modifier.fillMaxSize()){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=onClose){Icon(Icons.Default.KeyboardArrowDown,null,tint=NexaBright)}
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Text(sourceLabel(track).uppercase(),color=NexaMuted,fontSize=9.sp);Text("NOW PLAYING",color=NexaBright,fontWeight=FontWeight.Bold,fontSize=11.sp)}
                IconButton(onClick=onFavorite){Icon(if(favorite)Icons.Default.Favorite else Icons.Default.FavoriteBorder,null,tint=if(favorite)NexaPink else NexaBright)}
            }
            Spacer(Modifier.height(18.dp));NexaArtwork(track,Modifier.fillMaxWidth().aspectRatio(1f),34);Spacer(Modifier.height(22.dp))
            Text(track.title,color=NexaBright,fontSize=26.sp,fontWeight=FontWeight.Black,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(track.artist,color=NexaSoft,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(track.album.isNotBlank())Text(track.album,color=NexaMuted,fontSize=11.sp)
            state.error?.let{Text(it,color=NexaDanger,fontSize=11.sp)}
            Spacer(Modifier.height(12.dp))
            if(state.durationMs>0){
                Slider(value=(state.positionMs.toFloat()/state.durationMs.toFloat()).coerceIn(0f,1f),onValueChange={connection.seekTo((state.durationMs*it).toLong())})
                Row(Modifier.fillMaxWidth()){Text(clock(state.positionMs),color=NexaMuted,fontSize=10.sp);Spacer(Modifier.weight(1f));Text(clock(state.durationMs),color=NexaMuted,fontSize=10.sp)}
            }else Text("● LIVE",color=NexaPink,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly){
                IconButton(onClick={connection.setShuffle(!state.shuffle)}){Icon(Icons.Default.Shuffle,null,tint=if(state.shuffle)NexaCyan else NexaMuted)}
                IconButton(onClick=connection::previous){Icon(Icons.Default.SkipPrevious,null,tint=NexaBright,modifier=Modifier.size(34.dp))}
                FilledIconButton(onClick=connection::toggle,modifier=Modifier.size(72.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color.White,contentColor=Color.Black)){Icon(if(state.isPlaying)Icons.Default.Pause else Icons.Default.PlayArrow,null,Modifier.size(38.dp))}
                IconButton(onClick=connection::next){Icon(Icons.Default.SkipNext,null,tint=NexaBright,modifier=Modifier.size(34.dp))}
                IconButton(onClick={connection.setRepeatOne(state.repeatMode!=androidx.media3.common.Player.REPEAT_MODE_ONE)}){Icon(Icons.Default.RepeatOne,null,tint=if(state.repeatMode==androidx.media3.common.Player.REPEAT_MODE_ONE)NexaPink else NexaMuted)}
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround){
                PlayerAction(Icons.Default.Lyrics,"Lyrics",onLyrics)
                if(capabilities.countsTowardNexaOfflineLimit)PlayerAction(Icons.Default.Download,"Offline",onOffline)
                PlayerAction(Icons.Default.Timer,connection.sleepMinutes?.let{"$it min"}?:"Timer",connection::cycleSleepTimer)
                PlayerAction(Icons.Default.QueueMusic,"Queue",{})
            }
        }
    }
}
@Composable private fun PlayerAction(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,onClick:()->Unit){
    TextButton(onClick=onClick){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,null,tint=NexaSoft);Text(label,color=NexaSoft,fontSize=10.sp)}}
}
