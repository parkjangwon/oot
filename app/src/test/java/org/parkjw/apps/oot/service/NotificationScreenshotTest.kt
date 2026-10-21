package org.parkjw.apps.oot.service

import android.app.Activity
import android.app.Notification
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorError
import org.parkjw.apps.oot.data.model.VendorUsage
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class NotificationScreenshotTest {

    // Same fake set as the debug demo (DebugDemoNotificationReceiver).
    private fun fakeUsages(): List<VendorUsage> {
        val now = Instant.now()
        return listOf(
            VendorUsage(
                accountId = "demo-claude",
                providerType = ProviderType.CLAUDE,
                displayName = "claude-demo@example.com",
                windows = listOf(
                    UsageWindow("five_hour", "5시간", Remaining.Percent(62.0), now.plusSeconds(100 * 60)),
                    UsageWindow("seven_day", "주간", Remaining.Percent(81.0), null)
                )
            ),
            VendorUsage(
                accountId = "demo-glm",
                providerType = ProviderType.ZAI,
                displayName = "glm-demo@example.com",
                windows = listOf(
                    UsageWindow("monthly", "월간", Remaining.Percent(98.0), null)
                )
            ),
            VendorUsage(
                accountId = "demo-xai",
                providerType = ProviderType.XAI,
                displayName = "xai-demo@example.com",
                windows = listOf(
                    UsageWindow("primary", "primary", Remaining.Percent(14.0), null)
                )
            ),
            VendorUsage(
                accountId = "demo-openrouter",
                providerType = ProviderType.OPENROUTER,
                displayName = "or-demo@example.com",
                windows = listOf(
                    UsageWindow("credit", "credit", Remaining.Balance(12.40))
                )
            )
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-notnight")
    fun notifCollapsed360Light() {
        captureCollapsed(360, "notif-collapsed-360-light.png")
    }

    @Test
    @Config(qualifiers = "w412dp-h800dp-notnight")
    fun notifCollapsed412Light() {
        captureCollapsed(412, "notif-collapsed-412-light.png")
    }

    @Test
    @Config(qualifiers = "w412dp-h800dp-notnight")
    fun notifExpanded412Light() {
        captureExpanded(412, "notif-expanded-412-light.png")
    }

    @Test
    @Config(qualifiers = "w412dp-h800dp-night")
    fun notifCollapsed412Dark() {
        captureCollapsed(412, "notif-collapsed-412-dark.png")
    }

    @Test
    @Config(qualifiers = "w412dp-h800dp-night")
    fun notifExpanded412Dark() {
        captureExpanded(412, "notif-expanded-412-dark.png")
    }

    // Dummy Antigravity line for the 3x review shots: only the Google window reaches the notification.
    private fun antigravityUsage(): VendorUsage = VendorUsage(
        accountId = "demo-antigravity",
        providerType = ProviderType.ANTIGRAVITY,
        displayName = "agy-demo@example.com",
        windows = listOf(
            UsageWindow("google", "Google 모델", Remaining.Percent(62.0), Instant.now().plusSeconds(2 * 3600 + 30)),
            UsageWindow("third_party", "타사 모델", Remaining.Percent(18.0), Instant.now().plusSeconds(75 * 60))
        )
    )

    // 3x = xxhdpi; Android's qualifier order puts night mode before density.
    @Test
    @Config(qualifiers = "w412dp-h800dp-notnight-xxhdpi")
    fun notifExpanded412AntigravityLight3x() {
        captureExpanded(412, "notif-expanded-412-antigravity-light-3x.png", fakeUsages() + antigravityUsage())
    }

    @Test
    @Config(qualifiers = "w412dp-h800dp-night-xxhdpi")
    fun notifExpanded412AntigravityDark3x() {
        captureExpanded(412, "notif-expanded-412-antigravity-dark-3x.png", fakeUsages() + antigravityUsage())
    }

    // 14% used to pick the amber "warn" entry; that color no longer exists.
    @Test
    fun barHasNoWarningColorAt14Percent() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertEquals(0, activity.resources.getIdentifier("notif_bar_warn", "color", activity.packageName))
    }

    private fun captureCollapsed(widthDp: Int, fileName: String) {
        val notification = buildNotification()
        capture(notification.contentView!!, widthDp, fileName)
    }

    private fun captureExpanded(widthDp: Int, fileName: String, usages: List<VendorUsage> = fakeUsages()) {
        val notification = buildNotification(usages)
        capture(notification.bigContentView!!, widthDp, fileName)
    }

    private fun buildNotification(usages: List<VendorUsage> = fakeUsages()): Notification =
        TokenNotificationManager(Robolectric.buildActivity(Activity::class.java).setup().get())
            .buildNotification(usages)

    private fun capture(views: android.widget.RemoteViews, widthDp: Int, fileName: String) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val container = FrameLayout(activity)
        val view = views.apply(activity, container)
        view.setBackgroundColor(notificationBackground(activity))
        container.addView(
            view,
            FrameLayout.LayoutParams(widthDp.dpPx(activity), ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        val widthPx = widthDp.dpPx(activity)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        container.layout(0, 0, container.measuredWidth, container.measuredHeight)
        activity.setContentView(container)
        view.captureRoboImage(relativeOutput(fileName))
    }

    // Real notifications draw on the system surface background; approximate it per light/dark.
    private fun notificationBackground(activity: Activity): Int {
        val dark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return Color.parseColor(if (dark) "#1F1B16" else "#FCF8F8")
    }

    private fun relativeOutput(fileName: String): String {
        val dir = System.getProperty("roborazzi.output.dir")
            ?: "build/outputs/roborazzi"
        return File(dir, fileName).path
    }

    private fun Int.dpPx(activity: Activity): Int =
        (this * activity.resources.displayMetrics.density).toInt()
}
