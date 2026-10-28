package com.retro.gba

import com.retro.gba.data.model.Achievement
import com.retro.gba.emulator.AchievementEvaluator
import com.retro.gba.emulator.GbaMemory
import com.retro.gba.security.RomSecurityValidator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EmulatorViewModelTest {

    private lateinit var evaluator: AchievementEvaluator
    private lateinit var memory: GbaMemory

    @Before
    fun setUp() {
        evaluator = AchievementEvaluator()
        memory = GbaMemory()
    }

    @Test
    fun testRomSecurityValidator_InvalidSmallRom() {
        val smallRom = ByteArray(100) // Menor que 192 bytes
        val result = RomSecurityValidator.validateRomBytes(smallRom)
        assertTrue(result is RomSecurityValidator.ValidationResult.Invalid)
    }

    @Test
    fun testRomSecurityValidator_ValidGbaHeader() {
        val validRom = ByteArray(1024)
        // Nintendo Logo magic bytes em offset 0x04..0xA0
        validRom[0x04] = 0x24.toByte()
        validRom[0x05] = 0xFB.toByte()
        validRom[0x06] = 0xA2.toByte()
        validRom[0x07] = 0x9A.toByte()

        val result = RomSecurityValidator.validateRomBytes(validRom)
        assertTrue(result is RomSecurityValidator.ValidationResult.Valid)
    }

    @Test
    fun testAchievementEvaluator_ConditionEvaluation() {
        val ach = Achievement(
            id = 101L,
            title = "Primeiro Pulo",
            description = "Pule uma vez no jogo",
            points = 5,
            badgeName = "00101",
            memAddr = "0xH02000000=10"
        )

        evaluator.loadAchievements(listOf(ach))

        // Escreve valor 5 na memória (ainda não deve atingir a condição =10)
        memory.write8(0x02000000L, 5)
        var triggered = evaluator.evaluateFrame(memory)
        assertTrue(triggered.isEmpty())

        // Escreve valor 10 na memória (deve disparar a conquista)
        memory.write8(0x02000000L, 10)
        triggered = evaluator.evaluateFrame(memory)
        assertEquals(1, triggered.size)
        assertEquals(101L, triggered[0].id)
    }
}
