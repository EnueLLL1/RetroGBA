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

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var isMuted = false

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

            audioTrack = AudioTrack.Builder()
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
                .setBufferSizeInBytes(minBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Throwable) {
            // Em testes unitários locais na JVM, AudioTrack não está presente no Android framework stub
            audioTrack = null
        }
    }

    fun start() {
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

    fun pause() {
        val track = audioTrack ?: return
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
                track.flush()
                isPlaying = false
            }
        } catch (e: Throwable) {}
    }

    fun resume() {
        start()
    }

    fun stop() {
        val track = audioTrack ?: return
        try {
            isPlaying = false
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.stop()
                track.flush()
            }
        } catch (e: Throwable) {}
    }

    fun writeSamples(samples: ShortArray, count: Int) {
        val track = audioTrack ?: return
        if (!isPlaying || isMuted || count <= 0) return

        try {
            track.write(samples, 0, count, AudioTrack.WRITE_NON_BLOCKING)
        } catch (e: Throwable) {}
    }

    fun setSpeedMultiplier(multiplier: Float) {
        val track = audioTrack ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    val params = PlaybackParams().apply {
                        speed = multiplier.coerceIn(0.5f, 4.0f)
                    }
                    track.playbackParams = params
                }
            } catch (e: Throwable) {}
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun release() {
        stop()
        try {
            audioTrack?.release()
        } catch (e: Throwable) {}
        audioTrack = null
    }
}
