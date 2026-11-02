package com.retro.gba.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.retro.gba.data.prefs.UserPreferences
import com.retro.gba.emulator.GbaInput
import com.retro.gba.ui.components.AchievementOverlay
import com.retro.gba.ui.components.GbaScreenCanvas
import com.retro.gba.ui.components.VirtualController
import com.retro.gba.ui.theme.GbaBackground
import com.retro.gba.ui.theme.GbaPrimary
import com.retro.gba.ui.theme.GbaSecondary
import com.retro.gba.ui.theme.TrophyGold
import com.retro.gba.ui.viewmodel.EmulatorViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulatorScreen(
    viewModel: EmulatorViewModel,
    onNavigateToAchievements: () -> Unit,
    onNavigateToLibrary: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentGame by viewModel.currentGame.collectAsState()
    val recentAchievement by viewModel.recentUnlockedAchievement.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isTurbo by viewModel.isTurboActive.collectAsState()
    val activeSlot by viewModel.activeStateSlot.collectAsState()
    val saveSlots by viewModel.saveSlots.collectAsState()
    val controllerOpacity by viewModel.controllerOpacity.collectAsState()
    val hapticFeedbackEnabled by viewModel.hapticFeedbackEnabled.collectAsState()
    val fastForwardSpeed by viewModel.fastForwardSpeed.collectAsState()
    val isEcoMode by viewModel.isEcoMode.collectAsState()
    val currentFps by viewModel.currentFps.collectAsState()
    val cheats by viewModel.cheats.collectAsState()
    val isHardcore = viewModel.raRepository.isHardcore()

    val controllerTheme by viewModel.controllerTheme.collectAsState()
    val showQuickSaveLoad by viewModel.showQuickSaveLoad.collectAsState()
    val showAbCombo by viewModel.showAbCombo.collectAsState()
    val showTurboButton by viewModel.showTurboButton.collectAsState()
    val showFpsCounter by viewModel.showFpsCounter.collectAsState()
    val stretchToFill by viewModel.stretchToFill.collectAsState()
    val isLayoutEditMode by viewModel.isLayoutEditMode.collectAsState()
    val portraitControls by viewModel.portraitControls.collectAsState()
    val landscapeControls by viewModel.landscapeControls.collectAsState()

    var showMenuSheet by remember { mutableStateOf(false) }
    var showAddCheatDialog by remember { mutableStateOf(false) }
    var cheatDesc by remember { mutableStateOf("") }
    var cheatCodeText by remember { mutableStateOf("") }
    var cheatTypeSelected by remember { mutableStateOf(com.retro.gba.data.model.CheatType.AUTODETECT) }

    // Pausa automaticamente o jogo enquanto o Menu Rápido estiver aberto
    LaunchedEffect(showMenuSheet) {
        viewModel.setMenuPaused(showMenuSheet)
    }

    // Intercepta o botão/gesto Voltar do Android para sair da edição de layout ou abrir o menu de pausa
    BackHandler(enabled = true) {
        if (isLayoutEditMode) {
            viewModel.setLayoutEditMode(false)
        } else {
            showMenuSheet = !showMenuSheet
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> viewModel.onAppPause()
                Lifecycle.Event.ON_RESUME -> {
                    if (!showMenuSheet) {
                        viewModel.onAppResume()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Auto-dispensa mensagens rápidas no topo após 2.2 segundos
    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(2200)
            viewModel.clearStatusMessage()
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GbaBackground)
    ) {
        if (isLandscape) {
            // Modo Paisagem Imersivo (Tela Cheia)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                GbaScreenCanvas(
                    frameFlow = viewModel.currentFrame,
                    stretchToFill = stretchToFill,
                    modifier = if (stretchToFill) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.fillMaxHeight()
                    }
                )

                VirtualController(
                    onButtonChange = { mask, pressed ->
                        viewModel.gbaCore.setButtonPressed(mask, pressed)
                    },
                    onMenuClick = {
                        viewModel.gbaCore.setButtonPressed(GbaInput.ALL_RELEASED, false)
                        showMenuSheet = true
                    },
                    onTurboToggle = { viewModel.toggleTurbo() },
                    opacity = controllerOpacity,
                    theme = controllerTheme,
                    controls = landscapeControls,
                    onControlOffsetChange = { id, dx, dy ->
                        viewModel.updateControlOffset(id, isLandscape = true, dx, dy)
                    },
                    onControlScaleChange = { id, scale ->
                        viewModel.updateControlScale(id, isLandscape = true, scale)
                    },
                    onResetSingleControl = { id ->
                        viewModel.resetSingleControl(id, isLandscape = true)
                    },
                    onResetLayout = { viewModel.resetLayoutOffsets(isLandscape = true) },
                    onExitEditMode = { viewModel.setLayoutEditMode(false) },
                    showAbCombo = showAbCombo,
                    showQuickSaveLoad = showQuickSaveLoad,
                    showTurboButton = showTurboButton,
                    showFpsCounter = showFpsCounter,
                    isTurboActive = isTurbo,
                    currentFps = currentFps,
                    isEditMode = isLayoutEditMode,
                    onQuickSave = { viewModel.quickSave() },
                    onQuickLoad = { viewModel.quickLoad() },
                    hapticFeedbackEnabled = hapticFeedbackEnabled,
                    isLandscape = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Modo Retrato: Tela GBA encostada no topo sem bordas desperdiçadas + Controles embaixo
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GbaScreenCanvas(
                    frameFlow = viewModel.currentFrame,
                    stretchToFill = false,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color(0xFF28223C)
                )

                VirtualController(
                    onButtonChange = { mask, pressed ->
                        viewModel.gbaCore.setButtonPressed(mask, pressed)
                    },
                    onMenuClick = {
                        viewModel.gbaCore.setButtonPressed(GbaInput.ALL_RELEASED, false)
                        showMenuSheet = true
                    },
                    onTurboToggle = { viewModel.toggleTurbo() },
                    opacity = controllerOpacity,
                    theme = controllerTheme,
                    controls = portraitControls,
                    onControlOffsetChange = { id, dx, dy ->
                        viewModel.updateControlOffset(id, isLandscape = false, dx, dy)
                    },
                    onControlScaleChange = { id, scale ->
                        viewModel.updateControlScale(id, isLandscape = false, scale)
                    },
                    onResetSingleControl = { id ->
                        viewModel.resetSingleControl(id, isLandscape = false)
                    },
                    onResetLayout = { viewModel.resetLayoutOffsets(isLandscape = false) },
                    onExitEditMode = { viewModel.setLayoutEditMode(false) },
                    showAbCombo = showAbCombo,
                    showQuickSaveLoad = showQuickSaveLoad,
                    showTurboButton = showTurboButton,
                    showFpsCounter = showFpsCounter,
                    isTurboActive = isTurbo,
                    currentFps = currentFps,
                    isEditMode = isLayoutEditMode,
                    onQuickSave = { viewModel.quickSave() },
                    onQuickLoad = { viewModel.quickLoad() },
                    hapticFeedbackEnabled = hapticFeedbackEnabled,
                    isLandscape = false,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }

        // Notificação compacta discreta no topo (substitui o Snackbar gigante que cobria os controles)
        AnimatedVisibility(
            visible = statusMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 46.dp, start = 16.dp, end = 16.dp)
        ) {
            Surface(
                color = Color(0xE61E1A2E),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, GbaSecondary.copy(alpha = 0.6f))
            ) {
                Text(
                    text = statusMessage ?: "",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        // Notificação flutuante de conquista desbloqueada
        AchievementOverlay(
            achievement = recentAchievement,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Menu Rápido de Pausa (Bottom Sheet com Navegação, Saves, Proporção de Vídeo e Controles)
        if (showMenuSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMenuSheet = false },
                containerColor = Color(0xFF1E1A2E)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Cabeçalho com título do jogo e botão Continuar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentGame?.title ?: "Jogo Pausado",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1
                            )
                            Text(
                                text = "Slot ativo: $activeSlot • ${if (isHardcore) "Modo Hardcore RA" else "Modo Softcore"}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }

                        Button(
                            onClick = { showMenuSheet = false },
                            colors = ButtonDefaults.buttonColors(containerColor = GbaSecondary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Continuar", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // Ações de Navegação Rápida (Biblioteca & Troféus RA)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showMenuSheet = false
                                viewModel.persistBatterySave()
                                onNavigateToLibrary()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Biblioteca", color = Color.White, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                showMenuSheet = false
                                onNavigateToAchievements()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = GbaPrimary)
                        ) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = TrophyGold, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Conquistas RA", fontSize = 12.sp)
                        }
                    }

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
                                        fontWeight = FontWeight.Bold,
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

                    // Configurações de Controles Táteis e Vídeo
                    Text(
                        text = "Vídeo & Controles Virtuais",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )

                    // Proporção de Vídeo (3:2 Original vs Tela Cheia)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Proporção de Vídeo (Paisagem):",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !stretchToFill,
                                onClick = { viewModel.setStretchToFill(false) },
                                label = { Text("3:2 Original (Pixel Perfect)", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = stretchToFill,
                                onClick = { viewModel.setStretchToFill(true) },
                                label = { Text("Tela Cheia (Esticar)", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Botão de Destaque: Editar Layout e Posição dos Controles
                    Button(
                        onClick = {
                            showMenuSheet = false
                            viewModel.setLayoutEditMode(true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = GbaSecondary)
                    ) {
                        Icon(Icons.Default.DashboardCustomize, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Editar Posição e Tamanho dos Controles",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Seletor de Tema Visual dos Controles
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Tema Visual dos Controles:",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        val controllerThemes = listOf(
                            UserPreferences.THEME_FOLLOW_APP to "Seguir App",
                            UserPreferences.THEME_GBA_INDIGO to "GBA Índigo",
                            UserPreferences.THEME_GLACIER to "Glacier",
                            UserPreferences.THEME_SP_PLATINUM to "SP Platinum",
                            UserPreferences.THEME_FAMICOM to "Famicom",
                            UserPreferences.THEME_OLED_STEALTH to "OLED Stealth"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            controllerThemes.take(3).forEach { (key, label) ->
                                FilterChip(
                                    selected = controllerTheme == key || (key == UserPreferences.THEME_GBA_INDIGO && controllerTheme == UserPreferences.THEME_RETRO_GBA),
                                    onClick = { viewModel.setControllerTheme(key) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            controllerThemes.drop(3).forEach { (key, label) ->
                                FilterChip(
                                    selected = controllerTheme == key || (key == UserPreferences.THEME_OLED_STEALTH && controllerTheme == UserPreferences.THEME_CLEAN_MYBOY),
                                    onClick = { viewModel.setControllerTheme(key) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

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
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = controllerOpacity,
                            onValueChange = { viewModel.setControllerOpacity(it) },
                            valueRange = 0.2f..1.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Switch para Botão Combo A+B
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Botão Combo A+B",
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Exibe botão central que aciona A e B simultaneamente",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = showAbCombo,
                            onCheckedChange = { viewModel.setShowAbCombo(it) }
                        )
                    }

                    // Switch para Botão Turbo Flutuante
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Botão Turbo Flutuante",
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Ativa botão de aceleração direta na tela",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = showTurboButton,
                            onCheckedChange = { viewModel.setShowTurboButton(it) }
                        )
                    }

                    // Switch para Atalhos Rápidos (Quick Save / Quick Load)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Atalhos Rápidos (Quick Save / Quick Load)",
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Botões de salvar/carregar rápido na tela",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = showQuickSaveLoad,
                            onCheckedChange = { viewModel.setShowQuickSaveLoad(it) }
                        )
                    }

                    // Switch para Contador de FPS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Contador de Taxa de Quadros (FPS)",
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Exibe telemetria de FPS na tela",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = showFpsCounter,
                            onCheckedChange = { viewModel.setShowFpsCounter(it) }
                        )
                    }

                    // Switch de Feedback Háptico (Vibração)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
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

                    // Switch de Economia de Bateria (Modo Eco)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Modo Eco de Bateria (Frameskip)",
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Reduz carga gráfica e aquecimento para partidas longas",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = isEcoMode,
                            onCheckedChange = { viewModel.setEcoMode(it) }
                        )
                    }

                    HorizontalDivider(color = Color.DarkGray)

                    // Seção de Códigos de Trapaça (Cheats)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Códigos de Trapaça (Cheats)",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                        if (!isHardcore) {
                            TextButton(onClick = { showAddCheatDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Novo Cheat", fontSize = 12.sp)
                            }
                        }
                    }

                    if (isHardcore) {
                        Surface(
                            color = Color(0xFF442A12),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB74D),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Modo Hardcore ativo: O uso de cheats é bloqueado para manter a integridade dos troféus no RetroAchievements.",
                                    color = Color(0xFFFFE0B2),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        if (cheats.isEmpty()) {
                            Text(
                                text = "Nenhum cheat cadastrado. Clique em 'Novo Cheat' para inserir códigos GameShark, Action Replay ou CodeBreaker.",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                cheats.forEach { cheat ->
                                    Surface(
                                        color = Color(0xFF26203D),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = cheat.description,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "${cheat.type.displayName} • ${cheat.code.replace("\n", " ")}",
                                                    color = Color.LightGray,
                                                    fontSize = 10.sp,
                                                    maxLines = 1
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Switch(
                                                    checked = cheat.isEnabled,
                                                    onCheckedChange = { viewModel.toggleCheat(cheat.id) },
                                                    modifier = Modifier.height(24.dp)
                                                )
                                                IconButton(
                                                    onClick = { viewModel.removeCheat(cheat.id) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Remover",
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Diálogo para adicionar novo Cheat
        if (showAddCheatDialog) {
            AlertDialog(
                onDismissRequest = { showAddCheatDialog = false },
                containerColor = Color(0xFF1E1A2E),
                title = {
                    Text("Adicionar Código de Cheat", color = Color.White, fontSize = 16.sp)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = cheatDesc,
                            onValueChange = { cheatDesc = it },
                            label = { Text("Descrição (ex: Vidas Infinitas)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = GbaSecondary,
                                unfocusedBorderColor = Color.Gray
                            )
                        )

                        Text("Formato do Código:", color = Color.LightGray, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                com.retro.gba.data.model.CheatType.AUTODETECT to "Auto",
                                com.retro.gba.data.model.CheatType.GAMESHARK to "GameShark",
                                com.retro.gba.data.model.CheatType.CODEBREAKER to "CodeBreaker"
                            ).forEach { (type, label) ->
                                FilterChip(
                                    selected = cheatTypeSelected == type,
                                    onClick = { cheatTypeSelected = type },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = cheatCodeText,
                            onValueChange = { cheatCodeText = it },
                            label = { Text("Código Hexadecimal (1 ou mais linhas)") },
                            minLines = 3,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = GbaSecondary,
                                unfocusedBorderColor = Color.Gray
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.addCheat(cheatDesc, cheatCodeText, cheatTypeSelected)
                            cheatDesc = ""
                            cheatCodeText = ""
                            showAddCheatDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GbaPrimary)
                    ) {
                        Text("Ativar Cheat")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddCheatDialog = false }) {
                        Text("Cancelar", color = Color.Gray)
                    }
                }
            )
        }
    }
}
