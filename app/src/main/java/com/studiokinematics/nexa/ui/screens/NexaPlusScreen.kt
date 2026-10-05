package com.studiokinematics.nexa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studiokinematics.nexa.billing.NexaPlusState
import com.studiokinematics.nexa.ui.theme.*

@Composable
fun NexaPlusScreen(state:NexaPlusState,onBuy:()->Unit,onRestore:()->Unit,onClose:()->Unit){
    Box(Modifier.fillMaxSize().background(NexaBg).padding(20.dp)){
        Column(Modifier.fillMaxSize()){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=onClose){Icon(Icons.Default.Close,null,tint=NexaBright)}
                Text("NEXA+",color=NexaBright,fontSize=22.sp,fontWeight=FontWeight.Black)
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Brush.linearGradient(listOf(NexaPurple,NexaPink,NexaCyan))).padding(24.dp)){
                Column{
                    Text("MORE MUSIC.\nMORE OFFLINE.",color=NexaBright,fontSize=34.sp,lineHeight=34.sp,fontWeight=FontWeight.Black)
                    Text("NEXA Free includes 5 eligible online offline songs. Your On Device music stays unlimited.",color=NexaBright.copy(.9f))
                }
            }
            Spacer(Modifier.height(22.dp))
            listOf("More eligible NEXA offline downloads","On Device music stays unlimited","Private in-app storage","Same NEXA background player").forEach{
                Row(Modifier.padding(vertical=8.dp)){Icon(Icons.Default.CheckCircle,null,tint=NexaCyan);Spacer(Modifier.width(10.dp));Text(it,color=NexaBright)}
            }
            Spacer(Modifier.weight(1f))
            when(state){
                is NexaPlusState.Available->Button(onClick=onBuy,modifier=Modifier.fillMaxWidth().height(54.dp)){Text("Upgrade • ${state.offer.price}",fontWeight=FontWeight.Bold)}
                NexaPlusState.Active->Button(onClick={},enabled=false,modifier=Modifier.fillMaxWidth()){Text("NEXA+ Active")}
                NexaPlusState.Pending->Text("Purchase pending — Free limits remain until Google Play confirms payment.",color=NexaCyan)
                NexaPlusState.Loading->CircularProgressIndicator(color=NexaCyan)
                is NexaPlusState.Unavailable->Text(state.reason,color=NexaMuted)
                is NexaPlusState.Error->Text(state.message,color=NexaDanger)
                NexaPlusState.Free->Text("NEXA Free • 5 eligible offline songs",color=NexaMuted)
            }
            TextButton(onClick=onRestore,modifier=Modifier.fillMaxWidth()){Text("Restore / refresh purchase")}
            Text("YouTube and radio remain streaming-only. Source permissions still apply.",color=NexaMuted,fontSize=10.sp)
        }
    }
}
