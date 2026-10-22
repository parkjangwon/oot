package org.parkjw.apps.oot.notification

import android.content.Context
import org.parkjw.apps.oot.R

/** Reads [NotificationStrings] from this context's resources (pass a context already in the app language). */
fun Context.notificationStrings(): NotificationStrings = NotificationStrings(
    needsCheck = getString(R.string.status_needs_check),
    vendorNeedsCheck = getString(R.string.notification_vendor_needs_check),
    left = getString(R.string.remaining_left),
    window5h = getString(R.string.notification_window_5h),
    window7d = getString(R.string.notification_window_7d),
    emptyNoProviders = getString(R.string.notification_empty_no_providers),
    emptyAllHidden = getString(R.string.notification_empty_all_hidden),
    inDays = getString(R.string.time_in_days),
    inDaysHours = getString(R.string.time_in_days_hours),
    inHours = getString(R.string.time_in_hours),
    inHoursMinutes = getString(R.string.time_in_hours_minutes),
    inMinutes = getString(R.string.time_in_minutes),
)
