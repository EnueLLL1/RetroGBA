package com.retro.gba.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.retro.gba.data.model.PendingAchievementUnlock
import com.retro.gba.security.CryptoManager
import com.retro.gba.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.WeakHashMap

/**
 * Gerenciador de preferências do usuário com criptografia AES-256-GCM
 * para credenciais sensíveis (token do RetroAchievements e chaves de API)
 * e estado reativo compartilhado entre instâncias ligadas ao mesmo SharedPreferences.
 */
class UserPreferences(
    private val prefs: SharedPreferences,
    private val cryptoManager: CryptoManager = CryptoManager()
) {

    constructor(context: Context) : this(
        context.getSharedPreferences("retrogba_prefs", Context.MODE_PRIVATE),
        CryptoManager()
    )

    private class SharedPrefsState(prefs: SharedPreferences) {
        val queueLock = Any()
        val isHardcoreMode = MutableStateFlow(prefs.getBoolean(KEY_HARDCORE_MODE, true))
        val controllerOpacity = MutableStateFlow(prefs.getFloat(KEY_CONTROLLER_OPACITY, 0.75f))
        val hapticFeedbackEnabled = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true))
        val fastForwardSpeed = MutableStateFlow(prefs.getFloat(KEY_FAST_FORWARD, 2.0f))
        val hideControls = MutableStateFlow(prefs.getBoolean(KEY_HIDE_CONTROLS, false))
        val activeLayoutProfileId = MutableStateFlow(readNormalizedProfileId(prefs))
        val layoutProfilesJson = MutableStateFlow(prefs.getString(KEY_LAYOUT_PROFILES_JSON, null))

        // Mantém referência forte ao listener sem capturar o próprio SharedPreferences.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (sp == null) return@OnSharedPreferenceChangeListener
            when (key) {
                null -> syncAll(sp)
                KEY_HARDCORE_MODE -> isHardcoreMode.value = sp.getBoolean(KEY_HARDCORE_MODE, true)
                KEY_CONTROLLER_OPACITY -> controllerOpacity.value = sp.getFloat(KEY_CONTROLLER_OPACITY, 0.75f)
                KEY_HAPTIC_FEEDBACK -> hapticFeedbackEnabled.value = sp.getBoolean(KEY_HAPTIC_FEEDBACK, true)
                KEY_FAST_FORWARD -> fastForwardSpeed.value = sp.getFloat(KEY_FAST_FORWARD, 2.0f)
                KEY_HIDE_CONTROLS -> hideControls.value = sp.getBoolean(KEY_HIDE_CONTROLS, false)
                KEY_ACTIVE_LAYOUT_PROFILE_ID -> activeLayoutProfileId.value = readNormalizedProfileId(sp)
                KEY_LAYOUT_PROFILES_JSON -> layoutProfilesJson.value = sp.getString(KEY_LAYOUT_PROFILES_JSON, null)
            }
        }

        private fun syncAll(sp: SharedPreferences) {
            isHardcoreMode.value = sp.getBoolean(KEY_HARDCORE_MODE, true)
            controllerOpacity.value = sp.getFloat(KEY_CONTROLLER_OPACITY, 0.75f)
            hapticFeedbackEnabled.value = sp.getBoolean(KEY_HAPTIC_FEEDBACK, true)
            fastForwardSpeed.value = sp.getFloat(KEY_FAST_FORWARD, 2.0f)
            hideControls.value = sp.getBoolean(KEY_HIDE_CONTROLS, false)
            activeLayoutProfileId.value = readNormalizedProfileId(sp)
            layoutProfilesJson.value = sp.getString(KEY_LAYOUT_PROFILES_JSON, null)
        }
    }

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
        private const val KEY_HIDE_CONTROLS = "hide_controls"
        private const val KEY_SAVED_ROMS = "saved_roms_json"
        private const val KEY_ACTIVE_LAYOUT_PROFILE_ID = "active_layout_profile_id"
        private const val KEY_LAYOUT_PROFILES_JSON = "layout_profiles_json"
        private const val KEY_CACHED_SCORE = "ra_cached_score"
        private const val KEY_CACHED_SOFTCORE_SCORE = "ra_cached_softcore_score"
        private const val KEY_CACHED_AVATAR_URL = "ra_cached_avatar_url"
        private const val KEY_PENDING_UNLOCKS = "ra_pending_unlocks_json"
        private const val DEFAULT_LAYOUT_PROFILE_ID = "default"

        private val stateHoldersLock = Any()
        private val stateHolders = WeakHashMap<SharedPreferences, SharedPrefsState>()
        private val gson = Gson()
        private val pendingUnlocksType = object : TypeToken<List<PendingAchievementUnlock>>() {}.type

        private fun readNormalizedProfileId(prefs: SharedPreferences): String {
            return prefs.getString(KEY_ACTIVE_LAYOUT_PROFILE_ID, DEFAULT_LAYOUT_PROFILE_ID)
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_LAYOUT_PROFILE_ID
        }

        private fun getOrCreateState(prefs: SharedPreferences): SharedPrefsState {
            synchronized(stateHoldersLock) {
                val existing = stateHolders[prefs]
                if (existing != null) return existing
                val created = SharedPrefsState(prefs)
                prefs.registerOnSharedPreferenceChangeListener(created.listener)
                stateHolders[prefs] = created
                return created
            }
        }
    }

    private val sharedState: SharedPrefsState = getOrCreateState(prefs)

    val isHardcoreModeFlow: StateFlow<Boolean> = sharedState.isHardcoreMode.asStateFlow()
    val controllerOpacityFlow: StateFlow<Float> = sharedState.controllerOpacity.asStateFlow()
    val hapticFeedbackEnabledFlow: StateFlow<Boolean> = sharedState.hapticFeedbackEnabled.asStateFlow()
    val fastForwardSpeedFlow: StateFlow<Float> = sharedState.fastForwardSpeed.asStateFlow()
    val hideControlsFlow: StateFlow<Boolean> = sharedState.hideControls.asStateFlow()
    val activeLayoutProfileIdFlow: StateFlow<String> = sharedState.activeLayoutProfileId.asStateFlow()
    val layoutProfilesJsonFlow: StateFlow<String?> = sharedState.layoutProfilesJson.asStateFlow()

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
        set(value) {
            prefs.edit().putBoolean(KEY_HARDCORE_MODE, value).apply()
            sharedState.isHardcoreMode.value = value
        }

    var hapticFeedbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
        set(value) {
            prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, value).apply()
            sharedState.hapticFeedbackEnabled.value = value
        }

    var controllerOpacity: Float
        get() = prefs.getFloat(KEY_CONTROLLER_OPACITY, 0.75f)
        set(value) {
            prefs.edit().putFloat(KEY_CONTROLLER_OPACITY, value).apply()
            sharedState.controllerOpacity.value = value
        }

    var fastForwardSpeed: Float
        get() = prefs.getFloat(KEY_FAST_FORWARD, 2.0f)
        set(value) {
            prefs.edit().putFloat(KEY_FAST_FORWARD, value).apply()
            sharedState.fastForwardSpeed.value = value
        }

    var hideControls: Boolean
        get() = prefs.getBoolean(KEY_HIDE_CONTROLS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_HIDE_CONTROLS, value).apply()
            sharedState.hideControls.value = value
        }

    var savedRomsJson: String?
        get() = prefs.getString(KEY_SAVED_ROMS, null)
        set(value) = prefs.edit().putString(KEY_SAVED_ROMS, value).apply()

    var activeLayoutProfileId: String
        get() = readNormalizedProfileId(prefs)
        set(value) {
            val normalized = value.ifBlank { DEFAULT_LAYOUT_PROFILE_ID }
            prefs.edit().putString(KEY_ACTIVE_LAYOUT_PROFILE_ID, normalized).apply()
            sharedState.activeLayoutProfileId.value = normalized
        }

    var layoutProfilesJson: String?
        get() = prefs.getString(KEY_LAYOUT_PROFILES_JSON, null)
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_LAYOUT_PROFILES_JSON).apply()
            } else {
                prefs.edit().putString(KEY_LAYOUT_PROFILES_JSON, value).apply()
            }
            sharedState.layoutProfilesJson.value = value
        }

    var cachedScore: Int
        get() = prefs.getInt(KEY_CACHED_SCORE, 0)
        set(value) = prefs.edit().putInt(KEY_CACHED_SCORE, value.coerceAtLeast(0)).apply()

    var cachedSoftcoreScore: Int
        get() = prefs.getInt(KEY_CACHED_SOFTCORE_SCORE, 0)
        set(value) = prefs.edit().putInt(KEY_CACHED_SOFTCORE_SCORE, value.coerceAtLeast(0)).apply()

    var cachedAvatarUrl: String
        get() = prefs.getString(KEY_CACHED_AVATAR_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CACHED_AVATAR_URL, value).apply()

    fun getPendingUnlocks(): List<PendingAchievementUnlock> {
        synchronized(sharedState.queueLock) {
            return readPendingUnlocksLocked()
        }
    }

    fun enqueuePendingUnlock(unlock: PendingAchievementUnlock) {
        synchronized(sharedState.queueLock) {
            val current = readPendingUnlocksLocked()
            val exists = current.any {
                it.achievementId == unlock.achievementId && it.hardcore == unlock.hardcore
            }
            if (!exists) {
                writePendingUnlocksLocked(current + unlock)
            }
        }
    }

    fun removePendingUnlock(achievementId: Int, hardcore: Boolean) {
        synchronized(sharedState.queueLock) {
            val current = readPendingUnlocksLocked()
            val filtered = current.filterNot {
                it.achievementId == achievementId && it.hardcore == hardcore
            }
            if (filtered.size != current.size) {
                writePendingUnlocksLocked(filtered)
            }
        }
    }

    private fun readPendingUnlocksLocked(): List<PendingAchievementUnlock> {
        val raw = prefs.getString(KEY_PENDING_UNLOCKS, null)
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<PendingAchievementUnlock>>(raw, pendingUnlocksType) ?: emptyList()
        } catch (e: JsonParseException) {
            AppLogger.log("UserPreferences", "Fila offline de conquistas inválida; resetando", "WARN", e)
            prefs.edit().remove(KEY_PENDING_UNLOCKS).apply()
            emptyList()
        }
    }

    private fun writePendingUnlocksLocked(items: List<PendingAchievementUnlock>) {
        if (items.isEmpty()) {
            prefs.edit().remove(KEY_PENDING_UNLOCKS).apply()
        } else {
            prefs.edit().putString(KEY_PENDING_UNLOCKS, gson.toJson(items, pendingUnlocksType)).apply()
        }
    }

    fun isLoggedIn(): Boolean = !username.isNullOrBlank() && !token.isNullOrBlank()

    fun logout() {
        synchronized(sharedState.queueLock) {
            prefs.edit()
                .remove(KEY_USERNAME)
                .remove(KEY_TOKEN)
                .remove(KEY_LEGACY_TOKEN)
                .remove(KEY_WEB_API_KEY)
                .remove(KEY_LEGACY_WEB_API_KEY)
                .remove(KEY_CACHED_SCORE)
                .remove(KEY_CACHED_SOFTCORE_SCORE)
                .remove(KEY_CACHED_AVATAR_URL)
                .remove(KEY_PENDING_UNLOCKS)
                .apply()
        }
    }
}
