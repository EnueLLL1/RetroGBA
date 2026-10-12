package com.retro.gba.emulator

import android.graphics.Bitmap
import com.retro.gba.data.model.Achievement
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Motor nativo real de emulação do Game Boy Advance baseado no core mGBA (C/C++).
 * Integra o rasterizador de vídeo double-buffered, áudio PCM de baixa latência e a
 * biblioteca oficial rcheevos para avaliação legítima de condições de conquistas.
 */
class NativeGbaEngine(
    private val onFrameReady: (Bitmap) -> Unit,
    private val onAchievementTriggered: (Achievement) -> Unit
) : IGbaEngine {

    companion object {
        const val SCREEN_WIDTH = 240
        const val SCREEN_HEIGHT = 160
        private const val GBA_FPS = 59.7275
        private const val AUDIO_CHUNK_SIZE = 2048

        private var isNativeLibLoaded = false

        init {
            try {
                System.loadLibrary("retrogba")
                isNativeLibLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                isNativeLibLoaded = false
            }
        }

        fun isNativeAvailable(): Boolean = isNativeLibLoaded
    }

    override val isSimulationMode: Boolean = false
    override val isRunning: Boolean get() = _isRunning.get()
    override val isPaused: Boolean get() = _isPaused.get()

    private val _isRunning = AtomicBoolean(false)
    private val _isPaused = AtomicBoolean(false)
    private var emulationJob: Job? = null

    private var speedMultiplier: Float = 1.0f
    private var currentButtons: Int = GbaInput.ALL_RELEASED

    // Double buffering de bitmaps para evitar novas alocações e garantir recomposição reativa no Compose
    private val frameBuffers: Array<Bitmap?> = arrayOfNulls(2)
    private var activeBufferIndex = 0

    private var directVideoBuffer: ByteBuffer? = null
    private var audioPlayer: GbaAudioPlayer? = null
    private val audioSampleBuffer = ShortArray(AUDIO_CHUNK_SIZE)

    // Mapa de conquistas ativas monitoradas pelo rcheevos
    private val activeAchievementsMap = ConcurrentHashMap<Long, Achievement>()

    init {
        initBitmaps()
    }

    private fun initBitmaps() {
        try {
            frameBuffers[0] = Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888)
            frameBuffers[1] = Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888)
        } catch (e: Throwable) {
            // Em testes unitários locais JVM puro, Bitmap nativo pode ser stubbed
        }
    }

    override fun loadRom(romBytes: ByteArray, achievements: List<Achievement>, saveBytes: ByteArray?): Boolean {
        if (!isNativeLibLoaded) return false
        val validation = com.retro.gba.security.RomSecurityValidator.validateRomBytes(romBytes)
        if (validation is com.retro.gba.security.RomSecurityValidator.ValidationResult.Invalid) {
            return false
        }

        val loaded = nativeLoadRom(romBytes, romBytes.size)
        if (!loaded) return false

        saveBytes?.let {
            if (it.isNotEmpty()) {
                nativeLoadSaveRam(it)
            }
        }

        directVideoBuffer = nativeGetVideoBuffer()

        // 1. Inicializa ou reconfigura reprodutor de áudio
        val sampleRate = nativeGetAudioSampleRate().takeIf { it > 0 } ?: GbaAudioPlayer.DEFAULT_SAMPLE_RATE
        audioPlayer?.release()
        audioPlayer = GbaAudioPlayer(sampleRate)

        // 2. Registra as conquistas no motor oficial rcheevos
        nativeClearAchievements()
        activeAchievementsMap.clear()

        achievements.filter { !it.isUnlocked && !it.memAddr.isNullOrBlank() }.forEach { ach ->
            val conditions = ach.memAddr ?: return@forEach
            val activated = nativeActivateAchievement(ach.id, conditions)
            if (activated) {
                activeAchievementsMap[ach.id] = ach
            }
        }

        return true
    }

    override fun start() {
        if (!isNativeLibLoaded) return
        if (_isRunning.get()) return
        _isRunning.set(true)
        _isPaused.set(false)

        audioPlayer?.start()

        emulationJob = CoroutineScope(Dispatchers.Default).launch {
            val frameTimeNs = (1_000_000_000.0 / GBA_FPS).toLong()

            while (_isRunning.get()) {
                if (!_isPaused.get()) {
                    val frameStartNs = System.nanoTime()

                    // 1. Executa instruções ARM7TDMI e rasterização PPU/APU para 1 frame + rcheevos
                    nativeRunFrame()

                    // 2. Renderização de vídeo via DirectBuffer com alternância de double-buffering
                    directVideoBuffer?.let { buffer ->
                        buffer.rewind()
                        val bmp = frameBuffers[activeBufferIndex]
                        activeBufferIndex = 1 - activeBufferIndex

                        if (bmp != null) {
                            bmp.copyPixelsFromBuffer(buffer)
                            onFrameReady(bmp)
                        }
                    }

                    // 3. Drena amostras de áudio PCM sintetizadas e envia ao AudioTrack
                    audioPlayer?.let { player ->
                        val readSamples = nativeGetAudioSamples(audioSampleBuffer, audioSampleBuffer.size)
                        if (readSamples > 0) {
                            player.writeSamples(audioSampleBuffer, readSamples)
                        }
                    }

                    // 4. Verifica se o rcheevos disparou o desbloqueio de alguma conquista
                    val triggeredIds = nativeGetTriggeredAchievements()
                    if (triggeredIds != null && triggeredIds.isNotEmpty()) {
                        for (id in triggeredIds) {
                            activeAchievementsMap[id]?.let { ach ->
                                onAchievementTriggered(ach)
                            }
                        }
                    }

                    // 5. Temporização precisa baseada em nanosegundos
                    val elapsedNs = System.nanoTime() - frameStartNs
                    val targetNs = (frameTimeNs / speedMultiplier).toLong()
                    val sleepNs = targetNs - elapsedNs

                    if (sleepNs > 1_000_000L) { // Mais de 1ms restante
                        delay(sleepNs / 1_000_000L)
                    }
                } else {
                    delay(50)
                }
            }
        }
    }

    override fun pause() {
        _isPaused.set(true)
        audioPlayer?.pause()
    }

    override fun resume() {
        _isPaused.set(false)
        audioPlayer?.resume()
    }

    override fun stop() {
        _isRunning.set(false)
        emulationJob?.cancel()
        audioPlayer?.stop()
        audioPlayer?.release()
        audioPlayer = null
        activeAchievementsMap.clear()

        if (isNativeLibLoaded) {
            nativeDestroy()
        }
    }

    override fun setButtonPressed(buttonMask: Int, pressed: Boolean) {
        currentButtons = if (pressed) {
            currentButtons and buttonMask.inv()
        } else {
            currentButtons or buttonMask
        }
        if (isNativeLibLoaded) {
            nativeSetButtons(currentButtons)
        }
    }

    override fun setSpeedMultiplier(multiplier: Float) {
        speedMultiplier = multiplier
        audioPlayer?.setSpeedMultiplier(multiplier)
    }

    override fun getSaveRam(): ByteArray? {
        if (!isNativeLibLoaded) return null
        return nativeGetSaveRam()
    }

    override fun saveState(): ByteArray? {
        if (!isNativeLibLoaded) return null
        return nativeSaveState()
    }

    override fun loadState(stateBytes: ByteArray): Boolean {
        if (!isNativeLibLoaded || stateBytes.isEmpty()) return false
        return nativeLoadState(stateBytes)
    }

    override fun readMemory(address: Long, size: Int): Long {
        if (!isNativeLibLoaded) return 0L
        return nativeReadMemory(address, size)
    }

    override fun addCheat(description: String, code: String, type: Int, enabled: Boolean): Boolean {
        if (!isNativeLibLoaded) return false
        return nativeAddCheat(description, code, type, enabled)
    }

    override fun clearCheats() {
        if (isNativeLibLoaded) {
            nativeClearCheats()
        }
    }

    override fun setCheatEnabled(index: Int, enabled: Boolean) {
        if (isNativeLibLoaded) {
            nativeSetCheatEnabled(index, enabled)
        }
    }

    // Declarações JNI nativas da ponte C++
    private external fun nativeLoadRom(romBytes: ByteArray, size: Int): Boolean
    private external fun nativeRunFrame()
    private external fun nativeSetButtons(buttons: Int)
    private external fun nativeGetVideoBuffer(): ByteBuffer?
    private external fun nativeGetAudioSamples(buffer: ShortArray, maxSamples: Int): Int
    private external fun nativeGetAudioSampleRate(): Int
    private external fun nativeActivateAchievement(id: Long, memaddr: String): Boolean
    private external fun nativeDeactivateAchievement(id: Long)
    private external fun nativeClearAchievements()
    private external fun nativeGetTriggeredAchievements(): LongArray?
    private external fun nativeGetSaveRam(): ByteArray?
    private external fun nativeLoadSaveRam(saveBytes: ByteArray): Boolean
    private external fun nativeSaveState(): ByteArray?
    private external fun nativeLoadState(stateBytes: ByteArray): Boolean
    private external fun nativeReadMemory(address: Long, size: Int): Long
    private external fun nativeAddCheat(description: String, code: String, type: Int, enabled: Boolean): Boolean
    private external fun nativeClearCheats()
    private external fun nativeSetCheatEnabled(index: Int, enabled: Boolean)
    private external fun nativeDestroy()
}
