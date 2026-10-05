package com.studiokinematics.nexa.local

import android.Manifest

object LocalMediaPermission {
    fun permissionForSdk(sdkInt: Int): String =
        if (sdkInt >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
}
