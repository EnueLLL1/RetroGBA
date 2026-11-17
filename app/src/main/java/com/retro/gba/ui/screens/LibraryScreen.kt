package com.retro.gba.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.retro.gba.data.model.RomItem
import com.retro.gba.ui.theme.GbaBackground
import com.retro.gba.ui.theme.GbaCard
import com.retro.gba.ui.theme.GbaPrimary
import com.retro.gba.ui.theme.GbaSecondary
import com.retro.gba.ui.theme.RetroGBATheme
import com.retro.gba.ui.theme.TrophyGold
import com.retro.gba.ui.viewmodel.EmulatorViewModel

@Composable
fun LibraryScreen(
    emulatorViewModel: EmulatorViewModel,
    onGameSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var savedRoms by remember { mutableStateOf<List<RomItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        savedRoms = emulatorViewModel.getSavedRoms()
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val fileName = resolveFileDisplayName(context, selectedUri)
            emulatorViewModel.loadRomFromUri(selectedUri, fileName)
            onGameSelected()
        }
    }

    LibraryScreenContent(
        savedRoms = savedRoms,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onPickRomClick = { filePicker.launch(arrayOf("*/*")) },
        onRomClick = { rom ->
            emulatorViewModel.loadRomFromUri(Uri.parse(rom.uri), rom.fileName)
            onGameSelected()
        },
        modifier = modifier
    )
}

@Composable
fun LibraryScreenContent(
    savedRoms: List<RomItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onPickRomClick: () -> Unit,
    onRomClick: (RomItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredRoms = remember(searchQuery, savedRoms) {
        if (searchQuery.isBlank()) {
            savedRoms
        } else {
            savedRoms.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.fileName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = GbaBackground,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPickRomClick,
                containerColor = GbaSecondary,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Carregar ROM")
                Spacer(Modifier.width(8.dp))
                Text("Carregar Jogo (.gba)", fontWeight = FontWeight.Bold)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Biblioteca de Jogos",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Selecione uma ROM de GBA para jogar com RetroAchievements",
                color = Color.Gray,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (savedRoms.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Pesquisar na biblioteca...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GbaSecondary,
                        unfocusedBorderColor = Color(0xFF2C2449),
                        focusedContainerColor = GbaCard,
                        unfocusedContainerColor = GbaCard,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            if (filteredRoms.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        GbaCartridgeFallbackIcon(modifier = Modifier.size(84.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Nenhum jogo encontrado" else "Nenhuma ROM carregada ainda",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "Tente buscar com outro termo."
                            } else {
                                "Toque no botão abaixo para escolher uma ROM (.gba ou .zip) do armazenamento do seu aparelho."
                            },
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(
                        items = filteredRoms,
                        key = { rom -> "${rom.md5Hash}:${rom.uri}" }
                    ) { rom ->
                        LibraryRomCard(
                            rom = rom,
                            onClick = { onRomClick(rom) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryRomCard(
    rom: RomItem,
    onClick: () -> Unit
) {
    val isInspection = LocalInspectionMode.current
    var imageFailed by remember(rom.badgeIconUrl) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = GbaCard),
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
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1A2E))
                    .border(1.dp, Color(0xFF332B52), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val showFallback = rom.badgeIconUrl.isNullOrBlank() || imageFailed || isInspection
                if (showFallback) {
                    GbaCartridgeFallbackIcon(modifier = Modifier.size(38.dp))
                } else {
                    AsyncImage(
                        model = rom.badgeIconUrl,
                        contentDescription = rom.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = { imageFailed = true },
                        onSuccess = { imageFailed = false }
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rom.title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = rom.fileName,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = TrophyGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${rom.unlockedAchievements}/${rom.totalAchievements}",
                    color = TrophyGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GbaCartridgeFallbackIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Topo largo do cartucho GBA
            drawRoundRect(
                color = GbaPrimary.copy(alpha = 0.85f),
                topLeft = Offset(w * 0.08f, h * 0.14f),
                size = Size(w * 0.84f, h * 0.18f),
                cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
            )
            // Corpo principal do cartucho
            drawRoundRect(
                color = Color(0xFF3E3568),
                topLeft = Offset(w * 0.14f, h * 0.24f),
                size = Size(w * 0.72f, h * 0.64f),
                cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
            )
            // Rótulo central do cartucho
            drawRoundRect(
                color = Color(0xFF1A162B),
                topLeft = Offset(w * 0.22f, h * 0.36f),
                size = Size(w * 0.56f, h * 0.40f),
                cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
            )
            // Faixa de destaque cyan no rótulo
            drawRoundRect(
                color = GbaSecondary.copy(alpha = 0.7f),
                topLeft = Offset(w * 0.25f, h * 0.39f),
                size = Size(w * 0.50f, h * 0.07f),
                cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
            )
        }
        Icon(
            imageVector = Icons.Default.SportsEsports,
            contentDescription = null,
            tint = GbaSecondary.copy(alpha = 0.8f),
            modifier = Modifier
                .fillMaxSize(0.32f)
                .offset(y = 3.dp)
        )
    }
}

private fun resolveFileDisplayName(context: Context, uri: Uri): String {
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
    } catch (_: Exception) {
        // Fallback para segmento da URI quando o provider não expõe DISPLAY_NAME
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "game.gba" } ?: "game.gba"
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun LibraryScreenPopulatedPreview() {
    RetroGBATheme {
        LibraryScreenContent(
            savedRoms = listOf(
                RomItem(
                    uri = "content://roms/firered.gba",
                    fileName = "Pokemon - FireRed Version.gba",
                    title = "Pokémon FireRed",
                    md5Hash = "e26ee0d44e809351c8ce2d73c7400cdd",
                    gameId = 515L,
                    unlockedAchievements = 18,
                    totalAchievements = 54,
                    totalPoints = 410
                ),
                RomItem(
                    uri = "content://roms/metroid.zip",
                    fileName = "Metroid - Zero Mission.zip",
                    title = "Metroid: Zero Mission",
                    md5Hash = "ebbce58109988b6da61ebb06c7a432d5",
                    gameId = 789L,
                    unlockedAchievements = 7,
                    totalAchievements = 42,
                    totalPoints = 300
                )
            ),
            searchQuery = "",
            onSearchQueryChange = {},
            onPickRomClick = {},
            onRomClick = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun LibraryScreenEmptyPreview() {
    RetroGBATheme {
        LibraryScreenContent(
            savedRoms = emptyList(),
            searchQuery = "",
            onSearchQueryChange = {},
            onPickRomClick = {},
            onRomClick = {}
        )
    }
}
