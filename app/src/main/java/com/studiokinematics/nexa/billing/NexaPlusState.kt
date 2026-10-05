package com.studiokinematics.nexa.billing

data class ProductOffer(val title:String,val price:String,val offerToken:String)

sealed interface NexaPlusState{
    data object Free:NexaPlusState
    data object Loading:NexaPlusState
    data class Available(val offer:ProductOffer):NexaPlusState
    data object Active:NexaPlusState
    data object Pending:NexaPlusState
    data class Unavailable(val reason:String):NexaPlusState
    data class Error(val message:String):NexaPlusState
}
