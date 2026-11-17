package com.retro.gba.emulator

object GbaInput {
    const val BUTTON_A      = 1 shl 0
    const val BUTTON_B      = 1 shl 1
    const val BUTTON_SELECT = 1 shl 2
    const val BUTTON_START  = 1 shl 3
    const val BUTTON_RIGHT  = 1 shl 4
    const val BUTTON_LEFT   = 1 shl 5
    const val BUTTON_UP     = 1 shl 6
    const val BUTTON_DOWN   = 1 shl 7
    const val BUTTON_R      = 1 shl 8
    const val BUTTON_L      = 1 shl 9

    const val ALL_RELEASED             = 0x03FF
    const val ALL_RELEASED_ACTIVE_HIGH = 0x0000

    fun toActiveHighMask(activeLowMask: Int): Int = (activeLowMask.inv()) and 0x03FF

    fun toActiveLowMask(activeHighMask: Int): Int = (activeHighMask.inv()) and 0x03FF

    fun pressActiveLow(currentActiveLow: Int, buttonMask: Int): Int =
        (currentActiveLow and buttonMask.inv()) and 0x03FF

    fun releaseActiveLow(currentActiveLow: Int, buttonMask: Int): Int =
        (currentActiveLow or buttonMask) and 0x03FF
}

