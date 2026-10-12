package com.retro.gba.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GbaAudioPlayerTest {

    @Test
    fun testAudioPlayerLifecycleAndDegradation() {
        val player = GbaAudioPlayer(32768)
        assertNotNull(player)
        assertEquals(32768, player.sampleRate)

        // Deve tolerar chamadas sem quebrar na JVM do host
        player.start()
        player.pause()
        player.resume()

        val sampleBuffer = ShortArray(512) { (it % 100).toShort() }
        player.writeSamples(sampleBuffer, sampleBuffer.size)

        player.setSpeedMultiplier(2.0f)
        player.setMuted(true)
        player.setMuted(false)

        player.stop()
        player.release()
    }
}
