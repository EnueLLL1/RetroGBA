package com.retro.gba.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.retro.gba.data.model.Achievement
import com.retro.gba.data.model.GameInfo
import com.retro.gba.ui.theme.GbaBackground
import com.retro.gba.ui.theme.GbaCard
import com.retro.gba.ui.theme.RetroGBATheme
import com.retro.gba.ui.theme.RetroGreen
import com.retro.gba.ui.theme.TrophyGold
import com.retro.gba.ui.viewmodel.EmulatorViewModel

@Composable
fun AchievementsScreen(
    viewModel: EmulatorViewModel,
    modifier: Modifier = Modifier
) {
    val currentGame by viewModel.currentGame.collectAsState()
    val achievements by viewModel.achievements.collectAsState()

    AchievementsScreenContent(
        currentGame = currentGame,
        achievements = achievements,
        modifier = modifier
    )
}

@Composable
fun AchievementsScreenContent(
    currentGame: GameInfo?,
    achievements: List<Achievement>,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableIntStateOf(0) } // 0 = TODAS, 1 = UNLOCKED, 2 = LOCKED

    val unlockedCount = achievements.count { it.isUnlocked }
    val totalCount = achievements.size
    val currentPoints = achievements.filter { it.isUnlocked }.sumOf { it.points }
    val totalPoints = achievements.sumOf { it.points }

    val filteredAchievements = remember(achievements, selectedFilter) {
        when (selectedFilter) {
            1 -> achievements.filter { it.isUnlocked }
            2 -> achievements.filter { !it.isUnlocked }
            else -> achievements
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GbaBackground)
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GbaCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = currentGame?.title ?: "Nenhum Jogo Selecionado",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Progresso: $unlockedCount / $totalCount Conquistas",
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )
                    Text(
                        text = "$currentPoints / $totalPoints Pontos",
                        fontSize = 13.sp,
                        color = TrophyGold,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { if (totalCount > 0) unlockedCount.toFloat() / totalCount else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = TrophyGold,
                    trackColor = Color(0xFF1B1828)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (achievements.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == 0,
                    onClick = { selectedFilter = 0 },
                    label = { Text("Todas ($totalCount)", fontSize = 12.sp, maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedFilter == 1,
                    onClick = { selectedFilter = 1 },
                    label = { Text("Obtidas ($unlockedCount)", fontSize = 12.sp, maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedFilter == 2,
                    onClick = { selectedFilter = 2 },
                    label = { Text("Pendentes (${totalCount - unlockedCount})", fontSize = 12.sp, maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (filteredAchievements.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = Color.DarkGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (achievements.isEmpty()) {
                            "Nenhuma conquista disponível no momento.\nCarregue uma ROM suportada pelo RetroAchievements."
                        } else {
                            "Nenhuma conquista nesta categoria."
                        },
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = filteredAchievements,
                    key = { it.id }
                ) { achievement ->
                    AchievementItemCard(achievement = achievement)
                }
            }
        }
    }
}

@Composable
fun AchievementItemCard(achievement: Achievement) {
    val isInspection = LocalInspectionMode.current
    var badgeLoadFailed by remember(achievement.badgeUrl) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (achievement.isUnlocked) Color(0xFF26203D) else Color(0xFF1B1926)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF12101C))
                    .border(
                        1.dp,
                        if (achievement.isUnlocked) TrophyGold else Color.DarkGray,
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val showFallback = isInspection || badgeLoadFailed || achievement.badgeName.isBlank()
                if (showFallback) {
                    Icon(
                        imageVector = if (badgeLoadFailed) {
                            Icons.Default.CloudOff
                        } else if (achievement.isUnlocked) {
                            Icons.Default.EmojiEvents
                        } else {
                            Icons.Default.Lock
                        },
                        contentDescription = achievement.title,
                        tint = if (achievement.isUnlocked) TrophyGold else Color.Gray,
                        modifier = Modifier.size(26.dp)
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
                Text(
                    text = achievement.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (achievement.isUnlocked) Color.White else Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = achievement.description,
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${achievement.points} pts",
                    color = TrophyGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (achievement.isUnlocked) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Desbloqueada",
                        tint = RetroGreen,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Bloqueada",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun AchievementsScreenPopulatedPreview() {
    RetroGBATheme {
        AchievementsScreenContent(
            currentGame = GameInfo(
                id = 515L,
                title = "Pokémon FireRed Version"
            ),
            achievements = listOf(
                Achievement(
                    id = 101L,
                    title = "Primeiro Parceiro",
                    description = "Escolha Bulbasaur, Charmander ou Squirtle no laboratório do Prof. Carvalho.",
                    points = 5,
                    badgeName = "00101",
                    isUnlocked = true
                ),
                Achievement(
                    id = 102L,
                    title = "Insígnia da Rocha",
                    description = "Derrote o líder de ginásio Brock em Pewter City.",
                    points = 10,
                    badgeName = "00102",
                    isUnlocked = true
                ),
                Achievement(
                    id = 103L,
                    title = "Mestre de Kanto",
                    description = "Vença a Elite dos Quatro e entre no Hall da Fama.",
                    points = 50,
                    badgeName = "00103",
                    isUnlocked = false
                )
            )
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun AchievementsScreenEmptyPreview() {
    RetroGBATheme {
        AchievementsScreenContent(
            currentGame = null,
            achievements = emptyList()
        )
    }
}
