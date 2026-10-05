package com.studiokinematics.nexa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studiokinematics.nexa.model.Track
import com.studiokinematics.nexa.ui.components.*
import com.studiokinematics.nexa.ui.theme.*

@Composable
fun HomeScreen(stations:List<Track>,recent:List<Track>,loading:Boolean,error:String?,onRetry:()->Unit,onSearch:()->Unit,onPlay:(Track,List<Track>)->Unit,favorites:Set<String>,onFavorite:(Track)->Unit,onSettings:()->Unit){
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)){
        item{TopBrand("Music beyond borders",onSearch,onSettings)}
        item{
            Box(Modifier.padding(horizontal=18.dp).fillMaxWidth().height(210.dp).clip(RoundedCornerShape(32.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF4B116F),Color(0xFF0C566B),Color(0xFFA11759)))).padding(24.dp)){
                Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(.8f)){
                    Surface(color=Color.White.copy(.13f),shape=RoundedCornerShape(20.dp)){Text("YOUR SOUND • EVERY LANGUAGE",Modifier.padding(horizontal=11.dp,vertical=6.dp),color=NexaBright,fontSize=10.sp,fontWeight=FontWeight.Bold)}
                    Spacer(Modifier.height(12.dp));Text("Search it.\nPlay it.",color=NexaBright,fontSize=38.sp,lineHeight=38.sp,fontWeight=FontWeight.Black)
                    Text("Songs, artists, movies, local music + radio.",color=NexaSoft,fontSize=12.sp)
                }
                FilledIconButton(onClick=onSearch,Modifier.align(Alignment.BottomEnd).size(58.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color.White,contentColor=Color.Black)){Icon(Icons.Default.Search,null,Modifier.size(30.dp))}
            }
        }
        if(recent.isNotEmpty()){
            item{SectionTitle("Continue listening","Pick up where you left off")}
            item{LazyRow(contentPadding=PaddingValues(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                items(recent.take(12),key={it.id}){t->Column(Modifier.width(142.dp).clickable{onPlay(t,recent)}){
                    NexaArtwork(t,Modifier.size(142.dp),24);Spacer(Modifier.height(8.dp))
                    Text(t.title,color=NexaBright,fontWeight=FontWeight.Bold,maxLines=1);Text(t.artist,color=NexaMuted,fontSize=11.sp,maxLines=1)
                }}
            }}
        }
        item{SectionTitle("Explore your world","Hindi • English • Punjabi • Tamil • Telugu • more")}
        item{LazyRow(contentPadding=PaddingValues(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            items(listOf("Hindi Hits","Punjabi","Tamil","Telugu","Indie","Lo-Fi","Devotional","Electronic")){label->
                Box(Modifier.width(126.dp).height(76.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(NexaPurple.copy(.65f),NexaCyan.copy(.28f)))).clickable(onClick=onSearch).padding(12.dp),contentAlignment=Alignment.BottomStart){
                    Text(label,color=NexaBright,fontWeight=FontWeight.Bold)
                }
            }
        }}
        item{SectionTitle("Live now","Radio stays available when you want endless music")}
        if(loading)item{LoadingBlock("Tuning live music…")}
        if(error!=null)item{ErrorBlock(error,onRetry)}
        items(stations.take(10),key={it.id}){t->TrackRow(t,t.id in favorites,{onPlay(t,stations)},{onFavorite(t)})}
    }
}
