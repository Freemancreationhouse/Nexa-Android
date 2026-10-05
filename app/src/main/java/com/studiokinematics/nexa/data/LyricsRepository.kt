package com.studiokinematics.nexa.data

import com.studiokinematics.nexa.model.LyricsState
import com.studiokinematics.nexa.model.Track

/**
 * NEXA only displays lyrics returned by a permitted/licensed provider.
 * V0.4 intentionally does not scrape, reconstruct, or fabricate copyrighted lyrics.
 */
class LyricsRepository{
    suspend fun getLyrics(track:Track):LyricsState =
        if(track.title.contains("instrumental",ignoreCase=true))LyricsState.Instrumental
        else LyricsState.Unavailable
}
