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
        private const val KEY_SAVED_ROMS = "saved_roms_json"

        const val KEY_CONTROLLER_SCALE = "controller_scale"
        const val KEY_CONTROLLER_THEME = "controller_theme"
        const val KEY_SHOW_QUICK_SAVE_LOAD = "show_quick_save_load"
        const val KEY_SHOW_AB_COMBO = "show_ab_combo"
        const val KEY_PORTRAIT_OFFSETS = "portrait_layout_offsets"
        const val KEY_LANDSCAPE_OFFSETS = "landscape_layout_offsets"

        const val THEME_CLEAN_MYBOY = "CLEAN_MYBOY"
        const val THEME_RETRO_GBA = "RETRO_GBA"
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

    var savedRomsJson: String?
        get() = prefs.getString(KEY_SAVED_ROMS, null)
        set(value) = prefs.edit().putString(KEY_SAVED_ROMS, value).apply()

    var controllerScale: Float
        get() = prefs.getFloat(KEY_CONTROLLER_SCALE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_CONTROLLER_SCALE, value).apply()

    var controllerTheme: String
        get() = prefs.getString(KEY_CONTROLLER_THEME, THEME_CLEAN_MYBOY) ?: THEME_CLEAN_MYBOY
        set(value) = prefs.edit().putString(KEY_CONTROLLER_THEME, value).apply()

    var showQuickSaveLoad: Boolean
        get() = prefs.getBoolean(KEY_SHOW_QUICK_SAVE_LOAD, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_QUICK_SAVE_LOAD, value).apply()

    var showAbCombo: Boolean
        get() = prefs.getBoolean(KEY_SHOW_AB_COMBO, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_AB_COMBO, value).apply()

    fun getControlOffsets(isLandscape: Boolean): Map<String, Pair<Float, Float>> {
        val key = if (isLandscape) KEY_LANDSCAPE_OFFSETS else KEY_PORTRAIT_OFFSETS
        val raw = prefs.getString(key, null) ?: return emptyMap()
        if (raw.isBlank()) return emptyMap()

        val result = mutableMapOf<String, Pair<Float, Float>>()
        raw.split(";").forEach { item ->
            val parts = item.split(":")
            if (parts.size == 2) {
                val id = parts[0]
                val coords = parts[1].split(",")
                if (coords.size == 2) {
                    val dx = coords[0].toFloatOrNull()
                    val dy = coords[1].toFloatOrNull()
                    if (dx != null && dy != null) {
                        result[id] = Pair(dx, dy)
                    }
                }
            }
        }
        return result
    }

    fun setControlOffsets(isLandscape: Boolean, offsets: Map<String, Pair<Float, Float>>) {
        val key = if (isLandscape) KEY_LANDSCAPE_OFFSETS else KEY_PORTRAIT_OFFSETS
        val serialized = offsets.entries.joinToString(";") { "${it.key}:${it.value.first},${it.value.second}" }
        prefs.edit().putString(key, serialized).apply()
    }

    fun resetControlOffsets(isLandscape: Boolean) {
        val key = if (isLandscape) KEY_LANDSCAPE_OFFSETS else KEY_PORTRAIT_OFFSETS
        prefs.edit().remove(key).apply()
    }

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
