package com.retro.gba.data.prefs

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("retrogba_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USERNAME = "ra_username"
        private const val KEY_TOKEN = "ra_token"
        private const val KEY_WEB_API_KEY = "ra_web_api_key"
        private const val KEY_HARDCORE_MODE = "ra_hardcore_mode"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        private const val KEY_CONTROLLER_OPACITY = "controller_opacity"
        private const val KEY_FAST_FORWARD = "fast_forward_speed"
    }

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var webApiKey: String?
        get() = prefs.getString(KEY_WEB_API_KEY, null)
        set(value) = prefs.edit().putString(KEY_WEB_API_KEY, value).apply()

    var isHardcoreMode: Boolean
        get() = prefs.getBoolean(KEY_HARDCORE_MODE, true) // Por padrão ligado (hardcore)
        set(value) = prefs.edit().putBoolean(KEY_HARDCORE_MODE, value).apply()

    var hapticFeedbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, value).apply()

    var controllerOpacity: Float
        get() = prefs.getFloat(KEY_CONTROLLER_OPACITY, 0.75f)
        set(value) = prefs.edit().putFloat(KEY_CONTROLLER_OPACITY, value).apply()

    var fastForwardSpeed: Float
        get() = prefs.getFloat(KEY_FAST_FORWARD, 2.0f)
        set(value) = prefs.edit().putFloat(KEY_FAST_FORWARD, value).apply()

    fun isLoggedIn(): Boolean = !username.isNullOrBlank() && !token.isNullOrBlank()

    fun logout() {
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_TOKEN)
            .remove(KEY_WEB_API_KEY)
            .apply()
    }
}

