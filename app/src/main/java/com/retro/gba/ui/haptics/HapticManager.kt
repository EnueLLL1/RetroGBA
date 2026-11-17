package com.retro.gba.ui.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Gerenciador de Feedback Háptico não-bloqueante para controles virtuais e eventos de jogo.
 * Faz cache de Vibrator, estado hasVibrator e VibrationEffects pré-alocados, aplicando
 * cooldown curto e despacho assíncrono para evitar jank por chamadas Binder na UI thread.
 */
class HapticManager(
    context: Context
) {
    private val appContext = context.applicationContext ?: context

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    @Volatile
    private var cachedHasVibrator: Boolean? = null

    private val clickEffect: VibrationEffect? = createClickEffect()
    private val turboEffect: VibrationEffect? = createTurboEffect()
    private val achievementEffect: VibrationEffect? = createAchievementEffect()

    private val lastClickNs = AtomicLong(0L)

    /**
     * Feedback tátil rápido via View (sem Binder síncrono no fluxo de toque) com fallback em cache.
     */
    fun performButtonClick(view: View?, enabled: Boolean = true) {
        if (!enabled) return
        val now = System.nanoTime()
        val prev = lastClickNs.get()
        if (now - prev < CLICK_COOLDOWN_NS) return
        if (!lastClickNs.compareAndSet(prev, now)) return

        if (view != null) {
            try {
                if (view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)) {
                    return
                }
            } catch (_: Exception) {
                // Segue para fallback assíncrono
            }
        }
        dispatchVibration(clickEffect, legacyDurationMs = 10L)
    }

    /**
     * Feedback tátil não-bloqueante para toque de botão comum (A, B, D-Pad, L, R).
     */
    fun performButtonClick(enabled: Boolean = true) {
        if (!enabled || vibrator == null) return
        val now = System.nanoTime()
        val prev = lastClickNs.get()
        if (now - prev < CLICK_COOLDOWN_NS) return
        if (!lastClickNs.compareAndSet(prev, now)) return

        dispatchVibration(clickEffect, legacyDurationMs = 10L)
    }

    /**
     * Feedback tátil marcante para alternar Turbo / Fast Forward.
     */
    fun performTurboToggle(enabled: Boolean = true) {
        if (!enabled || vibrator == null) return
        dispatchVibration(turboEffect, legacyDurationMs = 22L)
    }

    /**
     * Feedback comemorativo de desbloqueio de conquista do RetroAchievements (duplo pulso festivo).
     */
    fun performAchievementUnlock(enabled: Boolean = true) {
        if (!enabled || vibrator == null) return
        dispatchWaveform(
            effect = achievementEffect,
            timings = ACHIEVEMENT_TIMINGS
        )
    }

    private fun dispatchVibration(effect: VibrationEffect?, legacyDurationMs: Long) {
        val vib = vibrator ?: return
        hapticExecutor.execute {
            try {
                if (!checkHasVibrator(vib)) return@execute
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && effect != null) {
                    vib.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(legacyDurationMs)
                }
            } catch (_: Exception) {
                // Ignora silenciosamente se o dispositivo não permitir vibração no momento
            }
        }
    }

    private fun dispatchWaveform(effect: VibrationEffect?, timings: LongArray) {
        val vib = vibrator ?: return
        hapticExecutor.execute {
            try {
                if (!checkHasVibrator(vib)) return@execute
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && effect != null) {
                    vib.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(timings, -1)
                }
            } catch (_: Exception) {
                // Ignora silenciosamente
            }
        }
    }

    private fun checkHasVibrator(vib: Vibrator): Boolean {
        cachedHasVibrator?.let { return it }
        val result = try {
            vib.hasVibrator()
        } catch (_: Exception) {
            false
        }
        cachedHasVibrator = result
        return result
    }

    private fun createClickEffect(): VibrationEffect? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createOneShot(10L, VibrationEffect.DEFAULT_AMPLITUDE)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    private fun createTurboEffect(): VibrationEffect? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createOneShot(22L, 200)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    private fun createAchievementEffect(): VibrationEffect? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createWaveform(ACHIEVEMENT_TIMINGS, ACHIEVEMENT_AMPLITUDES, -1)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val CLICK_COOLDOWN_NS = 18_000_000L // 18ms anti-spam no arraste de polegar
        private val ACHIEVEMENT_TIMINGS = longArrayOf(0L, 40L, 60L, 80L)
        private val ACHIEVEMENT_AMPLITUDES = intArrayOf(0, 180, 0, 255)

        private val hapticExecutor = ThreadPoolExecutor(
            1,
            1,
            30L,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(4),
            ThreadFactory { r ->
                Thread(r, "retrogba-haptics").apply { isDaemon = true }
            },
            ThreadPoolExecutor.DiscardOldestPolicy()
        )
    }
}
