package com.retro.gba.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.retro.gba.security.CryptoManager

/**
 * Gerenciador de preferências do usuário com criptografia AES-256-GCM
 * para credenciais sensíveis (token do RetroAchievements e chaves de API).
 */
class UserPreferences(
    private val prefs: SharedPreferences,
    private val cryptoManager: CryptoManager = CryptoManager()
) {

    constructor(context: Context) : this(
        context.getSharedPreferences("retrogba_prefs", Context.MODE_PRIVATE),
        CryptoManager()
    )

    companion object {
        private const val KEY_USERNAME = "ra_username"
        private const val KEY_TOKEN = "ra_token_enc"
        private const val KEY_LEGACY_TOKEN = "ra_token"
        private const val KEY_WEB_API_KEY = "ra_web_api_key_enc"
        private const val KEY_LEGACY_WEB_API_KEY = "ra_web_api_key"
        private const val KEY_HARDCORE_MODE = "ra_hardcore_mode"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        private const val KEY_CONTROLLER_OPACITY = "controller_opacity"
        private const val KEY_FAST_FORWARD = "fast_forward_speed"
    }

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var token: String?
        get() {
            val encrypted = prefs.getString(KEY_TOKEN, null)
            if (!encrypted.isNullOrBlank()) {
                val decrypted = cryptoManager.decrypt(encrypted)
                if (decrypted.isNotBlank()) return decrypted
            }
            // Migração transparente de tokens legados em texto puro
            val legacy = prefs.getString(KEY_LEGACY_TOKEN, null)
            if (!legacy.isNullOrBlank()) {
                token = legacy // Salva de forma criptografada
                prefs.edit().remove(KEY_LEGACY_TOKEN).apply()
                return legacy
            }
            return null
        }
        set(value) {
            if (value.isNullOrBlank()) {
                prefs.edit().remove(KEY_TOKEN).remove(KEY_LEGACY_TOKEN).apply()
            } else {
                val encrypted = cryptoManager.encrypt(value)
                prefs.edit()
                    .putString(KEY_TOKEN, encrypted)
                    .remove(KEY_LEGACY_TOKEN)
                    .apply()
            }
        }

    var webApiKey: String?
        get() {
            val encrypted = prefs.getString(KEY_WEB_API_KEY, null)
            if (!encrypted.isNullOrBlank()) {
                val decrypted = cryptoManager.decrypt(encrypted)
                if (decrypted.isNotBlank()) return decrypted
            }
            val legacy = prefs.getString(KEY_LEGACY_WEB_API_KEY, null)
            if (!legacy.isNullOrBlank()) {
                webApiKey = legacy
                prefs.edit().remove(KEY_LEGACY_WEB_API_KEY).apply()
                return legacy
            }
            return null
        }
        set(value) {
            if (value.isNullOrBlank()) {
                prefs.edit().remove(KEY_WEB_API_KEY).remove(KEY_LEGACY_WEB_API_KEY).apply()
            } else {
                val encrypted = cryptoManager.encrypt(value)
                prefs.edit()
                    .putString(KEY_WEB_API_KEY, encrypted)
                    .remove(KEY_LEGACY_WEB_API_KEY)
                    .apply()
            }
        }

    var isHardcoreMode: Boolean
        get() = prefs.getBoolean(KEY_HARDCORE_MODE, true)
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
            .remove(KEY_LEGACY_TOKEN)
            .remove(KEY_WEB_API_KEY)
            .remove(KEY_LEGACY_WEB_API_KEY)
            .apply()
    }
}
