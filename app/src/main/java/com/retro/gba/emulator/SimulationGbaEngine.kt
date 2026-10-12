package com.retro.gba.emulator

import android.graphics.Bitmap
import com.retro.gba.data.model.Achievement
import kotlinx.coroutines.*
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Motor isolado de simulação gráfica / demonstração técnica.
 * 
 * ATENÇÃO: Esta classe NÃO executa instruções ARM7TDMI nem emula o hardware real do GBA.
 * Ela existe exclusivamente para testes de interface, renderização de tela e validação
 * visual dos controles na ausência de uma ROM comercial ou do núcleo nativo.
 */
class SimulationGbaEngine(
    private val onFrameReady: (Bitmap) -> Unit,
    private val onAchievementTriggered: (Achievement) -> Unit
) : IGbaEngine {

    companion object {
        const val SCREEN_WIDTH = 240
        const val SCREEN_HEIGHT = 160
    }

    override val isSimulationMode: Boolean = true
    override val isRunning: Boolean get() = _isRunning.get()
    override val isPaused: Boolean get() = _isPaused.get()

    private val _isRunning = AtomicBoolean(false)
    private val _isPaused = AtomicBoolean(false)
    private var emulatorJob: Job? = null

    val memory = GbaMemory()
    private val evaluator = AchievementEvaluator()

    private var currentButtons = GbaInput.ALL_RELEASED
    private var speedMultiplier: Float = 1.0f

    // Framebuffer
    private val pixels = IntArray(SCREEN_WIDTH * SCREEN_HEIGHT)
    private var bitmap: Bitmap? = null

    private fun getOrCreateBitmap(): Bitmap? {
        if (bitmap == null) {
            try {
                bitmap = Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888)
            } catch (e: Throwable) {
                // Suporte a testes unitários na JVM
            }
        }
        return bitmap
    }

    // Save states em memória da simulação
    private var savedEwram: ByteArray? = null
    private var savedIwram: ByteArray? = null

    // Game Metadata
    private var romTitle = "MODO SIMULACAO"
    private var romCode = "DEMO"

    // Interactive Player State
    private var playerX = 120
    private var playerY = 75
    private var score = 0

    override fun loadRom(romBytes: ByteArray, achievements: List<Achievement>, saveBytes: ByteArray?): Boolean {
        if (romBytes.isEmpty()) return false
        memory.rom = romBytes
        evaluator.loadAchievements(achievements)

        // Extrai metadados do Header da ROM GBA se disponível
        if (romBytes.size >= 0xB4) {
            val titleBytes = ByteArray(12)
            System.arraycopy(romBytes, 0xA0, titleBytes, 0, 12)
            val extractedTitle = String(titleBytes, StandardCharsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
            if (extractedTitle.isNotEmpty()) {
                romTitle = extractedTitle
            }

            val codeBytes = ByteArray(4)
            System.arraycopy(romBytes, 0xAC, codeBytes, 0, 4)
            val extractedCode = String(codeBytes, StandardCharsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
            if (extractedCode.isNotEmpty()) {
                romCode = extractedCode
            }
        }
        return true
    }

    override fun start() {
        if (_isRunning.get()) return
        _isRunning.set(true)
        _isPaused.set(false)

        emulatorJob = CoroutineScope(Dispatchers.Default).launch {
            var frameCount = 0L
            val targetFrameTimeMs = (1000L / 60L) // 60 FPS (~16.6ms)

            while (_isRunning.get()) {
                if (!_isPaused.get()) {
                    val startTime = System.currentTimeMillis()

                    // Atualiza simulação visual
                    runFrame(frameCount)

                    // Avaliação de conquistas
                    val triggered = evaluator.evaluateFrame(memory)
                    for (ach in triggered) {
                        onAchievementTriggered(ach)
                    }

                    // Renderiza o buffer de vídeo no Bitmap
                    getOrCreateBitmap()?.let { bmp ->
                        bmp.setPixels(pixels, 0, SCREEN_WIDTH, 0, 0, SCREEN_WIDTH, SCREEN_HEIGHT)
                        onFrameReady(bmp)
                    }

                    frameCount++

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

    override fun pause() {
        _isPaused.set(true)
    }

    override fun resume() {
        _isPaused.set(false)
    }

    override fun stop() {
        _isRunning.set(false)
        emulatorJob?.cancel()
    }

    override fun setButtonPressed(buttonMask: Int, pressed: Boolean) {
        currentButtons = if (pressed) {
            currentButtons and buttonMask.inv()
        } else {
            currentButtons or buttonMask
        }
    }

    override fun setSpeedMultiplier(multiplier: Float) {
        speedMultiplier = multiplier
    }

    override fun getSaveRam(): ByteArray? = null

    override fun saveState(): ByteArray? {
        savedEwram = memory.ewram.clone()
        savedIwram = memory.iwram.clone()
        return (savedEwram ?: ByteArray(0)) + (savedIwram ?: ByteArray(0))
    }

    override fun loadState(stateBytes: ByteArray): Boolean {
        if (stateBytes.size < (memory.ewram.size + memory.iwram.size)) return false
        System.arraycopy(stateBytes, 0, memory.ewram, 0, memory.ewram.size)
        System.arraycopy(stateBytes, memory.ewram.size, memory.iwram, 0, memory.iwram.size)
        return true
    }

    override fun readMemory(address: Long, size: Int): Long {
        return when (size) {
            1 -> memory.read8(address).toLong()
            2 -> memory.read16(address).toLong()
            4 -> memory.read32(address)
            else -> memory.read8(address).toLong()
        }
    }

    override fun addCheat(description: String, code: String, type: Int, enabled: Boolean): Boolean {
        return true
    }

    override fun clearCheats() {}

    override fun setCheatEnabled(index: Int, enabled: Boolean) {}

    private fun runFrame(frameCount: Long) {
        // Atualiza chave de controle
        memory.write8(0x04000130L, currentButtons and 0xFF)
        memory.write8(0x04000131L, (currentButtons shr 8) and 0xFF)

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

        memory.write8(0x02000000L, playerX)
        memory.write8(0x02000001L, playerY)
        memory.write16(0x02000002L, score)

        val bgBaseColor = 0xFF0B0E14.toInt()
        val gridColor = 0xFF161C26.toInt()

        val scrollOffset = (frameCount % 64).toInt()
        for (y in 0 until SCREEN_HEIGHT) {
            for (x in 0 until SCREEN_WIDTH) {
                val index = y * SCREEN_WIDTH + x
                val isGridX = (x + scrollOffset) % 24 == 0
                val isGridY = (y + scrollOffset) % 24 == 0
                pixels[index] = if (isGridX || isGridY) gridColor else bgBaseColor
            }
        }

        // Header Superior com aviso explícito de MODO SIMULAÇÃO
        val headerColor = 0xFF2A1515.toInt() // Avermelhado para destacar modo simulação
        val accentLine = 0xFFFF5555.toInt()
        for (y in 0..15) {
            for (x in 0 until SCREEN_WIDTH) {
                pixels[y * SCREEN_WIDTH + x] = if (y == 15) accentLine else headerColor
            }
        }
        drawText("[SIMULACAO] $romTitle", 4, 4, 0xFFFFFFFF.toInt())
        drawText(romCode, 206, 4, 0xFFFFBE0B.toInt())

        // Sprite de demonstração
        val spriteColor = when {
            isAPressed -> 0xFF00FF66.toInt()
            isBPressed -> 0xFFFF2A6D.toInt()
            else -> 0xFF00E5FF.toInt()
        }

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

        val hudBg = 0xFF12101F.toInt()
        for (y in 143 until SCREEN_HEIGHT) {
            for (x in 0 until SCREEN_WIDTH) {
                pixels[y * SCREEN_WIDTH + x] = hudBg
            }
        }

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

    private fun drawButtonIndicator(label: String, x: Int, y: Int, isPressed: Boolean, activeColor: Int) {
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
            '[' -> intArrayOf(0x0E, 0x08, 0x08, 0x08, 0x08, 0x08, 0x0E)
            ']' -> intArrayOf(0x0E, 0x02, 0x02, 0x02, 0x02, 0x02, 0x0E)
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
}
