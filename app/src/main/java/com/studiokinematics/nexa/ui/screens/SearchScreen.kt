package com.studiokinematics.nexa.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.studiokinematics.nexa.data.AudiusRepository
import com.studiokinematics.nexa.data.RadioBrowserRepository
import com.studiokinematics.nexa.data.YouTubeRepository
import com.studiokinematics.nexa.model.SearchMode
import com.studiokinematics.nexa.model.Track
import com.studiokinematics.nexa.ui.components.*
import com.studiokinematics.nexa.ui.theme.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

@Composable
fun SearchScreen(radio:RadioBrowserRepository,audius:AudiusRepository,youtube:YouTubeRepository,localTracks:List<Track>,onPlay:(Track,List<Track>)->Unit,favorites:Set<String>,onFavorite:(Track)->Unit,onSettings:()->Unit){
    var query by rememberSaveable{mutableStateOf("")};var mode by rememberSaveable{mutableStateOf(SearchMode.ALL)}
    var results by remember{mutableStateOf<List<Track>>(emptyList())};var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)};var notes by remember{mutableStateOf<List<String>>(emptyList())}
    val scope=rememberCoroutineScope()

    fun search(value:String=query){
        val q=value.trim();if(q.isBlank())return
        loading=true;error=null;notes=emptyList()
        scope.launch{
            try{
                val providerResults=supervisorScope{
                    val yt=async{runCatching{youtube.search(q,mode)}}
                    val au=async{runCatching{audius.search(q)}}
                    val rb=async{runCatching{radio.search(q)}}
                    Triple(yt.await(),au.await(),rb.await())
                }
                val local=localTracks.filter{
                    it.title.contains(q,true)||it.artist.contains(q,true)||it.album.contains(q,true)||it.genre.contains(q,true)
                }
                results=(local+providerResults.second.getOrDefault(emptyList())+providerResults.first.getOrDefault(emptyList())+providerResults.third.getOrDefault(emptyList())).distinctBy{it.id}
                notes=buildList{
                    providerResults.first.exceptionOrNull()?.message?.let(::add)
                    providerResults.second.exceptionOrNull()?.message?.let{add("Audius: $it")}
                    providerResults.third.exceptionOrNull()?.message?.let{add("Radio: $it")}
                }.distinct()
                error=if(results.isEmpty())notes.joinToString(" • ").ifBlank{"No results. Try a song, artist, movie or album name."}else null
            }catch(t:Throwable){error="Search could not finish: ${t.message?:"unknown error"}"}finally{loading=false}
        }
    }

    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)){
        item{TopBrand("Search songs, artists, movies",onSettings=onSettings)}
        item{OutlinedTextField(value=query,onValueChange={query=it},singleLine=true,
            placeholder={Text("Song, artist, movie, album…",color=NexaMuted)},leadingIcon={Icon(Icons.Default.Search,null,tint=NexaCyan)},
            trailingIcon={IconButton(onClick={search()}){Icon(Icons.Default.ArrowForward,null,tint=NexaBright)}},
            modifier=Modifier.padding(horizontal=18.dp).fillMaxWidth(),shape=RoundedCornerShape(22.dp),
            colors=OutlinedTextFieldDefaults.colors(focusedTextColor=NexaBright,unfocusedTextColor=NexaBright,focusedBorderColor=NexaPurple,unfocusedBorderColor=NexaPanel2,focusedContainerColor=NexaPanel2,unfocusedContainerColor=NexaPanel2))}
        item{LazyRow(contentPadding=PaddingValues(horizontal=18.dp,vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            items(SearchMode.entries){m->Surface(color=if(m==mode)NexaPurple.copy(.55f)else NexaPanel2,shape=RoundedCornerShape(30.dp),modifier=Modifier.clickable{mode=m;if(query.isNotBlank())search()}){Text(m.label,Modifier.padding(horizontal=14.dp,vertical=8.dp),color=NexaBright,fontWeight=FontWeight.Bold)}}
        }}
        if(query.isBlank()){
            item{SectionTitle("Find your sound","Search naturally by song, artist, movie, album or language")}
            item{LazyRow(contentPadding=PaddingValues(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(9.dp)){
                items(listOf("Arijit Singh","A R Rahman","Hindi romantic","Punjabi hits","Tamil melody","English pop")){x->
                    Surface(color=NexaPanel,shape=RoundedCornerShape(18.dp),modifier=Modifier.clickable{query=x;search(x)}){Text(x,Modifier.padding(14.dp),color=NexaBright,fontWeight=FontWeight.SemiBold)}
                }
            }}
        }else item{SectionTitle("Results","${results.size} discoveries")}
        if(loading)item{LoadingBlock("Searching NEXA sources…")}
        error?.let{item{ErrorBlock(it,null)}}
        if(notes.any{it.contains("not configured",true)})item{Button(onClick=onSettings,modifier=Modifier.padding(18.dp).fillMaxWidth()){Icon(Icons.Default.Settings,null);Spacer(Modifier.width(8.dp));Text("Configure mainstream search")}}
        if(notes.isNotEmpty()&&results.isNotEmpty())item{ErrorBlock("Some sources are unavailable: ${notes.joinToString(" • ")}",null)}
        items(results,key={it.id}){t->TrackRow(t,t.id in favorites,{onPlay(t,results)},{onFavorite(t)})}
    }
}
