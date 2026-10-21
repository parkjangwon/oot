package org.parkjw.apps.oot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import org.parkjw.apps.oot.R
import org.parkjw.apps.oot.data.model.VendorUsage
import org.parkjw.apps.oot.notification.NotificationContent
import org.parkjw.apps.oot.ui.MainActivity

class TokenNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val collapsedItemIds = intArrayOf(R.id.item0, R.id.item1, R.id.item2)
    private val collapsedMarkIds = intArrayOf(R.id.mark0, R.id.mark1, R.id.mark2)
    private val collapsedBarIds = intArrayOf(R.id.bar0, R.id.bar1, R.id.bar2)
    private val collapsedLabelIds = intArrayOf(R.id.label0, R.id.label1, R.id.label2)

    private val expandedRowIds = intArrayOf(R.id.row0, R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5, R.id.row6, R.id.row7)
    private val expandedMarkIds = intArrayOf(R.id.mark0, R.id.mark1, R.id.mark2, R.id.mark3, R.id.mark4, R.id.mark5, R.id.mark6, R.id.mark7)
    private val expandedNameIds = intArrayOf(R.id.name0, R.id.name1, R.id.name2, R.id.name3, R.id.name4, R.id.name5, R.id.name6, R.id.name7)
    private val expandedTextIds = intArrayOf(R.id.text0, R.id.text1, R.id.text2, R.id.text3, R.id.text4, R.id.text5, R.id.text6, R.id.text7)
    private val expandedBarIds = intArrayOf(R.id.bar0, R.id.bar1, R.id.bar2, R.id.bar3, R.id.bar4, R.id.bar5, R.id.bar6, R.id.bar7)

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

    fun buildNotification(usages: List<VendorUsage>): Notification {
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

        if (usages.isEmpty()) {
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

        // Text-only fallback (option C) is the standard contentText/ticker,
        // so the notification stays readable wherever custom views break.
        val summary = NotificationContent.textFallback(usages)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_oot_token)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(summary)
            .setTicker(summary)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_rotate,
                "새로고침",
                refreshPendingIntent
            )

        if (NotificationContent.FORCE_TEXT_ONLY) {
            val bigText = NotificationContent.expandedItems(usages)
                .joinToString("\n") { "${it.name} ${it.text}" }
            return builder
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .build()
        }

        builder
            .setCustomContentView(collapsedViews(usages))
            .setCustomBigContentView(expandedViews(usages))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())

        return builder.build()
    }

    private fun collapsedViews(usages: List<VendorUsage>): RemoteViews {
        val maxItems = context.resources.getInteger(R.integer.notification_collapsed_items)
        val items = NotificationContent.collapsedItems(usages, maxItems)
        val views = RemoteViews(context.packageName, R.layout.notification_collapsed)
        repeat(collapsedItemIds.size) { i ->
            if (i >= items.size) {
                views.setViewVisibility(collapsedItemIds[i], View.GONE)
                return@repeat
            }
            val item = items[i]
            views.setViewVisibility(collapsedItemIds[i], View.VISIBLE)
            views.setTextViewText(collapsedMarkIds[i], item.mark)
            views.setTextViewText(collapsedLabelIds[i], item.label)
            if (item.percent == null) {
                views.setViewVisibility(collapsedBarIds[i], View.GONE)
                views.setTextColor(
                    collapsedLabelIds[i],
                    context.getColor(if (item.isError) R.color.notif_text_danger else R.color.notif_text_primary)
                )
            } else {
                views.setViewVisibility(collapsedBarIds[i], View.VISIBLE)
                views.setTextColor(collapsedLabelIds[i], context.getColor(R.color.notif_text_primary))
                views.setInt(collapsedBarIds[i], "setImageLevel", item.percent * 100)
            }
        }
        return views
    }

    // shortcut: fixed 8 rows; vendors beyond 8 are truncated until RemoteViews supports dynamic rows
    private fun expandedViews(usages: List<VendorUsage>): RemoteViews {
        val items = NotificationContent.expandedItems(usages).take(expandedRowIds.size)
        val views = RemoteViews(context.packageName, R.layout.notification_expanded)
        repeat(expandedRowIds.size) { i ->
            if (i >= items.size) {
                views.setViewVisibility(expandedRowIds[i], View.GONE)
                return@repeat
            }
            val item = items[i]
            views.setViewVisibility(expandedRowIds[i], View.VISIBLE)
            views.setTextViewText(expandedMarkIds[i], item.mark)
            views.setTextViewText(expandedNameIds[i], item.name)
            views.setTextViewText(expandedTextIds[i], item.text)
            if (item.isError) {
                views.setViewVisibility(expandedBarIds[i], View.GONE)
                views.setTextColor(expandedTextIds[i], context.getColor(R.color.notif_text_danger))
            } else if (item.percent == null) {
                views.setViewVisibility(expandedBarIds[i], View.GONE)
                views.setTextColor(expandedTextIds[i], context.getColor(R.color.notif_text_secondary))
            } else {
                views.setViewVisibility(expandedBarIds[i], View.VISIBLE)
                views.setTextColor(expandedTextIds[i], context.getColor(R.color.notif_text_secondary))
                views.setInt(expandedBarIds[i], "setImageLevel", item.percent * 100)
            }
        }
        return views
    }

    fun updateNotification(usages: List<VendorUsage>) {
        val notification = buildNotification(usages)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "oot_channel_token_monitor"
        const val NOTIFICATION_ID = 1001
    }
}
