package org.parkjw.apps.oot.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class InstalledVersionTest {
    @Test
    fun installedVersionNameIsComparable() {
        val name = ApplicationProvider.getApplicationContext<Context>().installedVersionName()
        assertNotNull(name, AppVersion.parse(name))
    }
}
