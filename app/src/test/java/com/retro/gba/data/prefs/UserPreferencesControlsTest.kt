package com.retro.gba.data.prefs

import android.content.SharedPreferences
import com.retro.gba.data.model.ControlElementState
import com.retro.gba.security.CryptoManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class UserPreferencesControlsTest {

    private lateinit var preferences: UserPreferences
    private val memoryStore = ConcurrentHashMap<String, Any>()

    @Before
    fun setUp() {
        val fakePrefs = object : SharedPreferences {
            override fun getAll(): MutableMap<String, *> = memoryStore
            override fun getString(key: String?, defValue: String?): String? =
                (memoryStore[key] as? String) ?: defValue
            override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
            override fun getInt(key: String?, defValue: Int): Int =
                (memoryStore[key] as? Int) ?: defValue
            override fun getLong(key: String?, defValue: Long): Long =
                (memoryStore[key] as? Long) ?: defValue
            override fun getFloat(key: String?, defValue: Float): Float =
                (memoryStore[key] as? Float) ?: defValue
            override fun getBoolean(key: String?, defValue: Boolean): Boolean =
                (memoryStore[key] as? Boolean) ?: defValue
            override fun contains(key: String?): Boolean = memoryStore.containsKey(key)
            override fun edit(): SharedPreferences.Editor = FakeEditor(memoryStore)
            override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
            override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        }

        preferences = UserPreferences(fakePrefs, CryptoManager("TestKey"))
    }

    private class FakeEditor(private val store: ConcurrentHashMap<String, Any>) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        override fun putString(key: String?, value: String?) = apply { key?.let { pending[it] = value } }
        override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { key?.let { pending[it] = values } }
        override fun putInt(key: String?, value: Int) = apply { key?.let { pending[it] = value } }
        override fun putLong(key: String?, value: Long) = apply { key?.let { pending[it] = value } }
        override fun putFloat(key: String?, value: Float) = apply { key?.let { pending[it] = value } }
        override fun putBoolean(key: String?, value: Boolean) = apply { key?.let { pending[it] = value } }
        override fun remove(key: String?) = apply { key?.let { pending[it] = null } }
        override fun clear() = apply { store.clear() }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            pending.forEach { (k, v) ->
                if (v == null) store.remove(k) else store[k] = v
            }
        }
    }

    @Test
    fun controllerOpacity_defaultsTo75Percent_andUpdatesProperly() {
        assertEquals(0.75f, preferences.controllerOpacity, 0.01f)

        preferences.controllerOpacity = 0.50f
        assertEquals(0.50f, preferences.controllerOpacity, 0.01f)

        preferences.controllerOpacity = 1.0f
        assertEquals(1.0f, preferences.controllerOpacity, 0.01f)
    }

    @Test
    fun hapticFeedback_defaultsToTrue_andCanBeToggled() {
        assertTrue(preferences.hapticFeedbackEnabled)

        preferences.hapticFeedbackEnabled = false
        assertFalse(preferences.hapticFeedbackEnabled)

        preferences.hapticFeedbackEnabled = true
        assertTrue(preferences.hapticFeedbackEnabled)
    }

    @Test
    fun fastForwardSpeed_defaultsTo2x_andUpdatesProperly() {
        assertEquals(2.0f, preferences.fastForwardSpeed, 0.01f)

        preferences.fastForwardSpeed = 3.5f
        assertEquals(3.5f, preferences.fastForwardSpeed, 0.01f)
    }

    @Test
    fun encryptedCredentials_persistAndRecoverCorrectly() {
        preferences.username = "retro_gamer"
        preferences.token = "secure_token_123"

        assertTrue(preferences.isLoggedIn())
        assertEquals("retro_gamer", preferences.username)
        assertEquals("secure_token_123", preferences.token)

        preferences.logout()
        assertFalse(preferences.isLoggedIn())
        assertNull(preferences.username)
        assertNull(preferences.token)
    }

    @Test
    fun controllerTheme_defaultsToFollowApp_andUpdatesProperly() {
        assertEquals(UserPreferences.THEME_FOLLOW_APP, preferences.controllerTheme)

        preferences.controllerTheme = UserPreferences.THEME_GBA_INDIGO
        assertEquals(UserPreferences.THEME_GBA_INDIGO, preferences.controllerTheme)

        preferences.controllerTheme = UserPreferences.THEME_GLACIER
        assertEquals(UserPreferences.THEME_GLACIER, preferences.controllerTheme)

        preferences.controllerTheme = UserPreferences.THEME_CLEAN_MYBOY
        assertEquals(UserPreferences.THEME_CLEAN_MYBOY, preferences.controllerTheme)
    }

    @Test
    fun appTheme_defaultsToGbaIndigo_andUpdatesProperly() {
        assertEquals(UserPreferences.APP_THEME_GBA_INDIGO, preferences.appTheme)

        preferences.appTheme = UserPreferences.APP_THEME_GLACIER
        assertEquals(UserPreferences.APP_THEME_GLACIER, preferences.appTheme)

        preferences.appTheme = UserPreferences.APP_THEME_OLED_STEALTH
        assertEquals(UserPreferences.APP_THEME_OLED_STEALTH, preferences.appTheme)
    }

    @Test
    fun optionalControls_defaultToFalse_andToggleProperly() {
        // Por padrão o layout deve ser limpo e fiel à referência do My Boy!
        assertFalse(preferences.showQuickSaveLoad)
        assertFalse(preferences.showAbCombo)
        assertFalse(preferences.showTurboButton)
        assertFalse(preferences.showFpsCounter)
        assertFalse(preferences.stretchToFill)

        preferences.showQuickSaveLoad = true
        preferences.showAbCombo = true
        preferences.showTurboButton = true
        preferences.showFpsCounter = true
        preferences.stretchToFill = true

        assertTrue(preferences.showQuickSaveLoad)
        assertTrue(preferences.showAbCombo)
        assertTrue(preferences.showTurboButton)
        assertTrue(preferences.showFpsCounter)
        assertTrue(preferences.stretchToFill)
    }

    @Test
    fun controlStates_persistsCoordinatesAndScaleSeparatelyForPortraitAndLandscape() {
        // Inicialmente vazios (usam defaults calculados na UI)
        assertTrue(preferences.getControlStates(isLandscape = false).isEmpty())
        assertTrue(preferences.getControlStates(isLandscape = true).isEmpty())

        // Configuração para Retrato (Em Pé)
        val portraitMap = mapOf(
            "b_button" to ControlElementState(dx = 10f, dy = -5f, scale = 1.10f),
            "a_button" to ControlElementState(dx = 20f, dy = 0f, scale = 1.25f),
            "dpad" to ControlElementState(dx = 0f, dy = 0f, scale = 0.90f)
        )

        // Configuração para Paisagem (Deitado)
        val landscapeMap = mapOf(
            "b_button" to ControlElementState(dx = -40f, dy = -10f, scale = 1.40f),
            "a_button" to ControlElementState(dx = -15f, dy = -10f, scale = 0.85f),
            "dpad" to ControlElementState(dx = 15f, dy = 10f, scale = 1.15f)
        )

        preferences.setControlStates(isLandscape = false, portraitMap)
        preferences.setControlStates(isLandscape = true, landscapeMap)

        val loadedPortrait = preferences.getControlStates(isLandscape = false)
        val loadedLandscape = preferences.getControlStates(isLandscape = true)

        // Verifica que o modo Em Pé possui os seus próprios tamanhos e posições
        assertEquals(3, loadedPortrait.size)
        assertEquals(10f, loadedPortrait["b_button"]!!.dx, 0.01f)
        assertEquals(-5f, loadedPortrait["b_button"]!!.dy, 0.01f)
        assertEquals(1.10f, loadedPortrait["b_button"]!!.scale, 0.01f)
        assertEquals(1.25f, loadedPortrait["a_button"]!!.scale, 0.01f)

        // Verifica que o modo Deitado tem valores totalmente independentes
        assertEquals(3, loadedLandscape.size)
        assertEquals(-40f, loadedLandscape["b_button"]!!.dx, 0.01f)
        assertEquals(1.40f, loadedLandscape["b_button"]!!.scale, 0.01f)
        assertEquals(0.85f, loadedLandscape["a_button"]!!.scale, 0.01f)

        // Reset individual: resetar apenas o b_button no modo Paisagem não altera Retrato nem outros botões
        preferences.resetSingleControl("b_button", isLandscape = true)
        val afterSingleResetLandscape = preferences.getControlStates(isLandscape = true)
        assertFalse(afterSingleResetLandscape.containsKey("b_button"))
        assertTrue(afterSingleResetLandscape.containsKey("a_button"))
        assertTrue(preferences.getControlStates(isLandscape = false).containsKey("b_button"))

        // Reset completo da orientação
        preferences.resetControlStates(isLandscape = true)
        assertTrue(preferences.getControlStates(isLandscape = true).isEmpty())
        assertEquals(3, preferences.getControlStates(isLandscape = false).size)
    }

    @Test
    fun controlStates_clampsCoordinatesAndScalesProperly() {
        val outOfBounds = mapOf(
            "dpad" to ControlElementState(dx = -500f, dy = 600f, scale = 3.5f),
            "a_button" to ControlElementState(dx = 400f, dy = -400f, scale = 0.1f)
        )
        preferences.setControlStates(isLandscape = false, outOfBounds)
        val loaded = preferences.getControlStates(isLandscape = false)

        // Limites: dx em -360..360, dy em -260..260, scale em 0.50..1.80
        assertEquals(-360f, loaded["dpad"]!!.dx, 0.01f)
        assertEquals(260f, loaded["dpad"]!!.dy, 0.01f)
        assertEquals(1.80f, loaded["dpad"]!!.scale, 0.01f)

        assertEquals(360f, loaded["a_button"]!!.dx, 0.01f)
        assertEquals(-260f, loaded["a_button"]!!.dy, 0.01f)
        assertEquals(0.50f, loaded["a_button"]!!.scale, 0.01f)
    }
}
