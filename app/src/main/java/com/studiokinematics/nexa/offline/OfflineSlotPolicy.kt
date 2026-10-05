package com.studiokinematics.nexa.offline

import com.studiokinematics.nexa.model.Source
import com.studiokinematics.nexa.model.Track

object OfflineSlotPolicy {
    const val FREE_LIMIT = 5

    fun isEligible(track:Track):Boolean =
        track.source==Source.AUDIUS &&
        track.isDownloadable &&
        (track.downloadUri.startsWith("https://") || track.downloadUri.startsWith("http://"))

    fun countsTowardLimit(track:Track):Boolean = when(track.source){
        Source.LOCAL,Source.YOUTUBE,Source.RADIO->false
        Source.AUDIUS->isEligible(track)
    }

    fun canAccept(occupied:Int,entitlement:OfflineEntitlement):Boolean =
        entitlement==OfflineEntitlement.NEXA_PLUS || occupied<FREE_LIMIT
}
