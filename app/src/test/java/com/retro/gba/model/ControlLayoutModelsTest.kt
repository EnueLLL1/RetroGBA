package com.retro.gba.model

import com.retro.gba.emulator.GbaInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlLayoutModelsTest {

    @Test
    fun virtualControlTypesHaveExactExpectedEntriesMasksAndSpecialFlags() {
        val expectedNames = setOf(
            "DPAD",
            "BUTTON_A",
            "BUTTON_B",
            "BUTTON_L",
            "BUTTON_R",
            "BUTTON_START",
            "BUTTON_SELECT",
            "BUTTON_MENU",
            "BUTTON_FAST_FORWARD",
            "COMBO_AB",
            "COMBO_LR",
            "COMBO_LA",
            "COMBO_LB",
            "COMBO_RA",
            "COMBO_RB",
            "QUICK_SAVE",
            "QUICK_LOAD"
        )
        assertEquals(expectedNames, VirtualControlType.entries.map { it.name }.toSet())

        assertEquals(0, VirtualControlType.DPAD.gbaButtonMask)
        assertFalse(VirtualControlType.DPAD.isSpecialAction)

        assertEquals(GbaInput.BUTTON_A, VirtualControlType.BUTTON_A.gbaButtonMask)
        assertEquals(GbaInput.BUTTON_B, VirtualControlType.BUTTON_B.gbaButtonMask)
        assertEquals(GbaInput.BUTTON_L, VirtualControlType.BUTTON_L.gbaButtonMask)
        assertEquals(GbaInput.BUTTON_R, VirtualControlType.BUTTON_R.gbaButtonMask)
        assertEquals(GbaInput.BUTTON_START, VirtualControlType.BUTTON_START.gbaButtonMask)
        assertEquals(GbaInput.BUTTON_SELECT, VirtualControlType.BUTTON_SELECT.gbaButtonMask)

        assertEquals(GbaInput.BUTTON_A or GbaInput.BUTTON_B, VirtualControlType.COMBO_AB.gbaButtonMask)
        assertEquals(GbaInput.BUTTON_L or GbaInput.BUTTON_R, VirtualControlType.COMBO_LR.gbaButtonMask)
        assertEquals(GbaInput.BUTTON_L or GbaInput.BUTTON_A, VirtualControlType.COMBO_LA.gbaButtonMask)
        assertEquals(GbaInput.BUTTON_L or GbaInput.BUTTON_B, VirtualControlType.COMBO_LB.gbaButtonMask)
        assertEquals(GbaInput.BUTTON_R or GbaInput.BUTTON_A, VirtualControlType.COMBO_RA.gbaButtonMask)
        assertEquals(GbaInput.BUTTON_R or GbaInput.BUTTON_B, VirtualControlType.COMBO_RB.gbaButtonMask)

        assertTrue(VirtualControlType.BUTTON_MENU.isSpecialAction)
        assertTrue(VirtualControlType.BUTTON_FAST_FORWARD.isSpecialAction)
        assertTrue(VirtualControlType.QUICK_SAVE.isSpecialAction)
        assertTrue(VirtualControlType.QUICK_LOAD.isSpecialAction)
        assertEquals(0, VirtualControlType.BUTTON_MENU.gbaButtonMask)
        assertEquals(0, VirtualControlType.BUTTON_FAST_FORWARD.gbaButtonMask)
        assertEquals(0, VirtualControlType.QUICK_SAVE.gbaButtonMask)
        assertEquals(0, VirtualControlType.QUICK_LOAD.gbaButtonMask)
    }

    @Test
    fun evaluateDPadDirectionRespectsDeadzone() {
        val radius = 100f
        assertEquals(0, ControlLayoutPresets.evaluateDPadDirection(0f, 0f, radius))
        assertEquals(0, ControlLayoutPresets.evaluateDPadDirection(10f, 10f, radius)) // hypot ~14.14 < 18
        assertEquals(0, ControlLayoutPresets.evaluateDPadDirection(0f, -17.9f, radius))
        assertEquals(GbaInput.BUTTON_UP, ControlLayoutPresets.evaluateDPadDirection(0f, -18.1f, radius))
        assertEquals(0, ControlLayoutPresets.evaluateDPadDirection(50f, 0f, 0f))
        assertEquals(0, ControlLayoutPresets.evaluateDPadDirection(50f, 0f, -10f))
    }

    @Test
    fun evaluateDPadDirectionCoversEightDirectionsAndTwentyTwoPointFiveDegreeBoundary() {
        val r = 100f

        // 4 cardinals
        assertEquals(GbaInput.BUTTON_RIGHT, ControlLayoutPresets.evaluateDPadDirection(70f, 0f, r))
        assertEquals(GbaInput.BUTTON_LEFT, ControlLayoutPresets.evaluateDPadDirection(-70f, 0f, r))
        assertEquals(GbaInput.BUTTON_UP, ControlLayoutPresets.evaluateDPadDirection(0f, -70f, r))
        assertEquals(GbaInput.BUTTON_DOWN, ControlLayoutPresets.evaluateDPadDirection(0f, 70f, r))

        // 4 diagonals
        assertEquals(GbaInput.BUTTON_UP or GbaInput.BUTTON_RIGHT, ControlLayoutPresets.evaluateDPadDirection(60f, -60f, r))
        assertEquals(GbaInput.BUTTON_DOWN or GbaInput.BUTTON_RIGHT, ControlLayoutPresets.evaluateDPadDirection(60f, 60f, r))
        assertEquals(GbaInput.BUTTON_UP or GbaInput.BUTTON_LEFT, ControlLayoutPresets.evaluateDPadDirection(-60f, -60f, r))
        assertEquals(GbaInput.BUTTON_DOWN or GbaInput.BUTTON_LEFT, ControlLayoutPresets.evaluateDPadDirection(-60f, 60f, r))

        // tan(22.5°) ≈ 0.41421356f sector boundary checks
        assertEquals(GbaInput.BUTTON_RIGHT, ControlLayoutPresets.evaluateDPadDirection(100f, 40f, r))
        assertEquals(GbaInput.BUTTON_RIGHT or GbaInput.BUTTON_DOWN, ControlLayoutPresets.evaluateDPadDirection(100f, 43f, r))
        assertEquals(GbaInput.BUTTON_UP, ControlLayoutPresets.evaluateDPadDirection(40f, -100f, r))
        assertEquals(GbaInput.BUTTON_UP or GbaInput.BUTTON_RIGHT, ControlLayoutPresets.evaluateDPadDirection(43f, -100f, r))
    }

    @Test
    fun evaluateTouchPointsSupportsMultiTouchDpadButtonAAndShoulder() {
        val width = 1080f
        val height = 2400f
        val layout = ControlLayoutPresets.defaultProfile().portrait

        val dpad = layout.controls.first { it.type == VirtualControlType.DPAD }
        val btnA = layout.controls.first { it.type == VirtualControlType.BUTTON_A }
        val btnL = layout.controls.first { it.type == VirtualControlType.BUTTON_L }

        val pointers = listOf(
            // Finger 1: D-Pad diagonal UP + LEFT
            (dpad.centerX * width - 55f) to (dpad.centerY * height - 55f),
            // Finger 2: Button A
            (btnA.centerX * width) to (btnA.centerY * height),
            // Finger 3: Shoulder L
            (btnL.centerX * width) to (btnL.centerY * height)
        )

        val result = ControlLayoutPresets.evaluateTouchPoints(pointers, layout, width, height)
        val expectedMask = GbaInput.BUTTON_UP or GbaInput.BUTTON_LEFT or GbaInput.BUTTON_A or GbaInput.BUTTON_L
        assertEquals(expectedMask, result.activeHighGbaMask)
        assertTrue(result.activeControlIds.containsAll(listOf(dpad.id, btnA.id, btnL.id)))
        assertFalse(result.menuTriggered)
        assertFalse(result.fastForwardTriggered)
    }

    @Test
    fun evaluateTouchPointsSupportsSlideBetweenBAndAAndMidpointABZone() {
        val width = 1080f
        val height = 2400f
        val layout = ControlLayoutPresets.defaultProfile().portrait
        val btnB = layout.controls.first { it.type == VirtualControlType.BUTTON_B }
        val btnA = layout.controls.first { it.type == VirtualControlType.BUTTON_A }

        val bx = btnB.centerX * width
        val by = btnB.centerY * height
        val ax = btnA.centerX * width
        val ay = btnA.centerY * height

        // 1. On B center -> B only
        val onB = ControlLayoutPresets.evaluateTouchPoints(listOf(bx to by), layout, width, height)
        assertEquals(GbaInput.BUTTON_B, onB.activeHighGbaMask)
        assertTrue(onB.activeControlIds.contains(btnB.id))

        // 2. On midpoint between B and A -> both A and B active
        val midX = (bx + ax) * 0.5f
        val midY = (by + ay) * 0.5f
        val onMid = ControlLayoutPresets.evaluateTouchPoints(listOf(midX to midY), layout, width, height)
        assertEquals(GbaInput.BUTTON_A or GbaInput.BUTTON_B, onMid.activeHighGbaMask)
        assertTrue(onMid.activeControlIds.contains(btnA.id))
        assertTrue(onMid.activeControlIds.contains(btnB.id))

        // 3. Slide past midpoint toward A -> switches to A only
        val nearAx = bx + (ax - bx) * 0.78f
        val nearAy = by + (ay - by) * 0.78f
        val onSlideA = ControlLayoutPresets.evaluateTouchPoints(listOf(nearAx to nearAy), layout, width, height)
        assertEquals(GbaInput.BUTTON_A, onSlideA.activeHighGbaMask)
        assertTrue(onSlideA.activeControlIds.contains(btnA.id))
    }

    @Test
    fun evaluateTouchPointsActivatesComboAndSpecialActions() {
        val width = 1000f
        val height = 2000f
        val layout = OrientationLayoutConfig(
            screenRect = ScreenViewportRect(),
            controls = listOf(
                ControlPlacement("combo_ab", VirtualControlType.COMBO_AB, 0.25f, 0.5f),
                ControlPlacement("combo_lr", VirtualControlType.COMBO_LR, 0.75f, 0.5f),
                ControlPlacement("menu", VirtualControlType.BUTTON_MENU, 0.2f, 0.8f),
                ControlPlacement("ff", VirtualControlType.BUTTON_FAST_FORWARD, 0.4f, 0.8f),
                ControlPlacement("qs", VirtualControlType.QUICK_SAVE, 0.6f, 0.8f),
                ControlPlacement("ql", VirtualControlType.QUICK_LOAD, 0.8f, 0.8f),
                ControlPlacement("hidden_a", VirtualControlType.BUTTON_A, 0.5f, 0.2f, visible = false)
            )
        )

        val resCombos = ControlLayoutPresets.evaluateTouchPoints(
            listOf(250f to 1000f, 750f to 1000f, 500f to 400f),
            layout,
            width,
            height
        )
        assertEquals(
            GbaInput.BUTTON_A or GbaInput.BUTTON_B or GbaInput.BUTTON_L or GbaInput.BUTTON_R,
            resCombos.activeHighGbaMask
        )
        assertFalse(resCombos.activeControlIds.contains("hidden_a"))

        val resSpecials = ControlLayoutPresets.evaluateTouchPoints(
            listOf(200f to 1600f, 400f to 1600f, 600f to 1600f, 800f to 1600f),
            layout,
            width,
            height
        )
        assertEquals(0, resSpecials.activeHighGbaMask)
        assertTrue(resSpecials.menuTriggered)
        assertTrue(resSpecials.fastForwardTriggered)
        assertTrue(resSpecials.quickSaveTriggered)
        assertTrue(resSpecials.quickLoadTriggered)
    }

    @Test
    fun clampingBoundsControlPlacementAndScreenViewport() {
        val extremeHigh = ControlPlacement("c1", VirtualControlType.BUTTON_A, 1.5f, 2.0f, scale = 5.0f).clamped()
        assertEquals(0.96f, extremeHigh.centerX, 0.0001f)
        assertEquals(0.96f, extremeHigh.centerY, 0.0001f)
        assertEquals(2.2f, extremeHigh.scale, 0.0001f)

        val extremeLow = ControlPlacement("c2", VirtualControlType.BUTTON_B, -0.3f, 0.0f, scale = 0.1f).clamped()
        assertEquals(0.04f, extremeLow.centerX, 0.0001f)
        assertEquals(0.04f, extremeLow.centerY, 0.0001f)
        assertEquals(0.5f, extremeLow.scale, 0.0001f)

        val nanPlacement = ControlPlacement("c3", VirtualControlType.DPAD, Float.NaN, Float.POSITIVE_INFINITY, Float.NaN).clamped()
        assertEquals(0.5f, nanPlacement.centerX, 0.0001f)
        assertEquals(0.96f, nanPlacement.centerY, 0.0001f)
        assertEquals(1.0f, nanPlacement.scale, 0.0001f)

        val screen = ScreenViewportRect(centerX = -1f, centerY = 3f, scale = 9f).clamped()
        assertEquals(0.04f, screen.centerX, 0.0001f)
        assertEquals(0.96f, screen.centerY, 0.0001f)
        assertEquals(2.2f, screen.scale, 0.0001f)
    }

    @Test
    fun jsonRoundTripAndResilienceToInvalidInput() {
        val custom = ControlLayoutPresets.defaultProfile().copy(
            id = "custom_1",
            name = "Meu Layout",
            isBuiltIn = false
        )
        val inputList = ControlLayoutPresets.builtInProfiles() + custom
        val json = ControlLayoutPresets.toJsonList(inputList)
        val parsed = ControlLayoutPresets.fromJsonList(json)

        assertEquals(inputList.size, parsed.size)
        assertEquals("default", parsed[0].id)
        assertEquals("custom_1", parsed.last().id)
        assertEquals("Meu Layout", parsed.last().name)
        assertEquals(inputList[0].portrait.controls.size, parsed[0].portrait.controls.size)

        // Invalid or null JSON safely returns emptyList
        assertTrue(ControlLayoutPresets.fromJsonList(null).isEmpty())
        assertTrue(ControlLayoutPresets.fromJsonList("").isEmpty())
        assertTrue(ControlLayoutPresets.fromJsonList("   ").isEmpty())
        assertTrue(ControlLayoutPresets.fromJsonList("{not_a_list}").isEmpty())
        assertTrue(ControlLayoutPresets.fromJsonList("[\"broken\"]").isEmpty())
    }

    @Test
    fun builtInPresetsStayInSafeBoundsAndAvoidScreenOverlapInLandscape() {
        val profiles = ControlLayoutPresets.builtInProfiles()
        assertEquals(3, profiles.size)
        assertEquals(listOf("default", "comfortable", "minimal"), profiles.map { it.id })

        val landW = 844f
        val landH = 390f

        for (profile in profiles) {
            assertTrue(profile.isBuiltIn)
            for (config in listOf(profile.portrait, profile.landscape)) {
                assertTrue(config.controls.any { it.type == VirtualControlType.BUTTON_MENU })
                for (c in config.controls) {
                    assertTrue(c.centerX in 0.04f..0.96f)
                    assertTrue(c.centerY in 0.04f..0.96f)
                    assertTrue(c.scale in 0.5f..2.2f)
                }
            }

            // Landscape screen bounds must not overlap with any control
            val screenBounds = ControlLayoutPresets.computeScreenBounds(profile.landscape.screenRect, landW, landH)
            for (c in profile.landscape.controls) {
                val cSize = ControlLayoutPresets.computeControlSize(c.type, c.scale, landW, landH)
                val cx = c.centerX * landW
                val cy = c.centerY * landH
                val cLeft = cx - cSize.width / 2f
                val cRight = cx + cSize.width / 2f
                val cTop = cy - cSize.height / 2f
                val cBottom = cy + cSize.height / 2f

                val overlapsX = cRight > screenBounds.left && cLeft < (screenBounds.left + screenBounds.width)
                val overlapsY = cBottom > screenBounds.top && cTop < (screenBounds.top + screenBounds.height)
                assertFalse(
                    "Profile ${profile.id} control ${c.id} overlaps landscape screen",
                    overlapsX && overlapsY
                )
            }
        }
    }
}
