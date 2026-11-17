package com.retro.gba.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.retro.gba.emulator.GbaInput
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

enum class VirtualControlType(
    val label: String,
    val gbaButtonMask: Int = 0,
    val isSpecialAction: Boolean = false
) {
    DPAD("D-Pad", gbaButtonMask = 0, isSpecialAction = false),
    BUTTON_A("A", gbaButtonMask = GbaInput.BUTTON_A, isSpecialAction = false),
    BUTTON_B("B", gbaButtonMask = GbaInput.BUTTON_B, isSpecialAction = false),
    BUTTON_L("L", gbaButtonMask = GbaInput.BUTTON_L, isSpecialAction = false),
    BUTTON_R("R", gbaButtonMask = GbaInput.BUTTON_R, isSpecialAction = false),
    BUTTON_START("Start", gbaButtonMask = GbaInput.BUTTON_START, isSpecialAction = false),
    BUTTON_SELECT("Select", gbaButtonMask = GbaInput.BUTTON_SELECT, isSpecialAction = false),
    BUTTON_MENU("Menu", gbaButtonMask = 0, isSpecialAction = true),
    BUTTON_FAST_FORWARD("Avanço Rápido", gbaButtonMask = 0, isSpecialAction = true),
    COMBO_AB("A+B", gbaButtonMask = GbaInput.BUTTON_A or GbaInput.BUTTON_B, isSpecialAction = false),
    COMBO_LR("L+R", gbaButtonMask = GbaInput.BUTTON_L or GbaInput.BUTTON_R, isSpecialAction = false),
    COMBO_LA("L+A", gbaButtonMask = GbaInput.BUTTON_L or GbaInput.BUTTON_A, isSpecialAction = false),
    COMBO_LB("L+B", gbaButtonMask = GbaInput.BUTTON_L or GbaInput.BUTTON_B, isSpecialAction = false),
    COMBO_RA("R+A", gbaButtonMask = GbaInput.BUTTON_R or GbaInput.BUTTON_A, isSpecialAction = false),
    COMBO_RB("R+B", gbaButtonMask = GbaInput.BUTTON_R or GbaInput.BUTTON_B, isSpecialAction = false),
    QUICK_SAVE("Quick Save", gbaButtonMask = 0, isSpecialAction = true),
    QUICK_LOAD("Quick Load", gbaButtonMask = 0, isSpecialAction = true);

    fun shortBadgeText(): String = when (this) {
        DPAD -> "+"
        BUTTON_A -> "A"
        BUTTON_B -> "B"
        BUTTON_L -> "L"
        BUTTON_R -> "R"
        BUTTON_START -> "START"
        BUTTON_SELECT -> "SELECT"
        BUTTON_MENU -> "MENU"
        BUTTON_FAST_FORWARD -> "FF"
        COMBO_AB -> "A+B"
        COMBO_LR -> "L+R"
        COMBO_LA -> "L+A"
        COMBO_LB -> "L+B"
        COMBO_RA -> "R+A"
        COMBO_RB -> "R+B"
        QUICK_SAVE -> "QS"
        QUICK_LOAD -> "QL"
    }
}

data class ControlPlacement(
    val id: String,
    val type: VirtualControlType,
    val centerX: Float,
    val centerY: Float,
    val scale: Float = 1f,
    val visible: Boolean = true
) {
    fun clamped(): ControlPlacement = copy(
        centerX = sanitizeFloat(centerX, 0.5f).coerceIn(ControlLayoutPresets.MIN_COORD, ControlLayoutPresets.MAX_COORD),
        centerY = sanitizeFloat(centerY, 0.5f).coerceIn(ControlLayoutPresets.MIN_COORD, ControlLayoutPresets.MAX_COORD),
        scale = sanitizeFloat(scale, 1f).coerceIn(ControlLayoutPresets.MIN_SCALE, ControlLayoutPresets.MAX_SCALE)
    )
}

data class ScreenViewportRect(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.25f,
    val scale: Float = 1f
) {
    fun clamped(): ScreenViewportRect = copy(
        centerX = sanitizeFloat(centerX, 0.5f).coerceIn(ControlLayoutPresets.MIN_COORD, ControlLayoutPresets.MAX_COORD),
        centerY = sanitizeFloat(centerY, 0.25f).coerceIn(ControlLayoutPresets.MIN_COORD, ControlLayoutPresets.MAX_COORD),
        scale = sanitizeFloat(scale, 1f).coerceIn(ControlLayoutPresets.MIN_SCALE, ControlLayoutPresets.MAX_SCALE)
    )
}

data class OrientationLayoutConfig(
    val screenRect: ScreenViewportRect,
    val controls: List<ControlPlacement>
) {
    fun clamped(): OrientationLayoutConfig = copy(
        screenRect = screenRect.clamped(),
        controls = controls.map { it.clamped() }
    )
}

data class ControlLayoutProfile(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean = false,
    val portrait: OrientationLayoutConfig,
    val landscape: OrientationLayoutConfig
)

data class TouchEvaluationResult(
    val activeHighGbaMask: Int,
    val activeControlIds: Set<String>,
    val menuTriggered: Boolean = false,
    val fastForwardTriggered: Boolean = false,
    val quickSaveTriggered: Boolean = false,
    val quickLoadTriggered: Boolean = false
)

data class ControlBoundsSize(
    val width: Float,
    val height: Float,
    val hitWidth: Float,
    val hitHeight: Float
)

data class ScreenBoundsPx(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

private fun sanitizeFloat(value: Float, fallback: Float): Float =
    if (value.isNaN()) fallback else value

object ControlLayoutPresets {
    const val MIN_COORD = 0.04f
    const val MAX_COORD = 0.96f
    const val MIN_SCALE = 0.5f
    const val MAX_SCALE = 2.2f

    private const val DPAD_DEADZONE_RATIO = 0.18f
    private const val TAN_22_5_DEG = 0.41421356f
    private const val GBA_ASPECT_RATIO = 1.5f

    private val gson = Gson()

    // DTOs tolerantes para parsing JSON resiliente a campos ausentes ou enums desconhecidos
    private data class RawScreenViewportRect(
        val centerX: Float? = null,
        val centerY: Float? = null,
        val scale: Float? = null
    )

    private data class RawControlPlacement(
        val id: String? = null,
        val type: VirtualControlType? = null,
        val centerX: Float? = null,
        val centerY: Float? = null,
        val scale: Float? = null,
        val visible: Boolean? = null
    )

    private data class RawOrientationLayoutConfig(
        val screenRect: RawScreenViewportRect? = null,
        val controls: List<RawControlPlacement?>? = null
    )

    private data class RawControlLayoutProfile(
        val id: String? = null,
        val name: String? = null,
        val isBuiltIn: Boolean? = null,
        val portrait: RawOrientationLayoutConfig? = null,
        val landscape: RawOrientationLayoutConfig? = null
    )

    fun defaultProfile(): ControlLayoutProfile = ControlLayoutProfile(
        id = "default",
        name = "Padrão (My Boy!)",
        isBuiltIn = true,
        portrait = OrientationLayoutConfig(
            screenRect = ScreenViewportRect(centerX = 0.50f, centerY = 0.23f, scale = 1.0f),
            controls = listOf(
                ControlPlacement("btn_l", VirtualControlType.BUTTON_L, 0.13f, 0.45f, 1.0f),
                ControlPlacement("btn_ff", VirtualControlType.BUTTON_FAST_FORWARD, 0.42f, 0.45f, 1.0f),
                ControlPlacement("btn_menu", VirtualControlType.BUTTON_MENU, 0.58f, 0.45f, 1.0f),
                ControlPlacement("btn_r", VirtualControlType.BUTTON_R, 0.87f, 0.45f, 1.0f),
                ControlPlacement("dpad", VirtualControlType.DPAD, 0.20f, 0.70f, 1.0f),
                ControlPlacement("btn_b", VirtualControlType.BUTTON_B, 0.73f, 0.74f, 1.0f),
                ControlPlacement("btn_a", VirtualControlType.BUTTON_A, 0.88f, 0.66f, 1.0f),
                ControlPlacement("btn_select", VirtualControlType.BUTTON_SELECT, 0.38f, 0.90f, 1.0f),
                ControlPlacement("btn_start", VirtualControlType.BUTTON_START, 0.62f, 0.90f, 1.0f)
            )
        ),
        landscape = OrientationLayoutConfig(
            screenRect = ScreenViewportRect(centerX = 0.50f, centerY = 0.42f, scale = 1.0f),
            controls = listOf(
                ControlPlacement("btn_l", VirtualControlType.BUTTON_L, 0.10f, 0.16f, 1.0f),
                ControlPlacement("btn_r", VirtualControlType.BUTTON_R, 0.90f, 0.16f, 1.0f),
                ControlPlacement("dpad", VirtualControlType.DPAD, 0.11f, 0.62f, 1.0f),
                ControlPlacement("btn_b", VirtualControlType.BUTTON_B, 0.83f, 0.68f, 1.0f),
                ControlPlacement("btn_a", VirtualControlType.BUTTON_A, 0.92f, 0.54f, 1.0f),
                ControlPlacement("btn_select", VirtualControlType.BUTTON_SELECT, 0.34f, 0.91f, 1.0f),
                ControlPlacement("btn_ff", VirtualControlType.BUTTON_FAST_FORWARD, 0.45f, 0.91f, 1.0f),
                ControlPlacement("btn_menu", VirtualControlType.BUTTON_MENU, 0.55f, 0.91f, 1.0f),
                ControlPlacement("btn_start", VirtualControlType.BUTTON_START, 0.66f, 0.91f, 1.0f)
            )
        )
    )

    fun comfortableProfile(): ControlLayoutProfile = ControlLayoutProfile(
        id = "comfortable",
        name = "Confortável",
        isBuiltIn = true,
        portrait = OrientationLayoutConfig(
            screenRect = ScreenViewportRect(centerX = 0.50f, centerY = 0.22f, scale = 0.96f),
            controls = listOf(
                ControlPlacement("btn_l", VirtualControlType.BUTTON_L, 0.14f, 0.44f, 1.15f),
                ControlPlacement("btn_ff", VirtualControlType.BUTTON_FAST_FORWARD, 0.41f, 0.44f, 1.10f),
                ControlPlacement("btn_menu", VirtualControlType.BUTTON_MENU, 0.59f, 0.44f, 1.10f),
                ControlPlacement("btn_r", VirtualControlType.BUTTON_R, 0.86f, 0.44f, 1.15f),
                ControlPlacement("dpad", VirtualControlType.DPAD, 0.22f, 0.68f, 1.25f),
                ControlPlacement("btn_b", VirtualControlType.BUTTON_B, 0.70f, 0.72f, 1.25f),
                ControlPlacement("btn_a", VirtualControlType.BUTTON_A, 0.87f, 0.63f, 1.25f),
                ControlPlacement("btn_select", VirtualControlType.BUTTON_SELECT, 0.37f, 0.90f, 1.15f),
                ControlPlacement("btn_start", VirtualControlType.BUTTON_START, 0.63f, 0.90f, 1.15f)
            )
        ),
        landscape = OrientationLayoutConfig(
            screenRect = ScreenViewportRect(centerX = 0.50f, centerY = 0.41f, scale = 0.90f),
            controls = listOf(
                ControlPlacement("btn_l", VirtualControlType.BUTTON_L, 0.11f, 0.16f, 1.15f),
                ControlPlacement("btn_r", VirtualControlType.BUTTON_R, 0.89f, 0.16f, 1.15f),
                ControlPlacement("dpad", VirtualControlType.DPAD, 0.13f, 0.60f, 1.25f),
                ControlPlacement("btn_b", VirtualControlType.BUTTON_B, 0.81f, 0.69f, 1.25f),
                ControlPlacement("btn_a", VirtualControlType.BUTTON_A, 0.91f, 0.52f, 1.25f),
                ControlPlacement("btn_select", VirtualControlType.BUTTON_SELECT, 0.34f, 0.90f, 1.10f),
                ControlPlacement("btn_ff", VirtualControlType.BUTTON_FAST_FORWARD, 0.45f, 0.90f, 1.10f),
                ControlPlacement("btn_menu", VirtualControlType.BUTTON_MENU, 0.55f, 0.90f, 1.10f),
                ControlPlacement("btn_start", VirtualControlType.BUTTON_START, 0.66f, 0.90f, 1.10f)
            )
        )
    )

    fun minimalProfile(): ControlLayoutProfile = ControlLayoutProfile(
        id = "minimal",
        name = "Compacto",
        isBuiltIn = true,
        portrait = OrientationLayoutConfig(
            screenRect = ScreenViewportRect(centerX = 0.50f, centerY = 0.25f, scale = 1.05f),
            controls = listOf(
                ControlPlacement("btn_l", VirtualControlType.BUTTON_L, 0.11f, 0.49f, 0.80f),
                ControlPlacement("btn_ff", VirtualControlType.BUTTON_FAST_FORWARD, 0.44f, 0.49f, 0.78f),
                ControlPlacement("btn_menu", VirtualControlType.BUTTON_MENU, 0.56f, 0.49f, 0.78f),
                ControlPlacement("btn_r", VirtualControlType.BUTTON_R, 0.89f, 0.49f, 0.80f),
                ControlPlacement("dpad", VirtualControlType.DPAD, 0.17f, 0.75f, 0.85f),
                ControlPlacement("btn_b", VirtualControlType.BUTTON_B, 0.76f, 0.78f, 0.85f),
                ControlPlacement("btn_a", VirtualControlType.BUTTON_A, 0.89f, 0.71f, 0.85f),
                ControlPlacement("btn_select", VirtualControlType.BUTTON_SELECT, 0.40f, 0.92f, 0.82f),
                ControlPlacement("btn_start", VirtualControlType.BUTTON_START, 0.60f, 0.92f, 0.82f)
            )
        ),
        landscape = OrientationLayoutConfig(
            screenRect = ScreenViewportRect(centerX = 0.50f, centerY = 0.42f, scale = 1.08f),
            controls = listOf(
                ControlPlacement("btn_l", VirtualControlType.BUTTON_L, 0.09f, 0.14f, 0.78f),
                ControlPlacement("btn_r", VirtualControlType.BUTTON_R, 0.91f, 0.14f, 0.78f),
                ControlPlacement("dpad", VirtualControlType.DPAD, 0.10f, 0.65f, 0.85f),
                ControlPlacement("btn_b", VirtualControlType.BUTTON_B, 0.84f, 0.73f, 0.85f),
                ControlPlacement("btn_a", VirtualControlType.BUTTON_A, 0.92f, 0.57f, 0.85f),
                ControlPlacement("btn_select", VirtualControlType.BUTTON_SELECT, 0.34f, 0.93f, 0.78f),
                ControlPlacement("btn_ff", VirtualControlType.BUTTON_FAST_FORWARD, 0.45f, 0.93f, 0.78f),
                ControlPlacement("btn_menu", VirtualControlType.BUTTON_MENU, 0.55f, 0.93f, 0.78f),
                ControlPlacement("btn_start", VirtualControlType.BUTTON_START, 0.66f, 0.93f, 0.78f)
            )
        )
    )

    fun builtInProfiles(): List<ControlLayoutProfile> = listOf(
        defaultProfile(),
        comfortableProfile(),
        minimalProfile()
    )

    fun toJsonList(profiles: List<ControlLayoutProfile>): String = gson.toJson(profiles)

    fun fromJsonList(json: String?): List<ControlLayoutProfile> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<RawControlLayoutProfile?>>() {}.type
            val rawList: List<RawControlLayoutProfile?> = gson.fromJson(json, type) ?: return emptyList()
            val defaults = defaultProfile()
            rawList.mapNotNull { raw ->
                val id = raw?.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = raw.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val portrait = sanitizeOrientation(raw.portrait, defaults.portrait) ?: return@mapNotNull null
                val landscape = sanitizeOrientation(raw.landscape, defaults.landscape) ?: return@mapNotNull null
                ControlLayoutProfile(
                    id = id,
                    name = name,
                    isBuiltIn = raw.isBuiltIn ?: false,
                    portrait = portrait,
                    landscape = landscape
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun sanitizeOrientation(
        raw: RawOrientationLayoutConfig?,
        fallback: OrientationLayoutConfig
    ): OrientationLayoutConfig? {
        if (raw == null) return null
        val rect = raw.screenRect
        val screenRect = if (rect != null) {
            ScreenViewportRect(
                centerX = rect.centerX ?: fallback.screenRect.centerX,
                centerY = rect.centerY ?: fallback.screenRect.centerY,
                scale = rect.scale ?: fallback.screenRect.scale
            ).clamped()
        } else {
            fallback.screenRect
        }
        val controls = raw.controls?.mapNotNull { c ->
            val id = c?.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val type = c.type ?: return@mapNotNull null
            ControlPlacement(
                id = id,
                type = type,
                centerX = c.centerX ?: 0.5f,
                centerY = c.centerY ?: 0.5f,
                scale = c.scale ?: 1f,
                visible = c.visible ?: true
            ).clamped()
        } ?: return null

        return OrientationLayoutConfig(screenRect = screenRect, controls = controls)
    }

    fun computeControlSize(
        type: VirtualControlType,
        scale: Float,
        containerWidth: Float,
        containerHeight: Float
    ): ControlBoundsSize {
        val minSide = min(containerWidth, containerHeight).coerceAtLeast(1f)
        val safeScale = sanitizeFloat(scale, 1f).coerceIn(MIN_SCALE, MAX_SCALE)
        return when (type) {
            VirtualControlType.DPAD -> {
                val diameter = minSide * 0.31f * safeScale
                val hitDiameter = diameter * 1.25f
                ControlBoundsSize(diameter, diameter, hitDiameter, hitDiameter)
            }
            VirtualControlType.BUTTON_A,
            VirtualControlType.BUTTON_B -> {
                val diameter = minSide * 0.136f * safeScale
                val hitDiameter = diameter * 1.25f
                ControlBoundsSize(diameter, diameter, hitDiameter, hitDiameter)
            }
            VirtualControlType.BUTTON_L,
            VirtualControlType.BUTTON_R -> {
                val w = minSide * 0.21f * safeScale
                val h = minSide * 0.095f * safeScale
                ControlBoundsSize(w, h, w * 1.20f, h * 1.30f)
            }
            VirtualControlType.BUTTON_START,
            VirtualControlType.BUTTON_SELECT -> {
                val w = minSide * 0.135f * safeScale
                val h = minSide * 0.068f * safeScale
                ControlBoundsSize(w, h, w * 1.25f, h * 1.50f)
            }
            VirtualControlType.COMBO_AB,
            VirtualControlType.COMBO_LR,
            VirtualControlType.COMBO_LA,
            VirtualControlType.COMBO_LB,
            VirtualControlType.COMBO_RA,
            VirtualControlType.COMBO_RB -> {
                val w = minSide * 0.145f * safeScale
                val h = minSide * 0.105f * safeScale
                ControlBoundsSize(w, h, w * 1.22f, h * 1.22f)
            }
            VirtualControlType.BUTTON_MENU,
            VirtualControlType.BUTTON_FAST_FORWARD,
            VirtualControlType.QUICK_SAVE,
            VirtualControlType.QUICK_LOAD -> {
                val w = minSide * 0.105f * safeScale
                val h = minSide * 0.092f * safeScale
                ControlBoundsSize(w, h, w * 1.30f, h * 1.30f)
            }
        }
    }

    fun computeScreenBounds(
        screenRect: ScreenViewportRect,
        containerWidth: Float,
        containerHeight: Float
    ): ScreenBoundsPx {
        if (containerWidth <= 0f || containerHeight <= 0f) {
            return ScreenBoundsPx(0f, 0f, 0f, 0f)
        }
        val clamped = screenRect.clamped()
        val isLandscape = containerWidth > containerHeight
        val maxScreenWidth = containerWidth * 0.98f
        val maxScreenHeight = containerHeight * 0.98f
        val minScreenWidth = containerWidth * 0.25f
        val baseWidthAtScale1 = if (isLandscape) {
            min(containerHeight * 0.78f * GBA_ASPECT_RATIO, containerWidth * 0.62f)
        } else {
            min(containerWidth * 0.96f, containerHeight * 0.56f * GBA_ASPECT_RATIO)
        }

        var width = (baseWidthAtScale1 * clamped.scale).coerceIn(minScreenWidth, maxScreenWidth)
        var height = width / GBA_ASPECT_RATIO
        if (height > maxScreenHeight) {
            height = maxScreenHeight
            width = height * GBA_ASPECT_RATIO
        }

        val halfW = width / 2f
        val halfH = height / 2f
        val cx = (clamped.centerX * containerWidth).coerceIn(halfW, containerWidth - halfW)
        val cy = (clamped.centerY * containerHeight).coerceIn(halfH, containerHeight - halfH)

        return ScreenBoundsPx(
            left = cx - halfW,
            top = cy - halfH,
            width = width,
            height = height
        )
    }

    fun evaluateDPadDirection(dx: Float, dy: Float, radius: Float): Int {
        if (radius <= 0f || dx.isNaN() || dy.isNaN() || radius.isNaN()) return 0
        val distance = hypot(dx, dy)
        if (distance < radius * DPAD_DEADZONE_RATIO) return 0

        val absX = abs(dx)
        val absY = abs(dy)

        if (absY <= absX * TAN_22_5_DEG) {
            return if (dx > 0f) GbaInput.BUTTON_RIGHT else GbaInput.BUTTON_LEFT
        }
        if (absX <= absY * TAN_22_5_DEG) {
            return if (dy > 0f) GbaInput.BUTTON_DOWN else GbaInput.BUTTON_UP
        }

        val horizontal = if (dx > 0f) GbaInput.BUTTON_RIGHT else GbaInput.BUTTON_LEFT
        val vertical = if (dy > 0f) GbaInput.BUTTON_DOWN else GbaInput.BUTTON_UP
        return horizontal or vertical
    }

    fun evaluateTouchPoints(
        pointers: List<Pair<Float, Float>>,
        layout: OrientationLayoutConfig,
        containerWidth: Float,
        containerHeight: Float
    ): TouchEvaluationResult {
        if (pointers.isEmpty() || containerWidth <= 0f || containerHeight <= 0f) {
            return TouchEvaluationResult(activeHighGbaMask = 0, activeControlIds = emptySet())
        }

        val visibleControls = layout.controls.asSequence().filter { it.visible }.map { it.clamped() }.toList()
        if (visibleControls.isEmpty()) {
            return TouchEvaluationResult(activeHighGbaMask = 0, activeControlIds = emptySet())
        }

        var activeMask = 0
        val activeIds = mutableSetOf<String>()
        var menuTriggered = false
        var fastForwardTriggered = false
        var quickSaveTriggered = false
        var quickLoadTriggered = false

        val buttonA = visibleControls.firstOrNull { it.type == VirtualControlType.BUTTON_A }
        val buttonB = visibleControls.firstOrNull { it.type == VirtualControlType.BUTTON_B }

        for ((px, py) in pointers) {
            if (px.isNaN() || py.isNaN()) continue

            for (placement in visibleControls) {
                val cx = placement.centerX * containerWidth
                val cy = placement.centerY * containerHeight
                val size = computeControlSize(placement.type, placement.scale, containerWidth, containerHeight)

                when (placement.type) {
                    VirtualControlType.DPAD -> {
                        val visualRadius = size.width * 0.5f
                        val hitRadius = size.hitWidth * 0.5f
                        val dx = px - cx
                        val dy = py - cy
                        if (hypot(dx, dy) <= hitRadius) {
                            activeIds.add(placement.id)
                            activeMask = activeMask or evaluateDPadDirection(dx, dy, visualRadius)
                        }
                    }

                    VirtualControlType.BUTTON_A,
                    VirtualControlType.BUTTON_B -> {
                        val hitRadius = size.hitWidth * 0.5f
                        if (hypot(px - cx, py - cy) <= hitRadius) {
                            activeIds.add(placement.id)
                            activeMask = activeMask or placement.type.gbaButtonMask
                        }
                    }

                    else -> {
                        val halfHitW = size.hitWidth * 0.5f
                        val halfHitH = size.hitHeight * 0.5f
                        if (abs(px - cx) <= halfHitW && abs(py - cy) <= halfHitH) {
                            activeIds.add(placement.id)
                            if (placement.type.isSpecialAction) {
                                when (placement.type) {
                                    VirtualControlType.BUTTON_MENU -> menuTriggered = true
                                    VirtualControlType.BUTTON_FAST_FORWARD -> fastForwardTriggered = true
                                    VirtualControlType.QUICK_SAVE -> quickSaveTriggered = true
                                    VirtualControlType.QUICK_LOAD -> quickLoadTriggered = true
                                    else -> {}
                                }
                            } else {
                                activeMask = activeMask or placement.type.gbaButtonMask
                            }
                        }
                    }
                }
            }

            // Região intermediária deslizável B <-> A com zona central A+B (estilo My Boy!)
            if (buttonA != null && buttonB != null) {
                val bx = buttonB.centerX * containerWidth
                val by = buttonB.centerY * containerHeight
                val ax = buttonA.centerX * containerWidth
                val ay = buttonA.centerY * containerHeight
                val abx = ax - bx
                val aby = ay - by
                val lenSq = abx * abx + aby * aby
                if (lenSq > 1f) {
                    val lenAB = hypot(abx, aby)
                    val hitRadiusB = computeControlSize(buttonB.type, buttonB.scale, containerWidth, containerHeight).hitWidth * 0.5f
                    val hitRadiusA = computeControlSize(buttonA.type, buttonA.scale, containerWidth, containerHeight).hitWidth * 0.5f
                    if (lenAB <= (hitRadiusA + hitRadiusB) * 2.2f) {
                        val t = ((px - bx) * abx + (py - by) * aby) / lenSq
                        if (t in 0f..1f) {
                            val projX = bx + t * abx
                            val projY = by + t * aby
                            val perpDist = hypot(px - projX, py - projY)
                            val bridgeWidth = maxOf(hitRadiusA, hitRadiusB) * 0.95f
                            if (perpDist <= bridgeWidth) {
                                when {
                                    t <= 0.42f -> {
                                        activeIds.add(buttonB.id)
                                        activeMask = activeMask or GbaInput.BUTTON_B
                                    }
                                    t >= 0.58f -> {
                                        activeIds.add(buttonA.id)
                                        activeMask = activeMask or GbaInput.BUTTON_A
                                    }
                                    else -> {
                                        activeIds.add(buttonB.id)
                                        activeIds.add(buttonA.id)
                                        activeMask = activeMask or GbaInput.BUTTON_A or GbaInput.BUTTON_B
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return TouchEvaluationResult(
            activeHighGbaMask = activeMask,
            activeControlIds = activeIds,
            menuTriggered = menuTriggered,
            fastForwardTriggered = fastForwardTriggered,
            quickSaveTriggered = quickSaveTriggered,
            quickLoadTriggered = quickLoadTriggered
        )
    }
}
