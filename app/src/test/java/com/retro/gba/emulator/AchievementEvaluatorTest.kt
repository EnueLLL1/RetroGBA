package com.retro.gba.emulator

import com.retro.gba.data.model.Achievement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AchievementEvaluatorTest {

    private lateinit var evaluator: AchievementEvaluator
    private lateinit var memory: GbaMemory

    @Before
    fun setUp() {
        evaluator = AchievementEvaluator()
        memory = GbaMemory()
    }

    @Test
    fun testSimpleAchievementEvaluation() {
        val testAch = Achievement(
            id = 101L,
            title = "Primeira Vitória",
            description = "Complete a fase 1",
            points = 5,
            badgeName = "00101",
            memAddr = "0xH02000000=1" // EWRAM byte em 0x02000000 == 1
        )

        evaluator.loadAchievements(listOf(testAch))

        // Inicialmente estado é 0, não deve desbloquear
        memory.write8(0x02000000L, 0)
        var triggered = evaluator.evaluateFrame(memory)
        assertTrue("Não deve desbloquear com valor 0", triggered.isEmpty())

        // Atualiza memória para 1
        memory.write8(0x02000000L, 1)
        triggered = evaluator.evaluateFrame(memory)

        assertEquals("Deve desbloquear 1 conquista", 1, triggered.size)
        assertEquals(101L, triggered[0].id)
    }

    @Test
    fun testMultipleConditionsEvaluation() {
        val testAch = Achievement(
            id = 102L,
            title = "Mestre dos Pontos",
            description = "Alcance 100 pontos no Nível 2",
            points = 10,
            badgeName = "00102",
            memAddr = "0xH02000000=2_0x02000002>=100" // Level == 2 AND Score >= 100
        )

        evaluator.loadAchievements(listOf(testAch))

        memory.write8(0x02000000L, 2) // Level 2
        memory.write16(0x02000002L, 50) // Score 50 (ainda insuficiente)
        assertTrue(evaluator.evaluateFrame(memory).isEmpty())

        memory.write16(0x02000002L, 120) // Score 120 (agora atinge ambos os requisitos)
        val triggered = evaluator.evaluateFrame(memory)
        assertEquals(1, triggered.size)
        assertEquals(102L, triggered[0].id)
    }

    @Test
    fun testVirtualFlatAddressMapping() {
        // Teste de mapeamento virtual plano do rcheevos (IWRAM offset 0x0000 = 0x03000000)
        val testAch = Achievement(
            id = 103L,
            title = "IWRAM Test",
            description = "Teste de IWRAM",
            points = 5,
            badgeName = "00103",
            memAddr = "0xH00000000=42" // Address virtual plano 0x00000000 -> IWRAM 0x03000000
        )

        evaluator.loadAchievements(listOf(testAch))

        memory.write8(0x03000000L, 42)
        val triggered = evaluator.evaluateFrame(memory)
        assertEquals(1, triggered.size)
        assertEquals(103L, triggered[0].id)
    }
}
