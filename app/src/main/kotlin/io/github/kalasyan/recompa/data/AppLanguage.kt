package io.github.kalasyan.recompa.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The language of Recompa, chosen in its settings: English unless the user picks
 * another, whatever the language of the system. From Android 13 the choice is the
 * app's language in the system, so it can also be changed from the system settings.
 */
object AppLanguage {
    const val DEFAULT = "en"

    /** The languages Recompa and its catalog come in, each named in itself. */
    val supported: Map<String, String> = linkedMapOf(
        "en" to "English",
        "es" to "Español",
        "fr" to "Français",
        "pt" to "Português",
        "de" to "Deutsch",
    )

    fun current(context: Context): String {
        val chosen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.get(0)?.language
        } else {
            prefs(context).getString(KEY, null)
        }
        return chosen?.takeIf { it in supported } ?: DEFAULT
    }

    /** Switches to [language]; the activity is recreated in it. */
    fun set(activity: Activity, language: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android recreates the activities itself.
            activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language)
        } else {
            prefs(activity).edit().putString(KEY, language).apply()
            activity.recreate()
        }
    }

    /** [base] in the chosen language, for `attachBaseContext` and for texts shown outside the activity. */
    fun wrap(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(current(base))))
        return base.createConfigurationContext(config)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("language", Context.MODE_PRIVATE)

    private const val KEY = "language"
}
