package com.retro.gba.emulator

import com.retro.gba.data.model.Achievement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GbaEngineTest {

    @Test
    fun testSimulationGbaEnginePropertiesAndLifecycle() {
        var frameDelivered = false
        val engine = SimulationGbaEngine(
            onFrameReady = { frameDelivered = true },
            onAchievementTriggered = {}
        )

        assertTrue(engine.isSimulationMode)
        assertFalse(engine.isRunning)
        assertFalse(engine.isPaused)

        // Empty ROM should be rejected
        assertFalse(engine.loadRom(ByteArray(0)))

        // Valid ROM header should be accepted
        val testRom = ByteArray(512)
        val title = "POKEMON EMER"
        System.arraycopy(title.toByteArray(StandardCharsets.US_ASCII), 0, testRom, 0xA0, title.length)
        assertTrue(engine.loadRom(testRom))

        // Speed multiplier
        engine.setSpeedMultiplier(2.5f)

        // Buttons & memory register KEYINPUT (active-low at 0x04000130..0x04000131)
        engine.setButtonPressed(GbaInput.BUTTON_A, true)
        engine.runFrame(0L)
        assertEquals(GbaInput.ALL_RELEASED and GbaInput.BUTTON_A.inv(), engine.readMemory(0x04000130L, 2).toInt())

        engine.setButtonsMask(GbaInput.BUTTON_B or GbaInput.BUTTON_L)
        engine.runFrame(1L)
        val expectedBL = GbaInput.toActiveLowMask(GbaInput.BUTTON_B or GbaInput.BUTTON_L)
        assertEquals(expectedBL, engine.readMemory(0x04000130L, 2).toInt())

        engine.resetAllButtons()
        engine.runFrame(2L)
        assertEquals(GbaInput.ALL_RELEASED, engine.readMemory(0x04000130L, 2).toInt())

        // Save State & Load State
        val stateBytes = engine.saveState()
        assertNotNull(stateBytes)
        assertTrue(stateBytes!!.isNotEmpty())
        assertTrue(engine.loadState(stateBytes))

        // Rejection of invalid short state
        assertFalse(engine.loadState(ByteArray(10)))
    }

    @Test
    fun testSimulationGbaEngineConcurrentButtonUpdatesAreAtomic() {
        val engine = SimulationGbaEngine(
            onFrameReady = {},
            onAchievementTriggered = {}
        )
        val executor = Executors.newFixedThreadPool(2)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(2)

        // Touch thread presses/releases A repeatedly; gamepad thread holds L continuously
        executor.submit {
            startLatch.await()
            repeat(500) {
                engine.setButtonPressed(GbaInput.BUTTON_A, true)
                engine.setButtonPressed(GbaInput.BUTTON_A, false)
            }
            doneLatch.countDown()
        }
        executor.submit {
            startLatch.await()
            repeat(500) {
                engine.setButtonPressed(GbaInput.BUTTON_L, true)
            }
            doneLatch.countDown()
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS))
        executor.shutdownNow()

        // L must never be lost under concurrent updates to A
        val state = engine.getCurrentButtonsActiveLow()
        assertTrue((state and GbaInput.BUTTON_L) == 0)
        assertTrue((state and GbaInput.BUTTON_A) != 0)
    }

    @Test
    fun testNativeGbaEngineGracefulDegradationWhenNativeLibNotPresent() {
        val nativeEngine = NativeGbaEngine(
            onFrameReady = {},
            onAchievementTriggered = {}
        )

        assertFalse(nativeEngine.isSimulationMode)
        // No ambiente de testes unitários da JVM (sem libretrogba.so compilada),
        // deve degradar graciosamente sem lançar exceções não tratadas:
        assertFalse(NativeGbaEngine.isNativeAvailable())
        assertFalse(nativeEngine.loadRom(ByteArray(100)))
        assertEquals(null, nativeEngine.saveState())
        assertEquals(null, nativeEngine.getSaveRam())
        assertFalse(nativeEngine.loadState(ByteArray(100)))

        nativeEngine.setButtonsMask(GbaInput.BUTTON_A or GbaInput.BUTTON_R)
        assertEquals(
            GbaInput.toActiveLowMask(GbaInput.BUTTON_A or GbaInput.BUTTON_R),
            nativeEngine.getCurrentButtonsActiveLow()
        )
        nativeEngine.resetAllButtons()
        assertEquals(GbaInput.ALL_RELEASED, nativeEngine.getCurrentButtonsActiveLow())

        nativeEngine.activateRichPresence("Display:\nPlaying")
        assertEquals("", nativeEngine.getRichPresence())
    }

    @Test
    fun testIGbaEngineDefaultMethodsDelegateCorrectly() {
        val buttonStates = mutableMapOf<Int, Boolean>()
        val minimalEngine = object : IGbaEngine {
            override val isRunning: Boolean = false
            override val isPaused: Boolean = false
            override val isSimulationMode: Boolean = true
            override var frameskip: Int = 0
            override val currentFps: Int = 60
            override fun loadRom(romBytes: ByteArray, achievements: List<Achievement>, saveBytes: ByteArray?) = true
            override fun start() {}
            override fun pause() {}
            override fun resume() {}
            override fun stop() {}
            override fun setButtonPressed(buttonMask: Int, pressed: Boolean) {
                buttonStates[buttonMask] = pressed
            }
            override fun setSpeedMultiplier(multiplier: Float) {}
            override fun getSaveRam(): ByteArray? = null
            override fun saveState(): ByteArray? = null
            override fun loadState(stateBytes: ByteArray): Boolean = false
            override fun readMemory(address: Long, size: Int): Long = 0L
            override fun addCheat(description: String, code: String, type: Int, enabled: Boolean) = false
            override fun clearCheats() {}
            override fun setCheatEnabled(index: Int, enabled: Boolean) {}
        }

        minimalEngine.setButtonsMask(GbaInput.BUTTON_START or GbaInput.BUTTON_RIGHT)
        assertEquals(true, buttonStates[GbaInput.BUTTON_START])
        assertEquals(true, buttonStates[GbaInput.BUTTON_RIGHT])
        assertEquals(false, buttonStates[GbaInput.BUTTON_A])

        minimalEngine.resetAllButtons()
        assertTrue(buttonStates.values.all { !it })
        assertEquals("", minimalEngine.getRichPresence())
        minimalEngine.activateRichPresence("Display:\nTest")
    }

    @Test
    fun testAudioPlayerFastForwardDropsExcessSamplesSafely() {
        val player = GbaAudioPlayer(32768)
        // Normal speed: 4096 buffer frames, 3000 queued -> 1096 available frames (2192 shorts)
        assertEquals(2048, player.computeWritableShorts(2048, 4096, 3000, speedMultiplier = 1.0f))

        // Fast-forward caps buffer at 75% (3072 frames): 3100 queued -> 0 writable (discards excess)
        assertEquals(0, player.computeWritableShorts(2048, 4096, 3100, speedMultiplier = 3.0f))

        // Fast-forward with partial space: 3000 queued vs 3072 cap -> 72 frames = 144 shorts
        assertEquals(144, player.computeWritableShorts(2048, 4096, 3000, speedMultiplier = 2.0f))

        // Odd sample counts are stereo-aligned down to even short count
        assertEquals(10, player.computeWritableShorts(11, 4096, 0, speedMultiplier = 1.0f))
    }
}
