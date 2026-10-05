package com.studiokinematics.nexa.offline

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.studiokinematics.nexa.data.LibraryStore
import com.studiokinematics.nexa.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

enum class OfflineStatus{QUEUED,DOWNLOADING,COMPLETED,FAILED,STOPPED}
data class OfflineRecord(val track:Track,val status:OfflineStatus,val percent:Float=0f,val bytes:Long=0L)
sealed interface EnqueueResult{
    data object Accepted:EnqueueResult
    data object UpgradeRequired:EnqueueResult
    data object AlreadySaved:EnqueueResult
    data class Ineligible(val reason:String):EnqueueResult
}

@OptIn(UnstableApi::class)
class DownloadRepository(
    context:Context,
    private val entitlement:()->OfflineEntitlement={OfflineEntitlement.FREE}
):DownloadManager.Listener{
    private val app=context.applicationContext
    private val prefs=app.getSharedPreferences("nexa_offline",Context.MODE_PRIVATE)
    private val manager=OfflineEngine.manager(app)
    private val downloadIndex=manager.downloadIndex
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val records=ConcurrentHashMap<String,OfflineRecord>()
    private val _downloads=MutableStateFlow<List<OfflineRecord>>(emptyList())
    val downloads:StateFlow<List<OfflineRecord>> = _downloads.asStateFlow()

    init{
        load().forEach{records[it.track.id]=it}
        manager.addListener(this)
        publish()
        scope.launch{reconcileIndex()}
    }

    fun enqueue(track:Track):EnqueueResult{
        if(!OfflineSlotPolicy.isEligible(track))return EnqueueResult.Ineligible("This source does not permit NEXA offline storage.")
        if(records[track.id]?.status in setOf(OfflineStatus.QUEUED,OfflineStatus.DOWNLOADING,OfflineStatus.COMPLETED,OfflineStatus.STOPPED))return EnqueueResult.AlreadySaved
        val occupied=records.values.count{it.status in setOf(OfflineStatus.QUEUED,OfflineStatus.DOWNLOADING,OfflineStatus.COMPLETED,OfflineStatus.STOPPED)}
        if(!OfflineSlotPolicy.canAccept(occupied,entitlement()))return EnqueueResult.UpgradeRequired

        records[track.id]=OfflineRecord(track,OfflineStatus.QUEUED);persist();publish()
        val request=DownloadRequest.Builder(track.id,Uri.parse(track.downloadUri))
            .setCustomCacheKey(track.id)
            .setData(LibraryStore.encode(listOf(track)).toByteArray(Charsets.UTF_8))
            .build()
        DownloadService.sendAddDownload(app,NexaDownloadService::class.java,request,false)
        return EnqueueResult.Accepted
    }

    fun remove(trackId:String){
        records.remove(trackId);persist();publish()
        DownloadService.sendRemoveDownload(app,NexaDownloadService::class.java,trackId,false)
    }

    fun setWifiOnly(enabled:Boolean){
        val req=Requirements(if(enabled)Requirements.NETWORK_UNMETERED else Requirements.NETWORK)
        DownloadService.sendSetRequirements(app,NexaDownloadService::class.java,req,false)
        prefs.edit().putBoolean("wifi_only",enabled).apply()
    }
    fun wifiOnly():Boolean=prefs.getBoolean("wifi_only",false)

    override fun onDownloadChanged(downloadManager:DownloadManager,download:Download,finalException:Exception?){
        val existing=records[download.request.id]?:decode(download.request.data)?.let{OfflineRecord(it,OfflineStatus.QUEUED)}?:return
        val status=when(download.state){
            Download.STATE_QUEUED->OfflineStatus.QUEUED
            Download.STATE_DOWNLOADING->OfflineStatus.DOWNLOADING
            Download.STATE_COMPLETED->OfflineStatus.COMPLETED
            Download.STATE_STOPPED->OfflineStatus.STOPPED
            Download.STATE_FAILED->OfflineStatus.FAILED
            else->existing.status
        }
        if(status==OfflineStatus.FAILED)records.remove(download.request.id)
        else records[download.request.id]=existing.copy(status=status,percent=download.percentDownloaded,bytes=download.bytesDownloaded)
        persist();publish()
    }

    fun close(){manager.removeListener(this);scope.cancel()}

    private fun reconcileIndex(){
        runCatching{
            downloadIndex.getDownloads().use{cursor->
                while(cursor.moveToNext()){
                    val d=cursor.download
                    val track=decode(d.request.data)?:records[d.request.id]?.track?:continue
                    val status=when(d.state){
                        Download.STATE_QUEUED->OfflineStatus.QUEUED
                        Download.STATE_DOWNLOADING->OfflineStatus.DOWNLOADING
                        Download.STATE_COMPLETED->OfflineStatus.COMPLETED
                        Download.STATE_STOPPED->OfflineStatus.STOPPED
                        Download.STATE_FAILED->OfflineStatus.FAILED
                        else->records[d.request.id]?.status?:OfflineStatus.QUEUED
                    }
                    if(status==OfflineStatus.FAILED)records.remove(d.request.id)
                    else records[d.request.id]=OfflineRecord(track,status,d.percentDownloaded,d.bytesDownloaded)
                }
            }
            persist();publish()
        }
    }
    private fun publish(){_downloads.value=records.values.sortedWith(compareBy<OfflineRecord>{it.status!=OfflineStatus.COMPLETED}.thenBy{it.track.title.lowercase()})}
    private fun decode(data:ByteArray):Track?=runCatching{LibraryStore.decode(data.toString(Charsets.UTF_8)).firstOrNull()}.getOrNull()
    private fun persist(){
        val arr=JSONArray()
        records.values.forEach{r->arr.put(JSONObject().apply{
            put("track",LibraryStore.encode(listOf(r.track)));put("status",r.status.name);put("percent",r.percent.toDouble());put("bytes",r.bytes)
        })}
        prefs.edit().putString("records",arr.toString()).apply()
    }
    private fun load():List<OfflineRecord> = runCatching{
        val arr=JSONArray(prefs.getString("records","[]").orEmpty().ifBlank{"[]"});buildList{
            for(i in 0 until arr.length()){
                val o=arr.optJSONObject(i)?:continue
                val track=LibraryStore.decode(o.optString("track")).firstOrNull()?:continue
                val status=runCatching{OfflineStatus.valueOf(o.optString("status"))}.getOrDefault(OfflineStatus.FAILED)
                if(status!=OfflineStatus.FAILED)add(OfflineRecord(track,status,o.optDouble("percent",0.0).toFloat(),o.optLong("bytes",0L)))
            }
        }
    }.getOrDefault(emptyList())
}
