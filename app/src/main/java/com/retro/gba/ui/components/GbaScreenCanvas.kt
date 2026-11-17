package com.retro.gba.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.gba.ui.theme.GbaPrimary
import com.retro.gba.ui.theme.GbaSecondary
import com.retro.gba.ui.theme.RetroGBATheme

/**
 * Superfície de exibição do framebuffer 240x160 (proporção nativa 3:2) do Game Boy Advance.
 * Respeita integralmente as dimensões do [modifier] recebido sem forçar fillMaxWidth,
 * mantendo pixel art nítido via FilterQuality.None.
 */
@Composable
fun GbaScreenCanvas(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    val isInspection = LocalInspectionMode.current
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .aspectRatio(3f / 2f)
            .clip(shape)
            .background(Color.Black)
            .border(2.dp, Color(0xFF2E2744), shape),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "GBA Display",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None
            )
        } else if (isInspection) {
            GbaPreviewPlaceholder()
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "GAME BOY ADVANCE",
                    color = Color(0xFF5D48B3),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Nenhum jogo em execução",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun GbaPreviewPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0B0E18), Color(0xFF17132E), Color(0xFF0A0C14))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 18.dp.toPx()
            val gridColor = Color(0xFF231F3D)
            var x = step
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
            drawLine(
                color = GbaSecondary.copy(alpha = 0.35f),
                start = Offset(0f, size.height * 0.16f),
                end = Offset(size.width, size.height * 0.16f),
                strokeWidth = 2f
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "GAME BOY ADVANCE",
                color = GbaSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "240 × 160 • 60 FPS",
                color = GbaPrimary.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0D14)
@Composable
fun GbaScreenCanvasPreview() {
    RetroGBATheme {
        Box(modifier = Modifier.padding(12.dp)) {
            GbaScreenCanvas(
                bitmap = null,
                modifier = Modifier.width(320.dp)
            )
        }
    }
}
