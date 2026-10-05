package com.studiokinematics.nexa.model

data class SourceCapabilities(
    val nativePlayback: Boolean,
    val embeddedPlayback: Boolean,
    val background: Boolean,
    val offline: Boolean,
    val countsTowardNexaOfflineLimit: Boolean
) {
    companion object {
        fun forTrack(track: Track): SourceCapabilities = when (track.source) {
            Source.YOUTUBE -> SourceCapabilities(
                nativePlayback = false, embeddedPlayback = true, background = false,
                offline = false, countsTowardNexaOfflineLimit = false
            )
            Source.RADIO -> SourceCapabilities(
                nativePlayback = true, embeddedPlayback = false, background = true,
                offline = false, countsTowardNexaOfflineLimit = false
            )
            Source.AUDIUS -> SourceCapabilities(
                nativePlayback = true, embeddedPlayback = false, background = true,
                offline = track.isDownloadable && track.downloadUri.startsWith("http"),
                countsTowardNexaOfflineLimit = track.isDownloadable && track.downloadUri.startsWith("http")
            )
            Source.LOCAL -> SourceCapabilities(
                nativePlayback = true, embeddedPlayback = false, background = true,
                offline = true, countsTowardNexaOfflineLimit = false
            )
        }
    }
}
