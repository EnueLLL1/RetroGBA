package com.retro.gba.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.draw.scale
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
import com.retro.gba.data.prefs.UserPreferences
import com.retro.gba.emulator.GbaInput
import com.retro.gba.ui.haptics.HapticManager
import com.retro.gba.ui.theme.GbaBackground
import com.retro.gba.ui.theme.GbaPrimary
import com.retro.gba.ui.theme.GbaSecondary
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

fun getControllerPalette(theme: String): ControllerPalette {
    return if (theme == UserPreferences.THEME_RETRO_GBA) {
        ControllerPalette(
            isCleanMyBoy = false,
            dpadBackground = Color(0xAA2B283A),
            dpadPressed = Color(0xCC5D48B3),
            dpadStroke = Color(0x66FFFFFF),
            buttonABackground = Color(0xCC2E7D32), // Verde Esmeralda
            buttonBBackground = Color(0xCCC62828), // Vermelho Rubi
            buttonAbBackground = Color(0xCC5D48B3), // Roxo Índigo
            shoulderBackground = Color(0xAA3B3B54),
            systemBackground = Color(0xAA423B5A),
            buttonPressedHighlight = Color(0xAA00E5FF),
            buttonBorderColor = Color(0x77FFFFFF),
            textColor = Color.White
        )
    } else {
        // CLEAN_MYBOY: Minimalista, translúcido e nítido
        ControllerPalette(
            isCleanMyBoy = true,
            dpadBackground = Color(0x5014141C),
            dpadPressed = Color(0x8800E5FF),
            dpadStroke = Color.White.copy(alpha = 0.55f),
            buttonABackground = Color(0x5014141C),
            buttonBBackground = Color(0x5014141C),
            buttonAbBackground = Color(0x5014141C),
            shoulderBackground = Color(0x5014141C),
            systemBackground = Color(0x5014141C),
            buttonPressedHighlight = Color(0x8800E5FF),
            buttonBorderColor = Color.White.copy(alpha = 0.55f),
            textColor = Color.White.copy(alpha = 0.95f)
        )
    }
}

/**
 * D-Pad Contínuo vetorial de 8 direções desenhado via Canvas com cantos arredondados,
 * setas direcionais vetoriais e atualização diferencial de toques para evitar stuck buttons.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ContinuousDPad(
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    palette: ControllerPalette = getControllerPalette(UserPreferences.THEME_CLEAN_MYBOY),
    size: Dp = 138.dp,
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
    val radiusPx = sizePx / 2f
    val deadzonePx = radiusPx * 0.16f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .pointerInteropFilter { event ->
                if (isEditMode) return@pointerInteropFilter false

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - centerPx
                        val dy = event.y - centerPx
                        val dist = hypot(dx, dy)

                        val newMask = if (dist < deadzonePx || dist > radiusPx * 1.35f) {
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
            val armThick = w * 0.36f
            val armMargin = (w - armThick) / 2f
            val cornerRad = CornerRadius(8.dp.toPx(), 8.dp.toPx())

            // Fundo horizontal e vertical da cruz
            drawRoundRect(
                color = palette.dpadBackground,
                topLeft = Offset(0f, armMargin),
                size = Size(w, armThick),
                cornerRadius = cornerRad
            )
            drawRoundRect(
                color = palette.dpadBackground,
                topLeft = Offset(armMargin, 0f),
                size = Size(armThick, h),
                cornerRadius = cornerRad
            )

            // Destaque visual dos setores pressionados (inclui diagonais)
            if ((currentMask and GbaInput.BUTTON_UP) != 0) {
                drawRoundRect(
                    color = palette.dpadPressed,
                    topLeft = Offset(armMargin, 0f),
                    size = Size(armThick, armMargin + armThick / 2f),
                    cornerRadius = cornerRad
                )
            }
            if ((currentMask and GbaInput.BUTTON_DOWN) != 0) {
                drawRoundRect(
                    color = palette.dpadPressed,
                    topLeft = Offset(armMargin, armMargin + armThick / 2f),
                    size = Size(armThick, armMargin + armThick / 2f),
                    cornerRadius = cornerRad
                )
            }
            if ((currentMask and GbaInput.BUTTON_LEFT) != 0) {
                drawRoundRect(
                    color = palette.dpadPressed,
                    topLeft = Offset(0f, armMargin),
                    size = Size(armMargin + armThick / 2f, armThick),
                    cornerRadius = cornerRad
                )
            }
            if ((currentMask and GbaInput.BUTTON_RIGHT) != 0) {
                drawRoundRect(
                    color = palette.dpadPressed,
                    topLeft = Offset(armMargin + armThick / 2f, armMargin),
                    size = Size(armMargin + armThick / 2f, armThick),
                    cornerRadius = cornerRad
                )
            }

            // Contornos vetoriais nítidos
            val strokeWidth = 1.75.dp.toPx()
            drawRoundRect(
                color = palette.dpadStroke,
                topLeft = Offset(0f, armMargin),
                size = Size(w, armThick),
                cornerRadius = cornerRad,
                style = Stroke(width = strokeWidth)
            )
            drawRoundRect(
                color = palette.dpadStroke,
                topLeft = Offset(armMargin, 0f),
                size = Size(armThick, h),
                cornerRadius = cornerRad,
                style = Stroke(width = strokeWidth)
            )

            // Reentrância central côncava
            drawCircle(
                color = Color.Black.copy(alpha = 0.28f),
                radius = armThick * 0.30f,
                center = Offset(w / 2f, h / 2f)
            )

            // Setas direcionais vetoriais
            val arrowSize = 5.5.dp.toPx()
            val arrowColor = if (palette.isCleanMyBoy) Color.White.copy(alpha = 0.70f) else Color.LightGray

            // Seta Cima
            val upPath = Path().apply {
                moveTo(w / 2f, armMargin * 0.38f)
                lineTo(w / 2f - arrowSize, armMargin * 0.38f + arrowSize)
                lineTo(w / 2f + arrowSize, armMargin * 0.38f + arrowSize)
                close()
            }
            drawPath(upPath, arrowColor)

            // Seta Baixo
            val downPath = Path().apply {
                moveTo(w / 2f, h - armMargin * 0.38f)
                lineTo(w / 2f - arrowSize, h - armMargin * 0.38f - arrowSize)
                lineTo(w / 2f + arrowSize, h - armMargin * 0.38f - arrowSize)
                close()
            }
            drawPath(downPath, arrowColor)

            // Seta Esquerda
            val leftPath = Path().apply {
                moveTo(armMargin * 0.38f, h / 2f)
                lineTo(armMargin * 0.38f + arrowSize, h / 2f - arrowSize)
                lineTo(armMargin * 0.38f + arrowSize, h / 2f + arrowSize)
                close()
            }
            drawPath(leftPath, arrowColor)

            // Seta Direita
            val rightPath = Path().apply {
                moveTo(w - armMargin * 0.38f, h / 2f)
                lineTo(w - armMargin * 0.38f - arrowSize, h / 2f - arrowSize)
                lineTo(w - armMargin * 0.38f - arrowSize, h / 2f + arrowSize)
                close()
            }
            drawPath(rightPath, arrowColor)
        }
    }
}

/**
 * Alias retrocompatível para ContinuousDPad.
 */
@Composable
fun DPad(
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    palette: ControllerPalette = getControllerPalette(UserPreferences.THEME_CLEAN_MYBOY),
    size: Dp = 138.dp,
    isEditMode: Boolean = false,
    onPressHaptic: () -> Unit = {}
) {
    ContinuousDPad(
        onButtonChange = onButtonChange,
        modifier = modifier,
        palette = palette,
        size = size,
        isEditMode = isEditMode,
        onPressHaptic = onPressHaptic
    )
}

/**
 * Botão circular individual com detecção actionMasked e proteção anti-stuck via DisposableEffect.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ControllerButton(
    text: String,
    buttonMask: Int,
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
    containerColor: Color = Color(0x5014141C),
    pressedColor: Color = Color(0x8800E5FF),
    borderColor: Color = Color.White.copy(alpha = 0.55f),
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
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onPressHaptic()
                        onButtonChange(buttonMask, true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
            fontSize = (size.value * 0.40f).sp
        )
    }
}

/**
 * Gatilho superior L ou R.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ShoulderButton(
    text: String,
    buttonMask: Int,
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0x5014141C),
    pressedColor: Color = Color(0x8800E5FF),
    borderColor: Color = Color.White.copy(alpha = 0.55f),
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
            .width(78.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPressed) pressedColor else containerColor)
            .border(1.5.dp, if (isPressed) pressedColor else borderColor, RoundedCornerShape(8.dp))
            .pointerInteropFilter { event ->
                if (isEditMode) return@pointerInteropFilter false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onPressHaptic()
                        onButtonChange(buttonMask, true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
            fontSize = 14.sp
        )
    }
}

/**
 * Botão de sistema clássico (SELECT / START).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SystemButton(
    text: String,
    buttonMask: Int,
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0x5014141C),
    pressedColor: Color = Color(0x8800E5FF),
    borderColor: Color = Color.White.copy(alpha = 0.45f),
    textColor: Color = Color.LightGray,
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

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isPressed) pressedColor else containerColor)
                .border(1.2.dp, if (isPressed) pressedColor else borderColor, RoundedCornerShape(8.dp))
                .pointerInteropFilter { event ->
                    if (isEditMode) return@pointerInteropFilter false
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            isPressed = true
                            onPressHaptic()
                            onButtonChange(buttonMask, true)
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            if (isPressed) {
                                isPressed = false
                                onButtonChange(buttonMask, false)
                            }
                            true
                        }
                        else -> false
                    }
                }
        )
        Text(
            text = text,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Cluster de ação ergonômico contendo botões B (inferior-esquerdo), A (superior-direito com 30°)
 * e botão combo A+B opcional centralizado.
 */
@Composable
fun ActionButtonsCluster(
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    palette: ControllerPalette,
    showAbCombo: Boolean = true,
    isEditMode: Boolean = false,
    modifier: Modifier = Modifier,
    onPressHaptic: () -> Unit = {}
) {
    val boxWidth = if (showAbCombo) 130.dp else 115.dp
    val boxHeight = if (showAbCombo) 130.dp else 115.dp

    Box(
        modifier = modifier.size(width = boxWidth, height = boxHeight)
    ) {
        // Botão B (Inferior-Esquerdo)
        ControllerButton(
            text = "B",
            buttonMask = GbaInput.BUTTON_B,
            onButtonChange = onButtonChange,
            size = 48.dp,
            containerColor = palette.buttonBBackground,
            pressedColor = palette.buttonPressedHighlight,
            borderColor = palette.buttonBorderColor,
            textColor = palette.textColor,
            isEditMode = isEditMode,
            onPressHaptic = onPressHaptic,
            modifier = Modifier.align(Alignment.BottomStart)
        )

        // Botão Combo A+B opcional (Centro)
        if (showAbCombo) {
            ControllerButton(
                text = "A+B",
                buttonMask = GbaInput.BUTTON_A or GbaInput.BUTTON_B,
                onButtonChange = onButtonChange,
                size = 38.dp,
                containerColor = palette.buttonAbBackground,
                pressedColor = palette.buttonPressedHighlight,
                borderColor = palette.buttonBorderColor,
                textColor = palette.textColor,
                isEditMode = isEditMode,
                onPressHaptic = onPressHaptic,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Botão A (Superior-Direito)
        ControllerButton(
            text = "A",
            buttonMask = GbaInput.BUTTON_A,
            onButtonChange = onButtonChange,
            size = 48.dp,
            containerColor = palette.buttonABackground,
            pressedColor = palette.buttonPressedHighlight,
            borderColor = palette.buttonBorderColor,
            textColor = palette.textColor,
            isEditMode = isEditMode,
            onPressHaptic = onPressHaptic,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

/**
 * Barra compacta de Ações Rápidas (Quick Save, Quick Load, Turbo, Menu).
 */
@Composable
fun QuickActionsBar(
    showQuickSaveLoad: Boolean,
    isTurboActive: Boolean,
    onTurboToggle: () -> Unit,
    onMenuClick: () -> Unit,
    onQuickSave: () -> Unit,
    onQuickLoad: () -> Unit,
    modifier: Modifier = Modifier,
    onPressHaptic: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .background(Color(0x6014141C), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showQuickSaveLoad) {
            IconButton(
                onClick = {
                    onPressHaptic()
                    onQuickSave()
                },
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0x402C2449), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Quick Save",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }

            IconButton(
                onClick = {
                    onPressHaptic()
                    onQuickLoad()
                },
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0x402C2449), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "Quick Load",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        IconButton(
            onClick = onTurboToggle,
            modifier = Modifier
                .size(32.dp)
                .background(
                    if (isTurboActive) GbaSecondary else Color(0x402C2449),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.FastForward,
                contentDescription = "Turbo",
                tint = if (isTurboActive) Color.Black else GbaSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .size(32.dp)
                .background(Color(0x402C2449), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Caixa arrastável para posicionamento livre no Modo de Edição de Layout.
 */
@Composable
fun DraggableControlBox(
    id: String,
    isLandscape: Boolean,
    isEditMode: Boolean,
    offsetMap: Map<String, Pair<Float, Float>>,
    onOffsetChange: (id: String, dxDp: Float, dyDp: Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    scale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val currentOffset = offsetMap[id] ?: Pair(0f, 0f)
    var localDx by remember(id, isLandscape) { mutableFloatStateOf(currentOffset.first) }
    var localDy by remember(id, isLandscape) { mutableFloatStateOf(currentOffset.second) }
    LaunchedEffect(currentOffset.first, currentOffset.second) {
        localDx = currentOffset.first
        localDy = currentOffset.second
    }

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    (localDx * density.density).roundToInt(),
                    (localDy * density.density).roundToInt()
                )
            }
            .scale(scale)
            .then(
                if (isEditMode) {
                    Modifier
                        .border(
                            width = 1.5.dp,
                            color = GbaSecondary,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(
                            color = GbaSecondary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .pointerInput(id, isLandscape) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val newDx = (localDx + dragAmount.x / density.density).coerceIn(-280f, 280f)
                                val newDy = (localDy + dragAmount.y / density.density).coerceIn(-280f, 280f)
                                localDx = newDx
                                localDy = newDy
                                onOffsetChange(id, newDx, newDy)
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
                color = GbaSecondary,
                shape = RoundedCornerShape(bottomEnd = 4.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = label,
                    color = Color.Black,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * Banner superior do Modo de Edição de Layout com ajuste de escala e botões de ação.
 */
@Composable
fun LayoutEditToolbar(
    isLandscape: Boolean,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    onResetLayout: () -> Unit,
    onExitEditMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xEE1E1A2E),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GbaSecondary.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Editando Layout (${if (isLandscape) "Paisagem" else "Retrato"})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onResetLayout,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.LightGray)
                        Spacer(Modifier.width(4.dp))
                        Text("Padrão", fontSize = 11.sp, color = Color.LightGray)
                    }

                    Button(
                        onClick = onExitEditMode,
                        colors = ButtonDefaults.buttonColors(containerColor = GbaSecondary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Black)
                        Spacer(Modifier.width(4.dp))
                        Text("Concluir", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Escala: ${(scale * 100).toInt()}%",
                    fontSize = 11.sp,
                    color = GbaSecondary,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = scale,
                    onValueChange = onScaleChange,
                    valueRange = 0.75f..1.45f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Controlador Virtual em Modo Retrato (Vertical).
 */
@Composable
fun PortraitVirtualController(
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    onMenuClick: () -> Unit,
    onTurboToggle: () -> Unit,
    modifier: Modifier = Modifier,
    opacity: Float = 0.85f,
    scale: Float = 1.0f,
    theme: String = UserPreferences.THEME_CLEAN_MYBOY,
    showQuickSaveLoad: Boolean = true,
    showAbCombo: Boolean = true,
    isTurboActive: Boolean = false,
    isEditMode: Boolean = false,
    offsets: Map<String, Pair<Float, Float>> = emptyMap(),
    onOffsetChange: (id: String, dxDp: Float, dyDp: Float) -> Unit = { _, _, _ -> },
    onResetLayout: () -> Unit = {},
    onExitEditMode: () -> Unit = {},
    onScaleChange: (Float) -> Unit = {},
    onQuickSave: () -> Unit = {},
    onQuickLoad: () -> Unit = {},
    hapticFeedbackEnabled: Boolean = true
) {
    val context = LocalContext.current
    val hapticManager = remember(context) { HapticManager(context) }
    val onHapticClick = remember(hapticFeedbackEnabled) {
        { hapticManager.performButtonClick(hapticFeedbackEnabled) }
    }
    val palette = remember(theme) { getControllerPalette(theme) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(opacity.coerceIn(0.2f, 1.0f)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isEditMode) {
            LayoutEditToolbar(
                isLandscape = false,
                scale = scale,
                onScaleChange = onScaleChange,
                onResetLayout = onResetLayout,
                onExitEditMode = onExitEditMode
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Linha Superior: L, Quick Actions Bar, R
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DraggableControlBox(
                        id = "l_button",
                        isLandscape = false,
                        isEditMode = isEditMode,
                        offsetMap = offsets,
                        onOffsetChange = onOffsetChange,
                        scale = scale,
                        label = "L"
                    ) {
                        ShoulderButton(
                            text = "L",
                            buttonMask = GbaInput.BUTTON_L,
                            onButtonChange = onButtonChange,
                            containerColor = palette.shoulderBackground,
                            pressedColor = palette.buttonPressedHighlight,
                            borderColor = palette.buttonBorderColor,
                            textColor = palette.textColor,
                            isEditMode = isEditMode,
                            onPressHaptic = onHapticClick
                        )
                    }

                    DraggableControlBox(
                        id = "quick_actions",
                        isLandscape = false,
                        isEditMode = isEditMode,
                        offsetMap = offsets,
                        onOffsetChange = onOffsetChange,
                        scale = scale,
                        label = "AÇÕES"
                    ) {
                        QuickActionsBar(
                            showQuickSaveLoad = showQuickSaveLoad,
                            isTurboActive = isTurboActive,
                            onTurboToggle = {
                                hapticManager.performTurboToggle(hapticFeedbackEnabled)
                                onTurboToggle()
                            },
                            onMenuClick = onMenuClick,
                            onQuickSave = onQuickSave,
                            onQuickLoad = onQuickLoad,
                            onPressHaptic = onHapticClick
                        )
                    }

                    DraggableControlBox(
                        id = "r_button",
                        isLandscape = false,
                        isEditMode = isEditMode,
                        offsetMap = offsets,
                        onOffsetChange = onOffsetChange,
                        scale = scale,
                        label = "R"
                    ) {
                        ShoulderButton(
                            text = "R",
                            buttonMask = GbaInput.BUTTON_R,
                            onButtonChange = onButtonChange,
                            containerColor = palette.shoulderBackground,
                            pressedColor = palette.buttonPressedHighlight,
                            borderColor = palette.buttonBorderColor,
                            textColor = palette.textColor,
                            isEditMode = isEditMode,
                            onPressHaptic = onHapticClick
                        )
                    }
                }

                // Linha Inferior: D-Pad, SELECT/START, Action Cluster (B/A/A+B)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DraggableControlBox(
                        id = "dpad",
                        isLandscape = false,
                        isEditMode = isEditMode,
                        offsetMap = offsets,
                        onOffsetChange = onOffsetChange,
                        scale = scale,
                        label = "D-PAD"
                    ) {
                        ContinuousDPad(
                            onButtonChange = onButtonChange,
                            palette = palette,
                            isEditMode = isEditMode,
                            onPressHaptic = onHapticClick
                        )
                    }

                    DraggableControlBox(
                        id = "system_bar",
                        isLandscape = false,
                        isEditMode = isEditMode,
                        offsetMap = offsets,
                        onOffsetChange = onOffsetChange,
                        scale = scale,
                        label = "SYSTEM",
                        modifier = Modifier.padding(top = 28.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SystemButton(
                                text = "SELECT",
                                buttonMask = GbaInput.BUTTON_SELECT,
                                onButtonChange = onButtonChange,
                                containerColor = palette.systemBackground,
                                pressedColor = palette.buttonPressedHighlight,
                                borderColor = palette.buttonBorderColor,
                                textColor = palette.textColor,
                                isEditMode = isEditMode,
                                onPressHaptic = onHapticClick
                            )
                            SystemButton(
                                text = "START",
                                buttonMask = GbaInput.BUTTON_START,
                                onButtonChange = onButtonChange,
                                containerColor = palette.systemBackground,
                                pressedColor = palette.buttonPressedHighlight,
                                borderColor = palette.buttonBorderColor,
                                textColor = palette.textColor,
                                isEditMode = isEditMode,
                                onPressHaptic = onHapticClick
                            )
                        }
                    }

                    DraggableControlBox(
                        id = "ab_cluster",
                        isLandscape = false,
                        isEditMode = isEditMode,
                        offsetMap = offsets,
                        onOffsetChange = onOffsetChange,
                        scale = scale,
                        label = "A / B"
                    ) {
                        ActionButtonsCluster(
                            onButtonChange = onButtonChange,
                            palette = palette,
                            showAbCombo = showAbCombo,
                            isEditMode = isEditMode,
                            onPressHaptic = onHapticClick
                        )
                    }
                }
            }
        }
    }
}

/**
 * Controlador Virtual em Modo Paisagem (Horizontal).
 */
@Composable
fun LandscapeVirtualController(
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    onMenuClick: () -> Unit,
    onTurboToggle: () -> Unit,
    modifier: Modifier = Modifier,
    opacity: Float = 0.85f,
    scale: Float = 1.0f,
    theme: String = UserPreferences.THEME_CLEAN_MYBOY,
    showQuickSaveLoad: Boolean = true,
    showAbCombo: Boolean = true,
    isTurboActive: Boolean = false,
    isEditMode: Boolean = false,
    offsets: Map<String, Pair<Float, Float>> = emptyMap(),
    onOffsetChange: (id: String, dxDp: Float, dyDp: Float) -> Unit = { _, _, _ -> },
    onResetLayout: () -> Unit = {},
    onExitEditMode: () -> Unit = {},
    onScaleChange: (Float) -> Unit = {},
    onQuickSave: () -> Unit = {},
    onQuickLoad: () -> Unit = {},
    hapticFeedbackEnabled: Boolean = true
) {
    val context = LocalContext.current
    val hapticManager = remember(context) { HapticManager(context) }
    val onHapticClick = remember(hapticFeedbackEnabled) {
        { hapticManager.performButtonClick(hapticFeedbackEnabled) }
    }
    val palette = remember(theme) { getControllerPalette(theme) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(opacity.coerceIn(0.2f, 1.0f))
            .padding(10.dp)
    ) {
        if (isEditMode) {
            LayoutEditToolbar(
                isLandscape = true,
                scale = scale,
                onScaleChange = onScaleChange,
                onResetLayout = onResetLayout,
                onExitEditMode = onExitEditMode,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // Canto Superior Centro/Direito: Barra de Ações Rápidas & Sistema
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(if (isEditMode) Modifier.padding(top = 68.dp) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DraggableControlBox(
                id = "quick_actions",
                isLandscape = true,
                isEditMode = isEditMode,
                offsetMap = offsets,
                onOffsetChange = onOffsetChange,
                scale = scale,
                label = "AÇÕES"
            ) {
                QuickActionsBar(
                    showQuickSaveLoad = showQuickSaveLoad,
                    isTurboActive = isTurboActive,
                    onTurboToggle = {
                        hapticManager.performTurboToggle(hapticFeedbackEnabled)
                        onTurboToggle()
                    },
                    onMenuClick = onMenuClick,
                    onQuickSave = onQuickSave,
                    onQuickLoad = onQuickLoad,
                    onPressHaptic = onHapticClick
                )
            }

            DraggableControlBox(
                id = "system_bar",
                isLandscape = true,
                isEditMode = isEditMode,
                offsetMap = offsets,
                onOffsetChange = onOffsetChange,
                scale = scale,
                label = "SYSTEM"
            ) {
                Row(
                    modifier = Modifier
                        .background(Color(0x6014141C), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SystemButton(
                        text = "SELECT",
                        buttonMask = GbaInput.BUTTON_SELECT,
                        onButtonChange = onButtonChange,
                        containerColor = palette.systemBackground,
                        pressedColor = palette.buttonPressedHighlight,
                        borderColor = palette.buttonBorderColor,
                        textColor = palette.textColor,
                        isEditMode = isEditMode,
                        onPressHaptic = onHapticClick
                    )
                    SystemButton(
                        text = "START",
                        buttonMask = GbaInput.BUTTON_START,
                        onButtonChange = onButtonChange,
                        containerColor = palette.systemBackground,
                        pressedColor = palette.buttonPressedHighlight,
                        borderColor = palette.buttonBorderColor,
                        textColor = palette.textColor,
                        isEditMode = isEditMode,
                        onPressHaptic = onHapticClick
                    )
                }
            }
        }

        // Lado Esquerdo: L Shoulder e D-Pad
        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            DraggableControlBox(
                id = "l_button",
                isLandscape = true,
                isEditMode = isEditMode,
                offsetMap = offsets,
                onOffsetChange = onOffsetChange,
                scale = scale,
                label = "L"
            ) {
                ShoulderButton(
                    text = "L",
                    buttonMask = GbaInput.BUTTON_L,
                    onButtonChange = onButtonChange,
                    containerColor = palette.shoulderBackground,
                    pressedColor = palette.buttonPressedHighlight,
                    borderColor = palette.buttonBorderColor,
                    textColor = palette.textColor,
                    isEditMode = isEditMode,
                    onPressHaptic = onHapticClick
                )
            }

            DraggableControlBox(
                id = "dpad",
                isLandscape = true,
                isEditMode = isEditMode,
                offsetMap = offsets,
                onOffsetChange = onOffsetChange,
                scale = scale,
                label = "D-PAD"
            ) {
                ContinuousDPad(
                    onButtonChange = onButtonChange,
                    palette = palette,
                    isEditMode = isEditMode,
                    onPressHaptic = onHapticClick
                )
            }
        }

        // Lado Direito: R Shoulder e Action Cluster (B/A/A+B)
        Column(
            modifier = Modifier.align(Alignment.BottomEnd),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            DraggableControlBox(
                id = "r_button",
                isLandscape = true,
                isEditMode = isEditMode,
                offsetMap = offsets,
                onOffsetChange = onOffsetChange,
                scale = scale,
                label = "R"
            ) {
                ShoulderButton(
                    text = "R",
                    buttonMask = GbaInput.BUTTON_R,
                    onButtonChange = onButtonChange,
                    containerColor = palette.shoulderBackground,
                    pressedColor = palette.buttonPressedHighlight,
                    borderColor = palette.buttonBorderColor,
                    textColor = palette.textColor,
                    isEditMode = isEditMode,
                    onPressHaptic = onHapticClick
                )
            }

            DraggableControlBox(
                id = "ab_cluster",
                isLandscape = true,
                isEditMode = isEditMode,
                offsetMap = offsets,
                onOffsetChange = onOffsetChange,
                scale = scale,
                label = "A / B"
            ) {
                ActionButtonsCluster(
                    onButtonChange = onButtonChange,
                    palette = palette,
                    showAbCombo = showAbCombo,
                    isEditMode = isEditMode,
                    onPressHaptic = onHapticClick
                )
            }
        }
    }
}

/**
 * Ponto de entrada unificado para o Controlador Virtual GBA.
 */
@Composable
fun VirtualController(
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    onMenuClick: () -> Unit,
    onTurboToggle: () -> Unit,
    modifier: Modifier = Modifier,
    opacity: Float = 0.85f,
    scale: Float = 1.0f,
    theme: String = UserPreferences.THEME_CLEAN_MYBOY,
    showQuickSaveLoad: Boolean = true,
    showAbCombo: Boolean = true,
    isTurboActive: Boolean = false,
    isEditMode: Boolean = false,
    offsets: Map<String, Pair<Float, Float>> = emptyMap(),
    onOffsetChange: (id: String, dxDp: Float, dyDp: Float) -> Unit = { _, _, _ -> },
    onResetLayout: () -> Unit = {},
    onExitEditMode: () -> Unit = {},
    onScaleChange: (Float) -> Unit = {},
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
            scale = scale,
            theme = theme,
            showQuickSaveLoad = showQuickSaveLoad,
            showAbCombo = showAbCombo,
            isTurboActive = isTurboActive,
            isEditMode = isEditMode,
            offsets = offsets,
            onOffsetChange = onOffsetChange,
            onResetLayout = onResetLayout,
            onExitEditMode = onExitEditMode,
            onScaleChange = onScaleChange,
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
            scale = scale,
            theme = theme,
            showQuickSaveLoad = showQuickSaveLoad,
            showAbCombo = showAbCombo,
            isTurboActive = isTurboActive,
            isEditMode = isEditMode,
            offsets = offsets,
            onOffsetChange = onOffsetChange,
            onResetLayout = onResetLayout,
            onExitEditMode = onExitEditMode,
            onScaleChange = onScaleChange,
            onQuickSave = onQuickSave,
            onQuickLoad = onQuickLoad,
            hapticFeedbackEnabled = hapticFeedbackEnabled
        )
    }
}

// ==========================================
// PREVIEWS JETPACK COMPOSE
// ==========================================

@Preview(name = "Retrato - Clean My Boy", widthDp = 390, heightDp = 320)
@Composable
fun VirtualControllerPortraitCleanPreview() {
    Surface(color = GbaBackground) {
        VirtualController(
            onButtonChange = { _, _ -> },
            onMenuClick = {},
            onTurboToggle = {},
            theme = UserPreferences.THEME_CLEAN_MYBOY,
            isLandscape = false
        )
    }
}

@Preview(name = "Paisagem - Clean My Boy", widthDp = 780, heightDp = 360)
@Composable
fun VirtualControllerLandscapeCleanPreview() {
    Surface(color = GbaBackground) {
        VirtualController(
            onButtonChange = { _, _ -> },
            onMenuClick = {},
            onTurboToggle = {},
            theme = UserPreferences.THEME_CLEAN_MYBOY,
            isLandscape = true
        )
    }
}

@Preview(name = "Retrato - Retrô GBA", widthDp = 390, heightDp = 320)
@Composable
fun VirtualControllerPortraitRetroPreview() {
    Surface(color = GbaBackground) {
        VirtualController(
            onButtonChange = { _, _ -> },
            onMenuClick = {},
            onTurboToggle = {},
            theme = UserPreferences.THEME_RETRO_GBA,
            isLandscape = false
        )
    }
}

@Preview(name = "Modo Edição de Layout", widthDp = 390, heightDp = 360)
@Composable
fun VirtualControllerEditModePreview() {
    Surface(color = GbaBackground) {
        VirtualController(
            onButtonChange = { _, _ -> },
            onMenuClick = {},
            onTurboToggle = {},
            isEditMode = true,
            isLandscape = false
        )
    }
}
