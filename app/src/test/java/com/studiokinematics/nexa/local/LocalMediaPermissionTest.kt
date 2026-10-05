package com.studiokinematics.nexa.local

import android.Manifest
import org.junit.Assert.*
import org.junit.Test

class LocalMediaPermissionTest {
    @Test fun android13AndLaterUsesReadMediaAudio() {
        assertEquals(Manifest.permission.READ_MEDIA_AUDIO, LocalMediaPermission.permissionForSdk(33))
        assertEquals(Manifest.permission.READ_MEDIA_AUDIO, LocalMediaPermission.permissionForSdk(36))
    }

    @Test fun olderSupportedAndroidUsesLegacyReadPermission() {
        assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, LocalMediaPermission.permissionForSdk(26))
        assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, LocalMediaPermission.permissionForSdk(32))
    }
}
