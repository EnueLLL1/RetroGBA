package com.retro.gba.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.gba.ui.components.AchievementOverlay
import com.retro.gba.ui.components.GbaScreenCanvas
import com.retro.gba.ui.components.VirtualController
import com.retro.gba.ui.theme.GbaBackground
import com.retro.gba.ui.theme.GbaPrimary
import com.retro.gba.ui.theme.GbaSecondary
import com.retro.gba.ui.theme.TrophyGold
import com.retro.gba.ui.viewmodel.EmulatorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulatorScreen(
    viewModel: EmulatorViewModel,
    onNavigateToAchievements: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentFrame by viewModel.currentFrame.collectAsState()
    val currentGame by viewModel.currentGame.collectAsState()
    val recentAchievement by viewModel.recentUnlockedAchievement.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isTurbo by viewModel.isTurboActive.collectAsState()
    val isSimulationMode by viewModel.isSimulationMode.collectAsState()
    val activeSlot by viewModel.activeStateSlot.collectAsState()
    val saveSlots by viewModel.saveSlots.collectAsState()
    val controllerOpacity by viewModel.controllerOpacity.collectAsState()
    val hapticFeedbackEnabled by viewModel.hapticFeedbackEnabled.collectAsState()
    val fastForwardSpeed by viewModel.fastForwardSpeed.collectAsState()

    var showMenuSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = GbaBackground
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Barra superior de status do jogo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = currentGame?.title ?: "RetroGBA Player",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                        if (isSimulationMode) {
                            Surface(
                                color = Color(0xFF441820),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "SIMULAÇÃO",
                                    color = Color(0xFFFF8A80),
                                    fontSize = 10.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (isTurbo) {
                        Text(
                            text = "⚡ TURBO ${"%.1f".format(fastForwardSpeed)}x",
                            color = GbaSecondary,
                            fontSize = 11.sp,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Tela de exibição do jogo GBA (aspect ratio 3:2)
                GbaScreenCanvas(
                    bitmap = currentFrame,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Controles Virtuais Táteis
                VirtualController(
                    onButtonChange = { mask, pressed ->
                        viewModel.gbaCore.setButtonPressed(mask, pressed)
                    },
                    onMenuClick = { showMenuSheet = true },
                    onTurboToggle = { viewModel.toggleTurbo() },
                    opacity = controllerOpacity,
                    hapticFeedbackEnabled = hapticFeedbackEnabled,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Notificação flutuante de conquista desbloqueada
            AchievementOverlay(
                achievement = recentAchievement,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Menu Rápido Inferior (Bottom Sheet com Gerenciador de Saves)
            if (showMenuSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showMenuSheet = false },
                    containerColor = Color(0xFF1E1A2E)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = currentGame?.title ?: "Menu de Operações & Saves",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )

                        HorizontalDivider(color = Color.DarkGray)

                        // Seletor de Slots de Save State (0 a 4)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Slot de Estado Ativo:",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                (0..4).forEach { slotIdx ->
                                    val isSelected = slotIdx == activeSlot
                                    val slotInfo = saveSlots.find { it.slot == slotIdx }
                                    val hasData = slotInfo?.exists == true

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .background(
                                                color = if (isSelected) GbaPrimary else Color(0xFF2C2449),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) GbaSecondary else if (hasData) Color.LightGray else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { viewModel.selectSlot(slotIdx) }
                                    ) {
                                        Text(
                                            text = "$slotIdx" + if (hasData) " •" else "",
                                            color = if (isSelected) Color.White else Color.Gray,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Ações de Save State
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.saveState(activeSlot)
                                    showMenuSheet = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2449))
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Salvar ($activeSlot)")
                            }

                            Button(
                                onClick = {
                                    viewModel.loadState(activeSlot)
                                    showMenuSheet = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2449))
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Carregar ($activeSlot)")
                            }
                        }

                        // Botão para persistir bateria (.SAV)
                        OutlinedButton(
                            onClick = {
                                viewModel.persistBatterySave()
                                showMenuSheet = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.BatteryChargingFull, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Gravar Bateria do Jogo (.SAV)")
                        }

                        HorizontalDivider(color = Color.DarkGray)

                        // Configurações de Controles Táteis e Interface
                        Text(
                            text = "Ajustes de Controles & Gameplay",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )

                        // Slider de Opacidade dos Controles
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Opacidade dos Controles:",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "${(controllerOpacity * 100).toInt()}%",
                                    fontSize = 12.sp,
                                    color = GbaSecondary,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                            Slider(
                                value = controllerOpacity,
                                onValueChange = { viewModel.setControllerOpacity(it) },
                                valueRange = 0.2f..1.0f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Switch de Feedback Háptico (Vibração)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Vibração ao Tocar (Háptico)",
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Resposta tátil nos botões e conquistas",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = hapticFeedbackEnabled,
                                onCheckedChange = { viewModel.setHapticFeedback(it) }
                            )
                        }

                        // Chips de Velocidade de Turbo
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Multiplicador de Velocidade (Turbo):",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(2.0f, 2.5f, 3.0f, 4.0f).forEach { speed ->
                                    val isSelected = fastForwardSpeed == speed
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setFastForwardSpeed(speed) },
                                        label = { Text("${speed}x", fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Color.DarkGray)

                        // Ver conquistas
                        Button(
                            onClick = {
                                showMenuSheet = false
                                onNavigateToAchievements()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = TrophyGold.copy(alpha = 0.25f))
                        ) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = TrophyGold)
                            Spacer(Modifier.width(8.dp))
                            Text("Ver Conquistas do Jogo", color = TrophyGold)
                        }

                        OutlinedButton(
                            onClick = { showMenuSheet = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Fechar Menu")
                        }
                    }
                }
            }
        }
    }
}
