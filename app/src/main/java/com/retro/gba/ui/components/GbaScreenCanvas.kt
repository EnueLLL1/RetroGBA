package com.retro.gba.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow

/**
 * Sobrecarga reativa que isola a coleta de quadros de 60 FPS exclusivamente dentro do Canvas,
 * evitando recomposições desnecessárias nos controles virtuais e no restante da tela.
 */
@Composable
fun GbaScreenCanvas(
    frameFlow: StateFlow<Bitmap?>,
    modifier: Modifier = Modifier,
    stretchToFill: Boolean = false
) {
    val bitmap by frameFlow.collectAsState()
    GbaScreenCanvas(
        bitmap = bitmap,
        modifier = modifier,
        stretchToFill = stretchToFill
    )
}

@Composable
fun GbaScreenCanvas(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
    stretchToFill: Boolean = false
) {
    val sizeModifier = if (stretchToFill) {
        modifier.fillMaxSize()
    } else {
        modifier.aspectRatio(3f / 2f) // Proporção nativa do Game Boy Advance (240:160)
    }

    Box(
        modifier = sizeModifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "GBA Display",
                modifier = Modifier.fillMaxSize(),
                contentScale = if (stretchToFill) ContentScale.FillBounds else ContentScale.Fit,
                filterQuality = FilterQuality.None // Pixel art nítido
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "GAME BOY ADVANCE", color = Color(0xFF5D48B3), fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Nenhum jogo em execução", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}
