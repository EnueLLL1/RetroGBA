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
    val context = LocalContext.current
    var romList by remember { mutableStateOf<List<RomItem>>(emptyList()) }

    // Launcher do SAF para seleção de arquivos .gba
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

            Spacer(modifier = Modifier.height(20.dp))

            if (romList.isEmpty()) {
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
                            text = "Nenhuma ROM carregada ainda",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Toque no botão abaixo para escolher uma ROM (.gba ou .zip) do armazenamento do seu aparelho.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(romList) { rom ->
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
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rom.title,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "MD5: ${rom.md5Hash.take(8)}...",
                                        color = Color.Gray,
                                        fontSize = 11.sp
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

