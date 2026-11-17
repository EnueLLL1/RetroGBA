package com.retro.gba.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class GbaInputTest {

    @Test
    fun testGbaButtonMasksMatchHardwareSpecification() {
        // No hardware GBA (REG_KEYINPUT 0x04000130), os bits são 0 a 9 em lógica ativa baixa:
        // Bit 0 = Button A (1 << 0 = 0x0001)
        // Bit 1 = Button B (1 << 1 = 0x0002)
        // Bit 2 = Select   (1 << 2 = 0x0004)
        // Bit 3 = Start    (1 << 3 = 0x0008)
        // Bit 4 = Right    (1 << 4 = 0x0010)
        // Bit 5 = Left     (1 << 5 = 0x0020)
        // Bit 6 = Up       (1 << 6 = 0x0040)
        // Bit 7 = Down     (1 << 7 = 0x0080)
        // Bit 8 = R        (1 << 8 = 0x0100)
        // Bit 9 = L        (1 << 9 = 0x0200)
        assertEquals(0x0001, GbaInput.BUTTON_A)
        assertEquals(0x0002, GbaInput.BUTTON_B)
        assertEquals(0x0004, GbaInput.BUTTON_SELECT)
        assertEquals(0x0008, GbaInput.BUTTON_START)
        assertEquals(0x0010, GbaInput.BUTTON_RIGHT)
        assertEquals(0x0020, GbaInput.BUTTON_LEFT)
        assertEquals(0x0040, GbaInput.BUTTON_UP)
        assertEquals(0x0080, GbaInput.BUTTON_DOWN)
        assertEquals(0x0100, GbaInput.BUTTON_R)
        assertEquals(0x0200, GbaInput.BUTTON_L)
        assertEquals(0x03FF, GbaInput.ALL_RELEASED)
    }

    @Test
    fun testButtonStateTransitions() {
        var buttons = GbaInput.ALL_RELEASED

        // Nenhum botão pressionado inicialmente (todos os 10 bits = 1)
        assertEquals(0x03FF, buttons)

        // Pressiona botão A (limpa o bit 0)
        buttons = buttons and GbaInput.BUTTON_A.inv()
        assertTrue((buttons and GbaInput.BUTTON_A) == 0)
        assertFalse((buttons and GbaInput.BUTTON_B) == 0)

        // Pressiona botão B simultaneamente
        buttons = buttons and GbaInput.BUTTON_B.inv()
        assertTrue((buttons and GbaInput.BUTTON_A) == 0)
        assertTrue((buttons and GbaInput.BUTTON_B) == 0)

        // Solta botão A (seta o bit 0 de volta)
        buttons = buttons or GbaInput.BUTTON_A
        assertFalse((buttons and GbaInput.BUTTON_A) == 0)
        assertTrue((buttons and GbaInput.BUTTON_B) == 0)

        // Solta todos
        buttons = buttons or GbaInput.BUTTON_B
        assertEquals(GbaInput.ALL_RELEASED, buttons)
    }

    @Test
    fun testActiveHighAndActiveLowConversions() {
        assertEquals(0x0000, GbaInput.ALL_RELEASED_ACTIVE_HIGH)
        assertEquals(GbaInput.ALL_RELEASED_ACTIVE_HIGH, GbaInput.toActiveHighMask(GbaInput.ALL_RELEASED))
        assertEquals(GbaInput.ALL_RELEASED, GbaInput.toActiveLowMask(GbaInput.ALL_RELEASED_ACTIVE_HIGH))

        val highAB = GbaInput.BUTTON_A or GbaInput.BUTTON_B
        val expectedLowAB = GbaInput.ALL_RELEASED and highAB.inv()
        assertEquals(expectedLowAB, GbaInput.toActiveLowMask(highAB))
        assertEquals(highAB, GbaInput.toActiveHighMask(expectedLowAB))

        // Bits acima do bit 9 (0x03FF) devem ser mascarados com segurança
        assertEquals(GbaInput.BUTTON_L, GbaInput.toActiveHighMask(0xFFFF and GbaInput.BUTTON_L.inv()))
        assertEquals(0x0000, GbaInput.toActiveLowMask(0xFFFF))
    }

    @Test
    fun testPressAndReleaseActiveLowHelpers() {
        var state = GbaInput.ALL_RELEASED

        state = GbaInput.pressActiveLow(state, GbaInput.BUTTON_A)
        assertEquals(GbaInput.BUTTON_A, GbaInput.toActiveHighMask(state))

        state = GbaInput.pressActiveLow(state, GbaInput.BUTTON_R or GbaInput.BUTTON_UP)
        assertEquals(
            GbaInput.BUTTON_A or GbaInput.BUTTON_R or GbaInput.BUTTON_UP,
            GbaInput.toActiveHighMask(state)
        )

        state = GbaInput.releaseActiveLow(state, GbaInput.BUTTON_A)
        assertEquals(
            GbaInput.BUTTON_R or GbaInput.BUTTON_UP,
            GbaInput.toActiveHighMask(state)
        )

        state = GbaInput.releaseActiveLow(state, GbaInput.BUTTON_R or GbaInput.BUTTON_UP)
        assertEquals(GbaInput.ALL_RELEASED, state)
    }
}
