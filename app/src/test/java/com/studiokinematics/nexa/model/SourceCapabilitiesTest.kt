package com.studiokinematics.nexa.model

import org.junit.Assert.*
import org.junit.Test

class SourceCapabilitiesTest {
    @Test fun youtubeIsEmbeddedOnly() {
        val c = SourceCapabilities.forTrack(Track(id="y", title="Song", artist="Artist", source=Source.YOUTUBE, videoId="id"))
        assertFalse(c.nativePlayback)
        assertTrue(c.embeddedPlayback)
        assertFalse(c.background)
        assertFalse(c.offline)
    }

    @Test fun localMusicIsNativeAndOfflineWithoutNexaDownloadSlot() {
        val c = SourceCapabilities.forTrack(Track(id="local:1", title="Local", artist="Artist", source=Source.LOCAL, contentUri="content://media/1"))
        assertTrue(c.nativePlayback)
        assertTrue(c.background)
        assertTrue(c.offline)
        assertFalse(c.countsTowardNexaOfflineLimit)
    }
}
