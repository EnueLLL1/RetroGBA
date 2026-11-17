package com.retro.gba.emulator

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Build

/**
 * Reprodutor de áudio PCM de baixa latência baseado em AudioTrack em modo streaming.
 * Consome amostras de som estéreo de 16 bits geradas em tempo real pela APU do mGBA.
 */
class GbaAudioPlayer(
    val sampleRate: Int = DEFAULT_SAMPLE_RATE
) {
    companion object {
        const val DEFAULT_SAMPLE_RATE = 32768
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val trackLock = Any()
    private var audioTrack: AudioTrack? = null
    @Volatile private var isPlaying = false
    @Volatile private var isMuted = false
    @Volatile private var speedMultiplier = 1.0f
    private var bufferSizeFrames = 0
    private var writtenFrames = 0L

    init {
        initAudioTrack()
    }

    private fun initAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ).coerceAtLeast(4096)

            val bufferBytes = minBufferSize * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            bufferSizeFrames = (track.bufferSizeInFrames).takeIf { it > 0 } ?: (bufferBytes / 4)
            writtenFrames = 0L
        } catch (e: Throwable) {
            // Em testes unitários locais na JVM, AudioTrack não está presente no Android framework stub
            audioTrack = null
            bufferSizeFrames = 0
            writtenFrames = 0L
        }
    }

    fun start() {
        synchronized(trackLock) {
            val track = audioTrack ?: return
            try {
                if (track.state == AudioTrack.STATE_INITIALIZED && !isPlaying) {
                    track.play()
                    isPlaying = true
                }
            } catch (e: Throwable) {
                // Tratamento silencioso de transição de estado de áudio
            }
        }
    }

    fun pause() {
        synchronized(trackLock) {
            val track = audioTrack ?: return
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                    writtenFrames = 0L
                    isPlaying = false
                }
            } catch (e: Throwable) {}
        }
    }

    fun resume() {
        start()
    }

    fun stop() {
        synchronized(trackLock) {
            val track = audioTrack ?: return
            try {
                isPlaying = false
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.stop()
                    track.flush()
                }
                writtenFrames = 0L
            } catch (e: Throwable) {}
        }
    }

    fun writeSamples(samples: ShortArray, count: Int) {
        if (!isPlaying || isMuted || count <= 0 || samples.isEmpty()) return
        val safeCount = count.coerceAtMost(samples.size)

        synchronized(trackLock) {
            val track = audioTrack ?: return
            if (!isPlaying || isMuted) return

            try {
                val playbackHead = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                if (playbackHead > writtenFrames) {
                    writtenFrames = playbackHead
                }
                val queuedFrames = (writtenFrames - playbackHead).coerceAtLeast(0L)
                val shortsToWrite = computeWritableShorts(
                    requestedShorts = safeCount,
                    bufferSizeFrames = bufferSizeFrames,
                    queuedFrames = queuedFrames,
                    speedMultiplier = speedMultiplier
                )
                if (shortsToWrite <= 0) {
                    // Buffer cheio (ex.: fast-forward): descarta excesso sem bloquear a thread de emulação
                    return
                }

                val written = track.write(samples, 0, shortsToWrite, AudioTrack.WRITE_NON_BLOCKING)
                if (written > 0) {
                    writtenFrames += (written / 2L)
                }
            } catch (e: Throwable) {}
        }
    }

    fun setSpeedMultiplier(multiplier: Float) {
        speedMultiplier = multiplier.coerceIn(0.5f, 5.0f)
        synchronized(trackLock) {
            val track = audioTrack ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    if (track.state == AudioTrack.STATE_INITIALIZED) {
                        val targetSpeed = multiplier.coerceIn(0.5f, 4.0f)
                        try {
                            track.playbackParams = PlaybackParams().apply {
                                speed = targetSpeed
                            }
                        } catch (_: IllegalArgumentException) {
                            // Alguns drivers Android limitam PlaybackParams a 2.0x; faz fallback seguro
                            track.playbackParams = PlaybackParams().apply {
                                speed = targetSpeed.coerceIn(0.5f, 2.0f)
                            }
                        }
                    }
                } catch (e: Throwable) {}
            }
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun release() {
        stop()
        synchronized(trackLock) {
            try {
                audioTrack?.release()
            } catch (e: Throwable) {}
            audioTrack = null
            bufferSizeFrames = 0
            writtenFrames = 0L
        }
    }

    internal fun computeWritableShorts(
        requestedShorts: Int,
        bufferSizeFrames: Int,
        queuedFrames: Long,
        speedMultiplier: Float = 1.0f
    ): Int {
        val alignedRequested = requestedShorts and 1.inv()
        if (alignedRequested <= 0 || bufferSizeFrames <= 0) return 0

        // Durante fast-forward mantém margem de segurança no buffer para evitar estalos e atraso acumulado
        val targetMaxFrames = if (speedMultiplier > 1.25f) {
            ((bufferSizeFrames * 3L) / 4L).coerceAtLeast(1L)
        } else {
            bufferSizeFrames.toLong()
        }

        val availableFrames = (targetMaxFrames - queuedFrames.coerceAtLeast(0L)).coerceAtLeast(0L)
        val maxWritableShorts = (availableFrames * 2L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return minOf(alignedRequested, maxWritableShorts)
    }
}
