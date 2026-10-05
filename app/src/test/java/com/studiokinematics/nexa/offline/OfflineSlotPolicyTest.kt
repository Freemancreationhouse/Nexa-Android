package com.studiokinematics.nexa.offline

import com.studiokinematics.nexa.model.Source
import com.studiokinematics.nexa.model.Track
import org.junit.Assert.*
import org.junit.Test

class OfflineSlotPolicyTest {
    private val eligible=Track(id="a",title="Song",artist="Artist",streamUrl="https://stream",source=Source.AUDIUS,downloadUri="https://download",isDownloadable=true)

    @Test fun freeAllowsFiveButNotSixth() {
        assertTrue(OfflineSlotPolicy.canAccept(0,OfflineEntitlement.FREE))
        assertTrue(OfflineSlotPolicy.canAccept(4,OfflineEntitlement.FREE))
        assertFalse(OfflineSlotPolicy.canAccept(5,OfflineEntitlement.FREE))
    }

    @Test fun localMusicNeverConsumesNexaOfflineSlot() {
        val local=Track(id="local:1",title="Local",artist="Artist",source=Source.LOCAL,contentUri="content://media/1")
        assertFalse(OfflineSlotPolicy.countsTowardLimit(local))
    }

    @Test fun youtubeAndRadioAreNeverDownloadEligible() {
        assertFalse(OfflineSlotPolicy.isEligible(eligible.copy(source=Source.YOUTUBE,videoId="x")))
        assertFalse(OfflineSlotPolicy.isEligible(eligible.copy(source=Source.RADIO)))
    }
}
