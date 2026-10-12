package com.retro.gba.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.gba.emulator.GbaInput
import com.retro.gba.ui.haptics.HapticManager
import com.retro.gba.ui.theme.GbaPrimary
import com.retro.gba.ui.theme.GbaSecondary

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ControllerButton(
    text: String,
    buttonMask: Int,
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    containerColor: Color = GbaPrimary,
    textColor: Color = Color.White,
    onPressHaptic: () -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isPressed) containerColor.copy(alpha = 0.6f) else containerColor)
            .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onPressHaptic()
                        onButtonChange(buttonMask, true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        onButtonChange(buttonMask, false)
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

@Composable
fun DPad(
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onPressHaptic: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .size(130.dp)
            .background(Color(0xFF222034), CircleShape)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Up
        ControllerButton(
            text = "▲",
            buttonMask = GbaInput.BUTTON_UP,
            onButtonChange = onButtonChange,
            size = 38.dp,
            containerColor = Color(0xFF3F3F74),
            modifier = Modifier.align(Alignment.TopCenter),
            onPressHaptic = onPressHaptic
        )
        // Down
        ControllerButton(
            text = "▼",
            buttonMask = GbaInput.BUTTON_DOWN,
            onButtonChange = onButtonChange,
            size = 38.dp,
            containerColor = Color(0xFF3F3F74),
            modifier = Modifier.align(Alignment.BottomCenter),
            onPressHaptic = onPressHaptic
        )
        // Left
        ControllerButton(
            text = "◀",
            buttonMask = GbaInput.BUTTON_LEFT,
            onButtonChange = onButtonChange,
            size = 38.dp,
            containerColor = Color(0xFF3F3F74),
            modifier = Modifier.align(Alignment.CenterStart),
            onPressHaptic = onPressHaptic
        )
        // Right
        ControllerButton(
            text = "▶",
            buttonMask = GbaInput.BUTTON_RIGHT,
            onButtonChange = onButtonChange,
            size = 38.dp,
            containerColor = Color(0xFF3F3F74),
            modifier = Modifier.align(Alignment.CenterEnd),
            onPressHaptic = onPressHaptic
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ShoulderButton(
    text: String,
    buttonMask: Int,
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onPressHaptic: () -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(80.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPressed) Color(0xFF5A5A80) else Color(0xFF3B3B54))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onPressHaptic()
                        onButtonChange(buttonMask, true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        onButtonChange(buttonMask, false)
                        true
                    }
                    else -> false
                }
            }
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SystemButton(
    text: String,
    buttonMask: Int,
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    onPressHaptic: () -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (isPressed) Color.Gray else Color.DarkGray)
                .pointerInteropFilter { event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            isPressed = true
                            onPressHaptic()
                            onButtonChange(buttonMask, true)
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            isPressed = false
                            onButtonChange(buttonMask, false)
                            true
                        }
                        else -> false
                    }
                }
        )
        Text(
            text = text,
            color = Color.LightGray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun VirtualController(
    onButtonChange: (mask: Int, pressed: Boolean) -> Unit,
    onMenuClick: () -> Unit,
    onTurboToggle: () -> Unit,
    modifier: Modifier = Modifier,
    opacity: Float = 0.85f,
    hapticFeedbackEnabled: Boolean = true
) {
    val context = LocalContext.current
    val hapticManager = remember(context) { HapticManager(context) }
    val onHapticClick = remember(hapticFeedbackEnabled) {
        { hapticManager.performButtonClick(hapticFeedbackEnabled) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(opacity.coerceIn(0.2f, 1.0f))
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShoulderButton(
                text = "L",
                buttonMask = GbaInput.BUTTON_L,
                onButtonChange = onButtonChange,
                onPressHaptic = onHapticClick
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = {
                        hapticManager.performTurboToggle(hapticFeedbackEnabled)
                        onTurboToggle()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF2C2449), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Turbo",
                        tint = GbaSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF2C2449), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            ShoulderButton(
                text = "R",
                buttonMask = GbaInput.BUTTON_R,
                onButtonChange = onButtonChange,
                onPressHaptic = onHapticClick
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DPad(
                onButtonChange = onButtonChange,
                onPressHaptic = onHapticClick
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                SystemButton(
                    text = "SELECT",
                    buttonMask = GbaInput.BUTTON_SELECT,
                    onButtonChange = onButtonChange,
                    onPressHaptic = onHapticClick
                )
                SystemButton(
                    text = "START",
                    buttonMask = GbaInput.BUTTON_START,
                    onButtonChange = onButtonChange,
                    onPressHaptic = onHapticClick
                )
            }

            Box(
                modifier = Modifier
                    .size(110.dp)
                    .padding(4.dp)
            ) {
                ControllerButton(
                    text = "B",
                    buttonMask = GbaInput.BUTTON_B,
                    onButtonChange = onButtonChange,
                    size = 46.dp,
                    containerColor = Color(0xFFD32F2F),
                    modifier = Modifier.align(Alignment.BottomStart),
                    onPressHaptic = onHapticClick
                )
                ControllerButton(
                    text = "A",
                    buttonMask = GbaInput.BUTTON_A,
                    onButtonChange = onButtonChange,
                    size = 46.dp,
                    containerColor = Color(0xFF388E3C),
                    modifier = Modifier.align(Alignment.TopEnd),
                    onPressHaptic = onHapticClick
                )
            }
        }
    }
}
