package com.retro.gba.emulator

class GbaMemory {
    // 256KB EWRAM (0x02000000 - 0x0203FFFF)
    val ewram = ByteArray(256 * 1024)

    // 32KB IWRAM (0x03000000 - 0x03007FFF)
    val iwram = ByteArray(32 * 1024)

    // 1KB I/O Registers (0x04000000)
    val ioRegs = ByteArray(1024)

    // 96KB VRAM (0x06000000)
    val vram = ByteArray(96 * 1024)

    // 1KB OAM (0x07000000)
    val oam = ByteArray(1024)

    // ROM buffer
    var rom: ByteArray = ByteArray(0)

    fun read8(address: Long): Int {
        val addr = address.toInt()
        return when (address and 0xFF000000L) {
            0x02000000L -> {
                val offset = addr and 0x0003FFFF
                ewram[offset].toInt() and 0xFF
            }
            0x03000000L -> {
                val offset = addr and 0x00007FFF
                iwram[offset].toInt() and 0xFF
            }
            0x04000000L -> {
                val offset = addr and 0x000003FF
                ioRegs[offset].toInt() and 0xFF
            }
            0x08000000L, 0x09000000L -> {
                val offset = addr and 0x01FFFFFF
                if (offset < rom.size) rom[offset].toInt() and 0xFF else 0
            }
            else -> 0
        }
    }

    fun read16(address: Long): Int {
        val low = read8(address)
        val high = read8(address + 1)
        return (high shl 8) or low
    }

    fun read32(address: Long): Long {
        val b0 = read8(address).toLong()
        val b1 = read8(address + 1).toLong()
        val b2 = read8(address + 2).toLong()
        val b3 = read8(address + 3).toLong()
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }

    fun write8(address: Long, value: Int) {
        val addr = address.toInt()
        val v = value.toByte()
        when (address and 0xFF000000L) {
            0x02000000L -> {
                val offset = addr and 0x0003FFFF
                ewram[offset] = v
            }
            0x03000000L -> {
                val offset = addr and 0x00007FFF
                iwram[offset] = v
            }
            0x04000000L -> {
                val offset = addr and 0x000003FF
                ioRegs[offset] = v
            }
        }
    }

    fun write16(address: Long, value: Int) {
        write8(address, value and 0xFF)
        write8(address + 1, (value shr 8) and 0xFF)
    }
}

