package com.retro.gba.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

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

        // Buttons
        engine.setButtonPressed(GbaInput.BUTTON_A, true)
        engine.setButtonPressed(GbaInput.BUTTON_A, false)

        // Save State & Load State com persistência de memória e restauração de player/score
        engine.memory.write8(0x02000000L, 88)
        engine.memory.write8(0x02000001L, 44)
        engine.memory.write16(0x02000002L, 300)

        val stateBytes = engine.saveState()
        assertNotNull(stateBytes)
        assertTrue(stateBytes!!.isNotEmpty())

        engine.playerX = 10
        engine.playerY = 20
        engine.score = 0

        assertTrue(engine.loadState(stateBytes))
        assertEquals(88, engine.playerX)
        assertEquals(44, engine.playerY)
        assertEquals(300, engine.score)

        // Rejection of invalid short state
        assertFalse(engine.loadState(ByteArray(10)))
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
    }
}
