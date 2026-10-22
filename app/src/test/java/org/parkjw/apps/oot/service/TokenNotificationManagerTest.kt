package org.parkjw.apps.oot.service

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorUsage
import org.parkjw.apps.oot.i18n.AppLanguage
import org.parkjw.apps.oot.i18n.AppLocale
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Instant

/** Channel creation/recreation and the posted notification for empty vs non-empty usages. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TokenNotificationManagerTest {

    private val app = ApplicationProvider.getApplicationContext<Context>()

    // The device locale under Robolectric is English, so read each expectation in the manager's language.
    private fun strings(language: AppLanguage): Context = AppLocale.wrap(app, language)

    private fun manager(
        hidden: Set<String> = emptySet(),
        language: AppLanguage = AppLanguage.KOREAN,
    ): TokenNotificationManager = TokenNotificationManager(
        app,
        hiddenAccountIds = { hidden },
        language = { language },
    )

    // Reset is rounded down to whole minutes when rendered; +30s keeps "N분" stable while the test runs.
    private fun resetIn(minutes: Long): Instant = Instant.now().plusSeconds(minutes * 60 + 30)

    // Same fixture values as NotificationScreenshotTest so the summary strings match its expectations.
    private fun fakeUsages(): List<VendorUsage> = listOf(
        VendorUsage(
            accountId = "demo-claude",
            providerType = ProviderType.CLAUDE,
            displayName = "claude-demo@example.com",
            windows = listOf(
                UsageWindow("five_hour", "5h", Remaining.Percent(62.0), resetIn(100)),
                UsageWindow("seven_day", "7d", Remaining.Percent(81.0), resetIn(3 * 24 * 60)),
            ),
        ),
        VendorUsage(
            accountId = "demo-glm",
            providerType = ProviderType.ZAI,
            displayName = "glm-demo@example.com",
            windows = listOf(UsageWindow("five_hour", "5h", Remaining.Percent(98.0), resetIn(3 * 60 + 27))),
        ),
        VendorUsage(
            accountId = "demo-xai",
            providerType = ProviderType.XAI,
            displayName = "xai-demo@example.com",
            windows = listOf(UsageWindow("weekly", "7d", Remaining.Percent(14.0), resetIn(2 * 24 * 60 + 3 * 60))),
        ),
        VendorUsage(
            accountId = "demo-openrouter",
            providerType = ProviderType.OPENROUTER,
            displayName = "or-demo@example.com",
            windows = listOf(
                UsageWindow("credit", "credit", Remaining.Balance(12.40)),
                UsageWindow("key", "$20.00", Remaining.Balance(7.60)),
            ),
        ),
    )

    @Test
    fun constructionCreatesALowImportanceChannelWithoutBadge() {
        manager()

        val channel = notificationManager(app).getNotificationChannel(TokenNotificationManager.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(android.app.NotificationManager.IMPORTANCE_LOW, channel!!.importance)
        assertFalse(channel.canShowBadge())
        assertEquals(strings(AppLanguage.KOREAN).getString(R.string.notification_channel_name), channel.name.toString())
        assertEquals(
            strings(AppLanguage.KOREAN).getString(R.string.notification_channel_desc),
            channel.description.toString(),
        )
    }

    @Test
    fun recreatedChannelFollowsTheSelectedLanguage() {
        manager(language = AppLanguage.ENGLISH).createNotificationChannel()

        val channel = notificationManager(app).getNotificationChannel(TokenNotificationManager.CHANNEL_ID)
        assertEquals(
            strings(AppLanguage.ENGLISH).getString(R.string.notification_channel_name),
            channel!!.name.toString(),
        )
    }

    @Test
    fun emptyUsagesPostTheEmptyStateWithoutCustomViews() {
        manager().updateNotification(emptyList())

        val notification = Shadows.shadowOf(notificationManager(app))
            .getNotification(TokenNotificationManager.NOTIFICATION_ID)
        assertNotNull(notification)
        assertEquals(
            "등록된 프로바이더가 없어요. 앱에서 추가해 주세요.",
            notification!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertEquals("oot · 토큰 모니터", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertNull(notification.contentView)
        assertEquals("새로고침", notification.actions.single().title.toString())
    }

    @Test
    fun nonEmptyUsagesPostTheSummaryWithCustomViews() {
        manager().updateNotification(fakeUsages())

        val notification = Shadows.shadowOf(notificationManager(app))
            .getNotification(TokenNotificationManager.NOTIFICATION_ID)
        assertNotNull(notification)
        assertEquals(
            "Claude 62% · GLM 98% · xAI 14% · OpenRouter $7.60",
            notification!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertNotNull(notification.contentView)
        assertNotNull(notification.bigContentView)
    }

    @Test
    fun updateReplacesThePreviousNotificationUnderTheSameId() {
        manager().updateNotification(emptyList())
        manager().updateNotification(fakeUsages())

        val shadow = Shadows.shadowOf(notificationManager(app))
        assertEquals(1, shadow.allNotifications.size)
        assertEquals(
            "Claude 62% · GLM 98% · xAI 14% · OpenRouter $7.60",
            shadow.getNotification(TokenNotificationManager.NOTIFICATION_ID)!!
                .extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
    }

    @Test
    fun hiddenAccountsShowTheAllHiddenMessageButKeepTheActions() {
        val hiddenIds = fakeUsages().map { it.accountId }.toSet()
        manager(hidden = hiddenIds).updateNotification(fakeUsages())

        val notification = Shadows.shadowOf(notificationManager(app))
            .getNotification(TokenNotificationManager.NOTIFICATION_ID)
        assertNotNull(notification)
        assertEquals(
            "알림에 표시할 프로바이더를 모두 껐어요. 설정에서 켜 주세요.",
            notification!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertTrue(notification.contentIntent != null)
        assertEquals("새로고침", notification.actions.single().title.toString())
    }

    @Test
    fun englishUpdateUsesEnglishStrings() {
        manager(language = AppLanguage.ENGLISH).updateNotification(emptyList())

        val notification = Shadows.shadowOf(notificationManager(app))
            .getNotification(TokenNotificationManager.NOTIFICATION_ID)
        assertEquals(
            "No providers yet. Add one in the app.",
            notification!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertEquals("Refresh", notification.actions.single().title.toString())
    }
}
