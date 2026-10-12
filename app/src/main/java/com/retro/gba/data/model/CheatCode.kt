package com.retro.gba.data.model

import java.util.UUID

enum class CheatType(val typeId: Int, val displayName: String) {
    AUTODETECT(0, "Auto-detectar"),
    GAMESHARK(1, "GameShark"),
    PRO_ACTION_REPLAY(2, "Action Replay"),
    CODEBREAKER(3, "CodeBreaker"),
    RAW(4, "Raw Memory")
}

data class CheatCode(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val code: String,
    val type: CheatType = CheatType.AUTODETECT,
    val isEnabled: Boolean = true
)
