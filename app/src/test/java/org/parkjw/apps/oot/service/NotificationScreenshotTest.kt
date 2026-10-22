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
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorError
import org.parkjw.apps.oot.data.model.VendorUsage
import org.parkjw.apps.oot.i18n.AppLanguage
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class NotificationScreenshotTest {

    // Reset is rounded down to whole minutes when rendered; +30s keeps "N분" stable while the test runs.
    private fun resetIn(minutes: Long): Instant = Instant.now().plusSeconds(minutes * 60 + 30)

    // Mirrors the debug demo (DebugDemoNotificationReceiver) with a long account name that must not reach the row text.
    private fun fakeUsages(): List<VendorUsage> = listOf(
        VendorUsage(
            accountId = "demo-claude",
            providerType = ProviderType.CLAUDE,
            displayName = "claude-demo@example.com",
            windows = listOf(
                UsageWindow("five_hour", "5h", Remaining.Percent(62.0), resetIn(100)),
                UsageWindow("seven_day", "7d", Remaining.Percent(81.0), resetIn(3 * 24 * 60))
            )
        ),
        VendorUsage(
            accountId = "demo-glm",
            providerType = ProviderType.ZAI,
            displayName = "glm-demo@example.com",
            windows = listOf(
                UsageWindow("five_hour", "5h", Remaining.Percent(98.0), resetIn(3 * 60 + 27)),
                UsageWindow("seven_day", "7d", Remaining.Percent(71.0), resetIn(4 * 24 * 60))
            )
        ),
        VendorUsage(
            accountId = "demo-xai",
            providerType = ProviderType.XAI,
            displayName = "xai-demo@example.com",
            windows = listOf(
                UsageWindow("weekly", "7d", Remaining.Percent(14.0), resetIn(2 * 24 * 60 + 3 * 60))
            )
        ),
        VendorUsage(
            accountId = "demo-openrouter",
            providerType = ProviderType.OPENROUTER,
            displayName = "or-demo@example.com",
            windows = listOf(
                UsageWindow("credit", "credit", Remaining.Balance(12.40)),
                UsageWindow("key", "$20.00", Remaining.Balance(7.60))
            )
        ),
        VendorUsage(
            accountId = "demo-antigravity",
            providerType = ProviderType.ANTIGRAVITY,
            displayName = "antigravity-very-long-demo-account-name@example.com",
            windows = listOf(
                UsageWindow("google", "Google 모델", Remaining.Percent(62.0), resetIn(2 * 60)),
                UsageWindow("third_party", "타사 모델", Remaining.Percent(18.0), resetIn(75))
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

    // Claude, first in user order, is switched off in Settings: the collapsed line pulls the next vendor in.
    @Test
    @Config(qualifiers = "w412dp-h800dp-notnight-xxhdpi")
    fun notifCollapsed412Light3xClaudeHidden() {
        captureCollapsed(412, "notif-collapsed-412-light-3x-claude-hidden.png", hidden = setOf("demo-claude"))
    }

    @Test
    @Config(qualifiers = "w412dp-h800dp-notnight-xxhdpi")
    fun notifExpanded412Light3xClaudeHidden() {
        captureExpanded(412, "notif-expanded-412-light-3x-claude-hidden.png", hidden = setOf("demo-claude"))
    }

    @Test
    fun hiddenVendorsLeaveEveryNotificationForm() {
        val hiddenClaude = buildNotification(hidden = setOf("demo-claude"))
        assertEquals("GLM 98% · xAI 14% · OpenRouter $7.60 · Antigravity 62%", hiddenClaude.contentText())
        val expanded = applyToNewActivity(hiddenClaude.bigContentView!!)
        assertEquals(View.VISIBLE, expanded.findViewById<View>(R.id.row3).visibility)
        assertEquals(View.GONE, expanded.findViewById<View>(R.id.row4).visibility)

        // Every vendor off: says so and points to Settings, not "nothing registered".
        val allHidden = buildNotification(hidden = allIds())
        assertEquals("알림에 표시할 프로바이더를 모두 껐어요. 설정에서 켜 주세요.", allHidden.contentText())
        assertEquals(null, allHidden.contentView)
    }

    @Test
    fun noProvidersRegisteredAsksToAddOne() {
        val empty = buildNotification(usages = emptyList())
        assertEquals("등록된 프로바이더가 없어요. 앱에서 추가해 주세요.", empty.contentText())
        // Tapping still opens the app, and the refresh action stays.
        assertEquals(true, empty.contentIntent != null)
        assertEquals("새로고침", empty.actions.single().title.toString())
    }

    @Test
    fun allHiddenKeepsTheTapAndRefreshAction() {
        val allHidden = buildNotification(hidden = allIds())
        assertEquals(true, allHidden.contentIntent != null)
        assertEquals("새로고침", allHidden.actions.single().title.toString())
    }

    // The empty states use the system template; render it the way the shade does.
    @Test
    @Config(qualifiers = "w412dp-h800dp-notnight-xxhdpi")
    fun notifEmptyStates412Light3x() = captureEmptyStates("light")

    @Test
    @Config(qualifiers = "w412dp-h800dp-night-xxhdpi")
    fun notifEmptyStates412Dark3x() = captureEmptyStates("dark")

    // English (#74): same captures with the language set to English in Settings.
    @Test
    @Config(qualifiers = "w412dp-h800dp-notnight-xxhdpi")
    fun notifCollapsed412Light3xEnglish() {
        captureCollapsed(412, "notif-collapsed-412-light-3x-en.png", language = AppLanguage.ENGLISH)
    }

    @Test
    @Config(qualifiers = "w412dp-h800dp-notnight-xxhdpi")
    fun notifExpanded412Light3xEnglish() {
        captureExpanded(412, "notif-expanded-412-light-3x-en.png", language = AppLanguage.ENGLISH)
    }

    @Test
    @Config(qualifiers = "w412dp-h800dp-night-xxhdpi")
    fun notifExpanded412Dark3xEnglish() {
        captureExpanded(412, "notif-expanded-412-dark-3x-en.png", language = AppLanguage.ENGLISH)
    }

    @Test
    @Config(qualifiers = "w412dp-h800dp-notnight-xxhdpi")
    fun notifEmptyStates412Light3xEnglish() = captureEmptyStates("light", AppLanguage.ENGLISH)

    @Test
    fun englishNotificationText() {
        val notification = buildNotification(language = AppLanguage.ENGLISH)
        assertEquals("Claude 62% · GLM 98% · xAI 14% · OpenRouter $7.60 · Antigravity 62%", notification.contentText())
        assertEquals("Refresh", notification.actions.single().title.toString())
        val expanded = applyToNewActivity(notification.bigContentView!!)
        val claude = expanded.findViewById<android.widget.TextView>(R.id.text0).text.toString()
        assertEquals(true, claude.startsWith("5H 62% left · in 1h 40m"))
        val empty = buildNotification(usages = emptyList(), language = AppLanguage.ENGLISH)
        assertEquals("No providers yet. Add one in the app.", empty.contentText())
        assertEquals(
            "All providers are hidden from the notification. Turn them on in Settings.",
            buildNotification(hidden = allIds(), language = AppLanguage.ENGLISH).contentText(),
        )
    }

    private fun captureEmptyStates(mode: String, language: AppLanguage = AppLanguage.KOREAN) {
        val suffix = if (language == AppLanguage.ENGLISH) "-en" else ""
        val cases = mapOf(
            "all-hidden$suffix" to buildNotification(hidden = allIds(), language = language),
            "no-providers$suffix" to buildNotification(usages = emptyList(), language = language),
        )
        for ((name, notification) in cases) {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val builder = Notification.Builder.recoverBuilder(activity, notification)
                // Robolectric's clock starts at 1970, so the shade's time would read "in 57y"; leave it out of the dummy.
                .setShowWhen(false)
            capture(builder.createContentView(), 412, "notif-collapsed-412-$mode-3x-$name.png")
            capture(builder.createBigContentView() ?: builder.createContentView(), 412, "notif-expanded-412-$mode-3x-$name.png")
        }
    }

    private fun allIds(): Set<String> = fakeUsages().map { it.accountId }.toSet()

    private fun Notification.contentText(): String = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    // 14% used to pick the amber "warn" entry; that color no longer exists.
    @Test
    fun barHasNoWarningColorAt14Percent() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertEquals(0, activity.resources.getIdentifier("notif_bar_warn", "color", activity.packageName))
    }

    private fun captureCollapsed(
        widthDp: Int,
        fileName: String,
        hidden: Set<String> = emptySet(),
        language: AppLanguage = AppLanguage.KOREAN,
    ) {
        val notification = buildNotification(hidden, language = language)
        capture(notification.contentView!!, widthDp, fileName)
    }

    private fun captureExpanded(
        widthDp: Int,
        fileName: String,
        hidden: Set<String> = emptySet(),
        language: AppLanguage = AppLanguage.KOREAN,
    ) {
        val notification = buildNotification(hidden, language = language)
        capture(notification.bigContentView!!, widthDp, fileName)
    }

    private fun buildNotification(
        hidden: Set<String> = emptySet(),
        usages: List<VendorUsage> = fakeUsages(),
        language: AppLanguage = AppLanguage.KOREAN,
    ): Notification =
        TokenNotificationManager(
            Robolectric.buildActivity(Activity::class.java).setup().get(),
            hiddenAccountIds = { hidden },
            language = { language },
        ).buildNotification(usages)

    private fun applyToNewActivity(views: android.widget.RemoteViews): View {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        return views.apply(activity, FrameLayout(activity))
    }

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
