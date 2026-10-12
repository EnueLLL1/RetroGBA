package com.retro.gba.ui.components

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.gba.ui.theme.GbaBackground

@Composable
fun GbaScreenCanvas(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(3f / 2f) // Aspect ratio nativo do Game Boy Advance (240:160)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(2.dp, Color(0xFF2E2744), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "GBA Display",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.None // Pixel art nítido
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

