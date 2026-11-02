package com.retro.gba.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.gba.data.model.ControlElementState
import com.retro.gba.data.prefs.UserPreferences
import com.retro.gba.emulator.GbaInput
import com.retro.gba.ui.haptics.HapticManager
import com.retro.gba.ui.theme.GbaBackground
import com.retro.gba.ui.theme.GbaSecondary
import com.retro.gba.ui.theme.LocalAppThemeColors
import com.retro.gba.ui.theme.RetroGBATheme
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Paleta de cores e propriedades visuais do controlador virtual.
 */
data class ControllerPalette(
    val isCleanMyBoy: Boolean,
    val dpadBackground: Color,
    val dpadPressed: Color,
    val dpadStroke: Color,
    val buttonABackground: Color,
    val buttonBBackground: Color,
    val buttonAbBackground: Color,
    val shoulderBackground: Color,
    val systemBackground: Color,
    val buttonPressedHighlight: Color,
    val buttonBorderColor: Color,
    val textColor: Color
)

fun getControllerPalette(
    theme: String = UserPreferences.THEME_FOLLOW_APP,
    appTheme: String = UserPreferences.APP_THEME_GBA_INDIGO
): ControllerPalette {
    val effectiveTheme = if (theme == UserPreferences.THEME_FOLLOW_APP) {
        when (appTheme) {
            UserPreferences.APP_THEME_GLACIER -> UserPreferences.THEME_GLACIER
            UserPreferences.APP_THEME_SP_PLATINUM -> UserPreferences.THEME_SP_PLATINUM
            UserPreferences.APP_THEME_FAMICOM -> UserPreferences.THEME_FAMICOM
            UserPreferences.APP_THEME_OLED_STEALTH -> UserPreferences.THEME_OLED_STEALTH
            else -> UserPreferences.THEME_GBA_INDIGO
        }
    } else {
        theme
    }

    return when (effectiveTheme) {
        UserPreferences.THEME_GLACIER -> ControllerPalette(
            isCleanMyBoy = true,
            dpadBackground = Color(0x55142436),
            dpadPressed = Color(0x9038BDF8),
            dpadStroke = Color(0x857DD3FC),
            buttonABackground = Color(0x55142436),
            buttonBBackground = Color(0x55142436),
            buttonAbBackground = Color(0x55142436),
            shoulderBackground = Color(0x55142436),
            systemBackground = Color(0x55142436),
            buttonPressedHighlight = Color(0x9938BDF8),
            buttonBorderColor = Color(0x857DD3FC),
            textColor = Color(0xFFF0F9FF)
        )
        UserPreferences.THEME_SP_PLATINUM -> ControllerPalette(
            isCleanMyBoy = false,
            dpadBackground = Color(0x701C2028),
            dpadPressed = Color(0x9560A5FA),
            dpadStroke = Color(0x8094A3B8),
            buttonABackground = Color(0x752A303C),
            buttonBBackground = Color(0x752A303C),
            buttonAbBackground = Color(0x75222731),
            shoulderBackground = Color(0x70222731),
            systemBackground = Color(0x70222731),
            buttonPressedHighlight = Color(0xA060A5FA),
            buttonBorderColor = Color(0x80CBD5E1),
            textColor = Color(0xFFF8FAFC)
        )
        UserPreferences.THEME_FAMICOM -> ControllerPalette(
            isCleanMyBoy = false,
            dpadBackground = Color(0x751E181B),
            dpadPressed = Color(0x99EAB308),
            dpadStroke = Color(0x80EAB308),
            buttonABackground = Color(0x80991B1B),
            buttonBBackground = Color(0x807F1D1D),
            buttonAbBackground = Color(0x805F1212),
            shoulderBackground = Color(0x702E1920),
            systemBackground = Color(0x702E1920),
            buttonPressedHighlight = Color(0xB5EAB308),
            buttonBorderColor = Color(0x80FACC15),
            textColor = Color(0xFFFFF1F2)
        )
        UserPreferences.THEME_OLED_STEALTH, UserPreferences.THEME_CLEAN_MYBOY -> ControllerPalette(
            isCleanMyBoy = true,
            dpadBackground = Color(0x3514141C),
            dpadPressed = Color(0x75FFFFFF),
            dpadStroke = Color.White.copy(alpha = 0.55f),
            buttonABackground = Color(0x3514141C),
            buttonBBackground = Color(0x3514141C),
            buttonAbBackground = Color(0x3514141C),
            shoulderBackground = Color(0x3514141C),
            systemBackground = Color(0x3514141C),
            buttonPressedHighlight = Color.White.copy(alpha = 0.70f),
            buttonBorderColor = Color.White.copy(alpha = 0.55f),
            textColor = Color.White.copy(alpha = 0.95f)
        )
        else -> /* THEME_GBA_INDIGO, THEME_RETRO_GBA, fallback */ ControllerPalette(
            isCleanMyBoy = false,
            dpadBackground = Color(0x702A273B),
            dpadPressed = Color(0x955D48B3),
            dpadStroke = Color(0x90A29BFE),
            buttonABackground = Color(0x808C3B4E), // Vinho nostálgico clássico A
            buttonBBackground = Color(0x806B2D3C), // Vinho nostálgico clássico B
            buttonAbBackground = Color(0x80502A3C),
            shoulderBackground = Color(0x70322C4A),
            systemBackground = Color(0x702F2944),
            buttonPressedHighlight = Color(0xB0A29BFE),
            buttonBorderColor = Color(0x80A29BFE),
            textColor = Color(0xFFF1F0F7)
        )
    }
}

/**
 * D-Pad Contínuo vetorial fiel ao My Boy!:
 * 4 blocos retangulares arredondados separados em cruz, com setas chanfradas/pentagonais vazadas
 * e centro aberto. O bloco inteiro escala como um único grupo ("dpad").
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ContinuousDPad(
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    palette: ControllerPalette = getControllerPalette(UserPreferences.THEME_CLEAN_MYBOY),
    size: Dp = 136.dp,
    isEditMode: Boolean = false,
    onPressHaptic: () -> Unit = {}
) {
    var currentMask by remember { mutableIntStateOf(0) }

    DisposableEffect(isEditMode) {
        onDispose {
            if (currentMask != 0) {
                onButtonChange(currentMask, false)
                currentMask = 0
            }
            onButtonChange(
                GbaInput.BUTTON_UP or GbaInput.BUTTON_DOWN or GbaInput.BUTTON_LEFT or GbaInput.BUTTON_RIGHT,
                false
            )
        }
    }

    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    val centerPx = sizePx / 2f
    val deadzonePx = sizePx * 0.12f
    val outerRadiusPx = sizePx * 0.58f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .pointerInteropFilter { event ->
                if (isEditMode) return@pointerInteropFilter false

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN -> {
                        val dx = event.x - centerPx
                        val dy = event.y - centerPx
                        val dist = hypot(dx, dy)

                        val newMask = if (dist < deadzonePx || dist > outerRadiusPx) {
                            0
                        } else {
                            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                            if (angle < 0) angle += 360.0

                            when {
                                angle >= 337.5 || angle < 22.5 -> GbaInput.BUTTON_RIGHT
                                angle in 22.5..67.5 -> GbaInput.BUTTON_DOWN or GbaInput.BUTTON_RIGHT
                                angle in 67.5..112.5 -> GbaInput.BUTTON_DOWN
                                angle in 112.5..157.5 -> GbaInput.BUTTON_DOWN or GbaInput.BUTTON_LEFT
                                angle in 157.5..202.5 -> GbaInput.BUTTON_LEFT
                                angle in 202.5..247.5 -> GbaInput.BUTTON_UP or GbaInput.BUTTON_LEFT
                                angle in 247.5..292.5 -> GbaInput.BUTTON_UP
                                angle in 292.5..337.5 -> GbaInput.BUTTON_UP or GbaInput.BUTTON_RIGHT
                                else -> 0
                            }
                        }

                        if (newMask != currentMask) {
                            val released = currentMask and newMask.inv()
                            if (released != 0) {
                                onButtonChange(released, false)
                            }
                            val pressed = newMask and currentMask.inv()
                            if (pressed != 0) {
                                onButtonChange(pressed, true)
                                onPressHaptic()
                            }
                            currentMask = newMask
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                        if (currentMask != 0) {
                            onButtonChange(currentMask, false)
                            currentMask = 0
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val armThick = w * 0.33f
            val armLen = w * 0.40f
            val cornerRad = CornerRadius(7.dp.toPx(), 7.dp.toPx())
            val strokeWidth = 1.75.dp.toPx()

            val isUpPressed = (currentMask and GbaInput.BUTTON_UP) != 0
            val isDownPressed = (currentMask and GbaInput.BUTTON_DOWN) != 0
            val isLeftPressed = (currentMask and GbaInput.BUTTON_LEFT) != 0
            val isRightPressed = (currentMask and GbaInput.BUTTON_RIGHT) != 0

            // 1. Bloco de Cima
            val upLeft = (w - armThick) / 2f
            val upTop = 0f
            drawRoundRect(
                color = if (isUpPressed) palette.dpadPressed else palette.dpadBackground,
                topLeft = Offset(upLeft, upTop),
                size = Size(armThick, armLen),
                cornerRadius = cornerRad
            )
            drawRoundRect(
                color = palette.dpadStroke,
                topLeft = Offset(upLeft, upTop),
                size = Size(armThick, armLen),
                cornerRadius = cornerRad,
                style = Stroke(width = strokeWidth)
            )

            // Seta Pentagonal Vazada Cima (estilo My Boy!)
            val upMidX = w / 2f
            val upArrowTop = armLen * 0.22f
            val upArrowW = armThick * 0.38f
            val upArrowH = armLen * 0.46f
            val upPath = Path().apply {
                moveTo(upMidX, upArrowTop) // Ponta da seta
                lineTo(upMidX + upArrowW, upArrowTop + upArrowH * 0.45f)
                lineTo(upMidX + upArrowW, upArrowTop + upArrowH)
                lineTo(upMidX - upArrowW, upArrowTop + upArrowH)
                lineTo(upMidX - upArrowW, upArrowTop + upArrowH * 0.45f)
                close()
            }
            drawPath(upPath, palette.dpadStroke, style = Stroke(width = 1.6.dp.toPx()))

            // 2. Bloco de Baixo
            val downLeft = (w - armThick) / 2f
            val downTop = h - armLen
            drawRoundRect(
                color = if (isDownPressed) palette.dpadPressed else palette.dpadBackground,
                topLeft = Offset(downLeft, downTop),
                size = Size(armThick, armLen),
                cornerRadius = cornerRad
            )
            drawRoundRect(
                color = palette.dpadStroke,
                topLeft = Offset(downLeft, downTop),
                size = Size(armThick, armLen),
                cornerRadius = cornerRad,
                style = Stroke(width = strokeWidth)
            )

            // Seta Pentagonal Vazada Baixo
            val downMidX = w / 2f
            val downArrowBottom = h - armLen * 0.22f
            val downArrowTop = downArrowBottom - upArrowH
            val downPath = Path().apply {
                moveTo(downMidX, downArrowBottom) // Ponta da seta
                lineTo(downMidX + upArrowW, downArrowBottom - upArrowH * 0.45f)
                lineTo(downMidX + upArrowW, downArrowTop)
                lineTo(downMidX - upArrowW, downArrowTop)
                lineTo(downMidX - upArrowW, downArrowBottom - upArrowH * 0.45f)
                close()
            }
            drawPath(downPath, palette.dpadStroke, style = Stroke(width = 1.6.dp.toPx()))

            // 3. Bloco da Esquerda
            val leftLeft = 0f
            val leftTop = (h - armThick) / 2f
            drawRoundRect(
                color = if (isLeftPressed) palette.dpadPressed else palette.dpadBackground,
                topLeft = Offset(leftLeft, leftTop),
                size = Size(armLen, armThick),
                cornerRadius = cornerRad
            )
            drawRoundRect(
                color = palette.dpadStroke,
                topLeft = Offset(leftLeft, leftTop),
                size = Size(armLen, armThick),
                cornerRadius = cornerRad,
                style = Stroke(width = strokeWidth)
            )

            // Seta Pentagonal Vazada Esquerda
            val leftMidY = h / 2f
            val leftArrowLeft = armLen * 0.22f
            val leftPath = Path().apply {
                moveTo(leftArrowLeft, leftMidY) // Ponta da seta
                lineTo(leftArrowLeft + upArrowH * 0.45f, leftMidY - upArrowW)
                lineTo(leftArrowLeft + upArrowH, leftMidY - upArrowW)
                lineTo(leftArrowLeft + upArrowH, leftMidY + upArrowW)
                lineTo(leftArrowLeft + upArrowH * 0.45f, leftMidY + upArrowW)
                close()
            }
            drawPath(leftPath, palette.dpadStroke, style = Stroke(width = 1.6.dp.toPx()))

            // 4. Bloco da Direita
            val rightLeft = w - armLen
            val rightTop = (h - armThick) / 2f
            drawRoundRect(
                color = if (isRightPressed) palette.dpadPressed else palette.dpadBackground,
                topLeft = Offset(rightLeft, rightTop),
                size = Size(armLen, armThick),
                cornerRadius = cornerRad
            )
            drawRoundRect(
                color = palette.dpadStroke,
                topLeft = Offset(rightLeft, rightTop),
                size = Size(armLen, armThick),
                cornerRadius = cornerRad,
                style = Stroke(width = strokeWidth)
            )

            // Seta Pentagonal Vazada Direita
            val rightMidY = h / 2f
            val rightArrowRight = w - armLen * 0.22f
            val rightPath = Path().apply {
                moveTo(rightArrowRight, rightMidY) // Ponta da seta
                lineTo(rightArrowRight - upArrowH * 0.45f, rightMidY - upArrowW)
                lineTo(rightArrowRight - upArrowH, rightMidY - upArrowW)
                lineTo(rightArrowRight - upArrowH, rightMidY + upArrowW)
                lineTo(rightArrowRight - upArrowH * 0.45f, rightMidY + upArrowW)
                close()
            }
            drawPath(rightPath, palette.dpadStroke, style = Stroke(width = 1.6.dp.toPx()))

            // Centro suavemente translúcido
            drawCircle(
                color = Color.Black.copy(alpha = 0.25f),
                radius = armThick * 0.26f,
                center = Offset(w / 2f, h / 2f)
            )
        }
    }
}

/**
 * Gatilho superior anatômico (L ou R) desenhado via Path com o chanfro curvo fiel ao My Boy!
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AnatomicalShoulderButton(
    isLeft: Boolean,
    buttonMask: Int,
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1.0f,
    palette: ControllerPalette = getControllerPalette(UserPreferences.THEME_CLEAN_MYBOY),
    isEditMode: Boolean = false,
    onPressHaptic: () -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }

    DisposableEffect(buttonMask, isEditMode) {
        onDispose {
            if (isPressed) {
                isPressed = false
                onButtonChange(buttonMask, false)
            }
        }
    }

    val widthDp = (80.dp * scale).coerceIn(40.dp, 160.dp)
    val heightDp = (36.dp * scale).coerceIn(20.dp, 80.dp)

    Box(
        contentAlignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd,
        modifier = modifier
            .width(widthDp)
            .height(heightDp)
            .pointerInteropFilter { event ->
                if (isEditMode) return@pointerInteropFilter false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        if (!isPressed) {
                            isPressed = true
                            onPressHaptic()
                            onButtonChange(buttonMask, true)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isPressed) {
                            isPressed = false
                            onButtonChange(buttonMask, false)
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = 1.75.dp.toPx()
            val r = 8.dp.toPx()

            val shoulderPath = Path().apply {
                if (isLeft) {
                    // Gatilho L: lateral externa esquerda alta, chanfro descendo para direita
                    moveTo(0f, r)
                    quadraticTo(0f, 0f, r, 0f)
                    lineTo(w - r, 0f)
                    quadraticTo(w, 0f, w, r * 0.8f)
                    // Curva inferior que sobe para dentro
                    quadraticTo(w * 0.50f, h * 0.40f, r, h)
                    quadraticTo(0f, h, 0f, h - r)
                    close()
                } else {
                    // Gatilho R: lateral externa direita alta, chanfro descendo para esquerda
                    moveTo(w, r)
                    quadraticTo(w, 0f, w - r, 0f)
                    lineTo(r, 0f)
                    quadraticTo(0f, 0f, 0f, r * 0.8f)
                    // Curva inferior que sobe para dentro
                    quadraticTo(w * 0.50f, h * 0.40f, w - r, h)
                    quadraticTo(w, h, w, h - r)
                    close()
                }
            }

            drawPath(
                path = shoulderPath,
                color = if (isPressed) palette.buttonPressedHighlight else palette.shoulderBackground
            )
            drawPath(
                path = shoulderPath,
                color = palette.buttonBorderColor,
                style = Stroke(width = strokeW)
            )
        }

        Text(
            text = if (isLeft) "L" else "R",
            color = palette.textColor,
            fontWeight = FontWeight.Bold,
            fontSize = (15f * scale.coerceIn(0.7f, 1.4f)).sp,
            modifier = Modifier.padding(horizontal = (12.dp * scale).coerceIn(6.dp, 24.dp))
        )
    }
}

/**
 * Botão clássico de Menu vazado (☰) estilo My Boy!:
 * Quadrado com cantos arredondados contendo 3 linhas horizontais internas.
 */
@Composable
fun MenuHambugerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1.0f,
    palette: ControllerPalette = getControllerPalette(UserPreferences.THEME_CLEAN_MYBOY),
    isEditMode: Boolean = false,
    onPressHaptic: () -> Unit = {}
) {
    val sizeDp = (38.dp * scale).coerceIn(24.dp, 80.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.systemBackground)
            .border(1.75.dp, palette.buttonBorderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = !isEditMode) {
                onPressHaptic()
                onClick()
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(sizeDp * 0.22f)) {
            val w = this.size.width
            val h = this.size.height
            val lineStroke = (2.2.dp * scale.coerceIn(0.7f, 1.4f)).toPx()
            val lineColor = palette.textColor

            // 3 Barras horizontais
            drawLine(lineColor, Offset(0f, h * 0.15f), Offset(w, h * 0.15f), strokeWidth = lineStroke)
            drawLine(lineColor, Offset(0f, h * 0.50f), Offset(w, h * 0.50f), strokeWidth = lineStroke)
            drawLine(lineColor, Offset(0f, h * 0.85f), Offset(w, h * 0.85f), strokeWidth = lineStroke)
        }
    }
}

/**
 * Pílula horizontal independente vazada estilo My Boy! para SELECT e START.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SystemPillButton(
    text: String,
    buttonMask: Int,
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1.0f,
    palette: ControllerPalette = getControllerPalette(UserPreferences.THEME_CLEAN_MYBOY),
    isEditMode: Boolean = false,
    onPressHaptic: () -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }

    DisposableEffect(buttonMask, isEditMode) {
        onDispose {
            if (isPressed) {
                isPressed = false
                onButtonChange(buttonMask, false)
            }
        }
    }

    val pillWidth = (46.dp * scale).coerceIn(26.dp, 96.dp)
    val pillHeight = (18.dp * scale).coerceIn(12.dp, 40.dp)

    Column(
        modifier = modifier
            .pointerInteropFilter { event ->
                if (isEditMode) return@pointerInteropFilter false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        if (!isPressed) {
                            isPressed = true
                            onPressHaptic()
                            onButtonChange(buttonMask, true)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isPressed) {
                            isPressed = false
                            onButtonChange(buttonMask, false)
                        }
                        true
                    }
                    else -> false
                }
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(pillWidth)
                .height(pillHeight)
                .clip(RoundedCornerShape(pillHeight / 2f))
                .background(if (isPressed) palette.buttonPressedHighlight else palette.systemBackground)
                .border(1.6.dp, if (isPressed) palette.buttonPressedHighlight else palette.buttonBorderColor, RoundedCornerShape(pillHeight / 2f))
        )
        Text(
            text = text,
            color = palette.textColor,
            fontSize = (8.5f * scale.coerceIn(0.7f, 1.3f)).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Botão circular de ação individual (A, B ou A+B) vazado e nítido.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ActionCircleButton(
    text: String,
    buttonMask: Int,
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    containerColor: Color = Color(0x4014141C),
    pressedColor: Color = Color(0x8800E5FF),
    borderColor: Color = Color.White.copy(alpha = 0.65f),
    textColor: Color = Color.White,
    isEditMode: Boolean = false,
    onPressHaptic: () -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }

    DisposableEffect(buttonMask, isEditMode) {
        onDispose {
            if (isPressed) {
                isPressed = false
                onButtonChange(buttonMask, false)
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isPressed) pressedColor else containerColor)
            .border(1.75.dp, if (isPressed) pressedColor else borderColor, CircleShape)
            .pointerInteropFilter { event ->
                if (isEditMode) return@pointerInteropFilter false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        if (!isPressed) {
                            isPressed = true
                            onPressHaptic()
                            onButtonChange(buttonMask, true)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isPressed) {
                            isPressed = false
                            onButtonChange(buttonMask, false)
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.38f).sp
        )
    }
}

/**
 * Container arrastável individual para posicionamento e escala independente no Modo de Edição.
 */
@Composable
fun EditableControlBox(
    id: String,
    label: String,
    isEditMode: Boolean,
    isSelected: Boolean,
    onSelect: (String) -> Unit,
    state: ControlElementState,
    onOffsetChange: (id: String, dxDp: Float, dyDp: Float) -> Unit,
    onScaleChange: (id: String, scale: Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current

    val currentDx = state.dx
    val currentDy = state.dy
    val currentScale = state.scale

    val updatedOnOffset = rememberUpdatedState(onOffsetChange)
    val updatedOnScale = rememberUpdatedState(onScaleChange)

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    (currentDx * density.density).roundToInt(),
                    (currentDy * density.density).roundToInt()
                )
            }
            .then(
                if (isEditMode) {
                    Modifier
                        .clickable { onSelect(id) }
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) GbaSecondary else Color.White.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(
                            color = if (isSelected) GbaSecondary.copy(alpha = 0.15f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .pointerInput(id) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                onSelect(id)
                                val newDx = (currentDx + pan.x / density.density).coerceIn(-360f, 360f)
                                val newDy = (currentDy + pan.y / density.density).coerceIn(-260f, 260f)
                                updatedOnOffset.value(id, newDx, newDy)

                                if (zoom != 1f) {
                                    val newScale = (currentScale * zoom).coerceIn(0.50f, 1.80f)
                                    updatedOnScale.value(id, newScale)
                                }
                            }
                        }
                } else {
                    Modifier
                }
            )
    ) {
        content()
        if (isEditMode) {
            Surface(
                color = if (isSelected) GbaSecondary else Color(0xCC1E1A2E),
                shape = RoundedCornerShape(bottomEnd = 4.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = if (isSelected) "$label ${(currentScale * 100).toInt()}%" else label,
                    color = if (isSelected) Color.Black else Color.White,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * Painel flutuante compacto posicionado no centro da tela do jogo no Modo de Edição,
 * permitindo ajustar o tamanho do botão selecionado sem cobrir L ou R.
 */
@Composable
fun LayoutEditOverlayCard(
    selectedControlId: String?,
    selectedControlLabel: String,
    scale: Float,
    isLandscape: Boolean,
    onScaleChange: (Float) -> Unit,
    onResetCurrentItem: () -> Unit,
    onResetAll: () -> Unit,
    onExitEditMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xF0181524),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, GbaSecondary.copy(alpha = 0.75f)),
        shadowElevation = 8.dp,
        modifier = modifier
            .widthIn(max = 340.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Edição (${if (isLandscape) "Deitado" else "Em Pé"})",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                    Text(
                        text = if (selectedControlId != null) "Botão: $selectedControlLabel" else "Toque em um botão para editar",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = onExitEditMode,
                    colors = ButtonDefaults.buttonColors(containerColor = GbaSecondary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.Black)
                    Spacer(Modifier.width(3.dp))
                    Text("Salvar", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            if (selectedControlId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${(scale * 100).toInt()}%",
                        fontSize = 11.sp,
                        color = GbaSecondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(38.dp)
                    )
                    Slider(
                        value = scale,
                        onValueChange = onScaleChange,
                        valueRange = 0.50f..1.80f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (selectedControlId != null) {
                    TextButton(
                        onClick = onResetCurrentItem,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.LightGray)
                        Spacer(Modifier.width(3.dp))
                        Text("Resetar Botão", fontSize = 10.sp, color = Color.LightGray)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                TextButton(
                    onClick = onResetAll,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFFFF8A80))
                    Spacer(Modifier.width(3.dp))
                    Text("Resetar Tudo", fontSize = 10.sp, color = Color(0xFFFF8A80))
                }
            }
        }
    }
}

/**
 * Controlador Virtual em Modo Retrato (Em Pé):
 * Distribuído na metade inferior da tela abaixo do display do GBA com estética fiel ao My Boy!
 */
@Composable
fun PortraitVirtualController(
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    onMenuClick: () -> Unit,
    onTurboToggle: () -> Unit,
    modifier: Modifier = Modifier,
    opacity: Float = 0.85f,
    theme: String = UserPreferences.THEME_FOLLOW_APP,
    controls: Map<String, ControlElementState> = emptyMap(),
    onControlOffsetChange: (id: String, dxDp: Float, dyDp: Float) -> Unit = { _, _, _ -> },
    onControlScaleChange: (id: String, scale: Float) -> Unit = { _, _ -> },
    onResetSingleControl: (id: String) -> Unit = {},
    onResetLayout: () -> Unit = {},
    onExitEditMode: () -> Unit = {},
    showAbCombo: Boolean = false,
    showQuickSaveLoad: Boolean = false,
    showTurboButton: Boolean = false,
    showFpsCounter: Boolean = false,
    currentFps: Int? = null,
    isTurboActive: Boolean = false,
    isEditMode: Boolean = false,
    onQuickSave: () -> Unit = {},
    onQuickLoad: () -> Unit = {},
    hapticFeedbackEnabled: Boolean = true
) {
    val context = LocalContext.current
    val hapticManager = remember(context) { HapticManager(context) }
    val onHapticClick = remember(hapticFeedbackEnabled) {
        { hapticManager.performButtonClick(hapticFeedbackEnabled) }
    }
    val currentAppTheme = LocalAppThemeColors.current.id
    val palette = remember(theme, currentAppTheme) { getControllerPalette(theme, currentAppTheme) }

    var selectedControlId by remember { mutableStateOf<String?>(null) }

    val labels = mapOf(
        "dpad" to "D-Pad",
        "l_button" to "L",
        "r_button" to "R",
        "menu_button" to "Menu",
        "select_button" to "SELECT",
        "start_button" to "START",
        "b_button" to "B",
        "a_button" to "A",
        "ab_button" to "A+B",
        "turbo_button" to "Turbo",
        "quicksave_button" to "Save",
        "quickload_button" to "Load",
        "fps_badge" to "FPS"
    )

    val currentSelectedState = controls[selectedControlId] ?: ControlElementState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(opacity.coerceIn(0.2f, 1.0f))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Painel flutuante de edição no topo/centro
        if (isEditMode) {
            LayoutEditOverlayCard(
                selectedControlId = selectedControlId,
                selectedControlLabel = labels[selectedControlId] ?: "Nenhum",
                scale = currentSelectedState.scale,
                isLandscape = false,
                onScaleChange = { newScale ->
                    selectedControlId?.let { onControlScaleChange(it, newScale) }
                },
                onResetCurrentItem = {
                    selectedControlId?.let { onResetSingleControl(it) }
                },
                onResetAll = onResetLayout,
                onExitEditMode = onExitEditMode,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            )
        }

        // Faixa Superior dos Controles: L, Menu (☰), R
        val lState = controls["l_button"] ?: ControlElementState()
        val menuState = controls["menu_button"] ?: ControlElementState()
        val rState = controls["r_button"] ?: ControlElementState()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = if (isEditMode) 96.dp else 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditableControlBox(
                id = "l_button",
                label = "L",
                isEditMode = isEditMode,
                isSelected = selectedControlId == "l_button",
                onSelect = { selectedControlId = it },
                state = lState,
                onOffsetChange = onControlOffsetChange,
                onScaleChange = onControlScaleChange
            ) {
                AnatomicalShoulderButton(
                    isLeft = true,
                    buttonMask = GbaInput.BUTTON_L,
                    onButtonChange = onButtonChange,
                    scale = lState.scale,
                    palette = palette,
                    isEditMode = isEditMode,
                    onPressHaptic = onHapticClick
                )
            }

            EditableControlBox(
                id = "menu_button",
                label = "Menu",
                isEditMode = isEditMode,
                isSelected = selectedControlId == "menu_button",
                onSelect = { selectedControlId = it },
                state = menuState,
                onOffsetChange = onControlOffsetChange,
                onScaleChange = onControlScaleChange
            ) {
                MenuHambugerButton(
                    onClick = onMenuClick,
                    scale = menuState.scale,
                    palette = palette,
                    isEditMode = isEditMode,
                    onPressHaptic = onHapticClick
                )
            }

            EditableControlBox(
                id = "r_button",
                label = "R",
                isEditMode = isEditMode,
                isSelected = selectedControlId == "r_button",
                onSelect = { selectedControlId = it },
                state = rState,
                onOffsetChange = onControlOffsetChange,
                onScaleChange = onControlScaleChange
            ) {
                AnatomicalShoulderButton(
                    isLeft = false,
                    buttonMask = GbaInput.BUTTON_R,
                    onButtonChange = onButtonChange,
                    scale = rState.scale,
                    palette = palette,
                    isEditMode = isEditMode,
                    onPressHaptic = onHapticClick
                )
            }
        }

        // D-Pad à esquerda
        val dpadState = controls["dpad"] ?: ControlElementState()
        EditableControlBox(
            id = "dpad",
            label = "D-Pad",
            isEditMode = isEditMode,
            isSelected = selectedControlId == "dpad",
            onSelect = { selectedControlId = it },
            state = dpadState,
            onOffsetChange = onControlOffsetChange,
            onScaleChange = onControlScaleChange,
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 12.dp)
        ) {
            ContinuousDPad(
                onButtonChange = onButtonChange,
                palette = palette,
                size = (136.dp * dpadState.scale).coerceIn(68.dp, 240.dp),
                isEditMode = isEditMode,
                onPressHaptic = onHapticClick
            )
        }

        // SELECT e START na direita (posicionados logo acima de B e A)
        val selectState = controls["select_button"] ?: ControlElementState()
        val startState = controls["start_button"] ?: ControlElementState()

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 76.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditableControlBox(
                id = "select_button",
                label = "SELECT",
                isEditMode = isEditMode,
                isSelected = selectedControlId == "select_button",
                onSelect = { selectedControlId = it },
                state = selectState,
                onOffsetChange = onControlOffsetChange,
                onScaleChange = onControlScaleChange
            ) {
                SystemPillButton(
                    text = "SELECT",
                    buttonMask = GbaInput.BUTTON_SELECT,
                    onButtonChange = onButtonChange,
                    scale = selectState.scale,
                    palette = palette,
                    isEditMode = isEditMode,
                    onPressHaptic = onHapticClick
                )
            }

            EditableControlBox(
                id = "start_button",
                label = "START",
                isEditMode = isEditMode,
                isSelected = selectedControlId == "start_button",
                onSelect = { selectedControlId = it },
                state = startState,
                onOffsetChange = onControlOffsetChange,
                onScaleChange = onControlScaleChange
            ) {
                SystemPillButton(
                    text = "START",
                    buttonMask = GbaInput.BUTTON_START,
                    onButtonChange = onButtonChange,
                    scale = startState.scale,
                    palette = palette,
                    isEditMode = isEditMode,
                    onPressHaptic = onHapticClick
                )
            }
        }

        // Botões B e A alinhados lado a lado na base direita
        val bState = controls["b_button"] ?: ControlElementState()
        val aState = controls["a_button"] ?: ControlElementState()

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 12.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditableControlBox(
                id = "b_button",
                label = "B",
                isEditMode = isEditMode,
                isSelected = selectedControlId == "b_button",
                onSelect = { selectedControlId = it },
                state = bState,
                onOffsetChange = onControlOffsetChange,
                onScaleChange = onControlScaleChange
            ) {
                ActionCircleButton(
                    text = "B",
                    buttonMask = GbaInput.BUTTON_B,
                    onButtonChange = onButtonChange,
                    size = (54.dp * bState.scale).coerceIn(28.dp, 96.dp),
                    containerColor = palette.buttonBBackground,
                    pressedColor = palette.buttonPressedHighlight,
                    borderColor = palette.buttonBorderColor,
                    textColor = palette.textColor,
                    isEditMode = isEditMode,
                    onPressHaptic = onHapticClick
                )
            }

            EditableControlBox(
                id = "a_button",
                label = "A",
                isEditMode = isEditMode,
                isSelected = selectedControlId == "a_button",
                onSelect = { selectedControlId = it },
                state = aState,
                onOffsetChange = onControlOffsetChange,
                onScaleChange = onControlScaleChange
            ) {
                ActionCircleButton(
                    text = "A",
                    buttonMask = GbaInput.BUTTON_A,
                    onButtonChange = onButtonChange,
                    size = (54.dp * aState.scale).coerceIn(28.dp, 96.dp),
                    containerColor = palette.buttonABackground,
                    pressedColor = palette.buttonPressedHighlight,
                    borderColor = palette.buttonBorderColor,
                    textColor = palette.textColor,
                    isEditMode = isEditMode,
                    onPressHaptic = onHapticClick
                )
            }
        }

        // Extras opcionais (somente se ativados no menu)
        if (showAbCombo) {
            val abState = controls["ab_button"] ?: ControlElementState()
            EditableControlBox(
                id = "ab_button",
                label = "A+B",
                isEditMode = isEditMode,
                isSelected = selectedControlId == "ab_button",
                onSelect = { selectedControlId = it },
                state = abState,
                onOffsetChange = onControlOffsetChange,
                onScaleChange = onControlScaleChange,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 142.dp, end = 40.dp)
            ) {
                ActionCircleButton(
                    text = "A+B",
                    buttonMask = GbaInput.BUTTON_A or GbaInput.BUTTON_B,
                    onButtonChange = onButtonChange,
                    size = (40.dp * abState.scale).coerceIn(24.dp, 72.dp),
                    containerColor = palette.buttonAbBackground,
                    pressedColor = palette.buttonPressedHighlight,
                    borderColor = palette.buttonBorderColor,
                    textColor = palette.textColor,
                    isEditMode = isEditMode,
                    onPressHaptic = onHapticClick
                )
            }
        }

        if (showTurboButton) {
            val turboState = controls["turbo_button"] ?: ControlElementState()
            EditableControlBox(
                id = "turbo_button",
                label = "Turbo",
                isEditMode = isEditMode,
                isSelected = selectedControlId == "turbo_button",
                onSelect = { selectedControlId = it },
                state = turboState,
                onOffsetChange = onControlOffsetChange,
                onScaleChange = onControlScaleChange,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp)
            ) {
                IconButton(
                    onClick = {
                        hapticManager.performTurboToggle(hapticFeedbackEnabled)
                        onTurboToggle()
                    },
                    modifier = Modifier
                        .size((36.dp * turboState.scale).coerceIn(24.dp, 64.dp))
                        .background(if (isTurboActive) GbaSecondary else palette.systemBackground, CircleShape)
                        .border(1.5.dp, palette.buttonBorderColor, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Turbo",
                        tint = if (isTurboActive) Color.Black else palette.textColor,
                        modifier = Modifier.size(20.dp * turboState.scale.coerceIn(0.7f, 1.3f))
                    )
                }
            }
        }

        if (showQuickSaveLoad) {
            val qsState = controls["quicksave_button"] ?: ControlElementState()
            val qlState = controls["quickload_button"] ?: ControlElementState()

            Row(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditableControlBox(
                    id = "quicksave_button",
                    label = "Save",
                    isEditMode = isEditMode,
                    isSelected = selectedControlId == "quicksave_button",
                    onSelect = { selectedControlId = it },
                    state = qsState,
                    onOffsetChange = onControlOffsetChange,
                    onScaleChange = onControlScaleChange
                ) {
                    IconButton(
                        onClick = {
                            onHapticClick()
                            onQuickSave()
                        },
                        modifier = Modifier
                            .size((34.dp * qsState.scale).coerceIn(24.dp, 64.dp))
                            .background(palette.systemBackground, CircleShape)
                            .border(1.5.dp, palette.buttonBorderColor, CircleShape)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Quick Save", tint = palette.textColor, modifier = Modifier.size(17.dp * qsState.scale))
                    }
                }

                EditableControlBox(
                    id = "quickload_button",
                    label = "Load",
                    isEditMode = isEditMode,
                    isSelected = selectedControlId == "quickload_button",
                    onSelect = { selectedControlId = it },
                    state = qlState,
                    onOffsetChange = onControlOffsetChange,
                    onScaleChange = onControlScaleChange
                ) {
                    IconButton(
                        onClick = {
                            onHapticClick()
                            onQuickLoad()
                        },
                        modifier = Modifier
                            .size((34.dp * qlState.scale).coerceIn(24.dp, 64.dp))
                            .background(palette.systemBackground, CircleShape)
                            .border(1.5.dp, palette.buttonBorderColor, CircleShape)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = "Quick Load", tint = palette.textColor, modifier = Modifier.size(17.dp * qlState.scale))
                    }
                }
            }
        }

        if (showFpsCounter && currentFps != null) {
            val fpsState = controls["fps_badge"] ?: ControlElementState()
            EditableControlBox(
                id = "fps_badge",
                label = "FPS",
                isEditMode = isEditMode,
                isSelected = selectedControlId == "fps_badge",
                onSelect = { selectedControlId = it },
                state = fpsState,
                onOffsetChange = onControlOffsetChange,
                onScaleChange = onControlScaleChange,
                modifier = Modifier.align(Alignment.TopStart).padding(top = 58.dp)
            ) {
                Surface(
                    color = Color(0x991B192A),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.buttonBorderColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "$currentFps FPS",
                        color = if (currentFps >= 50) Color(0xFF81C784) else Color(0xFFFFB74D),
                        fontSize = (10f * fpsState.scale.coerceIn(0.7f, 1.4f)).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Controlador Virtual em Modo Paisagem (Deitado):
 * Fiel à referência clássica do My Boy!:
 * - L no topo esquerdo, R no topo direito
 * - Menu (☰) no topo centro
 * - D-Pad no canto inferior esquerdo
 * - B e A lado a lado no canto inferior direito
 * - SELECT e START em pílulas alinhadas logo à esquerda de B
 */
@Composable
fun LandscapeVirtualController(
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    onMenuClick: () -> Unit,
    onTurboToggle: () -> Unit,
    modifier: Modifier = Modifier,
    opacity: Float = 0.85f,
    theme: String = UserPreferences.THEME_FOLLOW_APP,
    controls: Map<String, ControlElementState> = emptyMap(),
    onControlOffsetChange: (id: String, dxDp: Float, dyDp: Float) -> Unit = { _, _, _ -> },
    onControlScaleChange: (id: String, scale: Float) -> Unit = { _, _ -> },
    onResetSingleControl: (id: String) -> Unit = {},
    onResetLayout: () -> Unit = {},
    onExitEditMode: () -> Unit = {},
    showAbCombo: Boolean = false,
    showQuickSaveLoad: Boolean = false,
    showTurboButton: Boolean = false,
    showFpsCounter: Boolean = false,
    currentFps: Int? = null,
    isTurboActive: Boolean = false,
    isEditMode: Boolean = false,
    onQuickSave: () -> Unit = {},
    onQuickLoad: () -> Unit = {},
    hapticFeedbackEnabled: Boolean = true
) {
    val context = LocalContext.current
    val hapticManager = remember(context) { HapticManager(context) }
    val onHapticClick = remember(hapticFeedbackEnabled) {
        { hapticManager.performButtonClick(hapticFeedbackEnabled) }
    }
    val currentAppTheme = LocalAppThemeColors.current.id
    val palette = remember(theme, currentAppTheme) { getControllerPalette(theme, currentAppTheme) }

    var selectedControlId by remember { mutableStateOf<String?>(null) }

    val labels = mapOf(
        "dpad" to "D-Pad",
        "l_button" to "L",
        "r_button" to "R",
        "menu_button" to "Menu",
        "select_button" to "SELECT",
        "start_button" to "START",
        "b_button" to "B",
        "a_button" to "A",
        "ab_button" to "A+B",
        "turbo_button" to "Turbo",
        "quicksave_button" to "Save",
        "quickload_button" to "Load",
        "fps_badge" to "FPS"
    )

    val currentSelectedState = controls[selectedControlId] ?: ControlElementState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(opacity.coerceIn(0.2f, 1.0f))
            .padding(10.dp)
    ) {
        // Painel flutuante de edição no centro da tela
        if (isEditMode) {
            LayoutEditOverlayCard(
                selectedControlId = selectedControlId,
                selectedControlLabel = labels[selectedControlId] ?: "Nenhum",
                scale = currentSelectedState.scale,
                isLandscape = true,
                onScaleChange = { newScale ->
                    selectedControlId?.let { onControlScaleChange(it, newScale) }
                },
                onResetCurrentItem = {
                    selectedControlId?.let { onResetSingleControl(it) }
                },
                onResetAll = onResetLayout,
                onExitEditMode = onExitEditMode,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 44.dp)
            )
        }

        // Topo Esquerdo: Gatilho L
        val lState = controls["l_button"] ?: ControlElementState()
        EditableControlBox(
            id = "l_button",
            label = "L",
            isEditMode = isEditMode,
            isSelected = selectedControlId == "l_button",
            onSelect = { selectedControlId = it },
            state = lState,
            onOffsetChange = onControlOffsetChange,
            onScaleChange = onControlScaleChange,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            AnatomicalShoulderButton(
                isLeft = true,
                buttonMask = GbaInput.BUTTON_L,
                onButtonChange = onButtonChange,
                scale = lState.scale,
                palette = palette,
                isEditMode = isEditMode,
                onPressHaptic = onHapticClick
            )
        }

        // Topo Centro: Botão de Menu (☰)
        val menuState = controls["menu_button"] ?: ControlElementState()
        EditableControlBox(
            id = "menu_button",
            label = "Menu",
            isEditMode = isEditMode,
            isSelected = selectedControlId == "menu_button",
            onSelect = { selectedControlId = it },
            state = menuState,
            onOffsetChange = onControlOffsetChange,
            onScaleChange = onControlScaleChange,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            MenuHambugerButton(
                onClick = onMenuClick,
                scale = menuState.scale,
                palette = palette,
                isEditMode = isEditMode,
                onPressHaptic = onHapticClick
            )
        }

        // Topo Direito: Gatilho R
        val rState = controls["r_button"] ?: ControlElementState()
        EditableControlBox(
            id = "r_button",
            label = "R",
            isEditMode = isEditMode,
            isSelected = selectedControlId == "r_button",
            onSelect = { selectedControlId = it },
            state = rState,
            onOffsetChange = onControlOffsetChange,
            onScaleChange = onControlScaleChange,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            AnatomicalShoulderButton(
                isLeft = false,
                buttonMask = GbaInput.BUTTON_R,
                onButtonChange = onButtonChange,
                scale = rState.scale,
                palette = palette,
                isEditMode = isEditMode,
                onPressHaptic = onHapticClick
            )
        }

        // Canto Inferior Esquerdo: D-Pad
        val dpadState = controls["dpad"] ?: ControlElementState()
        EditableControlBox(
            id = "dpad",
            label = "D-Pad",
            isEditMode = isEditMode,
            isSelected = selectedControlId == "dpad",
            onSelect = { selectedControlId = it },
            state = dpadState,
            onOffsetChange = onControlOffsetChange,
            onScaleChange = onControlScaleChange,
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            ContinuousDPad(
                onButtonChange = onButtonChange,
                palette = palette,
                size = (136.dp * dpadState.scale).coerceIn(68.dp, 240.dp),
                isEditMode = isEditMode,
                onPressHaptic = onHapticClick
            )
        }

        // Base Direita: SELECT e START alinhados logo à esquerda de B e A
        val selectState = controls["select_button"] ?: ControlElementState()
        val startState = controls["start_button"] ?: ControlElementState()

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 6.dp, end = 138.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditableControlBox(
                id = "select_button",
                label = "SELECT",
                isEditMode = isEditMode,
                isSelected = selectedControlId == "select_button",
                onSelect = { selectedControlId = it },
                state = selectState,
                onOffsetChange = onControlOffsetChange,
                onScaleChange = onControlScaleChange
            ) {
                SystemPillButton(
                    text = "SELECT",
                    buttonMask = GbaInput.BUTTON_SELECT,
                    onButtonChange = onButtonChange,
                    scale = selectState.scale,
                    palette = palette,
                    isEditMode = isEditMode,
                    onPressHaptic = onHapticClick
                )
            }

            EditableControlBox(
                id = "start_button",
                label = "START",
                isEditMode = isEditMode,
                isSelected = selectedControlId == "start_button",
                onSelect = { selectedControlId = it },
                state = startState,
                onOffsetChange = onControlOffsetChange,
                onScaleChange = onControlScaleChange
            ) {
                SystemPillButton(
                    text = "START",
                    buttonMask = GbaInput.BUTTON_START,
                    onButtonChange = onButtonChange,
                    scale = startState.scale,
                    palette = palette,
                    isEditMode = isEditMode,
                    onPressHaptic = onHapticClick
                )
            }
        }

        // Canto Inferior Direito: Botões B e A lado a lado na horizontal
        val bState = controls["b_button"] ?: ControlElementState()
        val aState = controls["a_button"] ?: ControlElementState()

        Row(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditableControlBox(
                id = "b_button",
                label = "B",
                isEditMode = isEditMode,
                isSelected = selectedControlId == "b_button",
                onSelect = { selectedControlId = it },
                state = bState,
                onOffsetChange = onControlOffsetChange,
                onScaleChange = onControlScaleChange
            ) {
                ActionCircleButton(
                    text = "B",
                    buttonMask = GbaInput.BUTTON_B,
                    onButtonChange = onButtonChange,
                    size = (54.dp * bState.scale).coerceIn(28.dp, 96.dp),
                    containerColor = palette.buttonBBackground,
                    pressedColor = palette.buttonPressedHighlight,
                    borderColor = palette.buttonBorderColor,
                    textColor = palette.textColor,
                    isEditMode = isEditMode,
                    onPressHaptic = onHapticClick
                )
            }

            EditableControlBox(
                id = "a_button",
                label = "A",
                isEditMode = isEditMode,
                isSelected = selectedControlId == "a_button",
                onSelect = { selectedControlId = it },
                state = aState,
                onOffsetChange = onControlOffsetChange,
                onScaleChange = onControlScaleChange
            ) {
                ActionCircleButton(
                    text = "A",
                    buttonMask = GbaInput.BUTTON_A,
                    onButtonChange = onButtonChange,
                    size = (54.dp * aState.scale).coerceIn(28.dp, 96.dp),
                    containerColor = palette.buttonABackground,
                    pressedColor = palette.buttonPressedHighlight,
                    borderColor = palette.buttonBorderColor,
                    textColor = palette.textColor,
                    isEditMode = isEditMode,
                    onPressHaptic = onHapticClick
                )
            }
        }

        // Extras opcionais em modo paisagem (se ativados no menu)
        if (showAbCombo) {
            val abState = controls["ab_button"] ?: ControlElementState()
            EditableControlBox(
                id = "ab_button",
                label = "A+B",
                isEditMode = isEditMode,
                isSelected = selectedControlId == "ab_button",
                onSelect = { selectedControlId = it },
                state = abState,
                onOffsetChange = onControlOffsetChange,
                onScaleChange = onControlScaleChange,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 68.dp, end = 34.dp)
            ) {
                ActionCircleButton(
                    text = "A+B",
                    buttonMask = GbaInput.BUTTON_A or GbaInput.BUTTON_B,
                    onButtonChange = onButtonChange,
                    size = (40.dp * abState.scale).coerceIn(24.dp, 72.dp),
                    containerColor = palette.buttonAbBackground,
                    pressedColor = palette.buttonPressedHighlight,
                    borderColor = palette.buttonBorderColor,
                    textColor = palette.textColor,
                    isEditMode = isEditMode,
                    onPressHaptic = onHapticClick
                )
            }
        }

        if (showTurboButton) {
            val turboState = controls["turbo_button"] ?: ControlElementState()
            EditableControlBox(
                id = "turbo_button",
                label = "Turbo",
                isEditMode = isEditMode,
                isSelected = selectedControlId == "turbo_button",
                onSelect = { selectedControlId = it },
                state = turboState,
                onOffsetChange = onControlOffsetChange,
                onScaleChange = onControlScaleChange,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp)
            ) {
                IconButton(
                    onClick = {
                        hapticManager.performTurboToggle(hapticFeedbackEnabled)
                        onTurboToggle()
                    },
                    modifier = Modifier
                        .size((36.dp * turboState.scale).coerceIn(24.dp, 64.dp))
                        .background(if (isTurboActive) GbaSecondary else palette.systemBackground, CircleShape)
                        .border(1.5.dp, palette.buttonBorderColor, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Turbo",
                        tint = if (isTurboActive) Color.Black else palette.textColor,
                        modifier = Modifier.size(20.dp * turboState.scale.coerceIn(0.7f, 1.3f))
                    )
                }
            }
        }

        if (showQuickSaveLoad) {
            val qsState = controls["quicksave_button"] ?: ControlElementState()
            val qlState = controls["quickload_button"] ?: ControlElementState()

            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 46.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditableControlBox(
                    id = "quicksave_button",
                    label = "Save",
                    isEditMode = isEditMode,
                    isSelected = selectedControlId == "quicksave_button",
                    onSelect = { selectedControlId = it },
                    state = qsState,
                    onOffsetChange = onControlOffsetChange,
                    onScaleChange = onControlScaleChange
                ) {
                    IconButton(
                        onClick = {
                            onHapticClick()
                            onQuickSave()
                        },
                        modifier = Modifier
                            .size((34.dp * qsState.scale).coerceIn(24.dp, 64.dp))
                            .background(palette.systemBackground, CircleShape)
                            .border(1.5.dp, palette.buttonBorderColor, CircleShape)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Quick Save", tint = palette.textColor, modifier = Modifier.size(17.dp * qsState.scale))
                    }
                }

                EditableControlBox(
                    id = "quickload_button",
                    label = "Load",
                    isEditMode = isEditMode,
                    isSelected = selectedControlId == "quickload_button",
                    onSelect = { selectedControlId = it },
                    state = qlState,
                    onOffsetChange = onControlOffsetChange,
                    onScaleChange = onControlScaleChange
                ) {
                    IconButton(
                        onClick = {
                            onHapticClick()
                            onQuickLoad()
                        },
                        modifier = Modifier
                            .size((34.dp * qlState.scale).coerceIn(24.dp, 64.dp))
                            .background(palette.systemBackground, CircleShape)
                            .border(1.5.dp, palette.buttonBorderColor, CircleShape)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = "Quick Load", tint = palette.textColor, modifier = Modifier.size(17.dp * qlState.scale))
                    }
                }
            }
        }

        if (showFpsCounter && currentFps != null) {
            val fpsState = controls["fps_badge"] ?: ControlElementState()
            EditableControlBox(
                id = "fps_badge",
                label = "FPS",
                isEditMode = isEditMode,
                isSelected = selectedControlId == "fps_badge",
                onSelect = { selectedControlId = it },
                state = fpsState,
                onOffsetChange = onControlOffsetChange,
                onScaleChange = onControlScaleChange,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 88.dp, top = 6.dp)
            ) {
                Surface(
                    color = Color(0x991B192A),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.buttonBorderColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "$currentFps FPS",
                        color = if (currentFps >= 50) Color(0xFF81C784) else Color(0xFFFFB74D),
                        fontSize = (10f * fpsState.scale.coerceIn(0.7f, 1.4f)).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Ponto de entrada unificado para o Controlador Virtual GBA com suporte completo a
 * estado independente por botão (escala e posição) e isolamento Retrato vs Paisagem.
 */
@Composable
fun VirtualController(
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    onMenuClick: () -> Unit,
    onTurboToggle: () -> Unit,
    modifier: Modifier = Modifier,
    opacity: Float = 0.85f,
    theme: String = UserPreferences.THEME_FOLLOW_APP,
    controls: Map<String, ControlElementState> = emptyMap(),
    onControlOffsetChange: (id: String, dxDp: Float, dyDp: Float) -> Unit = { _, _, _ -> },
    onControlScaleChange: (id: String, scale: Float) -> Unit = { _, _ -> },
    onResetSingleControl: (id: String) -> Unit = {},
    onResetLayout: () -> Unit = {},
    onExitEditMode: () -> Unit = {},
    showAbCombo: Boolean = false,
    showQuickSaveLoad: Boolean = false,
    showTurboButton: Boolean = false,
    showFpsCounter: Boolean = false,
    currentFps: Int? = null,
    isTurboActive: Boolean = false,
    isEditMode: Boolean = false,
    onQuickSave: () -> Unit = {},
    onQuickLoad: () -> Unit = {},
    hapticFeedbackEnabled: Boolean = true,
    isLandscape: Boolean = false
) {
    if (isLandscape) {
        LandscapeVirtualController(
            onButtonChange = onButtonChange,
            onMenuClick = onMenuClick,
            onTurboToggle = onTurboToggle,
            modifier = modifier,
            opacity = opacity,
            theme = theme,
            controls = controls,
            onControlOffsetChange = onControlOffsetChange,
            onControlScaleChange = onControlScaleChange,
            onResetSingleControl = onResetSingleControl,
            onResetLayout = onResetLayout,
            onExitEditMode = onExitEditMode,
            showAbCombo = showAbCombo,
            showQuickSaveLoad = showQuickSaveLoad,
            showTurboButton = showTurboButton,
            showFpsCounter = showFpsCounter,
            currentFps = currentFps,
            isTurboActive = isTurboActive,
            isEditMode = isEditMode,
            onQuickSave = onQuickSave,
            onQuickLoad = onQuickLoad,
            hapticFeedbackEnabled = hapticFeedbackEnabled
        )
    } else {
        PortraitVirtualController(
            onButtonChange = onButtonChange,
            onMenuClick = onMenuClick,
            onTurboToggle = onTurboToggle,
            modifier = modifier,
            opacity = opacity,
            theme = theme,
            controls = controls,
            onControlOffsetChange = onControlOffsetChange,
            onControlScaleChange = onControlScaleChange,
            onResetSingleControl = onResetSingleControl,
            onResetLayout = onResetLayout,
            onExitEditMode = onExitEditMode,
            showAbCombo = showAbCombo,
            showQuickSaveLoad = showQuickSaveLoad,
            showTurboButton = showTurboButton,
            showFpsCounter = showFpsCounter,
            currentFps = currentFps,
            isTurboActive = isTurboActive,
            isEditMode = isEditMode,
            onQuickSave = onQuickSave,
            onQuickLoad = onQuickLoad,
            hapticFeedbackEnabled = hapticFeedbackEnabled
        )
    }
}

// ==========================================
// PREVIEWS JETPACK COMPOSE (THEMES & CONTROLS)
// ==========================================

@Preview(name = "Retrato - GBA Índigo Clássico", widthDp = 390, heightDp = 360)
@Composable
fun VirtualControllerPortraitIndigoPreview() {
    RetroGBATheme(appTheme = UserPreferences.APP_THEME_GBA_INDIGO) {
        Surface(color = LocalAppThemeColors.current.background) {
            VirtualController(
                onButtonChange = { _, _ -> },
                onMenuClick = {},
                onTurboToggle = {},
                currentFps = 60,
                theme = UserPreferences.THEME_GBA_INDIGO,
                isLandscape = false
            )
        }
    }
}

@Preview(name = "Retrato - Glacier Translúcido", widthDp = 390, heightDp = 360)
@Composable
fun VirtualControllerPortraitGlacierPreview() {
    RetroGBATheme(appTheme = UserPreferences.APP_THEME_GLACIER) {
        Surface(color = LocalAppThemeColors.current.background) {
            VirtualController(
                onButtonChange = { _, _ -> },
                onMenuClick = {},
                onTurboToggle = {},
                currentFps = 60,
                theme = UserPreferences.THEME_GLACIER,
                isLandscape = false
            )
        }
    }
}

@Preview(name = "Retrato - SP Platinum", widthDp = 390, heightDp = 360)
@Composable
fun VirtualControllerPortraitPlatinumPreview() {
    RetroGBATheme(appTheme = UserPreferences.APP_THEME_SP_PLATINUM) {
        Surface(color = LocalAppThemeColors.current.background) {
            VirtualController(
                onButtonChange = { _, _ -> },
                onMenuClick = {},
                onTurboToggle = {},
                currentFps = 60,
                theme = UserPreferences.THEME_SP_PLATINUM,
                isLandscape = false
            )
        }
    }
}

@Preview(name = "Retrato - Famicom 20th", widthDp = 390, heightDp = 360)
@Composable
fun VirtualControllerPortraitFamicomPreview() {
    RetroGBATheme(appTheme = UserPreferences.APP_THEME_FAMICOM) {
        Surface(color = LocalAppThemeColors.current.background) {
            VirtualController(
                onButtonChange = { _, _ -> },
                onMenuClick = {},
                onTurboToggle = {},
                currentFps = 60,
                theme = UserPreferences.THEME_FAMICOM,
                isLandscape = false
            )
        }
    }
}

@Preview(name = "Paisagem - OLED Stealth", widthDp = 780, heightDp = 360)
@Composable
fun VirtualControllerLandscapeStealthPreview() {
    RetroGBATheme(appTheme = UserPreferences.APP_THEME_OLED_STEALTH) {
        Surface(color = LocalAppThemeColors.current.background) {
            VirtualController(
                onButtonChange = { _, _ -> },
                onMenuClick = {},
                onTurboToggle = {},
                currentFps = 60,
                theme = UserPreferences.THEME_OLED_STEALTH,
                isLandscape = true
            )
        }
    }
}

@Preview(name = "Modo Edição de Layout", widthDp = 390, heightDp = 380)
@Composable
fun VirtualControllerEditModePreview() {
    RetroGBATheme(appTheme = UserPreferences.APP_THEME_GBA_INDIGO) {
        Surface(color = LocalAppThemeColors.current.background) {
            VirtualController(
                onButtonChange = { _, _ -> },
                onMenuClick = {},
                onTurboToggle = {},
                currentFps = 60,
                isEditMode = true,
                isLandscape = false
            )
        }
    }
}
