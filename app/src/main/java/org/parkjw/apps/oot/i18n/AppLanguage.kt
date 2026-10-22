package org.parkjw.apps.oot.i18n

import java.util.Locale

/** App UI language. Korean is the default; English is opt-in from Settings. */
enum class AppLanguage(val tag: String) {
    KOREAN("ko"),
    ENGLISH("en");

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        val DEFAULT = KOREAN

        /** Maps a BCP 47 tag ("en", "en-US", "ko_KR") to a supported language, or null. */
        fun fromTag(tag: String?): AppLanguage? {
            val language = tag?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase()
            return entries.firstOrNull { it.tag == language }
        }
    }
}
