package org.parkjw.apps.oot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.TokenStatus
import org.parkjw.apps.oot.ui.MainActivity

class TokenNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(statuses: List<TokenStatus>): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val refreshIntent = Intent(context, TokenMonitorForegroundService::class.java).apply {
            action = TokenMonitorForegroundService.ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getService(
            context,
            1,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (statuses.isEmpty()) {
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_oot_token)
                .setContentTitle("OOT: 토큰 모니터")
                .setContentText("등록된 AI 계정이 없습니다. 앱에서 추가해주세요.")
                .setContentIntent(openAppPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(
                    android.R.drawable.ic_menu_rotate,
                    "새로고침",
                    refreshPendingIntent
                )
                .build()
        }

        val hasCritical = statuses.any { it.isCritical }
        val hasWarning = statuses.any { it.isWarning }

        val titlePrefix = when {
            hasCritical -> "🚨 [위험] OOT 토큰 알림"
            hasWarning -> "⚠️ [주의] OOT 토큰 알림"
            else -> "⚡ OOT: AI 토큰 현황"
        }

        // Compact short summary for status bar collapsed view
        val shortSummary = statuses.joinToString(" | ") { status ->
            "${status.provider.displayName.take(8)}: ${status.statusSummary}"
        }

        // Expanded BigTextStyle view with full details
        val bigTextBuilder = StringBuilder()
        statuses.forEachIndexed { index, status ->
            val icon = when {
                status.isError -> "❌"
                status.isCritical -> "🔴"
                status.isWarning -> "🟡"
                else -> "🟢"
            }
            bigTextBuilder.append("$icon ${status.accountName} (${status.provider.displayName}): ${status.remainingFormatted}")
            if (status.totalLimitFormatted != null) {
                bigTextBuilder.append(" [${status.totalLimitFormatted}]")
            }
            if (status.resetTimeFormatted != null) {
                bigTextBuilder.append(" (${status.resetTimeFormatted})")
            }
            if (status.isError && status.errorMessage != null) {
                bigTextBuilder.append(" - ${status.errorMessage}")
            }
            if (index < statuses.lastIndex) {
                bigTextBuilder.append("\n")
            }
        }

        val bigTextStyle = NotificationCompat.BigTextStyle()
            .bigText(bigTextBuilder.toString())
            .setBigContentTitle(titlePrefix)
            .setSummaryText("${statuses.size}개 AI 구독/API 모니터링 중")

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_oot_token)
            .setContentTitle(titlePrefix)
            .setContentText(shortSummary)
            .setStyle(bigTextStyle)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_rotate,
                "새로고침",
                refreshPendingIntent
            )
            .build()
    }

    fun updateNotification(statuses: List<TokenStatus>) {
        val notification = buildNotification(statuses)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "oot_channel_token_monitor"
        const val NOTIFICATION_ID = 1001
    }
}
