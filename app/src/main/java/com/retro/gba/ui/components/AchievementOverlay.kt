package com.retro.gba.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.retro.gba.data.model.Achievement
import com.retro.gba.ui.theme.GbaSecondary
import com.retro.gba.ui.theme.RetroGBATheme
import com.retro.gba.ui.theme.TrophyGold

@Composable
fun AchievementOverlay(
    achievement: Achievement?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = achievement != null,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(animationSpec = tween(220)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(240)
        ) + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        if (achievement != null) {
            AchievementBannerCard(achievement = achievement)
        }
    }
}

@Composable
private fun AchievementBannerCard(
    achievement: Achievement,
    modifier: Modifier = Modifier
) {
    val isInspection = LocalInspectionMode.current
    var badgeLoadFailed by remember(achievement.badgeUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 460.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF1E1A34), Color(0xFF2C2454))
                    )
                )
                .border(1.5.dp, TrophyGold, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, GbaSecondary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isInspection || badgeLoadFailed || achievement.badgeName.isBlank()) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = achievement.title,
                        tint = TrophyGold,
                        modifier = Modifier.size(30.dp)
                    )
                } else {
                    AsyncImage(
                        model = achievement.badgeUrl,
                        contentDescription = achievement.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = { badgeLoadFailed = true },
                        onSuccess = { badgeLoadFailed = false }
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Troféu",
                        tint = TrophyGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "CONQUISTA DESBLOQUEADA!",
                        color = TrophyGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = achievement.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = achievement.description,
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(TrophyGold.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "+${achievement.points}",
                    color = TrophyGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0D14)
@Composable
fun AchievementOverlayPreview() {
    RetroGBATheme {
        AchievementBannerCard(
            achievement = Achievement(
                id = 101L,
                title = "Campeão da Liga Kanto",
                description = "Derrote a Elite dos Quatro sem usar itens de cura em batalha.",
                points = 25,
                badgeName = "00101",
                isUnlocked = true
            )
        )
    }
}
