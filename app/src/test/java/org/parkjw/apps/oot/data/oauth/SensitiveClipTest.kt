package org.parkjw.apps.oot.data.oauth

import android.content.ClipDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SensitiveClipTest {

    @Test
    @Config(sdk = [35])
    fun marksClipSensitiveOnAndroid13Plus() {
        val clip = sensitiveClip("verification code", "FAKE-CODE-0000")

        assertEquals(true, clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
    }

    @Test
    @Config(sdk = [32])
    fun leavesClipUnmarkedBeforeAndroid13() {
        val clip = sensitiveClip("verification code", "FAKE-CODE-0000")

        val extras = clip.description.extras
        assertTrue(extras == null || !extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false))
    }
}
