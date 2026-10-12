package com.retro.gba.performance

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

interface IPowerProfileManager {
    fun isPowerSaveModeActive(): Boolean
    fun getBatteryLevel(): Int
    fun isBatteryLow(): Boolean
    fun isCharging(): Boolean
    fun isThermalThrottling(): Boolean
    fun calculateRecommendedFrameskip(ecoModeEnabled: Boolean): Int
}

/**
 * Gerenciador de perfil térmico e economia de bateria.
 * Monitora o nível de carga, status de economia de energia e estrangulamento térmico (thermal throttling)
 * para sugerir ou aplicar otimizações adaptativas como Frameskip e conservação de recursos.
 */
class PowerProfileManager(
    private val context: Context
) : IPowerProfileManager {

    private val powerManager: PowerManager? = try {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    } catch (e: Exception) {
        null
    }

    override fun isPowerSaveModeActive(): Boolean {
        return try {
            powerManager?.isPowerSaveMode ?: false
        } catch (e: Exception) {
            false
        }
    }

    override fun getBatteryLevel(): Int {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, filter) ?: return 100
            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                ((level.toFloat() / scale.toFloat()) * 100).toInt()
            } else {
                100
            }
        } catch (e: Exception) {
            100
        }
    }

    override fun isBatteryLow(): Boolean {
        return getBatteryLevel() <= 15
    }

    override fun isCharging(): Boolean {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, filter) ?: return false
            val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } catch (e: Exception) {
            false
        }
    }

    override fun isThermalThrottling(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return try {
                val status = powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
                status >= PowerManager.THERMAL_STATUS_SEVERE
            } catch (e: Exception) {
                false
            }
        }
        return false
    }

    override fun calculateRecommendedFrameskip(ecoModeEnabled: Boolean): Int {
        if (ecoModeEnabled || isPowerSaveModeActive() || isThermalThrottling() || (isBatteryLow() && !isCharging())) {
            return 1 // Reduz carga da GPU pela metade
        }
        return 0 // 60 FPS normal
    }
}
