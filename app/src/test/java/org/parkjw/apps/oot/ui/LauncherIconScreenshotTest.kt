package org.parkjw.apps.oot.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.R
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LauncherIconScreenshotTest {

    @Test
    fun launcherIcon() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertEquals(R.mipmap.ic_launcher, activity.applicationInfo.icon)
        assertTrue(activity.getDrawable(R.mipmap.ic_launcher_round) is AdaptiveIconDrawable)

        val drawable = activity.getDrawable(R.mipmap.ic_launcher)
        assertTrue(drawable is AdaptiveIconDrawable)
        val adaptive = drawable as AdaptiveIconDrawable
        val background = requireNotNull(adaptive.background)
        val foreground = requireNotNull(adaptive.foreground)

        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        background.setBounds(0, 0, size, size)
        background.draw(canvas)
        foreground.setBounds(0, 0, size, size)
        foreground.draw(canvas)

        bitmap.captureRoboImage(File(roborazziSystemPropertyOutputDirectory(), "launcher-icon.png"))
    }

    @Test
    fun launcherIconMonochrome() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val adaptive = activity.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val background = requireNotNull(adaptive.background)
        val monochrome = requireNotNull(adaptive.monochrome)
        assertNotNull(activity.getDrawable(R.drawable.ic_stat_oot))

        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        background.setBounds(0, 0, size, size)
        background.draw(canvas)
        monochrome.setBounds(0, 0, size, size)
        monochrome.draw(canvas)

        bitmap.captureRoboImage(File(roborazziSystemPropertyOutputDirectory(), "launcher-icon-monochrome.png"))
    }
}
