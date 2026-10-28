package com.retro.gba.emulator

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GbaMemoryTest {

    private lateinit var memory: GbaMemory

    @Before
    fun setUp() {
        memory = GbaMemory()
    }

    @Test
    fun testEwramReadWrite() {
        val address = 0x02000100L
        memory.write8(address, 0xAB)
        assertEquals(0xAB, memory.read8(address))
    }

    @Test
    fun testIwramReadWrite() {
        val address = 0x03000050L
        memory.write8(address, 0x7F)
        assertEquals(0x7F, memory.read8(address))
    }

    @Test
    fun test16BitWriteAndRead() {
        val address = 0x02000200L
        memory.write16(address, 0x1234)
        assertEquals(0x1234, memory.read16(address))
        assertEquals(0x34, memory.read8(address))
        assertEquals(0x12, memory.read8(address + 1))
    }

    @Test
    fun test32BitRead() {
        val address = 0x02000300L
        memory.write8(address, 0x78)
        memory.write8(address + 1, 0x56)
        memory.write8(address + 2, 0x34)
        memory.write8(address + 3, 0x12)

        assertEquals(0x12345678L, memory.read32(address))
    }

    @Test
    fun testRomReading() {
        val dummyRom = ByteArray(256)
        dummyRom[0xA0] = 'P'.code.toByte()
        dummyRom[0xA1] = 'O'.code.toByte()
        dummyRom[0xA2] = 'K'.code.toByte()

        memory.rom = dummyRom

        assertEquals('P'.code, memory.read8(0x080000A0L))
        assertEquals('O'.code, memory.read8(0x080000A1L))
        assertEquals('K'.code, memory.read8(0x080000A2L))
    }
}
