package com.retro.gba.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.retro.gba.data.model.ControlElementState
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

        const val KEY_APP_THEME = "app_theme"
        const val KEY_CONTROLLER_SCALE = "controller_scale"
        const val KEY_CONTROLLER_THEME = "controller_theme"
        const val KEY_SHOW_QUICK_SAVE_LOAD = "show_quick_save_load"
        const val KEY_SHOW_AB_COMBO = "show_ab_combo"
        const val KEY_SHOW_TURBO_BUTTON = "show_turbo_button"
        const val KEY_SHOW_FPS_COUNTER = "show_fps_counter"
        const val KEY_STRETCH_TO_FILL = "stretch_to_fill"

        const val KEY_PORTRAIT_CONTROLS = "portrait_controls_v4"
        const val KEY_LANDSCAPE_CONTROLS = "landscape_controls_v4"

        // Temas da interface global do aplicativo
        const val APP_THEME_GBA_INDIGO = "GBA_INDIGO"
        const val APP_THEME_GLACIER = "GLACIER"
        const val APP_THEME_SP_PLATINUM = "SP_PLATINUM"
        const val APP_THEME_FAMICOM = "FAMICOM"
        const val APP_THEME_OLED_STEALTH = "OLED_STEALTH"

        // Temas visuais dos controles virtuais
        const val THEME_FOLLOW_APP = "FOLLOW_APP"
        const val THEME_GBA_INDIGO = "GBA_INDIGO"
        const val THEME_GLACIER = "GLACIER"
        const val THEME_SP_PLATINUM = "SP_PLATINUM"
        const val THEME_FAMICOM = "FAMICOM"
        const val THEME_OLED_STEALTH = "OLED_STEALTH"
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

    var appTheme: String
        get() = prefs.getString(KEY_APP_THEME, APP_THEME_GBA_INDIGO) ?: APP_THEME_GBA_INDIGO
        set(value) = prefs.edit().putString(KEY_APP_THEME, value).apply()

    var controllerScale: Float
        get() = prefs.getFloat(KEY_CONTROLLER_SCALE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_CONTROLLER_SCALE, value).apply()

    var controllerTheme: String
        get() = prefs.getString(KEY_CONTROLLER_THEME, THEME_FOLLOW_APP) ?: THEME_FOLLOW_APP
        set(value) = prefs.edit().putString(KEY_CONTROLLER_THEME, value).apply()

    var showQuickSaveLoad: Boolean
        get() = prefs.getBoolean(KEY_SHOW_QUICK_SAVE_LOAD, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_QUICK_SAVE_LOAD, value).apply()

    var showAbCombo: Boolean
        get() = prefs.getBoolean(KEY_SHOW_AB_COMBO, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_AB_COMBO, value).apply()

    var showTurboButton: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TURBO_BUTTON, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TURBO_BUTTON, value).apply()

    var showFpsCounter: Boolean
        get() = prefs.getBoolean(KEY_SHOW_FPS_COUNTER, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_FPS_COUNTER, value).apply()

    var stretchToFill: Boolean
        get() = prefs.getBoolean(KEY_STRETCH_TO_FILL, false)
        set(value) = prefs.edit().putBoolean(KEY_STRETCH_TO_FILL, value).apply()

    fun getControlStates(isLandscape: Boolean): Map<String, ControlElementState> {
        val key = if (isLandscape) KEY_LANDSCAPE_CONTROLS else KEY_PORTRAIT_CONTROLS
        val raw = prefs.getString(key, null) ?: return emptyMap()
        if (raw.isBlank()) return emptyMap()

        val result = mutableMapOf<String, ControlElementState>()
        raw.split(";").forEach { item ->
            val parts = item.split(":")
            if (parts.size == 2) {
                val id = parts[0]
                val values = parts[1].split(",")
                if (values.size >= 2) {
                    val dx = values[0].toFloatOrNull()?.coerceIn(-360f, 360f) ?: 0f
                    val dy = values[1].toFloatOrNull()?.coerceIn(-260f, 260f) ?: 0f
                    val scale = if (values.size >= 3) {
                        values[2].toFloatOrNull()?.coerceIn(0.50f, 1.80f) ?: 1.0f
                    } else 1.0f
                    result[id] = ControlElementState(dx = dx, dy = dy, scale = scale)
                }
            }
        }
        return result
    }

    fun setControlStates(isLandscape: Boolean, states: Map<String, ControlElementState>) {
        val key = if (isLandscape) KEY_LANDSCAPE_CONTROLS else KEY_PORTRAIT_CONTROLS
        val serialized = states.entries.joinToString(";") { (id, state) ->
            val cDx = state.dx.coerceIn(-360f, 360f)
            val cDy = state.dy.coerceIn(-260f, 260f)
            val cScale = state.scale.coerceIn(0.50f, 1.80f)
            "$id:$cDx,$cDy,$cScale"
        }
        prefs.edit().putString(key, serialized).apply()
    }

    fun resetControlStates(isLandscape: Boolean) {
        val key = if (isLandscape) KEY_LANDSCAPE_CONTROLS else KEY_PORTRAIT_CONTROLS
        prefs.edit().remove(key).apply()
    }

    fun resetSingleControl(id: String, isLandscape: Boolean) {
        val current = getControlStates(isLandscape).toMutableMap()
        current.remove(id)
        setControlStates(isLandscape, current)
    }

    fun getControlOffsets(isLandscape: Boolean): Map<String, Pair<Float, Float>> {
        return getControlStates(isLandscape).mapValues { Pair(it.value.dx, it.value.dy) }
    }

    fun setControlOffsets(isLandscape: Boolean, offsets: Map<String, Pair<Float, Float>>) {
        val current = getControlStates(isLandscape).toMutableMap()
        offsets.forEach { (id, pair) ->
            val existingScale = current[id]?.scale ?: 1.0f
            current[id] = ControlElementState(dx = pair.first, dy = pair.second, scale = existingScale)
        }
        setControlStates(isLandscape, current)
    }

    fun resetControlOffsets(isLandscape: Boolean) {
        resetControlStates(isLandscape)
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
