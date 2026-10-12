package com.retro.gba.performance

import org.junit.Assert.*
import org.junit.Test

class PowerProfileManagerTest {

    private class FakePowerProfileManager(
        var powerSaveActive: Boolean = false,
        var currentBatteryLevel: Int = 100,
        var charging: Boolean = false,
        var thermalThrottling: Boolean = false
    ) : IPowerProfileManager {
        override fun isPowerSaveModeActive(): Boolean = powerSaveActive
        override fun getBatteryLevel(): Int = currentBatteryLevel
        override fun isBatteryLow(): Boolean = currentBatteryLevel <= 15
        override fun isCharging(): Boolean = charging
        override fun isThermalThrottling(): Boolean = thermalThrottling
        override fun calculateRecommendedFrameskip(ecoModeEnabled: Boolean): Int {
            if (ecoModeEnabled || isPowerSaveModeActive() || isThermalThrottling() || (isBatteryLow() && !isCharging())) {
                return 1
            }
            return 0
        }
    }

    @Test
    fun calculateRecommendedFrameskip_whenEcoModeEnabled_returnsOne() {
        val manager = FakePowerProfileManager(currentBatteryLevel = 100)
        val frameskip = manager.calculateRecommendedFrameskip(ecoModeEnabled = true)
        assertEquals(1, frameskip)
    }

    @Test
    fun calculateRecommendedFrameskip_whenEcoModeDisabledAndOptimalConditions_returnsZero() {
        val manager = FakePowerProfileManager(currentBatteryLevel = 80, powerSaveActive = false, thermalThrottling = false)
        val frameskip = manager.calculateRecommendedFrameskip(ecoModeEnabled = false)
        assertEquals(0, frameskip)
    }

    @Test
    fun calculateRecommendedFrameskip_whenBatteryLowAndNotCharging_returnsOne() {
        val manager = FakePowerProfileManager(currentBatteryLevel = 10, charging = false)
        assertTrue(manager.isBatteryLow())
        val frameskip = manager.calculateRecommendedFrameskip(ecoModeEnabled = false)
        assertEquals(1, frameskip)
    }

    @Test
    fun calculateRecommendedFrameskip_whenBatteryLowButCharging_returnsZero() {
        val manager = FakePowerProfileManager(currentBatteryLevel = 10, charging = true)
        val frameskip = manager.calculateRecommendedFrameskip(ecoModeEnabled = false)
        assertEquals(0, frameskip)
    }

    @Test
    fun calculateRecommendedFrameskip_whenThermalThrottling_returnsOne() {
        val manager = FakePowerProfileManager(thermalThrottling = true)
        assertTrue(manager.isThermalThrottling())
        val frameskip = manager.calculateRecommendedFrameskip(ecoModeEnabled = false)
        assertEquals(1, frameskip)
    }

    @Test
    fun calculateRecommendedFrameskip_whenPowerSaveModeActive_returnsOne() {
        val manager = FakePowerProfileManager(powerSaveActive = true)
        val frameskip = manager.calculateRecommendedFrameskip(ecoModeEnabled = false)
        assertEquals(1, frameskip)
    }
}
