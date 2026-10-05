package com.studiokinematics.nexa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
fun RadioScreen(stations:List<Track>,loading:Boolean,error:String?,onRetry:()->Unit,onPlay:(Track,List<Track>)->Unit,favorites:Set<String>,onFavorite:(Track)->Unit){
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)){
        item{TopBrand("Live & endless")}
        item{Box(Modifier.padding(horizontal=18.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF35105F),Color(0xFF075A66)))).padding(22.dp)){
            Column{Text("NEXA RADIO",color=NexaCyan,fontWeight=FontWeight.Black,letterSpacing=2.sp);Text("Always on.",color=NexaBright,fontSize=34.sp,fontWeight=FontWeight.Black);Text("Live stations from India and around the world.",color=NexaSoft)}
        }}
        item{SectionTitle("Live stations","Native playback continues in background")}
        if(loading)item{LoadingBlock("Tuning stations…")}
        if(error!=null)item{ErrorBlock(error,onRetry)}
        items(stations,key={it.id}){t->TrackRow(t,t.id in favorites,{onPlay(t,stations)},{onFavorite(t)})}
    }
}
