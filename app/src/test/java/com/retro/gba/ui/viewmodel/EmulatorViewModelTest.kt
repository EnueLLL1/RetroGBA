package com.retro.gba.ui.viewmodel

import android.app.Application
import android.content.SharedPreferences
import android.graphics.Bitmap
import com.retro.gba.MainActivity
import com.retro.gba.data.api.RetroAchievementsApi
import com.retro.gba.data.model.Achievement
import com.retro.gba.data.model.CheatType
import com.retro.gba.data.prefs.UserPreferences
import com.retro.gba.data.repository.CheatRepository
import com.retro.gba.data.repository.RetroAchievementsRepository
import com.retro.gba.data.repository.RomRepository
import com.retro.gba.data.repository.SaveRepository
import com.retro.gba.emulator.GbaInput
import com.retro.gba.emulator.IGbaEngine
import com.retro.gba.model.ControlLayoutPresets
import com.retro.gba.model.ControlPlacement
import com.retro.gba.model.OrientationLayoutConfig
import com.retro.gba.model.ScreenViewportRect
import com.retro.gba.model.VirtualControlType
import com.retro.gba.performance.IPowerProfileManager
import com.retro.gba.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class EmulatorViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class InMemorySharedPreferences : SharedPreferences {
        private val store = ConcurrentHashMap<String, Any>()
        private val listeners = CopyOnWriteArraySet<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll(): MutableMap<String, *> = store.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? =
            (store[key] as? String) ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (store[key] as? MutableSet<String>) ?: defValues

        override fun getInt(key: String?, defValue: Int): Int =
            (store[key] as? Int) ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            (store[key] as? Long) ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            (store[key] as? Float) ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            (store[key] as? Boolean) ?: defValue

        override fun contains(key: String?): Boolean = key != null && store.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private var clearRequested = false

            override fun putString(key: String?, value: String?) = apply {
                if (key != null) pending[key] = value
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply {
                if (key != null) pending[key] = values
            }

            override fun putInt(key: String?, value: Int) = apply {
                if (key != null) pending[key] = value
            }

            override fun putLong(key: String?, value: Long) = apply {
                if (key != null) pending[key] = value
            }

            override fun putFloat(key: String?, value: Float) = apply {
                if (key != null) pending[key] = value
            }

            override fun putBoolean(key: String?, value: Boolean) = apply {
                if (key != null) pending[key] = value
            }

            override fun remove(key: String?) = apply {
                if (key != null) pending[key] = null
            }

            override fun clear() = apply { clearRequested = true }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearRequested) store.clear()
                val changed = mutableListOf<String>()
                for ((k, v) in pending) {
                    if (v == null) store.remove(k) else store[k] = v
                    changed.add(k)
                }
                for (listener in listeners) {
                    if (clearRequested) listener.onSharedPreferenceChanged(this@InMemorySharedPreferences, null)
                    for (k in changed) listener.onSharedPreferenceChanged(this@InMemorySharedPreferences, k)
                }
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            if (listener != null) listeners.add(listener)
        }

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            if (listener != null) listeners.remove(listener)
        }
    }

    private class FakeGbaEngine : IGbaEngine {
        override var isRunning: Boolean = false
        override var isPaused: Boolean = false
        override val isSimulationMode: Boolean = false
        override var frameskip: Int = 0
        override val currentFps: Int = 60

        var lastActiveHighMask: Int = GbaInput.ALL_RELEASED_ACTIVE_HIGH
        var resetAllButtonsCalls: Int = 0
        var saveRamToReturn: ByteArray? = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        var stateBytesToReturn: ByteArray? = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        var loadStateCalls: Int = 0
        val addedCheats = mutableListOf<String>()
        var lastRichPresenceScript: String = ""

        override fun loadRom(romBytes: ByteArray, achievements: List<Achievement>, saveBytes: ByteArray?): Boolean {
            return romBytes.isNotEmpty()
        }

        override fun start() {
            isRunning = true
            isPaused = false
        }

        override fun pause() {
            isPaused = true
        }

        override fun resume() {
            isPaused = false
        }

        override fun stop() {
            isRunning = false
            isPaused = false
        }

        override fun setButtonPressed(buttonMask: Int, pressed: Boolean) {
            lastActiveHighMask = if (pressed) {
                (lastActiveHighMask or buttonMask) and GbaInput.ALL_RELEASED
            } else {
                (lastActiveHighMask and buttonMask.inv()) and GbaInput.ALL_RELEASED
            }
        }

        override fun setButtonsMask(activeHighMask: Int) {
            lastActiveHighMask = activeHighMask and GbaInput.ALL_RELEASED
        }

        override fun resetAllButtons() {
            resetAllButtonsCalls++
            lastActiveHighMask = GbaInput.ALL_RELEASED_ACTIVE_HIGH
        }

        override fun activateRichPresence(script: String) {
            lastRichPresenceScript = script
        }

        override fun getRichPresence(): String = ""
        override fun setSpeedMultiplier(multiplier: Float) {}
        override fun getSaveRam(): ByteArray? = saveRamToReturn
        override fun saveState(): ByteArray? = stateBytesToReturn
        override fun loadState(stateBytes: ByteArray): Boolean {
            loadStateCalls++
            return stateBytes.isNotEmpty()
        }

        override fun readMemory(address: Long, size: Int): Long = 0L
        override fun addCheat(description: String, code: String, type: Int, enabled: Boolean): Boolean {
            addedCheats.add(code)
            return true
        }

        override fun clearCheats() {
            addedCheats.clear()
        }

        override fun setCheatEnabled(index: Int, enabled: Boolean) {}
    }

    private val fakePowerManager = object : IPowerProfileManager {
        override fun isPowerSaveModeActive(): Boolean = false
        override fun getBatteryLevel(): Int = 80
        override fun isBatteryLow(): Boolean = false
        override fun isCharging(): Boolean = false
        override fun isThermalThrottling(): Boolean = false
        override fun calculateRecommendedFrameskip(ecoModeEnabled: Boolean): Int = if (ecoModeEnabled) 1 else 0
    }

    private lateinit var app: Application
    private lateinit var prefsStore: InMemorySharedPreferences
    private lateinit var userPrefs: UserPreferences
    private lateinit var saveRepo: SaveRepository
    private lateinit var cheatRepo: CheatRepository
    private lateinit var romRepo: RomRepository
    private lateinit var fakeEngine: FakeGbaEngine
    private val recordedRequests = mutableListOf<String>()

    private fun createMockApi(): RetroAchievementsApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                recordedRequests.add(req.url.toString())
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"Success":true}""".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        return RetroAchievementsApi(baseUrl = "https://mock.ra.local", client = client)
    }

    private fun createViewModel(
        prefs: UserPreferences = userPrefs,
        engine: FakeGbaEngine = fakeEngine,
        startBackgroundTicker: Boolean = false
    ): EmulatorViewModel {
        val raRepo = RetroAchievementsRepository(createMockApi(), prefs)
        return EmulatorViewModel(
            application = app,
            userPrefs = prefs,
            raRepository = raRepo,
            romRepo = romRepo,
            saveRepository = saveRepo,
            cheatRepository = cheatRepo,
            powerManager = fakePowerManager,
            hapticManager = null,
            engineFactory = { _, _ -> engine },
            startBackgroundTicker = startBackgroundTicker
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        app = Application()
        prefsStore = InMemorySharedPreferences()
        userPrefs = UserPreferences(prefsStore, CryptoManager("UnitTestKey"))
        saveRepo = SaveRepository(tempFolder.newFolder("saves"))
        cheatRepo = CheatRepository(tempFolder.newFolder("cheats"))
        romRepo = RomRepository(app, userPrefs)
        fakeEngine = FakeGbaEngine()
        recordedRequests.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun touchAndPhysicalGamepadInputsCombineWithoutOverwritingEachOther() {
        val viewModel = createViewModel()

        // Touch segura A; gamepad segura L; analógico/hat segura RIGHT
        viewModel.setTouchButtonsMask(GbaInput.BUTTON_A)
        viewModel.onPhysicalButtonEvent(GbaInput.BUTTON_L, true)
        viewModel.onPhysicalAxisEvent(GbaInput.BUTTON_RIGHT)
        assertEquals(
            GbaInput.BUTTON_A or GbaInput.BUTTON_L or GbaInput.BUTTON_RIGHT,
            fakeEngine.lastActiveHighMask
        )

        // Soltar todo o toque preserva L e RIGHT do gamepad físico
        viewModel.setTouchButtonsMask(0)
        assertEquals(GbaInput.BUTTON_L or GbaInput.BUTTON_RIGHT, fakeEngine.lastActiveHighMask)

        // Reaplicar toque B + soltar eixo analógico mantém B e L
        viewModel.onButtonStateChanged(GbaInput.BUTTON_B, true)
        viewModel.onPhysicalAxisEvent(0)
        assertEquals(GbaInput.BUTTON_B or GbaInput.BUTTON_L, fakeEngine.lastActiveHighMask)

        // Limpar apenas físicos preserva toque B
        viewModel.clearPhysicalInputs()
        assertEquals(GbaInput.BUTTON_B, fakeEngine.lastActiveHighMask)

        // clearAllInputs zera tudo via resetAllButtons
        viewModel.clearAllInputs()
        assertEquals(GbaInput.ALL_RELEASED_ACTIVE_HIGH, fakeEngine.lastActiveHighMask)
        assertTrue(fakeEngine.resetAllButtonsCalls >= 1)
    }

    @Test
    fun hardcoreAllowsSaveStateButBlocksLoadStateAndCheats() = runBlocking {
        userPrefs.isHardcoreMode = true
        val viewModel = createViewModel()
        val romHash = "e26ee0d44e809351c8ce2d73c7400cdd"
        assertTrue(viewModel.loadRom(ByteArray(256), romHash))
        assertTrue(viewModel.sessionHardcoreActive.value)

        // Save State é permitido em Hardcore
        viewModel.saveState(slot = 1)
        viewModel.lastStateJob?.join()
        val savedBytes = saveRepo.loadState(romHash, 1)
        assertNotNull("Save State deve ser gravado normalmente em Hardcore", savedBytes)
        assertArrayEquals(fakeEngine.stateBytesToReturn, savedBytes)

        // Load State deve ser bloqueado em Hardcore
        viewModel.loadState(slot = 1)
        assertEquals(0, fakeEngine.loadStateCalls)
        assertTrue(viewModel.statusMessage.value?.contains("Hardcore", ignoreCase = true) == true)

        // Cheats devem ser bloqueados em Hardcore
        viewModel.addCheat("Dinheiro", "82003884 0063", CheatType.GAMESHARK)
        assertTrue(viewModel.cheats.value.isEmpty())
        assertTrue(fakeEngine.addedCheats.isEmpty())
    }

    @Test
    fun disablingHardcoreDuringSessionDowngradesIrreversiblyUntilNextLoadRomOrReset() = runBlocking {
        userPrefs.isHardcoreMode = true
        val viewModel = createViewModel()
        val romHash = "abcdef0123456789abcdef0123456789"
        assertTrue(viewModel.loadRom(ByteArray(256), romHash))
        assertTrue(viewModel.sessionHardcoreActive.value)

        // Desliga Hardcore durante a sessão -> rebaixa para Softcore
        userPrefs.isHardcoreMode = false
        assertFalse(viewModel.sessionHardcoreActive.value)

        // Religar Hardcore na mesma sessão NÃO reativa Hardcore
        userPrefs.isHardcoreMode = true
        assertFalse(viewModel.sessionHardcoreActive.value)

        // Load State agora é permitido porque a sessão está em Softcore
        assertTrue(saveRepo.saveState(romHash, 0, byteArrayOf(0x55, 0x66)))
        viewModel.loadState(0)
        viewModel.lastStateJob?.join()
        assertEquals(1, fakeEngine.loadStateCalls)

        // reset() com preferência global Hardcore = true restaura Hardcore na nova sessão
        viewModel.reset()
        assertTrue(viewModel.sessionHardcoreActive.value)

        // Novo rebaixamento seguido de loadRom() também restaura Hardcore quando preferência = true
        userPrefs.isHardcoreMode = false
        assertFalse(viewModel.sessionHardcoreActive.value)
        userPrefs.isHardcoreMode = true
        assertFalse(viewModel.sessionHardcoreActive.value)
        assertTrue(viewModel.loadRom(ByteArray(256), romHash))
        assertTrue(viewModel.sessionHardcoreActive.value)
    }

    @Test
    fun onAppPausePersistsBatterySavePausesEngineAndResetsAllButtons() = runBlocking {
        val viewModel = createViewModel()
        val romHash = "11223344556677889900aabbccddeeff"
        val expectedSram = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        fakeEngine.saveRamToReturn = expectedSram

        assertTrue(viewModel.loadRom(ByteArray(256), romHash))
        assertTrue(fakeEngine.isRunning)
        assertFalse(fakeEngine.isPaused)

        viewModel.setTouchButtonsMask(GbaInput.BUTTON_A)
        viewModel.onPhysicalButtonEvent(GbaInput.BUTTON_START, true)
        assertEquals(GbaInput.BUTTON_A or GbaInput.BUTTON_START, fakeEngine.lastActiveHighMask)

        viewModel.onAppPause()
        viewModel.lastSaveJob?.join()

        assertTrue(fakeEngine.isPaused)
        assertEquals(GbaInput.ALL_RELEASED_ACTIVE_HIGH, fakeEngine.lastActiveHighMask)
        assertTrue(fakeEngine.resetAllButtonsCalls >= 1)

        val persisted = saveRepo.loadBatteryRam(romHash)
        assertNotNull(persisted)
        assertArrayEquals(expectedSram, persisted)
    }

    @Test
    fun layoutProfilesPersistIndependentlyForPortraitAndLandscapeAndSyncWithProfileViewModel() {
        val profilePrefs = UserPreferences(prefsStore, CryptoManager("UnitTestKey"))
        val profileVm = ProfileViewModel(
            application = app,
            userPrefs = profilePrefs,
            repository = RetroAchievementsRepository(createMockApi(), profilePrefs)
        )
        val emulatorVm = createViewModel(prefs = userPrefs)

        assertEquals(3, emulatorVm.layoutProfiles.value.size)
        assertEquals("default", emulatorVm.activeLayoutProfile.value.id)

        // Alteração no ProfileViewModel reflete instantaneamente no EmulatorViewModel
        profileVm.selectLayoutProfile("comfortable")
        profileVm.setControllerOpacity(0.55f)
        profileVm.setHapticFeedback(false)
        profileVm.setFastForwardSpeed(3.0f)
        profileVm.setHideControls(true)

        assertEquals("comfortable", emulatorVm.activeLayoutProfile.value.id)
        assertEquals(0.55f, emulatorVm.controllerOpacity.value, 0.01f)
        assertFalse(emulatorVm.hapticFeedbackEnabled.value)
        assertEquals(3.0f, emulatorVm.fastForwardSpeed.value, 0.01f)
        assertTrue(emulatorVm.hideControls.value)

        // Criar perfil customizado e editar Portrait e Landscape separadamente
        emulatorVm.createCustomLayoutProfile("Pokémon 1 Mão")
        val customId = emulatorVm.activeLayoutProfile.value.id
        assertFalse(emulatorVm.activeLayoutProfile.value.isBuiltIn)
        assertEquals("Pokémon 1 Mão", emulatorVm.activeLayoutProfile.value.name)

        val originalLandscape = emulatorVm.activeLayoutProfile.value.landscape
        val editedPortrait = OrientationLayoutConfig(
            screenRect = ScreenViewportRect(centerX = 0.50f, centerY = 0.28f, scale = 0.92f),
            controls = listOf(
                ControlPlacement("dpad", VirtualControlType.DPAD, 0.24f, 0.72f, 1.1f),
                ControlPlacement("btn_menu", VirtualControlType.BUTTON_MENU, 0.50f, 0.52f, 1.0f)
            )
        )
        emulatorVm.saveOrientationLayout(isLandscape = false, config = editedPortrait)

        assertEquals(0.28f, emulatorVm.activeLayoutProfile.value.portrait.screenRect.centerY, 0.001f)
        assertEquals(2, emulatorVm.activeLayoutProfile.value.portrait.controls.size)
        assertEquals(originalLandscape, emulatorVm.activeLayoutProfile.value.landscape)

        val editedLandscape = originalLandscape.copy(
            screenRect = ScreenViewportRect(centerX = 0.50f, centerY = 0.45f, scale = 1.25f)
        )
        emulatorVm.saveOrientationLayout(isLandscape = true, config = editedLandscape)
        assertEquals(1.25f, emulatorVm.activeLayoutProfile.value.landscape.screenRect.scale, 0.001f)
        assertEquals(2, emulatorVm.activeLayoutProfile.value.portrait.controls.size)

        // Nova instância deve recuperar perfil ativo e customizações persistidas
        val reloadedVm = createViewModel(prefs = UserPreferences(prefsStore, CryptoManager("UnitTestKey")))
        assertEquals(customId, reloadedVm.activeLayoutProfile.value.id)
        assertEquals(1.25f, reloadedVm.activeLayoutProfile.value.landscape.screenRect.scale, 0.001f)
        assertEquals(2, reloadedVm.activeLayoutProfile.value.portrait.controls.size)

        // Presets integrados não podem ser excluídos; customizado pode e volta ao default
        reloadedVm.deleteCustomLayoutProfile("default")
        assertTrue(reloadedVm.layoutProfiles.value.any { it.id == "default" })

        reloadedVm.deleteCustomLayoutProfile(customId)
        assertFalse(reloadedVm.layoutProfiles.value.any { it.id == customId })
        assertEquals("default", reloadedVm.activeLayoutProfile.value.id)
    }

    @Test
    fun simultaneousAchievementUnlocksDisplaySequentiallyThroughChannelQueue() = runTest {
        val schedulerDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(schedulerDispatcher)

        val viewModel = createViewModel(startBackgroundTicker = false)
        runCurrent()

        val ach1 = Achievement(1L, "Primeiro Passo", "Desc 1", 5, "00001")
        val ach2 = Achievement(2L, "Segundo Passo", "Desc 2", 10, "00002")

        viewModel.handleAchievementUnlock(ach1)
        viewModel.handleAchievementUnlock(ach2)
        runCurrent()

        // Primeira conquista visível imediatamente sem ser sobrescrita pela segunda
        assertEquals(1L, viewModel.recentUnlockedAchievement.value?.id)

        // Após o tempo do primeiro banner + intervalo, exibe a segunda conquista
        advanceTimeBy(4100L)
        runCurrent()
        assertEquals(2L, viewModel.recentUnlockedAchievement.value?.id)

        // Após o segundo banner, limpa o overlay
        advanceTimeBy(4100L)
        runCurrent()
        assertNull(viewModel.recentUnlockedAchievement.value)
    }

    @Test
    fun achievementUnlockSubmitsGameHashAndSessionHardcoreFlag() = runBlocking {
        userPrefs.username = "Tester"
        userPrefs.token = "tok_123"
        userPrefs.isHardcoreMode = true

        val capturedFormBodies = mutableListOf<String>()
        val requestLatch = CountDownLatch(1)
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val buf = okio.Buffer()
                req.body?.writeTo(buf)
                capturedFormBodies.add(buf.readUtf8())
                requestLatch.countDown()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"Success":true}""".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        val api = RetroAchievementsApi(baseUrl = "https://mock.ra.local", client = client)
        val viewModel = EmulatorViewModel(
            application = app,
            userPrefs = userPrefs,
            raRepository = RetroAchievementsRepository(api, userPrefs),
            romRepo = romRepo,
            saveRepository = saveRepo,
            cheatRepository = cheatRepo,
            powerManager = fakePowerManager,
            hapticManager = null,
            engineFactory = { _, _ -> fakeEngine },
            startBackgroundTicker = false
        )

        val romHash = "deadbeef00112233445566778899aabb"
        assertTrue(viewModel.loadRom(ByteArray(256), romHash, richPresencePatch = "Display:\nTest"))
        assertEquals("Display:\nTest", fakeEngine.lastRichPresenceScript)

        // Rebaixa sessão para Softcore antes de desbloquear conquista
        userPrefs.isHardcoreMode = false
        viewModel.handleAchievementUnlock(Achievement(77L, "Conquista", "Desc", 10, "00077"))

        assertTrue(requestLatch.await(3, TimeUnit.SECONDS))
        assertEquals(1, capturedFormBodies.size)
        val body = capturedFormBodies.first()
        assertTrue(body.contains("r=awardachievement"))
        assertTrue(body.contains("a=77"))
        assertTrue(body.contains("h=0"))
        assertTrue(body.contains("m=$romHash"))
        assertTrue(body.contains("o=0"))
    }

    @Test
    fun mainActivityKeyMappingMapsGamepadAndKeyboardButtonsSafelyWithoutConsumingBack() {
        assertNull(MainActivity.mapKeyCodeToGbaButton(android.view.KeyEvent.KEYCODE_BACK))
        assertEquals(GbaInput.BUTTON_A, MainActivity.mapKeyCodeToGbaButton(android.view.KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(GbaInput.BUTTON_B, MainActivity.mapKeyCodeToGbaButton(android.view.KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(GbaInput.BUTTON_L, MainActivity.mapKeyCodeToGbaButton(android.view.KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(GbaInput.BUTTON_R, MainActivity.mapKeyCodeToGbaButton(android.view.KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals(GbaInput.BUTTON_START, MainActivity.mapKeyCodeToGbaButton(android.view.KeyEvent.KEYCODE_ENTER))
        assertEquals(GbaInput.BUTTON_SELECT, MainActivity.mapKeyCodeToGbaButton(android.view.KeyEvent.KEYCODE_SHIFT_LEFT))
        assertEquals(GbaInput.BUTTON_UP, MainActivity.mapKeyCodeToGbaButton(android.view.KeyEvent.KEYCODE_DPAD_UP))
    }
}
