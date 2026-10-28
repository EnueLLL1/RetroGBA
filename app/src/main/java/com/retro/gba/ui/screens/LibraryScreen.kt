package com.retro.gba.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.gba.data.model.RomItem
import com.retro.gba.ui.theme.GbaBackground
import com.retro.gba.ui.theme.GbaCard
import com.retro.gba.ui.theme.GbaSecondary
import com.retro.gba.ui.theme.TrophyGold
import com.retro.gba.ui.viewmodel.EmulatorViewModel

@Composable
fun LibraryScreen(
    emulatorViewModel: EmulatorViewModel,
    onGameSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    var savedRoms by remember { mutableStateOf<List<RomItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        savedRoms = emulatorViewModel.getSavedRoms()
    }

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

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val fileName = selectedUri.lastPathSegment?.substringAfterLast('/') ?: "game.gba"
            emulatorViewModel.loadRomFromUri(selectedUri, fileName)
            onGameSelected()
        }
    }

    Scaffold(
        containerColor = GbaBackground,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    filePicker.launch(arrayOf("*/*"))
                },
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
            modifier = modifier
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
                    onValueChange = { searchQuery = it },
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
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF3B3358),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Nenhum jogo encontrado" else "Nenhuma ROM carregada ainda",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Tente buscar com outro termo." else "Toque no botão abaixo para escolher uma ROM (.gba ou .zip) do armazenamento do seu aparelho.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredRoms.size) { index ->
                        val rom = filteredRoms[index]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    emulatorViewModel.loadRomFromUri(Uri.parse(rom.uri), rom.fileName)
                                    onGameSelected()
                                },
                            colors = CardDefaults.cardColors(containerColor = GbaCard),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Capa do Jogo (BoxArt / Ícone RA)
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1A2E)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!rom.badgeIconUrl.isNullOrBlank()) {
                                        coil.compose.AsyncImage(
                                            model = rom.badgeIconUrl,
                                            contentDescription = rom.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = GbaSecondary,
                                            modifier = Modifier.size(28.dp)
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
                                        maxLines = 1
                                    )
                                    Text(
                                        text = rom.fileName,
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }

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
                }
            }
        }
    }
}
