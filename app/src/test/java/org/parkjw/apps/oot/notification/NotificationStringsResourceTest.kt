package org.parkjw.apps.oot.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorUsage
import org.parkjw.apps.oot.i18n.AppLanguage
import org.parkjw.apps.oot.i18n.AppLocale
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationStringsResourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun koreanResourcesMatchFixture() {
        assertEquals(TestNotificationStrings.KO, AppLocale.wrap(context, AppLanguage.KOREAN).notificationStrings())
    }

    @Test
    fun englishResourcesMatchFixture() {
        assertEquals(TestNotificationStrings.EN, AppLocale.wrap(context, AppLanguage.ENGLISH).notificationStrings())
    }

    @Test
    fun englishNotificationLine() {
        val now = Instant.parse("2026-10-21T12:00:00Z")
        val usage = VendorUsage(
            accountId = "id",
            providerType = ProviderType.CLAUDE,
            displayName = "Claude",
            windows = listOf(UsageWindow("five_hour", "5h", Remaining.Percent(62.0), now.plusSeconds(100 * 60))),
        )
        val item = NotificationContent.expandedItems(listOf(usage), TestNotificationStrings.EN, now).single()
        assertEquals("5H 62% left · in 1h 40m", item.text)
        assertEquals(
            "Claude: check needed",
            NotificationContent.collapsedItems(
                listOf(usage.copy(windows = emptyList())),
                3,
                TestNotificationStrings.EN,
            ).single().label,
        )
    }
}
