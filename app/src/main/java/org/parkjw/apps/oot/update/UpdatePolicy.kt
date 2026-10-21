package org.parkjw.apps.oot.update

data class UpdateSnooze(val version: String, val epochDay: Long)

object UpdatePolicy {
    fun shouldAutoCheck(enabled: Boolean, lastAutoCheckDay: Long?, today: Long): Boolean =
        enabled && lastAutoCheckDay != today

    fun shouldPromptAfterAutoCheck(version: AppVersion, snooze: UpdateSnooze?, today: Long): Boolean =
        snooze == null || snooze.version != version.toString() || snooze.epochDay != today

    fun showsFailure(manual: Boolean): Boolean = manual
}
