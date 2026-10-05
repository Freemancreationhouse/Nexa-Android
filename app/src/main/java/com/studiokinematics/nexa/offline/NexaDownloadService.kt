package com.studiokinematics.nexa.offline

import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.studiokinematics.nexa.R

@UnstableApi
class NexaDownloadService : DownloadService(
    2304,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    "nexa_downloads",
    R.string.download_channel_name,
    R.string.download_channel_description
) {
    override fun getDownloadManager():DownloadManager=OfflineEngine.manager(this)
    override fun getScheduler():Scheduler?=null
    override fun getForegroundNotification(downloads:MutableList<Download>,notMetRequirements:Int):Notification =
        DownloadNotificationHelper(this,"nexa_downloads").buildProgressNotification(
            this,R.drawable.ic_nexa_logo,null,"Saving music inside NEXA",downloads,notMetRequirements
        )
}
