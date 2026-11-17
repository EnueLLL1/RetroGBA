package com.retro.gba.ui.screens

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.retro.gba.data.model.Achievement
import com.retro.gba.data.model.CheatCode
import com.retro.gba.data.model.CheatType
import com.retro.gba.data.model.GameInfo
import com.retro.gba.data.repository.SaveStateInfo
import com.retro.gba.model.ControlLayoutPresets
import com.retro.gba.model.ControlLayoutProfile
import com.retro.gba.model.OrientationLayoutConfig
import com.retro.gba.ui.components.AchievementOverlay
import com.retro.gba.ui.components.ControlLayoutEditor
import com.retro.gba.ui.components.GbaScreenCanvas
import com.retro.gba.ui.components.VirtualController
import com.retro.gba.ui.theme.GbaBackground
import com.retro.gba.ui.theme.GbaPrimary
import com.retro.gba.ui.theme.GbaSecondary
import com.retro.gba.ui.theme.RetroGBATheme
import com.retro.gba.ui.theme.TrophyGold
import com.retro.gba.ui.viewmodel.EmulatorViewModel
import com.retro.gba.ui.viewmodel.LayoutProfileHelper
import kotlin.math.roundToInt

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
    val hideControls by viewModel.hideControls.collectAsState()
    val isEcoMode by viewModel.isEcoMode.collectAsState()
    val currentFps by viewModel.currentFps.collectAsState()
    val cheats by viewModel.cheats.collectAsState()
    val isHardcore by viewModel.sessionHardcoreActive.collectAsState()
    val layoutProfiles by viewModel.layoutProfiles.collectAsState()
    val activeLayoutProfile by viewModel.activeLayoutProfile.collectAsState()
    val isEditingLayout by viewModel.isEditingLayout.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> viewModel.onAppPause()
                Lifecycle.Event.ON_RESUME -> viewModel.onAppResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onAppPause()
        }
    }

    var showMenuSheet by remember { mutableStateOf(false) }
    var showAddCheatDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showMenuSheet, showAddCheatDialog) {
        viewModel.setOverlayModalOpen(showMenuSheet || showAddCheatDialog)
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.setOverlayModalOpen(false)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    EmulatorScreenContent(
        currentFrame = currentFrame,
        currentGame = currentGame,
        recentAchievement = recentAchievement,
        isTurbo = isTurbo,
        isSimulationMode = isSimulationMode,
        activeSlot = activeSlot,
        saveSlots = saveSlots,
        controllerOpacity = controllerOpacity,
        hapticFeedbackEnabled = hapticFeedbackEnabled,
        fastForwardSpeed = fastForwardSpeed,
        hideControls = hideControls,
        isEcoMode = isEcoMode,
        currentFps = currentFps,
        cheats = cheats,
        isHardcore = isHardcore,
        layoutProfiles = layoutProfiles,
        activeLayoutProfile = activeLayoutProfile,
        isEditingLayout = isEditingLayout,
        snackbarHostState = snackbarHostState,
        showMenuSheet = showMenuSheet,
        onShowMenuSheetChange = { showMenuSheet = it },
        showAddCheatDialog = showAddCheatDialog,
        onShowAddCheatDialogChange = { showAddCheatDialog = it },
        onTouchButtonsMaskChanged = { mask -> viewModel.setTouchButtonsMask(mask) },
        onToggleTurbo = { viewModel.toggleTurbo() },
        onQuickSave = { viewModel.saveState(activeSlot) },
        onQuickLoad = { viewModel.loadState(activeSlot) },
        onSelectSlot = { viewModel.selectSlot(it) },
        onSaveState = { slot -> viewModel.saveState(slot) },
        onLoadState = { slot -> viewModel.loadState(slot) },
        onPersistBatterySave = { viewModel.persistBatterySave() },
        onControllerOpacityChange = { viewModel.setControllerOpacity(it) },
        onHapticFeedbackChange = { viewModel.setHapticFeedback(it) },
        onFastForwardSpeedChange = { viewModel.setFastForwardSpeed(it) },
        onHideControlsChange = { viewModel.setHideControls(it) },
        onEcoModeChange = { viewModel.setEcoMode(it) },
        onEnterLayoutEditor = { viewModel.enterLayoutEditor() },
        onExitLayoutEditor = { viewModel.exitLayoutEditor() },
        onSaveOrientationLayout = { isLandscape, config ->
            viewModel.saveOrientationLayout(isLandscape, config)
            viewModel.exitLayoutEditor()
        },
        onSelectLayoutProfile = { viewModel.selectLayoutProfile(it) },
        onCreateCustomLayoutProfile = { viewModel.createCustomLayoutProfile(it) },
        onDeleteCustomLayoutProfile = { viewModel.deleteCustomLayoutProfile(it) },
        onAddCheat = { desc, code, type -> viewModel.addCheat(desc, code, type) },
        onToggleCheat = { viewModel.toggleCheat(it) },
        onRemoveCheat = { viewModel.removeCheat(it) },
        onNavigateToAchievements = onNavigateToAchievements,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulatorScreenContent(
    currentFrame: Bitmap?,
    currentGame: GameInfo?,
    recentAchievement: Achievement?,
    isTurbo: Boolean,
    isSimulationMode: Boolean,
    activeSlot: Int,
    saveSlots: List<SaveStateInfo>,
    controllerOpacity: Float,
    hapticFeedbackEnabled: Boolean,
    fastForwardSpeed: Float,
    hideControls: Boolean,
    isEcoMode: Boolean,
    currentFps: Int,
    cheats: List<CheatCode>,
    isHardcore: Boolean,
    layoutProfiles: List<ControlLayoutProfile>,
    activeLayoutProfile: ControlLayoutProfile,
    isEditingLayout: Boolean,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    showMenuSheet: Boolean = false,
    onShowMenuSheetChange: (Boolean) -> Unit = {},
    showAddCheatDialog: Boolean = false,
    onShowAddCheatDialogChange: (Boolean) -> Unit = {},
    onTouchButtonsMaskChanged: (Int) -> Unit = {},
    onToggleTurbo: () -> Unit = {},
    onQuickSave: () -> Unit = {},
    onQuickLoad: () -> Unit = {},
    onSelectSlot: (Int) -> Unit = {},
    onSaveState: (Int) -> Unit = {},
    onLoadState: (Int) -> Unit = {},
    onPersistBatterySave: () -> Unit = {},
    onControllerOpacityChange: (Float) -> Unit = {},
    onHapticFeedbackChange: (Boolean) -> Unit = {},
    onFastForwardSpeedChange: (Float) -> Unit = {},
    onHideControlsChange: (Boolean) -> Unit = {},
    onEcoModeChange: (Boolean) -> Unit = {},
    onEnterLayoutEditor: () -> Unit = {},
    onExitLayoutEditor: () -> Unit = {},
    onSaveOrientationLayout: (Boolean, OrientationLayoutConfig) -> Unit = { _, _ -> },
    onSelectLayoutProfile: (String) -> Unit = {},
    onCreateCustomLayoutProfile: (String) -> Unit = {},
    onDeleteCustomLayoutProfile: (String) -> Unit = {},
    onAddCheat: (String, String, CheatType) -> Unit = { _, _, _ -> },
    onToggleCheat: (String) -> Unit = {},
    onRemoveCheat: (String) -> Unit = {},
    onNavigateToAchievements: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    var cheatDesc by remember { mutableStateOf("") }
    var cheatCodeText by remember { mutableStateOf("") }
    var cheatTypeSelected by remember { mutableStateOf(CheatType.AUTODETECT) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = GbaBackground
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .onSizeChanged { containerSize = it }
        ) {
            val widthPx = if (containerSize.width > 0) {
                containerSize.width.toFloat()
            } else {
                with(density) { maxWidth.toPx() }
            }
            val heightPx = if (containerSize.height > 0) {
                containerSize.height.toFloat()
            } else {
                with(density) { maxHeight.toPx() }
            }
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
                widthPx > heightPx

            val activeOrientationLayout = if (isLandscape) {
                activeLayoutProfile.landscape
            } else {
                activeLayoutProfile.portrait
            }
            val defaultBaselineProfile = LayoutProfileHelper.builtInDefaultFor(activeLayoutProfile.id)
            val defaultOrientationLayout = if (isLandscape) {
                defaultBaselineProfile.landscape
            } else {
                defaultBaselineProfile.portrait
            }

            if (isEditingLayout) {
                ControlLayoutEditor(
                    initialConfig = activeOrientationLayout,
                    defaultConfig = defaultOrientationLayout,
                    isLandscape = isLandscape,
                    profiles = layoutProfiles,
                    activeProfileId = activeLayoutProfile.id,
                    onSelectProfile = onSelectLayoutProfile,
                    onCreateProfile = onCreateCustomLayoutProfile,
                    onDeleteProfile = onDeleteCustomLayoutProfile,
                    onSaveLayout = { updated -> onSaveOrientationLayout(isLandscape, updated) },
                    onCancel = onExitLayoutEditor,
                    modifier = Modifier.fillMaxSize(),
                    screenContent = { screenModifier ->
                        GbaScreenCanvas(
                            bitmap = currentFrame,
                            modifier = screenModifier
                        )
                    }
                )
            } else {
                // Posição e escala 1:1 com o ControlLayoutEditor via computeScreenBounds
                val screenBounds = ControlLayoutPresets.computeScreenBounds(
                    screenRect = activeOrientationLayout.screenRect,
                    containerWidth = widthPx,
                    containerHeight = heightPx
                )
                val screenWidthDp = with(density) { screenBounds.width.toDp() }
                val screenHeightDp = with(density) { screenBounds.height.toDp() }

                GbaScreenCanvas(
                    bitmap = currentFrame,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = screenBounds.left.roundToInt(),
                                y = screenBounds.top.roundToInt()
                            )
                        }
                        .size(width = screenWidthDp, height = screenHeightDp)
                )

                if (!hideControls) {
                    VirtualController(
                        modifier = Modifier.fillMaxSize(),
                        layoutConfig = activeOrientationLayout,
                        opacity = controllerOpacity,
                        hapticEnabled = hapticFeedbackEnabled,
                        onButtonsMaskChanged = onTouchButtonsMaskChanged,
                        onButtonStateChanged = null,
                        onMenuClick = { onShowMenuSheetChange(true) },
                        onFastForwardToggle = onToggleTurbo,
                        isFastForwardActive = isTurbo,
                        onQuickSave = onQuickSave,
                        onQuickLoad = onQuickLoad
                    )
                }

                // Barra compacta de telemetria e menu discreto
                EmulatorHudBar(
                    gameTitle = currentGame?.title ?: "RetroGBA Player",
                    isSimulationMode = isSimulationMode,
                    isHardcore = isHardcore,
                    hasGame = currentGame != null,
                    currentFps = currentFps,
                    isEcoMode = isEcoMode,
                    isTurbo = isTurbo,
                    fastForwardSpeed = fastForwardSpeed,
                    showMenuIcon = hideControls,
                    onOpenMenu = { onShowMenuSheetChange(true) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            AchievementOverlay(
                achievement = recentAchievement,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            if (showMenuSheet) {
                ModalBottomSheet(
                    onDismissRequest = { onShowMenuSheetChange(false) },
                    containerColor = Color(0xFF1E1A2E)
                ) {
                    PauseMenuSheetContent(
                        gameTitle = currentGame?.title ?: "Menu de Operações & Saves",
                        activeSlot = activeSlot,
                        saveSlots = saveSlots,
                        isHardcore = isHardcore,
                        controllerOpacity = controllerOpacity,
                        hapticFeedbackEnabled = hapticFeedbackEnabled,
                        fastForwardSpeed = fastForwardSpeed,
                        hideControls = hideControls,
                        isEcoMode = isEcoMode,
                        cheats = cheats,
                        layoutProfiles = layoutProfiles,
                        activeProfileId = activeLayoutProfile.id,
                        onSelectSlot = onSelectSlot,
                        onSaveState = { slot ->
                            onSaveState(slot)
                            onShowMenuSheetChange(false)
                        },
                        onLoadState = { slot ->
                            onLoadState(slot)
                            onShowMenuSheetChange(false)
                        },
                        onPersistBatterySave = {
                            onPersistBatterySave()
                            onShowMenuSheetChange(false)
                        },
                        onCustomizeControls = {
                            onShowMenuSheetChange(false)
                            onEnterLayoutEditor()
                        },
                        onSelectLayoutProfile = onSelectLayoutProfile,
                        onControllerOpacityChange = onControllerOpacityChange,
                        onHapticFeedbackChange = onHapticFeedbackChange,
                        onFastForwardSpeedChange = onFastForwardSpeedChange,
                        onHideControlsChange = onHideControlsChange,
                        onEcoModeChange = onEcoModeChange,
                        onOpenAddCheat = { onShowAddCheatDialogChange(true) },
                        onToggleCheat = onToggleCheat,
                        onRemoveCheat = onRemoveCheat,
                        onNavigateToAchievements = {
                            onShowMenuSheetChange(false)
                            onNavigateToAchievements()
                        },
                        onClose = { onShowMenuSheetChange(false) }
                    )
                }
            }

            if (showAddCheatDialog) {
                AddCheatDialog(
                    cheatDesc = cheatDesc,
                    onCheatDescChange = { cheatDesc = it },
                    cheatCodeText = cheatCodeText,
                    onCheatCodeChange = { cheatCodeText = it },
                    cheatTypeSelected = cheatTypeSelected,
                    onCheatTypeSelect = { cheatTypeSelected = it },
                    onDismiss = { onShowAddCheatDialogChange(false) },
                    onConfirm = {
                        if (cheatCodeText.isNotBlank()) {
                            onAddCheat(cheatDesc, cheatCodeText, cheatTypeSelected)
                            cheatDesc = ""
                            cheatCodeText = ""
                            onShowAddCheatDialogChange(false)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun EmulatorHudBar(
    gameTitle: String,
    isSimulationMode: Boolean,
    isHardcore: Boolean,
    hasGame: Boolean,
    currentFps: Int,
    isEcoMode: Boolean,
    isTurbo: Boolean,
    fastForwardSpeed: Float,
    showMenuIcon: Boolean,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = Color(0x99161424),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = gameTitle,
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            if (isSimulationMode) {
                Surface(
                    color = Color(0xCC441820),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "SIMULAÇÃO",
                        color = Color(0xFFFF8A80),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            if (hasGame) {
                Surface(
                    color = if (isHardcore) Color(0xCC442A12) else Color(0xCC1E2D3D),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isHardcore) "HC" else "SC",
                        color = if (isHardcore) TrophyGold else GbaSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = Color(0x991B192A),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "$currentFps FPS",
                    color = if (currentFps >= 50) Color(0xFF81C784) else Color(0xFFFFB74D),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            if (isEcoMode) {
                Surface(
                    color = Color(0xCC1B3B24),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "ECO",
                        color = Color(0xFFA5D6A7),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (isTurbo) {
                Surface(
                    color = Color(0xCC241C42),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${"%.1f".format(fastForwardSpeed)}x",
                        color = GbaSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (showMenuIcon) {
                Surface(
                    color = Color(0xCC27223B),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable(onClick = onOpenMenu)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PauseMenuSheetContent(
    gameTitle: String,
    activeSlot: Int,
    saveSlots: List<SaveStateInfo>,
    isHardcore: Boolean,
    controllerOpacity: Float,
    hapticFeedbackEnabled: Boolean,
    fastForwardSpeed: Float,
    hideControls: Boolean,
    isEcoMode: Boolean,
    cheats: List<CheatCode>,
    layoutProfiles: List<ControlLayoutProfile>,
    activeProfileId: String,
    onSelectSlot: (Int) -> Unit,
    onSaveState: (Int) -> Unit,
    onLoadState: (Int) -> Unit,
    onPersistBatterySave: () -> Unit,
    onCustomizeControls: () -> Unit,
    onSelectLayoutProfile: (String) -> Unit,
    onControllerOpacityChange: (Float) -> Unit,
    onHapticFeedbackChange: (Boolean) -> Unit,
    onFastForwardSpeedChange: (Float) -> Unit,
    onHideControlsChange: (Boolean) -> Unit,
    onEcoModeChange: (Boolean) -> Unit,
    onOpenAddCheat: () -> Unit,
    onToggleCheat: (String) -> Unit,
    onRemoveCheat: (String) -> Unit,
    onNavigateToAchievements: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = gameTitle,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        HorizontalDivider(color = Color.DarkGray)

        // Personalização My Boy! e seleção rápida de perfil
        Button(
            onClick = onCustomizeControls,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = GbaPrimary)
        ) {
            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Personalizar Controles (My Boy!)", fontWeight = FontWeight.Bold)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Perfil de Controle Ativo:",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                layoutProfiles.forEach { profile ->
                    FilterChip(
                        selected = profile.id == activeProfileId,
                        onClick = { onSelectLayoutProfile(profile.id) },
                        label = { Text(profile.name, fontSize = 12.sp) }
                    )
                }
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
                            .clickable { onSelectSlot(slotIdx) }
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

        // Ações de Save State (Salvar liberado em Hardcore; Carregar bloqueado em Hardcore)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onSaveState(activeSlot) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2449))
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Salvar ($activeSlot)")
            }

            Button(
                onClick = { onLoadState(activeSlot) },
                enabled = !isHardcore,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2C2449),
                    disabledContainerColor = Color(0xFF221E33)
                )
            ) {
                Icon(
                    imageVector = if (isHardcore) Icons.Default.Lock else Icons.Default.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Carregar ($activeSlot)")
            }
        }

        if (isHardcore) {
            Text(
                text = "Hardcore ativo: você pode salvar backup (.ss/$activeSlot), mas carregar Save State é bloqueado.",
                color = Color(0xFFFFE0B2),
                fontSize = 11.sp
            )
        }

        OutlinedButton(
            onClick = onPersistBatterySave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.BatteryChargingFull, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Gravar Bateria do Jogo (.SAV)")
        }

        HorizontalDivider(color = Color.DarkGray)

        Text(
            text = "Ajustes de Controles & Gameplay",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White
        )

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Opacidade dos Controles:", fontSize = 12.sp, color = Color.Gray)
                Text(
                    text = "${(controllerOpacity * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = GbaSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = controllerOpacity,
                onValueChange = onControllerOpacityChange,
                valueRange = 0.2f..1.0f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Vibração ao Tocar (Háptico)", fontSize = 13.sp, color = Color.White)
                Text(text = "Resposta tátil nos botões e conquistas", fontSize = 11.sp, color = Color.Gray)
            }
            Switch(
                checked = hapticFeedbackEnabled,
                onCheckedChange = onHapticFeedbackChange
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Ocultar Controles Virtuais", fontSize = 13.sp, color = Color.White)
                Text(text = "Usar apenas gamepad Bluetooth/USB ou teclado", fontSize = 11.sp, color = Color.Gray)
            }
            Switch(
                checked = hideControls,
                onCheckedChange = onHideControlsChange
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "Multiplicador de Velocidade (Turbo):", fontSize = 12.sp, color = Color.Gray)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(2.0f, 2.5f, 3.0f, 4.0f).forEach { speed ->
                    FilterChip(
                        selected = fastForwardSpeed == speed,
                        onClick = { onFastForwardSpeedChange(speed) },
                        label = { Text("${speed}x", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Modo Eco de Bateria (Frameskip)", fontSize = 13.sp, color = Color.White)
                Text(
                    text = "Reduz carga gráfica e aquecimento para partidas longas",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            Switch(
                checked = isEcoMode,
                onCheckedChange = onEcoModeChange
            )
        }

        HorizontalDivider(color = Color.DarkGray)

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
                TextButton(onClick = onOpenAddCheat) {
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
        } else if (cheats.isEmpty()) {
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
                                    onCheckedChange = { onToggleCheat(cheat.id) },
                                    modifier = Modifier.height(24.dp)
                                )
                                IconButton(
                                    onClick = { onRemoveCheat(cheat.id) },
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

        HorizontalDivider(color = Color.DarkGray)

        Button(
            onClick = onNavigateToAchievements,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = TrophyGold.copy(alpha = 0.25f))
        ) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = TrophyGold)
            Spacer(Modifier.width(8.dp))
            Text("Ver Conquistas do Jogo", color = TrophyGold)
        }

        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Fechar Menu")
        }
    }
}

@Composable
private fun AddCheatDialog(
    cheatDesc: String,
    onCheatDescChange: (String) -> Unit,
    cheatCodeText: String,
    onCheatCodeChange: (String) -> Unit,
    cheatTypeSelected: CheatType,
    onCheatTypeSelect: (CheatType) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Adicionar Código de Cheat",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
            ) {
                OutlinedTextField(
                    value = cheatDesc,
                    onValueChange = onCheatDescChange,
                    label = { Text("Descrição (ex: Dinheiro Máximo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = cheatCodeText,
                    onValueChange = onCheatCodeChange,
                    label = { Text("Código (ex: 82003884 0063)") },
                    placeholder = { Text("Insira os dígitos hexadecimais") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Text(
                    text = "Tipo de Código:",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        CheatType.AUTODETECT,
                        CheatType.GAMESHARK,
                        CheatType.PRO_ACTION_REPLAY
                    ).forEach { type ->
                        FilterChip(
                            selected = cheatTypeSelected == type,
                            onClick = { onCheatTypeSelect(type) },
                            label = { Text(type.displayName, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = GbaSecondary)
            ) {
                Text("Salvar Cheat", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.LightGray)
            }
        },
        containerColor = Color(0xFF1E1A2E)
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun EmulatorScreenPortraitPreview() {
    val profiles = ControlLayoutPresets.builtInProfiles()
    RetroGBATheme {
        EmulatorScreenContent(
            currentFrame = null,
            currentGame = GameInfo(id = 515L, title = "Pokémon FireRed Version"),
            recentAchievement = null,
            isTurbo = false,
            isSimulationMode = false,
            activeSlot = 0,
            saveSlots = listOf(SaveStateInfo(slot = 0, exists = true, sizeBytes = 4096)),
            controllerOpacity = 0.8f,
            hapticFeedbackEnabled = true,
            fastForwardSpeed = 2.0f,
            hideControls = false,
            isEcoMode = false,
            currentFps = 60,
            cheats = emptyList(),
            isHardcore = true,
            layoutProfiles = profiles,
            activeLayoutProfile = profiles.first(),
            isEditingLayout = false
        )
    }
}

@Preview(showBackground = true, widthDp = 844, heightDp = 390)
@Composable
fun EmulatorScreenLandscapePreview() {
    val profiles = ControlLayoutPresets.builtInProfiles()
    RetroGBATheme {
        EmulatorScreenContent(
            currentFrame = null,
            currentGame = GameInfo(id = 515L, title = "Metroid: Zero Mission"),
            recentAchievement = null,
            isTurbo = true,
            isSimulationMode = false,
            activeSlot = 1,
            saveSlots = emptyList(),
            controllerOpacity = 0.78f,
            hapticFeedbackEnabled = true,
            fastForwardSpeed = 3.0f,
            hideControls = false,
            isEcoMode = true,
            currentFps = 60,
            cheats = emptyList(),
            isHardcore = false,
            layoutProfiles = profiles,
            activeLayoutProfile = profiles.first(),
            isEditingLayout = false
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, backgroundColor = 0xFF1E1A2E)
@Composable
fun PauseSheetPreview() {
    val profiles = ControlLayoutPresets.builtInProfiles()
    RetroGBATheme {
        Surface(color = Color(0xFF1E1A2E)) {
            PauseMenuSheetContent(
                gameTitle = "Pokémon FireRed Version",
                activeSlot = 0,
                saveSlots = listOf(
                    SaveStateInfo(slot = 0, exists = true, sizeBytes = 8192),
                    SaveStateInfo(slot = 1, exists = false)
                ),
                isHardcore = true,
                controllerOpacity = 0.8f,
                hapticFeedbackEnabled = true,
                fastForwardSpeed = 2.0f,
                hideControls = false,
                isEcoMode = false,
                cheats = emptyList(),
                layoutProfiles = profiles,
                activeProfileId = profiles.first().id,
                onSelectSlot = {},
                onSaveState = {},
                onLoadState = {},
                onPersistBatterySave = {},
                onCustomizeControls = {},
                onSelectLayoutProfile = {},
                onControllerOpacityChange = {},
                onHapticFeedbackChange = {},
                onFastForwardSpeedChange = {},
                onHideControlsChange = {},
                onEcoModeChange = {},
                onOpenAddCheat = {},
                onToggleCheat = {},
                onRemoveCheat = {},
                onNavigateToAchievements = {},
                onClose = {}
            )
        }
    }
}
