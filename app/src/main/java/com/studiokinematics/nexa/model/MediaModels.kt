package com.studiokinematics.nexa.model

enum class Source { RADIO, AUDIUS, YOUTUBE, LOCAL }

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val language: String = "Music",
    val genre: String = "Music",
    val streamUrl: String = "",
    val source: Source = Source.RADIO,
    val videoId: String = "",
    val thumbnailUrl: String = "",
    val artworkUrl: String = "",
    val countryCode: String = "",
    val homepage: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val durationMs: Long = 0L,
    val album: String = "",
    val contentUri: String = "",
    val albumId: Long = -1L,
    val downloadUri: String = "",
    val isDownloadable: Boolean = false
) {
    val effectiveArtworkUrl: String get() = artworkUrl.ifBlank { thumbnailUrl }
}

enum class SearchMode(val label: String) {
    ALL("All"), SONGS("Songs"), ARTISTS("Artists"), ALBUMS("Albums"), MOVIES("Movies")
}
