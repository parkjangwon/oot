package org.parkjw.apps.oot.i18n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes

/**
 * String lookup for code without a UI context (API clients, OAuth, the repository). Text comes from Android
 * resources in the language selected in Settings ([AppLocale.current]).
 *
 * Installed by the Application on start; tests running under Robolectric get it from OotApp as well.
 */
object AppText {
    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cache: Triple<AppLanguage, Configuration, Resources>? = null

    fun install(context: Context) {
        appContext = context.applicationContext ?: context
        cache = null
    }

    fun get(@StringRes id: Int): String = resources().getString(id)

    fun get(@StringRes id: Int, vararg args: Any): String = resources().getString(id, *args)

    fun resources(): Resources {
        val context = checkNotNull(appContext) { "AppText.install() was not called" }
        val language = AppLocale.current
        val base = context.resources.configuration
        cache?.let { (lang, config, res) -> if (lang == language && config == base) return res }
        val res = AppLocale.wrap(context, language).resources
        cache = Triple(language, Configuration(base), res)
        return res
    }
}
