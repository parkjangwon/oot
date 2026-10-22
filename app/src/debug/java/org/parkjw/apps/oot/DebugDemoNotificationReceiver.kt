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
                    UsageWindow("five_hour", "5시간", Remaining.Percent(98.0), now.plusSeconds((3 * 60 + 27) * 60)),
                    UsageWindow("seven_day", "주간", Remaining.Percent(71.0), now.plusSeconds(4 * 24 * 3600))
                )
            ),
            VendorUsage(
                accountId = "demo-xai",
                providerType = ProviderType.XAI,
                displayName = "xai-demo@example.com",
                windows = listOf(
                    UsageWindow("weekly", "주간", Remaining.Percent(14.0), now.plusSeconds(2 * 24 * 3600))
                )
            ),
            VendorUsage(
                accountId = "demo-openrouter",
                providerType = ProviderType.OPENROUTER,
                displayName = "or-demo@example.com",
                windows = listOf(
                    UsageWindow("credit", "credit", Remaining.Balance(12.40)),
                    UsageWindow("key", "키 한도 $20.00", Remaining.Balance(7.60))
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
