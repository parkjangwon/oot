package org.parkjw.apps.oot.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList

/**
 * Applies the selected [AppLanguage] to the app.
 *
 * Activities wrap their base context with [overrideConfiguration]; background work (notifications, the
 * refresh service, data-layer messages) reads strings through [wrap] or [AppText], so it follows the
 * selection on every API level. On Android 13+ the choice is also handed to [LocaleManager], which keeps the
 * system's per-app language setting (locales_config.xml) in sync.
 */
object AppLocale {
    @Volatile
    var current: AppLanguage = AppLanguage.DEFAULT
        private set

    /** Sets [language] for this process and mirrors it to the system per-app language on Android 13+. */
    fun apply(context: Context, language: AppLanguage) {
        current = language
        AppText.install(context.applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            val wanted = LocaleList.forLanguageTags(language.tag)
            if (manager.applicationLocales.toLanguageTags() != wanted.toLanguageTags()) {
                manager.applicationLocales = wanted
            }
        }
    }

    /**
     * The language picked in the system's per-app language settings (Android 13+), or null when none is set
     * or the device is older. Used on start so a choice made there wins over the stored one.
     */
    fun systemSelection(context: Context): AppLanguage? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return null
        if (locales.isEmpty) return null
        return AppLanguage.fromTag(locales[0].toLanguageTag())
    }

    /** A configuration delta that only sets the locale, for Activity.applyOverrideConfiguration. */
    fun overrideConfiguration(language: AppLanguage = current): Configuration =
        Configuration().apply { setLocales(LocaleList(language.locale)) }

    /** [context] with its resources in [language]; everything else in the configuration is kept. */
    fun wrap(context: Context, language: AppLanguage = current): Context {
        val config = Configuration(context.resources.configuration)
        if (config.locales.size() > 0 && config.locales[0] == language.locale) return context
        config.setLocales(LocaleList(language.locale))
        return context.createConfigurationContext(config)
    }
}
