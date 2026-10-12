package com.retro.gba.emulator

import android.graphics.Bitmap
import com.retro.gba.data.model.Achievement
import kotlinx.coroutines.*
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class GbaCore(
    private val onFrameReady: (Bitmap) -> Unit,
    private val onAchievementTriggered: (Achievement) -> Unit
) {
    companion object {
        const val SCREEN_WIDTH = 240
        const val SCREEN_HEIGHT = 160
    }

    val memory = GbaMemory()
    private val evaluator = AchievementEvaluator()

    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private var emulatorJob: Job? = null

    private var currentButtons = GbaInput.ALL_RELEASED
    var speedMultiplier: Float = 1.0f

    // Framebuffer
    private val pixels = IntArray(SCREEN_WIDTH * SCREEN_HEIGHT)
    private val bitmap = Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888)

    // Save states em memória
    private var savedEwram: ByteArray? = null
    private var savedIwram: ByteArray? = null

    // Game Metadata
    private var romTitle = "GAMEBOY ADVANCE"
    private var romCode = "GBA"

    // Interactive Player State
    private var playerX = 120
    private var playerY = 75
    private var score = 0

    fun loadRom(romData: ByteArray, achievements: List<Achievement> = emptyList()) {
        memory.rom = romData
        evaluator.loadAchievements(achievements)

        // Extrai metadados do Header da ROM GBA se disponível
        if (romData.size >= 0xB4) {
            val titleBytes = ByteArray(12)
            System.arraycopy(romData, 0xA0, titleBytes, 0, 12)
            val extractedTitle = String(titleBytes, StandardCharsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
            if (extractedTitle.isNotEmpty()) {
                romTitle = extractedTitle
            }

            val codeBytes = ByteArray(4)
            System.arraycopy(romData, 0xAC, codeBytes, 0, 4)
            val extractedCode = String(codeBytes, StandardCharsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
            if (extractedCode.isNotEmpty()) {
                romCode = extractedCode
            }
        }
    }

    fun start() {
        if (isRunning.get()) return
        isRunning.set(true)
        isPaused.set(false)

        emulatorJob = CoroutineScope(Dispatchers.Default).launch {
            var frameCount = 0L
            val targetFrameTimeMs = (1000L / 60L) // 60 FPS (~16.6ms)

            while (isRunning.get()) {
                if (!isPaused.get()) {
                    val startTime = System.currentTimeMillis()

                    // Atualiza simulação e renderiza o frame
                    runFrame(frameCount)

                    // Avalia conquistas do RetroAchievements em tempo real!
                    val triggered = evaluator.evaluateFrame(memory)
                    for (ach in triggered) {
                        onAchievementTriggered(ach)
                    }

                    // Renderiza o buffer de vídeo no Bitmap
                    bitmap.setPixels(pixels, 0, SCREEN_WIDTH, 0, 0, SCREEN_WIDTH, SCREEN_HEIGHT)
                    onFrameReady(bitmap)

                    frameCount++

                    // Controle de timing considerando o acelerador (Turbo)
                    val elapsed = System.currentTimeMillis() - startTime
                    val delayTime = ((targetFrameTimeMs / speedMultiplier) - elapsed).coerceAtLeast(0f).toLong()
                    if (delayTime > 0) {
                        delay(delayTime)
                    }
                } else {
                    delay(50)
                }
            }
        }
    }

    private fun runFrame(frameCount: Long) {
        // 1. Processa entrada do jogador e atualiza registrador KEYINPUT (0x04000130)
        memory.write8(0x04000130L, currentButtons and 0xFF)
        memory.write8(0x04000131L, (currentButtons shr 8) and 0xFF)

        // Movimentação com D-Pad
        val isLeftPressed = (currentButtons and GbaInput.BUTTON_LEFT) == 0
        val isRightPressed = (currentButtons and GbaInput.BUTTON_RIGHT) == 0
        val isUpPressed = (currentButtons and GbaInput.BUTTON_UP) == 0
        val isDownPressed = (currentButtons and GbaInput.BUTTON_DOWN) == 0
        val isAPressed = (currentButtons and GbaInput.BUTTON_A) == 0
        val isBPressed = (currentButtons and GbaInput.BUTTON_B) == 0

        if (isLeftPressed && playerX > 14) playerX -= 2
        if (isRightPressed && playerX < 226) playerX += 2
        if (isUpPressed && playerY > 24) playerY -= 2
        if (isDownPressed && playerY < 130) playerY += 2

        if (isAPressed && frameCount % 6L == 0L) score += 10
        if (isBPressed && frameCount % 6L == 0L) score += 5

        // Escreve estado na memória EWRAM para os monitores de conquistas
        memory.write8(0x02000000L, playerX)
        memory.write8(0x02000001L, playerY)
        memory.write16(0x02000002L, score)

        // 2. Renderização gráfica do display 240x160
        val bgBaseColor = 0xFF0B0E14.toInt()
        val gridColor = 0xFF161C26.toInt()

        // Fundo estrelado/grid com scroll
        val scrollOffset = (frameCount % 64).toInt()
        for (y in 0 until SCREEN_HEIGHT) {
            for (x in 0 until SCREEN_WIDTH) {
                val index = y * SCREEN_WIDTH + x
                val isGridX = (x + scrollOffset) % 24 == 0
                val isGridY = (y + scrollOffset) % 24 == 0
                pixels[index] = if (isGridX || isGridY) gridColor else bgBaseColor
            }
        }

        // 3. Renderiza Barra de Título Superior (0..15px)
        val headerColor = 0xFF181528.toInt()
        val accentLine = 0xFF00E5FF.toInt()
        for (y in 0..15) {
            for (x in 0 until SCREEN_WIDTH) {
                pixels[y * SCREEN_WIDTH + x] = if (y == 15) accentLine else headerColor
            }
        }
        drawText(romTitle, 6, 4, 0xFFFFFFFF.toInt())
        drawText(romCode, 210, 4, 0xFF00E5FF.toInt())

        // 4. Renderiza o Personagem / Sprite (12x12)
        val spriteColor = when {
            isAPressed -> 0xFF00FF66.toInt() // Verde se A pressionado
            isBPressed -> 0xFFFF2A6D.toInt() // Vermelho se B pressionado
            else -> 0xFF00E5FF.toInt()        // Cyan padrão
        }

        // Desenha sombra do sprite
        for (sy in -2..2) {
            for (sx in -8..8) {
                val px = playerX + sx
                val py = playerY + 8 + sy
                if (px in 0 until SCREEN_WIDTH && py in 16 until 142) {
                    pixels[py * SCREEN_WIDTH + px] = 0xFF05070A.toInt()
                }
            }
        }

        // Desenha corpo do sprite
        for (sy in -6..6) {
            for (sx in -6..6) {
                val px = playerX + sx
                val py = playerY + sy
                if (px in 0 until SCREEN_WIDTH && py in 16 until 142) {
                    val dist = sx * sx + sy * sy
                    if (dist <= 36) {
                        pixels[py * SCREEN_WIDTH + px] = spriteColor
                    }
                }
            }
        }

        // Olhos do sprite
        val eyeColor = 0xFF000000.toInt()
        if (playerX - 2 in 0 until SCREEN_WIDTH && playerY - 2 in 16 until 142) {
            pixels[(playerY - 2) * SCREEN_WIDTH + (playerX - 2)] = eyeColor
            pixels[(playerY - 2) * SCREEN_WIDTH + (playerX + 2)] = eyeColor
        }

        // Efeito visual de pulso se A ou B for pressionado
        if (isAPressed || isBPressed) {
            val ringRadius = ((frameCount % 10) * 2 + 8).toInt()
            var angle = 0
            while (angle <= 360) {
                val rad = Math.toRadians(angle.toDouble())
                val rx = playerX + (Math.cos(rad) * ringRadius).toInt()
                val ry = playerY + (Math.sin(rad) * ringRadius).toInt()
                if (rx in 0 until SCREEN_WIDTH && ry in 16 until 142) {
                    pixels[ry * SCREEN_WIDTH + rx] = spriteColor
                }
                angle += 15
            }
        }

        // 5. Renderiza Dashboard de Controles na parte inferior (143..159px)
        val hudBg = 0xFF12101F.toInt()
        for (y in 143 until SCREEN_HEIGHT) {
            for (x in 0 until SCREEN_WIDTH) {
                pixels[y * SCREEN_WIDTH + x] = hudBg
            }
        }

        // Indicadores dos botões no display
        drawButtonIndicator("L", 8, 146, (currentButtons and GbaInput.BUTTON_L) == 0, 0xFF9D4EDD.toInt())
        drawButtonIndicator("R", 26, 146, (currentButtons and GbaInput.BUTTON_R) == 0, 0xFF9D4EDD.toInt())
        drawButtonIndicator("◄", 50, 146, isLeftPressed, 0xFF00E5FF.toInt())
        drawButtonIndicator("▲", 62, 146, isUpPressed, 0xFF00E5FF.toInt())
        drawButtonIndicator("▼", 74, 146, isDownPressed, 0xFF00E5FF.toInt())
        drawButtonIndicator("►", 86, 146, isRightPressed, 0xFF00E5FF.toInt())
        drawButtonIndicator("SL", 110, 146, (currentButtons and GbaInput.BUTTON_SELECT) == 0, 0xFFFFBE0B.toInt())
        drawButtonIndicator("ST", 130, 146, (currentButtons and GbaInput.BUTTON_START) == 0, 0xFFFFBE0B.toInt())
        drawButtonIndicator("B", 165, 146, isBPressed, 0xFFFF2A6D.toInt())
        drawButtonIndicator("A", 185, 146, isAPressed, 0xFF00FF66.toInt())
    }

    private fun drawButtonIndicator(label: String, x: Int, y: Int, isPressed: Boolean, activeColor: ColorInt) {
        val bgColor = if (isPressed) activeColor else 0xFF2A2640.toInt()
        val textColor = if (isPressed) 0xFF000000.toInt() else 0xFF8A85A5.toInt()

        for (by in 0..10) {
            for (bx in 0..14) {
                val px = x + bx
                val py = y + by
                if (px in 0 until SCREEN_WIDTH && py in 0 until SCREEN_HEIGHT) {
                    pixels[py * SCREEN_WIDTH + px] = bgColor
                }
            }
        }
        drawText(label, x + 2, y + 2, textColor)
    }

    private fun drawText(text: String, startX: Int, startY: Int, color: Int) {
        var cx = startX
        for (char in text.uppercase(Locale.ROOT)) {
            val glyph = getMiniFontGlyph(char)
            for (row in 0 until 7) {
                val rowData = glyph[row]
                for (col in 0 until 5) {
                    if ((rowData and (1 shl (4 - col))) != 0) {
                        val px = cx + col
                        val py = startY + row
                        if (px in 0 until SCREEN_WIDTH && py in 0 until SCREEN_HEIGHT) {
                            pixels[py * SCREEN_WIDTH + px] = color
                        }
                    }
                }
            }
            cx += 6
            if (cx >= SCREEN_WIDTH - 6) break
        }
    }

    private fun getMiniFontGlyph(char: Char): IntArray {
        return when (char) {
            'A' -> intArrayOf(0x0E, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11)
            'B' -> intArrayOf(0x1E, 0x11, 0x11, 0x1E, 0x11, 0x11, 0x1E)
            'C' -> intArrayOf(0x0E, 0x11, 0x10, 0x10, 0x10, 0x11, 0x0E)
            'D' -> intArrayOf(0x1C, 0x12, 0x11, 0x11, 0x11, 0x12, 0x1C)
            'E' -> intArrayOf(0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x1F)
            'F' -> intArrayOf(0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x10)
            'G' -> intArrayOf(0x0E, 0x11, 0x10, 0x13, 0x11, 0x11, 0x0F)
            'H' -> intArrayOf(0x11, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11)
            'I' -> intArrayOf(0x0E, 0x04, 0x04, 0x04, 0x04, 0x04, 0x0E)
            'J' -> intArrayOf(0x1F, 0x02, 0x02, 0x02, 0x02, 0x12, 0x0C)
            'K' -> intArrayOf(0x11, 0x12, 0x14, 0x18, 0x14, 0x12, 0x11)
            'L' -> intArrayOf(0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x1F)
            'M' -> intArrayOf(0x11, 0x1B, 0x15, 0x11, 0x11, 0x11, 0x11)
            'N' -> intArrayOf(0x11, 0x11, 0x19, 0x15, 0x13, 0x11, 0x11)
            'O' -> intArrayOf(0x0E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E)
            'P' -> intArrayOf(0x1E, 0x11, 0x11, 0x1E, 0x10, 0x10, 0x10)
            'Q' -> intArrayOf(0x0E, 0x11, 0x11, 0x11, 0x15, 0x12, 0x0D)
            'R' -> intArrayOf(0x1E, 0x11, 0x11, 0x1E, 0x14, 0x12, 0x11)
            'S' -> intArrayOf(0x0F, 0x10, 0x10, 0x0E, 0x01, 0x01, 0x1E)
            'T' -> intArrayOf(0x1F, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04)
            'U' -> intArrayOf(0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E)
            'V' -> intArrayOf(0x11, 0x11, 0x11, 0x11, 0x11, 0x0A, 0x04)
            'W' -> intArrayOf(0x11, 0x11, 0x11, 0x15, 0x15, 0x1B, 0x11)
            'X' -> intArrayOf(0x11, 0x11, 0x0A, 0x04, 0x0A, 0x11, 0x11)
            'Y' -> intArrayOf(0x11, 0x11, 0x0A, 0x04, 0x04, 0x04, 0x04)
            'Z' -> intArrayOf(0x1F, 0x01, 0x02, 0x04, 0x08, 0x10, 0x1F)
            '0' -> intArrayOf(0x0E, 0x11, 0x13, 0x15, 0x19, 0x11, 0x0E)
            '1' -> intArrayOf(0x04, 0x0C, 0x04, 0x04, 0x04, 0x04, 0x0E)
            '2' -> intArrayOf(0x0E, 0x11, 0x01, 0x02, 0x04, 0x08, 0x1F)
            '3' -> intArrayOf(0x1F, 0x02, 0x04, 0x02, 0x01, 0x11, 0x0E)
            '4' -> intArrayOf(0x02, 0x06, 0x0A, 0x12, 0x1F, 0x02, 0x02)
            '5' -> intArrayOf(0x1F, 0x10, 0x1E, 0x01, 0x01, 0x11, 0x0E)
            '6' -> intArrayOf(0x06, 0x08, 0x10, 0x1E, 0x11, 0x11, 0x0E)
            '7' -> intArrayOf(0x1F, 0x01, 0x02, 0x04, 0x08, 0x08, 0x08)
            '8' -> intArrayOf(0x0E, 0x11, 0x11, 0x0E, 0x11, 0x11, 0x0E)
            '9' -> intArrayOf(0x0E, 0x11, 0x11, 0x0F, 0x01, 0x02, 0x0C)
            '◄' -> intArrayOf(0x02, 0x06, 0x0E, 0x1E, 0x0E, 0x06, 0x02)
            '►' -> intArrayOf(0x08, 0x0C, 0x0E, 0x0F, 0x0E, 0x0C, 0x08)
            '▲' -> intArrayOf(0x04, 0x0E, 0x1F, 0x00, 0x00, 0x00, 0x00)
            '▼' -> intArrayOf(0x00, 0x00, 0x00, 0x00, 0x1F, 0x0E, 0x04)
            else -> intArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        }
    }

    fun setButtonPressed(buttonMask: Int, pressed: Boolean) {
        currentButtons = if (pressed) {
            currentButtons and buttonMask.inv()
        } else {
            currentButtons or buttonMask
        }
    }

    fun pause() {
        isPaused.set(true)
    }

    fun resume() {
        isPaused.set(false)
    }

    fun stop() {
        isRunning.set(false)
        emulatorJob?.cancel()
    }

    fun saveState(): Boolean {
        savedEwram = memory.ewram.clone()
        savedIwram = memory.iwram.clone()
        return true
    }

    fun loadState(): Boolean {
        if (savedEwram == null || savedIwram == null) return false
        System.arraycopy(savedEwram!!, 0, memory.ewram, 0, memory.ewram.size)
        System.arraycopy(savedIwram!!, 0, memory.iwram, 0, memory.iwram.size)
        return true
    }
}

// Alias de conveniência para tipo ARGB no Kotlin
private typealias ColorInt = Int
