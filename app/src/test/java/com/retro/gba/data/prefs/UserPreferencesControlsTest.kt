package com.retro.gba.data.prefs

import android.content.SharedPreferences
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
}
