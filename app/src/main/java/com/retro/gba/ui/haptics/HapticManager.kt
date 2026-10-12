package com.retro.gba.ui.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Gerenciador de Feedback Háptico para os controles virtuais e eventos de jogo.
 * Suporta efeitos táteis precisos via VibrationEffect (Android Oreo e superior)
 * e respeita as preferências de ativação do usuário.
 */
class HapticManager(
    context: Context
) {
    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Feedback tátil para toque de botão comum (A, B, D-Pad, L, R).
     */
    fun performButtonClick(enabled: Boolean = true) {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(12)
            }
        } catch (e: Exception) {
            // Ignora silenciosamente se o dispositivo não permitir vibração no momento
        }
    }

    /**
     * Feedback tátil marcante para alternar Turbo / Fast Forward.
     */
    fun performTurboToggle(enabled: Boolean = true) {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(25, 200))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(25)
            }
        } catch (e: Exception) {
            // Ignora silenciosamente
        }
    }

    /**
     * Feedback comemorativo de desbloqueio de conquista do RetroAchievements (duplo pulso festivo).
     */
    fun performAchievementUnlock(enabled: Boolean = true) {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 40, 60, 80)
                val amplitudes = intArrayOf(0, 180, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 40, 60, 80), -1)
            }
        } catch (e: Exception) {
            // Ignora silenciosamente
        }
    }
}
