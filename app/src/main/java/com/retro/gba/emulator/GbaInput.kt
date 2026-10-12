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

    const val ALL_RELEASED  = 0x03FF
}

