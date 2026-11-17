package com.retro.gba.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.gba.emulator.GbaInput
import com.retro.gba.model.ControlLayoutPresets
import com.retro.gba.model.ControlPlacement
import com.retro.gba.model.OrientationLayoutConfig
import com.retro.gba.model.VirtualControlType
import com.retro.gba.ui.haptics.HapticManager
import com.retro.gba.ui.theme.GbaBackground
import com.retro.gba.ui.theme.GbaPrimary
import com.retro.gba.ui.theme.GbaSecondary
import com.retro.gba.ui.theme.RetroGBATheme
import kotlin.math.min
import kotlin.math.roundToInt

private val ALL_GBA_BITS = intArrayOf(
    GbaInput.BUTTON_A,
    GbaInput.BUTTON_B,
    GbaInput.BUTTON_SELECT,
    GbaInput.BUTTON_START,
    GbaInput.BUTTON_RIGHT,
    GbaInput.BUTTON_LEFT,
    GbaInput.BUTTON_UP,
    GbaInput.BUTTON_DOWN,
    GbaInput.BUTTON_R,
    GbaInput.BUTTON_L
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VirtualController(
    modifier: Modifier = Modifier,
    layoutConfig: OrientationLayoutConfig = ControlLayoutPresets.defaultProfile().portrait,
    opacity: Float = 0.65f,
    hapticEnabled: Boolean = true,
    onButtonsMaskChanged: (Int) -> Unit = {},
    onButtonStateChanged: ((Int, Boolean) -> Unit)? = null,
    onMenuClick: () -> Unit = {},
    onFastForwardToggle: () -> Unit = {},
    isFastForwardActive: Boolean = false,
    onQuickSave: () -> Unit = {},
    onQuickLoad: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isInspection = LocalInspectionMode.current
    val density = LocalDensity.current

    val hapticManager = remember(context, isInspection) {
        if (isInspection) null else HapticManager(context)
    }

    val latestLayoutConfig by rememberUpdatedState(layoutConfig)
    val latestHapticEnabled by rememberUpdatedState(hapticEnabled)
    val latestOnButtonsMaskChanged by rememberUpdatedState(onButtonsMaskChanged)
    val latestOnButtonStateChanged by rememberUpdatedState(onButtonStateChanged)
    val latestOnMenuClick by rememberUpdatedState(onMenuClick)
    val latestOnFastForwardToggle by rememberUpdatedState(onFastForwardToggle)
    val latestOnQuickSave by rememberUpdatedState(onQuickSave)
    val latestOnQuickLoad by rememberUpdatedState(onQuickLoad)

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var currentMask by remember { mutableIntStateOf(0) }
    var activeControlIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var prevMenuDown by remember { mutableStateOf(false) }
    var prevFastForwardDown by remember { mutableStateOf(false) }
    var prevQuickSaveDown by remember { mutableStateOf(false) }
    var prevQuickLoadDown by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (currentMask != 0) {
                val oldMask = currentMask
                currentMask = 0
                latestOnButtonsMaskChanged(0)
                latestOnButtonStateChanged?.let { cb ->
                    for (bit in ALL_GBA_BITS) {
                        if ((oldMask and bit) != 0) cb(bit, false)
                    }
                }
            }
        }
    }

    val safeOpacity = opacity.coerceIn(0.15f, 1.0f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        val widthPx = if (containerSize.width > 0) containerSize.width.toFloat() else with(density) { maxWidth.toPx() }
        val heightPx = if (containerSize.height > 0) containerSize.height.toFloat() else with(density) { maxHeight.toPx() }
        val latestWidthPx by rememberUpdatedState(widthPx)
        val latestHeightPx by rememberUpdatedState(heightPx)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    val wPx = latestWidthPx
                    val hPx = latestHeightPx
                    if (wPx <= 0f || hPx <= 0f) return@pointerInteropFilter false

                    val actionMasked = event.actionMasked
                    val actionIndex = event.actionIndex

                    val pointers: List<Pair<Float, Float>> = when (actionMasked) {
                        MotionEvent.ACTION_CANCEL -> emptyList()
                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_POINTER_UP -> {
                            buildList(event.pointerCount) {
                                for (i in 0 until event.pointerCount) {
                                    if (i != actionIndex) {
                                        add(event.getX(i) to event.getY(i))
                                    }
                                }
                            }
                        }
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_POINTER_DOWN,
                        MotionEvent.ACTION_MOVE -> {
                            buildList(event.pointerCount) {
                                for (i in 0 until event.pointerCount) {
                                    add(event.getX(i) to event.getY(i))
                                }
                            }
                        }
                        else -> return@pointerInteropFilter false
                    }

                    val eval = ControlLayoutPresets.evaluateTouchPoints(
                        pointers = pointers,
                        layout = latestLayoutConfig,
                        containerWidth = wPx,
                        containerHeight = hPx
                    )

                    activeControlIds = eval.activeControlIds

                    val newMask = eval.activeHighGbaMask
                    val changedBits = newMask xor currentMask
                    if (changedBits != 0) {
                        val newlyPressedBits = newMask and currentMask.inv()
                        currentMask = newMask
                        latestOnButtonsMaskChanged(newMask)
                        latestOnButtonStateChanged?.let { cb ->
                            for (bit in ALL_GBA_BITS) {
                                if ((changedBits and bit) != 0) {
                                    cb(bit, (newMask and bit) != 0)
                                }
                            }
                        }
                        if (newlyPressedBits != 0) {
                            hapticManager?.performButtonClick(view, latestHapticEnabled)
                        }
                    }

                    if (eval.menuTriggered && !prevMenuDown) {
                        hapticManager?.performButtonClick(view, latestHapticEnabled)
                        latestOnMenuClick()
                    }
                    prevMenuDown = eval.menuTriggered

                    if (eval.fastForwardTriggered && !prevFastForwardDown) {
                        hapticManager?.performTurboToggle(latestHapticEnabled)
                        latestOnFastForwardToggle()
                    }
                    prevFastForwardDown = eval.fastForwardTriggered

                    if (eval.quickSaveTriggered && !prevQuickSaveDown) {
                        hapticManager?.performButtonClick(view, latestHapticEnabled)
                        latestOnQuickSave()
                    }
                    prevQuickSaveDown = eval.quickSaveTriggered

                    if (eval.quickLoadTriggered && !prevQuickLoadDown) {
                        hapticManager?.performButtonClick(view, latestHapticEnabled)
                        latestOnQuickLoad()
                    }
                    prevQuickLoadDown = eval.quickLoadTriggered

                    true
                }
                .alpha(safeOpacity)
        ) {
            for (rawPlacement in layoutConfig.controls) {
                val placement = rawPlacement.clamped()
                if (!placement.visible) continue

                val sizePx = ControlLayoutPresets.computeControlSize(
                    type = placement.type,
                    scale = placement.scale,
                    containerWidth = widthPx,
                    containerHeight = heightPx
                )
                val widthDp = with(density) { sizePx.width.toDp() }
                val heightDp = with(density) { sizePx.height.toDp() }
                val leftPx = (placement.centerX * widthPx - sizePx.width * 0.5f).roundToInt()
                val topPx = (placement.centerY * heightPx - sizePx.height * 0.5f).roundToInt()
                val isPressed = placement.id in activeControlIds

                VirtualControlVisual(
                    placement = placement,
                    widthDp = widthDp,
                    heightDp = heightDp,
                    isPressed = isPressed,
                    activeGbaMask = currentMask,
                    isFastForwardActive = isFastForwardActive,
                    modifier = Modifier.offset { IntOffset(leftPx, topPx) }
                )
            }
        }
    }
}

@Composable
internal fun VirtualControlVisual(
    placement: ControlPlacement,
    widthDp: Dp,
    heightDp: Dp,
    isPressed: Boolean,
    activeGbaMask: Int,
    isFastForwardActive: Boolean,
    modifier: Modifier = Modifier
) {
    val pressScale = if (isPressed) 0.94f else 1.0f

    when (placement.type) {
        VirtualControlType.DPAD -> {
            DPadCanvasVisual(
                sizeDp = widthDp,
                activeMask = activeGbaMask,
                isTouched = isPressed,
                modifier = modifier
            )
        }

        VirtualControlType.BUTTON_A,
        VirtualControlType.BUTTON_B -> {
            val baseColor = if (placement.type == VirtualControlType.BUTTON_A) {
                Color(0xFF2E7D32)
            } else {
                Color(0xFFC62828)
            }
            val activeColor = if (placement.type == VirtualControlType.BUTTON_A) {
                Color(0xFF00E676)
            } else {
                Color(0xFFFF5252)
            }
            val fillColor = if (isPressed) activeColor else baseColor.copy(alpha = 0.88f)
            val borderColor = if (isPressed) Color.White else Color.White.copy(alpha = 0.42f)
            val fontSp = (min(widthDp.value, heightDp.value) * 0.44f).coerceIn(8f, 36f).sp

            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(widthDp, heightDp)
                    .scale(pressScale)
                    .clip(CircleShape)
                    .background(fillColor)
                    .border(if (isPressed) 2.5.dp else 1.5.dp, borderColor, CircleShape)
            ) {
                Text(
                    text = placement.type.shortBadgeText(),
                    color = if (isPressed && placement.type == VirtualControlType.BUTTON_A) Color(0xFF092214) else Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = fontSp,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        }

        VirtualControlType.BUTTON_L,
        VirtualControlType.BUTTON_R -> {
            val shape = if (placement.type == VirtualControlType.BUTTON_L) {
                RoundedCornerShape(topStart = 18.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 12.dp)
            } else {
                RoundedCornerShape(topStart = 8.dp, topEnd = 18.dp, bottomStart = 12.dp, bottomEnd = 8.dp)
            }
            val fillColor = if (isPressed) GbaSecondary.copy(alpha = 0.85f) else Color(0xFF2D2A42).copy(alpha = 0.90f)
            val borderColor = if (isPressed) Color.White else Color.White.copy(alpha = 0.30f)
            val fontSp = (heightDp.value * 0.48f).coerceIn(8f, 26f).sp

            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(widthDp, heightDp)
                    .scale(pressScale)
                    .clip(shape)
                    .background(fillColor)
                    .border(1.5.dp, borderColor, shape)
            ) {
                Text(
                    text = placement.type.shortBadgeText(),
                    color = if (isPressed) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSp,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        }

        VirtualControlType.BUTTON_START,
        VirtualControlType.BUTTON_SELECT -> {
            val fillColor = if (isPressed) GbaPrimary else Color(0xFF262338).copy(alpha = 0.92f)
            val borderColor = if (isPressed) GbaSecondary else Color.White.copy(alpha = 0.28f)
            val fontSp = (heightDp.value * 0.36f).coerceIn(5f, 16f).sp

            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(widthDp, heightDp)
                    .scale(pressScale)
                    .clip(CircleShape)
                    .background(fillColor)
                    .border(1.2.dp, borderColor, CircleShape)
            ) {
                Text(
                    text = placement.type.shortBadgeText(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSp,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        }

        VirtualControlType.COMBO_AB,
        VirtualControlType.COMBO_LR,
        VirtualControlType.COMBO_LA,
        VirtualControlType.COMBO_LB,
        VirtualControlType.COMBO_RA,
        VirtualControlType.COMBO_RB -> {
            val shape = RoundedCornerShape(14.dp)
            val fillColor = if (isPressed) GbaSecondary.copy(alpha = 0.90f) else Color(0xFF3E3175).copy(alpha = 0.90f)
            val borderColor = if (isPressed) Color.White else GbaSecondary.copy(alpha = 0.55f)
            val fontSp = (heightDp.value * 0.35f).coerceIn(7f, 20f).sp

            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(widthDp, heightDp)
                    .scale(pressScale)
                    .clip(shape)
                    .background(fillColor)
                    .border(1.5.dp, borderColor, shape)
            ) {
                Text(
                    text = placement.type.shortBadgeText(),
                    color = if (isPressed) Color.Black else Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = fontSp,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        }

        VirtualControlType.BUTTON_MENU,
        VirtualControlType.BUTTON_FAST_FORWARD,
        VirtualControlType.QUICK_SAVE,
        VirtualControlType.QUICK_LOAD -> {
            val isHighlighted = isPressed || (placement.type == VirtualControlType.BUTTON_FAST_FORWARD && isFastForwardActive)
            val fillColor = when {
                isHighlighted -> GbaSecondary.copy(alpha = 0.90f)
                else -> Color(0xFF27223B).copy(alpha = 0.88f)
            }
            val borderColor = if (isHighlighted) Color.White else Color.White.copy(alpha = 0.28f)
            val tint = if (isHighlighted) Color.Black else Color.White
            val icon = when (placement.type) {
                VirtualControlType.BUTTON_MENU -> Icons.Default.Menu
                VirtualControlType.BUTTON_FAST_FORWARD -> Icons.Default.FastForward
                VirtualControlType.QUICK_SAVE -> Icons.Default.Save
                VirtualControlType.QUICK_LOAD -> Icons.Default.Download
                else -> Icons.Default.Menu
            }
            val iconSize = (min(widthDp.value, heightDp.value) * 0.54f).dp

            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .size(widthDp, heightDp)
                    .scale(pressScale)
                    .clip(RoundedCornerShape(12.dp))
                    .background(fillColor)
                    .border(1.2.dp, borderColor, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = placement.type.label,
                    tint = tint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
private fun DPadCanvasVisual(
    sizeDp: Dp,
    activeMask: Int,
    isTouched: Boolean,
    modifier: Modifier = Modifier
) {
    val up = (activeMask and GbaInput.BUTTON_UP) != 0
    val down = (activeMask and GbaInput.BUTTON_DOWN) != 0
    val left = (activeMask and GbaInput.BUTTON_LEFT) != 0
    val right = (activeMask and GbaInput.BUTTON_RIGHT) != 0

    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.5f
        val outerR = min(w, h) * 0.49f

        // Base circular halo
        drawCircle(
            color = Color(0xCC161422),
            radius = outerR,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = if (isTouched) GbaSecondary.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.16f),
            radius = outerR,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx())
        )

        val armThick = w * 0.31f
        val armLen = w * 0.88f
        val armOffset = (w - armLen) * 0.5f
        val centerOffset = (w - armThick) * 0.5f
        val cornerR = CornerRadius(armThick * 0.20f, armThick * 0.20f)

        val baseArmColor = Color(0xFF2F2C44)
        val activeGlow = GbaSecondary.copy(alpha = 0.85f)

        // Cross base (horizontal + vertical bars)
        drawRoundRect(
            color = baseArmColor,
            topLeft = Offset(armOffset, centerOffset),
            size = Size(armLen, armThick),
            cornerRadius = cornerR
        )
        drawRoundRect(
            color = baseArmColor,
            topLeft = Offset(centerOffset, armOffset),
            size = Size(armThick, armLen),
            cornerRadius = cornerR
        )

        val halfArm = armThick * 0.5f
        val tipInset = armOffset + w * 0.02f
        val tipSpan = (centerOffset - tipInset).coerceAtLeast(1f)

        // Highlight active directional arms
        if (up) {
            drawRoundRect(
                color = activeGlow,
                topLeft = Offset(centerOffset, tipInset),
                size = Size(armThick, tipSpan),
                cornerRadius = cornerR
            )
        }
        if (down) {
            drawRoundRect(
                color = activeGlow,
                topLeft = Offset(centerOffset, centerOffset + armThick),
                size = Size(armThick, tipSpan),
                cornerRadius = cornerR
            )
        }
        if (left) {
            drawRoundRect(
                color = activeGlow,
                topLeft = Offset(tipInset, centerOffset),
                size = Size(tipSpan, armThick),
                cornerRadius = cornerR
            )
        }
        if (right) {
            drawRoundRect(
                color = activeGlow,
                topLeft = Offset(centerOffset + armThick, centerOffset),
                size = Size(tipSpan, armThick),
                cornerRadius = cornerR
            )
        }

        // Diagonal corner indicator dots for instant 8-way feedback
        val diagR = outerR * 0.11f
        val diagDist = outerR * 0.54f
        if (up && right) drawCircle(activeGlow, diagR, Offset(cx + diagDist, cy - diagDist))
        if (up && left) drawCircle(activeGlow, diagR, Offset(cx - diagDist, cy - diagDist))
        if (down && right) drawCircle(activeGlow, diagR, Offset(cx + diagDist, cy + diagDist))
        if (down && left) drawCircle(activeGlow, diagR, Offset(cx - diagDist, cy + diagDist))

        // Crisp geometric arrow triangles
        val triSize = armThick * 0.24f
        val arrowReach = outerR * 0.67f
        val inactiveArrow = Color.White.copy(alpha = 0.72f)
        val activeArrow = Color(0xFF0A141A)

        // Up triangle
        drawPath(
            path = Path().apply {
                moveTo(cx, cy - arrowReach - triSize * 0.6f)
                lineTo(cx - triSize, cy - arrowReach + triSize * 0.6f)
                lineTo(cx + triSize, cy - arrowReach + triSize * 0.6f)
                close()
            },
            color = if (up) activeArrow else inactiveArrow
        )
        // Down triangle
        drawPath(
            path = Path().apply {
                moveTo(cx, cy + arrowReach + triSize * 0.6f)
                lineTo(cx - triSize, cy + arrowReach - triSize * 0.6f)
                lineTo(cx + triSize, cy + arrowReach - triSize * 0.6f)
                close()
            },
            color = if (down) activeArrow else inactiveArrow
        )
        // Left triangle
        drawPath(
            path = Path().apply {
                moveTo(cx - arrowReach - triSize * 0.6f, cy)
                lineTo(cx - arrowReach + triSize * 0.6f, cy - triSize)
                lineTo(cx - arrowReach + triSize * 0.6f, cy + triSize)
                close()
            },
            color = if (left) activeArrow else inactiveArrow
        )
        // Right triangle
        drawPath(
            path = Path().apply {
                moveTo(cx + arrowReach + triSize * 0.6f, cy)
                lineTo(cx + arrowReach - triSize * 0.6f, cy - triSize)
                lineTo(cx + arrowReach - triSize * 0.6f, cy + triSize)
                close()
            },
            color = if (right) activeArrow else inactiveArrow
        )

        // Center thumb pivot dimple
        drawCircle(
            color = Color(0xFF1E1B2E),
            radius = halfArm * 0.45f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.14f),
            radius = halfArm * 0.45f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun VirtualControllerPortraitPreview() {
    RetroGBATheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GbaBackground)
        ) {
            VirtualController(
                layoutConfig = ControlLayoutPresets.defaultProfile().portrait,
                opacity = 0.85f,
                isFastForwardActive = true
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 844, heightDp = 390)
@Composable
fun VirtualControllerLandscapePreview() {
    RetroGBATheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GbaBackground)
        ) {
            VirtualController(
                layoutConfig = ControlLayoutPresets.defaultProfile().landscape,
                opacity = 0.85f,
                isFastForwardActive = false
            )
        }
    }
}
