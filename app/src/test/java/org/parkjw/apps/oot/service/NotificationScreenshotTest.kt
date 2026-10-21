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

    // Reset is rounded down to whole minutes when rendered; +30s keeps "N분" stable while the test runs.
    private fun resetIn(minutes: Long): Instant = Instant.now().plusSeconds(minutes * 60 + 30)

    // Mirrors the debug demo (DebugDemoNotificationReceiver) plus one vendor whose text is too long to fit.
    private fun fakeUsages(): List<VendorUsage> = listOf(
        VendorUsage(
            accountId = "demo-claude",
            providerType = ProviderType.CLAUDE,
            displayName = "claude-demo@example.com",
            windows = listOf(
                UsageWindow("five_hour", "5시간", Remaining.Percent(62.0), resetIn(100)),
                UsageWindow("seven_day", "주간", Remaining.Percent(81.0), resetIn(3 * 24 * 60))
            )
        ),
        VendorUsage(
            accountId = "demo-glm",
            providerType = ProviderType.ZAI,
            displayName = "glm-demo@example.com",
            windows = listOf(
                UsageWindow("five_hour", "5시간", Remaining.Percent(98.0), resetIn(3 * 60 + 27)),
                UsageWindow("seven_day", "주간", Remaining.Percent(71.0), resetIn(4 * 24 * 60))
            )
        ),
        VendorUsage(
            accountId = "demo-xai",
            providerType = ProviderType.XAI,
            displayName = "xai-demo@example.com",
            windows = listOf(
                UsageWindow("weekly", "주간", Remaining.Percent(14.0), resetIn(2 * 24 * 60))
            )
        ),
        VendorUsage(
            accountId = "demo-openrouter",
            providerType = ProviderType.OPENROUTER,
            displayName = "or-demo@example.com",
            windows = listOf(
                UsageWindow("credit", "credit", Remaining.Balance(12.40))
            )
        ),
        VendorUsage(
            accountId = "demo-antigravity-long",
            providerType = ProviderType.ANTIGRAVITY,
            displayName = "antigravity-very-long-demo-account-name@example.com",
            windows = listOf(
                UsageWindow(
                    "google",
                    "Gemini 3 Pro 고급 추론 일일 한도 (아주 긴 라벨 예시)",
                    Remaining.Percent(38.0),
                    resetIn(4 * 60 + 20)
                )
            )
        )
    )

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

    // 3x (xxhdpi) captures, the density real phones render notifications at.
    // Qualifier order matters: night mode must come before the density (Robolectric rejects ...-xxhdpi-night).
    @Test
    @Config(qualifiers = "w412dp-h800dp-notnight-xxhdpi")
    fun notifCollapsed412Light3x() {
        captureCollapsed(412, "notif-collapsed-412-light-3x.png")
    }

    @Test
    @Config(qualifiers = "w412dp-h800dp-notnight-xxhdpi")
    fun notifExpanded412Light3x() {
        captureExpanded(412, "notif-expanded-412-light-3x.png")
    }

    @Test
    @Config(qualifiers = "w412dp-h800dp-night-xxhdpi")
    fun notifCollapsed412Dark3x() {
        captureCollapsed(412, "notif-collapsed-412-dark-3x.png")
    }

    @Test
    @Config(qualifiers = "w412dp-h800dp-night-xxhdpi")
    fun notifExpanded412Dark3x() {
        captureExpanded(412, "notif-expanded-412-dark-3x.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-notnight-xxhdpi")
    fun notifCollapsed360Light3x() {
        captureCollapsed(360, "notif-collapsed-360-light-3x.png")
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

    private fun captureExpanded(widthDp: Int, fileName: String) {
        val notification = buildNotification()
        capture(notification.bigContentView!!, widthDp, fileName)
    }

    private fun buildNotification(): Notification =
        TokenNotificationManager(Robolectric.buildActivity(Activity::class.java).setup().get())
            .buildNotification(fakeUsages())

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
