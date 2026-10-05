package com.studiokinematics.nexa.model

sealed interface LyricsState{
    data object Loading:LyricsState
    data class Synced(val lines:List<TimedLyric>):LyricsState
    data class Plain(val text:String):LyricsState
    data object Instrumental:LyricsState
    data object Unavailable:LyricsState
    data class Error(val message:String):LyricsState
}
data class TimedLyric(val atMs:Long,val text:String)
