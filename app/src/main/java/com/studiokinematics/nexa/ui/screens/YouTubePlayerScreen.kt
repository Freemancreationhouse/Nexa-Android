package com.studiokinematics.nexa.ui.screens

import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.studiokinematics.nexa.model.Track
import com.studiokinematics.nexa.ui.theme.*

@Composable
fun YouTubePlayerOverlay(track:Track,minimized:Boolean,onMinimize:()->Unit,onExpand:()->Unit,onClose:()->Unit){
    var webView by remember(track.id){mutableStateOf<WebView?>(null)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,webView){
        val observer=LifecycleEventObserver{_,event->
            if(event==Lifecycle.Event.ON_STOP){
                webView?.evaluateJavascript("document.querySelector('video')?.pause();",null)
                webView?.onPause()
            }else if(event==Lifecycle.Event.ON_START)webView?.onResume()
        }
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer)}
    }
    val boxModifier=if(minimized)Modifier.fillMaxWidth().height(128.dp).padding(horizontal=12.dp,vertical=6.dp)
        else Modifier.fillMaxSize().background(NexaBg)
    Box(boxModifier){
        Column(Modifier.fillMaxSize().background(Color.Black)){
            Row(Modifier.fillMaxWidth().height(42.dp),verticalAlignment=Alignment.CenterVertically){
                Text(track.title,Modifier.weight(1f).padding(start=12.dp),color=NexaBright,maxLines=1)
                if(minimized)IconButton(onClick=onExpand){Icon(Icons.Default.OpenInFull,null,tint=NexaBright)}
                else IconButton(onClick=onMinimize){Icon(Icons.Default.Minimize,null,tint=NexaBright)}
                IconButton(onClick={webView?.stopLoading();webView?.destroy();onClose()}){Icon(Icons.Default.Close,null,tint=NexaBright)}
            }
            AndroidView(factory={ctx->WebView(ctx).apply{
                settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.mediaPlaybackRequiresUserGesture=false;settings.cacheMode=WebSettings.LOAD_DEFAULT
                webChromeClient=WebChromeClient();webViewClient=WebViewClient();setBackgroundColor(android.graphics.Color.BLACK)
                loadUrl("https://www.youtube.com/embed/${track.videoId}?autoplay=1&playsinline=1&controls=1&rel=0&enablejsapi=1",mapOf("Referer" to "https://com.studiokinematics.nexa"))
                webView=this
            }},modifier=Modifier.fillMaxSize())
        }
    }
}
