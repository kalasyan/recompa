package io.github.kalasyan.recompa.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The user's choices: favourite ports, frontend mode and update notifications. */
class UserPrefs(private val context: Context) {
    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow(prefs.getStringSet(FAVORITES, emptySet()).orEmpty().toSet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _frontendMode = MutableStateFlow(prefs.getBoolean(FRONTEND, false))
    val frontendMode: StateFlow<Boolean> = _frontendMode.asStateFlow()

    private val _updateNotifications = MutableStateFlow(prefs.getBoolean(NOTIFY, true))
    val updateNotifications: StateFlow<Boolean> = _updateNotifications.asStateFlow()

    fun toggleFavorite(portId: String) {
        val updated = _favorites.value.let { if (portId in it) it - portId else it + portId }
        prefs.edit().putStringSet(FAVORITES, updated).apply()
        _favorites.value = updated
    }

    /**
     * Frontend mode runs Recompa full screen and lets Android offer it as the home app,
     * through an activity alias that stays disabled otherwise.
     */
    fun setFrontendMode(enabled: Boolean) {
        prefs.edit().putBoolean(FRONTEND, enabled).apply()
        _frontendMode.value = enabled
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, "${context.packageName}.HomeActivity"),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    fun setUpdateNotifications(enabled: Boolean) {
        prefs.edit().putBoolean(NOTIFY, enabled).apply()
        _updateNotifications.value = enabled
    }

    /** Whether Recompa already asked for the notification permission, so it asks only once. */
    var askedNotificationPermission: Boolean
        get() = prefs.getBoolean(ASKED_NOTIFY, false)
        set(value) = prefs.edit().putBoolean(ASKED_NOTIFY, value).apply()

    private companion object {
        const val FAVORITES = "favorites"
        const val FRONTEND = "frontend_mode"
        const val NOTIFY = "update_notifications"
        const val ASKED_NOTIFY = "asked_notification_permission"
    }
}
