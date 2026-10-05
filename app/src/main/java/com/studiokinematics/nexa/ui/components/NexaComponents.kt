package com.studiokinematics.nexa.ui.components

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studiokinematics.nexa.model.Source
import com.studiokinematics.nexa.model.Track
import com.studiokinematics.nexa.playback.PlaybackUiState
import com.studiokinematics.nexa.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

enum class NexaTab(val label:String){ HOME("Home"), SEARCH("Search"), RADIO("Radio"), LIBRARY("Library") }

@Composable
fun NexaArtwork(track:Track?,modifier:Modifier=Modifier,corner:Int=20){
    val context=LocalContext.current
    val key="${track?.id}:${track?.effectiveArtworkUrl}"
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,key){
        value=withContext(Dispatchers.IO){
            val t=track?:return@withContext null
            fun embedded():android.graphics.Bitmap?{
                if(t.contentUri.isBlank())return null
                return runCatching{
                    val mmr=MediaMetadataRetriever()
                    try{
                        mmr.setDataSource(context,Uri.parse(t.contentUri))
                        mmr.embeddedPicture?.let{BitmapFactory.decodeByteArray(it,0,it.size)}
                    }finally{mmr.release()}
                }.getOrNull()
            }
            val url=t.effectiveArtworkUrl
            val primary=when{
                url.startsWith("content://")->runCatching{context.contentResolver.openInputStream(Uri.parse(url))?.use(BitmapFactory::decodeStream)}.getOrNull()
                url.startsWith("http://")||url.startsWith("https://")->runCatching{
                    val c=URI(url).toURL().openConnection() as HttpURLConnection
                    c.connectTimeout=6000;c.readTimeout=8000
                    try{if(c.responseCode in 200..299)c.inputStream.use(BitmapFactory::decodeStream) else null}finally{c.disconnect()}
                }.getOrNull()
                else->null
            }
            (primary?:embedded())?.asImageBitmap()
        }
    }
    Box(modifier.clip(RoundedCornerShape(corner.dp)).background(
        Brush.linearGradient(listOf(NexaPurple,NexaPink,NexaCyan))
    ),contentAlignment=Alignment.Center){
        if(bitmap!=null)Image(bitmap!!,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Column(horizontalAlignment=Alignment.CenterHorizontally){
            Icon(Icons.Default.GraphicEq,null,tint=NexaBright,modifier=Modifier.size(34.dp))
            Text("NEXA",color=NexaBright,fontWeight=FontWeight.Black,fontSize=10.sp,letterSpacing=2.sp)
        }
    }
}

@Composable
fun TopBrand(subtitle:String,onSearch:(()->Unit)?=null,onSettings:(()->Unit)?=null){
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(NexaPurple,NexaPink,NexaCyan))),contentAlignment=Alignment.Center){
            Text("N",fontWeight=FontWeight.Black,fontSize=25.sp,color=NexaBright)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)){
            Text("NEXA",fontSize=22.sp,fontWeight=FontWeight.Black,letterSpacing=3.sp,color=NexaBright)
            Text(subtitle,fontSize=11.sp,color=NexaMuted)
        }
        onSearch?.let{IconButton(onClick=it){Icon(Icons.Default.Search,null,tint=NexaBright)}}
        onSettings?.let{IconButton(onClick=it){Icon(Icons.Default.Settings,null,tint=NexaBright)}}
    }
}

@Composable fun SectionTitle(title:String,subtitle:String){
    Column(Modifier.fillMaxWidth().padding(start=18.dp,end=18.dp,top=22.dp,bottom=9.dp)){
        Text(title,fontSize=20.sp,fontWeight=FontWeight.ExtraBold,color=NexaBright)
        Text(subtitle,color=NexaMuted,fontSize=12.sp)
    }
}

fun sourceLabel(t:Track)=when(t.source){
    Source.YOUTUBE->"YouTube • official player"
    Source.AUDIUS->"Online audio"
    Source.RADIO->"Radio • live"
    Source.LOCAL->"On Device • offline"
}

@Composable
fun TrackRow(track:Track,favorite:Boolean,onPlay:()->Unit,onFavorite:()->Unit,trailing:@Composable RowScope.()->Unit={}){
    Row(Modifier.padding(horizontal=18.dp,vertical=5.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(NexaPanel).clickable(onClick=onPlay).padding(10.dp),verticalAlignment=Alignment.CenterVertically){
        NexaArtwork(track,Modifier.size(60.dp),16);Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)){
            Text(track.title,color=NexaBright,fontWeight=FontWeight.ExtraBold,maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=15.sp)
            Text(track.artist,color=NexaSoft,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp)
            Text(sourceLabel(track),color=NexaCyan,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=10.sp,fontWeight=FontWeight.Bold)
        }
        IconButton(onClick=onFavorite){Icon(if(favorite)Icons.Default.Favorite else Icons.Default.FavoriteBorder,null,tint=if(favorite)NexaPink else NexaMuted)}
        trailing()
        Icon(Icons.Default.PlayArrow,null,tint=NexaBright)
    }
}

@Composable
fun PersistentMiniPlayer(state:PlaybackUiState,onToggle:()->Unit,onOpen:()->Unit){
    val track=state.current?:return
    Column(Modifier.fillMaxWidth().background(NexaPanel2)){
        if(state.durationMs>0)LinearProgressIndicator(
            progress={(state.positionMs.toFloat()/state.durationMs.toFloat()).coerceIn(0f,1f)},
            modifier=Modifier.fillMaxWidth().height(2.dp),color=NexaCyan,trackColor=NexaPanel2
        )
        Row(Modifier.fillMaxWidth().clickable(onClick=onOpen).padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
            NexaArtwork(track,Modifier.size(46.dp),13);Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)){
                Text(track.title,color=NexaBright,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(track.artist,color=NexaSoft,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            IconButton(onClick=onToggle){Icon(if(state.isPlaying)Icons.Default.Pause else Icons.Default.PlayArrow,null,tint=NexaBright)}
        }
    }
}

@Composable
fun NexaBottomBar(selected:NexaTab,onSelect:(NexaTab)->Unit){
    NavigationBar(containerColor=Color(0xFF080C15),tonalElevation=0.dp){
        NexaTab.entries.forEach{tab->
            val icon=when(tab){NexaTab.HOME->Icons.Default.Home;NexaTab.SEARCH->Icons.Default.Search;NexaTab.RADIO->Icons.Default.Radio;NexaTab.LIBRARY->Icons.Default.LibraryMusic}
            NavigationBarItem(selected=selected==tab,onClick={onSelect(tab)},icon={Icon(icon,null)},label={Text(tab.label)},
                colors=NavigationBarItemDefaults.colors(selectedIconColor=NexaPink,selectedTextColor=NexaPink,indicatorColor=NexaPurple.copy(.18f),unselectedIconColor=NexaMuted,unselectedTextColor=NexaMuted))
        }
    }
}

@Composable fun LoadingBlock(text:String){
    Row(Modifier.fillMaxWidth().padding(24.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
        CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp,color=NexaCyan);Spacer(Modifier.width(12.dp));Text(text,color=NexaSoft)
    }
}
@Composable fun ErrorBlock(text:String,onRetry:(()->Unit)?=null){
    Row(Modifier.padding(horizontal=18.dp,vertical=8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(NexaDanger.copy(.12f)).padding(14.dp),verticalAlignment=Alignment.CenterVertically){
        Icon(Icons.Default.ErrorOutline,null,tint=NexaDanger);Spacer(Modifier.width(10.dp));Text(text,Modifier.weight(1f),color=NexaSoft,fontSize=12.sp)
        onRetry?.let{TextButton(onClick=it){Text("Retry",color=NexaCyan)}}
    }
}
@Composable fun EmptyState(title:String,body:String){
    Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Icon(Icons.Default.AutoAwesome,null,tint=NexaPurple,modifier=Modifier.size(36.dp));Spacer(Modifier.height(8.dp))
        Text(title,color=NexaBright,fontWeight=FontWeight.Bold);Text(body,color=NexaMuted,fontSize=12.sp)
    }
}
