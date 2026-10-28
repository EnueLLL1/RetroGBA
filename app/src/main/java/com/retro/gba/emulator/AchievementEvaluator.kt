package com.retro.gba.emulator

import com.retro.gba.data.model.Achievement

/**
 * Avaliador de condições de memória do RetroAchievements (rcheevos).
 * Analisa as strings de condição fornecidas pelo RetroAchievements (MemAddr)
 * e avalia se os requisitos foram satisfeitos a cada frame do emulador.
 */
class AchievementEvaluator {

    data class MemoryCondition(
        val size: Int, // 1 = byte, 2 = word, 4 = dword
        val address: Long,
        val operator: String,
        val targetValue: Long,
        val isDelta: Boolean = false
    )

    private val activeAchievements = mutableListOf<Pair<Achievement, List<MemoryCondition>>>()
    private val previousMemoryValues = mutableMapOf<Long, Long>()

    fun loadAchievements(achievements: List<Achievement>) {
        activeAchievements.clear()
        previousMemoryValues.clear()

        for (ach in achievements) {
            if (ach.isUnlocked || ach.memAddr.isNullOrBlank()) continue
            val conditions = parseConditionString(ach.memAddr)
            if (conditions.isNotEmpty()) {
                activeAchievements.add(Pair(ach, conditions))
            }
        }
    }

    private fun parseConditionString(memAddr: String): List<MemoryCondition> {
        val conditions = mutableListOf<MemoryCondition>()
        // RetroAchievements divide sub-condições por '_' (AND)
        val tokens = memAddr.split("_")
        for (token in tokens) {
            val cond = parseSingleCondition(token.trim())
            if (cond != null) {
                conditions.add(cond)
            }
        }
        return conditions
    }

    private fun parseSingleCondition(token: String): MemoryCondition? {
        val operators = listOf(">=", "<=", "!=", "=", ">", "<")
        var matchedOp: String? = null
        var opIndex = -1

        for (op in operators) {
            val idx = token.indexOf(op)
            if (idx != -1) {
                matchedOp = op
                opIndex = idx
                break
            }
        }

        if (matchedOp == null || opIndex == -1) return null

        val leftPart = token.substring(0, opIndex).trim()
        val rightPart = token.substring(opIndex + matchedOp.length).trim()

        val isDelta = leftPart.startsWith("d")
        val cleanLeft = if (isDelta) leftPart.substring(1) else leftPart

        var size = 1 // default byte
        var addrStr = cleanLeft

        if (cleanLeft.startsWith("0xH", ignoreCase = true)) {
            size = 1
            addrStr = cleanLeft.substring(3)
        } else if (cleanLeft.startsWith("0xX", ignoreCase = true)) {
            size = 4
            addrStr = cleanLeft.substring(3)
        } else if (cleanLeft.startsWith("0x", ignoreCase = true)) {
            size = 2
            addrStr = cleanLeft.substring(2)
        }

        val parsedAddr = addrStr.toLongOrNull(16) ?: return null
        val finalAddress = when {
            parsedAddr >= 0x02000000L -> parsedAddr
            parsedAddr <= 0x007FFFL -> 0x03000000L + parsedAddr               // IWRAM (0x000000 - 0x007FFF)
            parsedAddr <= 0x047FFFL -> 0x02000000L + (parsedAddr - 0x008000L) // EWRAM (0x008000 - 0x047FFF)
            parsedAddr <= 0x057FFFL -> 0x0E000000L + (parsedAddr - 0x048000L) // SRAM  (0x048000 - 0x057FFF)
            else -> parsedAddr
        }

        val targetVal = if (rightPart.startsWith("0x", ignoreCase = true)) {
            rightPart.substring(2).toLongOrNull(16) ?: 0L
        } else {
            rightPart.toLongOrNull() ?: 0L
        }

        return MemoryCondition(
            size = size,
            address = finalAddress,
            operator = matchedOp,
            targetValue = targetVal,
            isDelta = isDelta
        )
    }

    /**
     * Executado a cada frame do emulador para verificar se alguma conquista foi disparada.
     * Retorna a lista de conquistas completadas neste frame.
     */
    fun evaluateFrame(memory: GbaMemory): List<Achievement> {
        val unlockedNow = mutableListOf<Achievement>()
        val iterator = activeAchievements.iterator()

        while (iterator.hasNext()) {
            val (ach, conditions) = iterator.next()
            var allSatisfied = true

            for (cond in conditions) {
                val currentVal = when (cond.size) {
                    1 -> memory.read8(cond.address).toLong()
                    2 -> memory.read16(cond.address).toLong()
                    4 -> memory.read32(cond.address)
                    else -> memory.read8(cond.address).toLong()
                }

                val checkVal = if (cond.isDelta) {
                    previousMemoryValues[cond.address] ?: currentVal
                } else {
                    currentVal
                }

                val passes = when (cond.operator) {
                    "=" -> checkVal == cond.targetValue
                    "!=" -> checkVal != cond.targetValue
                    ">" -> checkVal > cond.targetValue
                    ">=" -> checkVal >= cond.targetValue
                    "<" -> checkVal < cond.targetValue
                    "<=" -> checkVal <= cond.targetValue
                    else -> false
                }

                if (!passes) {
                    allSatisfied = false
                    break
                }
            }

            if (allSatisfied) {
                unlockedNow.add(ach)
                iterator.remove() // Não avalia novamente após desbloquear
            }
        }

        // Atualizar snapshot anterior para condições 'delta'
        for ((_, conditions) in activeAchievements) {
            for (cond in conditions) {
                val currentVal = when (cond.size) {
                    1 -> memory.read8(cond.address).toLong()
                    2 -> memory.read16(cond.address).toLong()
                    4 -> memory.read32(cond.address)
                    else -> memory.read8(cond.address).toLong()
                }
                previousMemoryValues[cond.address] = currentVal
            }
        }

        return unlockedNow
    }
}
