package org.parkjw.apps.oot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.parkjw.apps.oot.data.model.ProviderType
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.UsageWindow
import org.parkjw.apps.oot.data.model.VendorError
import org.parkjw.apps.oot.data.model.VendorUsage
import org.parkjw.apps.oot.service.TokenNotificationManager
import java.time.Instant

class DebugDemoNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        val now = Instant.now()
        val fakeUsages = listOf(
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

        // TokenNotificationManager's init ensures the channel exists.
        TokenNotificationManager(context).updateNotification(fakeUsages)
    }

    companion object {
        const val ACTION = "org.parkjw.apps.oot.DEBUG_DEMO_NOTIFICATION"
    }
}
