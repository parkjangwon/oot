package org.parkjw.apps.oot.notification

/** The notification copy as in values/strings.xml (Korean) and values-en (English); NotificationStringsResourceTest keeps them in sync. */
object TestNotificationStrings {
    val KO = NotificationStrings(
        needsCheck = "확인 필요",
        vendorNeedsCheck = "%1\$s 확인 필요",
        left = "%1\$s 남음",
        window5h = "5H",
        window7d = "7D",
        emptyNoProviders = "등록된 프로바이더가 없어요. 앱에서 추가해 주세요.",
        emptyAllHidden = "알림에 표시할 프로바이더를 모두 껐어요. 설정에서 켜 주세요.",
        inDays = "%1\$d일 후",
        inDaysHours = "%1\$d일 %2\$d시간 후",
        inHours = "%1\$d시간 후",
        inHoursMinutes = "%1\$d시간 %2\$d분 후",
        inMinutes = "%1\$d분 후",
        previousValue = "(이전값)",
    )

    val EN = NotificationStrings(
        needsCheck = "Needs attention",
        vendorNeedsCheck = "%1\$s needs attention",
        left = "%1\$s left",
        window5h = "5H",
        window7d = "7D",
        emptyNoProviders = "No providers yet. Add one in the app.",
        emptyAllHidden = "All providers are hidden from the notification. Turn them on in Settings.",
        inDays = "in %1\$dd",
        inDaysHours = "in %1\$dd %2\$dh",
        inHours = "in %1\$dh",
        inHoursMinutes = "in %1\$dh %2\$dm",
        inMinutes = "in %1\$dm",
        previousValue = "(previous)",
    )
}
