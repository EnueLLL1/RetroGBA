package com.retro.gba.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.retro.gba.model.ControlLayoutPresets
import com.retro.gba.model.ControlLayoutProfile
import com.retro.gba.model.ControlPlacement
import com.retro.gba.model.OrientationLayoutConfig
import com.retro.gba.model.VirtualControlType
import com.retro.gba.ui.theme.GbaBackground
import com.retro.gba.ui.theme.GbaCard
import com.retro.gba.ui.theme.GbaPrimary
import com.retro.gba.ui.theme.GbaSecondary
import com.retro.gba.ui.theme.GbaSurface
import com.retro.gba.ui.theme.RetroGBATheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val MyBoyOrangeBorder = Color(0xFFFF8C00)
private val MyBoyNeonGreen = Color(0xFF00E676)

private sealed interface EditorSelection {
    data object Screen : EditorSelection
    data class Control(val id: String) : EditorSelection
}

@Composable
fun ControlLayoutEditor(
    initialConfig: OrientationLayoutConfig,
    defaultConfig: OrientationLayoutConfig,
    isLandscape: Boolean,
    profiles: List<ControlLayoutProfile>,
    activeProfileId: String,
    onSelectProfile: (String) -> Unit,
    onCreateProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSaveLayout: (OrientationLayoutConfig) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    screenContent: @Composable (Modifier) -> Unit = {}
) {
    val density = LocalDensity.current

    var workingConfig by remember(initialConfig, activeProfileId) {
        mutableStateOf(initialConfig.clamped())
    }
    var selected by remember { mutableStateOf<EditorSelection?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    var showEditorMenu by remember { mutableStateOf(false) }
    var showAddControlDialog by remember { mutableStateOf(false) }
    var showProfilesDialog by remember { mutableStateOf(false) }
    var showNewProfileDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    // Offset da barra flutuante reposicionável (em pixels relativos ao topo-centro)
    var toolbarOffsetX by remember(isLandscape) { mutableStateOf(0f) }
    var toolbarOffsetY by remember(isLandscape) { mutableStateOf(0f) }

    val latestConfig by rememberUpdatedState(workingConfig)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xCC0A0912))
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures { selected = null }
            }
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
        val minSidePx = min(widthPx, heightPx).coerceAtLeast(1f)
        val latestWidthPx by rememberUpdatedState(widthPx)
        val latestHeightPx by rememberUpdatedState(heightPx)
        val latestMinSidePx by rememberUpdatedState(minSidePx)

        // 1. Área editável da tela do jogo (proporção 3:2, borda laranja #FF8C00 estilo My Boy!)
        val screenBounds = ControlLayoutPresets.computeScreenBounds(
            screenRect = workingConfig.screenRect,
            containerWidth = widthPx,
            containerHeight = heightPx
        )
        val isScreenSelected = selected == EditorSelection.Screen
        val screenWidthDp = with(density) { screenBounds.width.toDp() }
        val screenHeightDp = with(density) { screenBounds.height.toDp() }
        val screenOuterPadDp = if (isScreenSelected) 14.dp else 0.dp
        val screenOuterPadPx = with(density) { screenOuterPadDp.toPx() }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (screenBounds.left - screenOuterPadPx).roundToInt(),
                        y = (screenBounds.top - screenOuterPadPx).roundToInt()
                    )
                }
                .size(
                    width = screenWidthDp + screenOuterPadDp * 2,
                    height = screenHeightDp + screenOuterPadDp * 2
                )
                .zIndex(if (isScreenSelected) 3f else 1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isScreenSelected) 10.dp else 0.dp)
                    .then(
                        if (isScreenSelected) {
                            Modifier
                                .border(2.dp, MyBoyNeonGreen, RoundedCornerShape(6.dp))
                                .padding(4.dp)
                        } else {
                            Modifier
                        }
                    )
                    .clip(RoundedCornerShape(4.dp))
                    .border(2.5.dp, MyBoyOrangeBorder, RoundedCornerShape(4.dp))
                    .pointerInput("screen_tap") {
                        detectTapGestures { selected = EditorSelection.Screen }
                    }
                    .pointerInput("screen_move") {
                        detectDragGestures(
                            onDragStart = { selected = EditorSelection.Screen }
                        ) { change, dragAmount ->
                            change.consume()
                            val current = latestConfig.screenRect
                            val newCx = current.centerX + dragAmount.x / latestWidthPx
                            val newCy = current.centerY + dragAmount.y / latestHeightPx
                            workingConfig = latestConfig.copy(
                                screenRect = current.copy(centerX = newCx, centerY = newCy).clamped()
                            )
                        }
                    }
            ) {
                screenContent(Modifier.fillMaxSize())
            }

            if (isScreenSelected) {
                ResizeCornerHandle(
                    modifier = Modifier.align(Alignment.TopEnd),
                    onScaleDrag = { dx, dy ->
                        val delta = (dx - dy) / (latestMinSidePx * 0.35f)
                        val current = latestConfig.screenRect
                        workingConfig = latestConfig.copy(
                            screenRect = current.copy(scale = current.scale + delta).clamped()
                        )
                    }
                )
            }
        }

        // 2. Controles virtuais editáveis (arrastáveis e escaláveis com borda verde-neon + handle ⤢)
        for (rawPlacement in workingConfig.controls) {
            val placement = rawPlacement.clamped()
            if (!placement.visible) continue

            key(placement.id) {
                val isSelected = selected == EditorSelection.Control(placement.id)
                val sizePx = ControlLayoutPresets.computeControlSize(
                    type = placement.type,
                    scale = placement.scale,
                    containerWidth = widthPx,
                    containerHeight = heightPx
                )
                val controlWidthDp = with(density) { sizePx.width.toDp() }
                val controlHeightDp = with(density) { sizePx.height.toDp() }
                val innerBoxW = if (isSelected) max(controlWidthDp.value + 12f, 44f).dp else controlWidthDp
                val innerBoxH = if (isSelected) max(controlHeightDp.value + 12f, 44f).dp else controlHeightDp
                val outerPadDp = if (isSelected) 12.dp else 0.dp
                val outerW = innerBoxW + outerPadDp * 2
                val outerH = innerBoxH + outerPadDp * 2
                val outerWPx = with(density) { outerW.toPx() }
                val outerHPx = with(density) { outerH.toPx() }

                val leftPx = (placement.centerX * widthPx - outerWPx * 0.5f).roundToInt()
                val topPx = (placement.centerY * heightPx - outerHPx * 0.5f).roundToInt()

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset { IntOffset(leftPx, topPx) }
                        .size(outerW, outerH)
                        .zIndex(if (isSelected) 5f else 2f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(innerBoxW, innerBoxH)
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .background(MyBoyNeonGreen.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                                        .border(2.dp, MyBoyNeonGreen, RoundedCornerShape(10.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .pointerInput(placement.id) {
                                detectTapGestures {
                                    selected = EditorSelection.Control(placement.id)
                                }
                            }
                            .pointerInput("drag_${placement.id}") {
                                detectDragGestures(
                                    onDragStart = {
                                        selected = EditorSelection.Control(placement.id)
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    val currentList = latestConfig.controls
                                    val updated = currentList.map { item ->
                                        if (item.id == placement.id) {
                                            item.copy(
                                                centerX = item.centerX + dragAmount.x / latestWidthPx,
                                                centerY = item.centerY + dragAmount.y / latestHeightPx
                                            ).clamped()
                                        } else {
                                            item
                                        }
                                    }
                                    workingConfig = latestConfig.copy(controls = updated)
                                }
                            }
                    ) {
                        VirtualControlVisual(
                            placement = placement,
                            widthDp = controlWidthDp,
                            heightDp = controlHeightDp,
                            isPressed = false,
                            activeGbaMask = 0,
                            isFastForwardActive = false
                        )
                    }

                    if (isSelected) {
                        ResizeCornerHandle(
                            modifier = Modifier.align(Alignment.TopEnd),
                            onScaleDrag = { dx, dy ->
                                val delta = (dx - dy) / (latestMinSidePx * 0.28f)
                                val currentList = latestConfig.controls
                                val updated = currentList.map { item ->
                                    if (item.id == placement.id) {
                                        item.copy(scale = item.scale + delta).clamped()
                                    } else {
                                        item
                                    }
                                }
                                workingConfig = latestConfig.copy(controls = updated)
                            }
                        )
                    }
                }
            }
        }

        // 3. Barra flutuante discreta e reposicionável do Editor
        val maxToolbarOffsetX = (widthPx * 0.36f)
        val maxToolbarOffsetY = (heightPx - with(density) { 80.dp.toPx() }).coerceAtLeast(0f)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .offset {
                    IntOffset(
                        x = toolbarOffsetX.coerceIn(-maxToolbarOffsetX, maxToolbarOffsetX).roundToInt(),
                        y = toolbarOffsetY.coerceIn(0f, maxToolbarOffsetY).roundToInt()
                    )
                }
                .zIndex(20f)
        ) {
            Surface(
                color = Color(0xEB181626),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
                    .pointerInput("toolbar_drag") {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            toolbarOffsetX = (toolbarOffsetX + dragAmount.x)
                                .coerceIn(-maxToolbarOffsetX, maxToolbarOffsetX)
                            toolbarOffsetY = (toolbarOffsetY + dragAmount.y)
                                .coerceIn(0f, maxToolbarOffsetY)
                        }
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = "Mover barra",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )

                    Surface(
                        color = GbaPrimary.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = if (isLandscape) "Landscape" else "Portrait",
                            color = GbaSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    IconButton(
                        onClick = { showAddControlDialog = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Adicionar controle",
                            tint = MyBoyNeonGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { showProfilesDialog = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Perfis",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showEditorMenu = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu do Editor",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        val selectedControl = (selected as? EditorSelection.Control)?.let { sel ->
                            workingConfig.controls.firstOrNull { it.id == sel.id && it.visible }
                        }
                        val canRemoveSelected = selectedControl != null && (
                            selectedControl.type != VirtualControlType.BUTTON_MENU ||
                                workingConfig.controls.count { it.visible && it.type == VirtualControlType.BUTTON_MENU } > 1
                            )

                        DropdownMenu(
                            expanded = showEditorMenu,
                            onDismissRequest = { showEditorMenu = false },
                            containerColor = GbaSurface
                        ) {
                            DropdownMenuItem(
                                text = { Text("Adicionar controle", color = Color.White) },
                                leadingIcon = {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = MyBoyNeonGreen)
                                },
                                onClick = {
                                    showEditorMenu = false
                                    showAddControlDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Perfis de layout", color = Color.White) },
                                leadingIcon = {
                                    Icon(Icons.Default.Layers, contentDescription = null, tint = GbaSecondary)
                                },
                                onClick = {
                                    showEditorMenu = false
                                    showProfilesDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (selectedControl != null) {
                                            "Remover (${selectedControl.type.label})"
                                        } else {
                                            "Remover selecionado"
                                        },
                                        color = if (canRemoveSelected) Color(0xFFFF8A80) else Color.Gray
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = if (canRemoveSelected) Color(0xFFFF8A80) else Color.Gray
                                    )
                                },
                                enabled = canRemoveSelected,
                                onClick = {
                                    showEditorMenu = false
                                    selectedControl?.let { target ->
                                        workingConfig = workingConfig.copy(
                                            controls = workingConfig.controls.map {
                                                if (it.id == target.id) it.copy(visible = false) else it
                                            }
                                        )
                                        selected = null
                                    }
                                }
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                            DropdownMenuItem(
                                text = { Text("Resetar padrão", color = Color.White) },
                                leadingIcon = {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = GbaSecondary)
                                },
                                onClick = {
                                    showEditorMenu = false
                                    workingConfig = defaultConfig.clamped()
                                    selected = null
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Remover todos (manter Menu)", color = Color(0xFFFFAB91)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFFAB91))
                                },
                                onClick = {
                                    showEditorMenu = false
                                    val hasMenu = workingConfig.controls.any { it.type == VirtualControlType.BUTTON_MENU }
                                    val updatedControls = if (hasMenu) {
                                        var keptMenu = false
                                        workingConfig.controls.map { item ->
                                            if (item.type == VirtualControlType.BUTTON_MENU && !keptMenu) {
                                                keptMenu = true
                                                item.copy(visible = true)
                                            } else {
                                                item.copy(visible = false)
                                            }
                                        }
                                    } else {
                                        workingConfig.controls.map { it.copy(visible = false) } +
                                            ControlPlacement("btn_menu", VirtualControlType.BUTTON_MENU, 0.5f, 0.14f, 1.0f, true)
                                    }
                                    workingConfig = workingConfig.copy(controls = updatedControls)
                                    selected = null
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancelar",
                            tint = Color(0xFFFF8A80),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Surface(
                        color = MyBoyNeonGreen,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSaveLayout(workingConfig.clamped()) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Salvar",
                                tint = Color(0xFF092214),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Salvar",
                                color = Color(0xFF092214),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            // Chip informativo do elemento selecionado
            val selectionLabel = when (val sel = selected) {
                is EditorSelection.Screen -> {
                    "Tela GBA · ${"%.2f".format(workingConfig.screenRect.scale)}x"
                }
                is EditorSelection.Control -> {
                    workingConfig.controls.firstOrNull { it.id == sel.id }?.let {
                        "${it.type.label} · ${"%.2f".format(it.scale)}x"
                    }
                }
                null -> null
            }
            if (selectionLabel != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = Color(0xCC12111C),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.border(1.dp, MyBoyNeonGreen.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = "$selectionLabel (arraste ⤢ para escalar)",
                        color = MyBoyNeonGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // 4. Modal "Adicionar controle" (My Boy! style: básicos, combos A+B/L+R/... e atalhos QS/QL)
        if (showAddControlDialog) {
            val missingTypes = VirtualControlType.entries.filter { type ->
                workingConfig.controls.none { it.type == type && it.visible }
            }
            AlertDialog(
                onDismissRequest = { showAddControlDialog = false },
                containerColor = GbaSurface,
                titleContentColor = Color.White,
                title = {
                    Text(
                        text = "Adicionar controle",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    if (missingTypes.isEmpty()) {
                        Text(
                            text = "Todos os controles, combos e atalhos já estão visíveis neste layout.",
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                    } else {
                        val maxDialogListHeight = if (isLandscape) 170.dp else 320.dp
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxDialogListHeight)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (type in missingTypes) {
                                val category = when {
                                    type.name.startsWith("COMBO_") -> "Combo de botões"
                                    type.isSpecialAction -> "Atalho de sistema"
                                    else -> "Controle GBA"
                                }
                                Surface(
                                    color = GbaCard,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            val existingHidden = workingConfig.controls.firstOrNull {
                                                it.type == type && !it.visible
                                            }
                                            val updatedControls = if (existingHidden != null) {
                                                workingConfig.controls.map {
                                                    if (it.id == existingHidden.id) {
                                                        it.copy(visible = true).clamped()
                                                    } else {
                                                        it
                                                    }
                                                }
                                            } else {
                                                val newId = "${type.name.lowercase()}_${workingConfig.controls.size + 1}"
                                                workingConfig.controls + ControlPlacement(
                                                    id = newId,
                                                    type = type,
                                                    centerX = 0.50f,
                                                    centerY = 0.56f,
                                                    scale = 1.0f,
                                                    visible = true
                                                )
                                            }
                                            val selectedId = existingHidden?.id
                                                ?: updatedControls.last().id
                                            workingConfig = workingConfig.copy(controls = updatedControls)
                                            selected = EditorSelection.Control(selectedId)
                                            showAddControlDialog = false
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .widthIn(min = 42.dp)
                                                .height(30.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    when {
                                                        type.name.startsWith("COMBO_") -> Color(0xFF453685)
                                                        type.isSpecialAction -> Color(0xFF273847)
                                                        else -> GbaPrimary
                                                    }
                                                )
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            Text(
                                                text = type.shortBadgeText(),
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = type.label,
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = category,
                                                color = Color.Gray,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAddControlDialog = false }) {
                        Text("Fechar", color = GbaSecondary)
                    }
                }
            )
        }

        // 5. Modal "Perfis de Layout" (estilo My Boy!)
        if (showProfilesDialog) {
            AlertDialog(
                onDismissRequest = { showProfilesDialog = false },
                containerColor = GbaSurface,
                titleContentColor = Color.White,
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Perfis de Layout",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        IconButton(onClick = {
                            newProfileName = ""
                            showNewProfileDialog = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Criar perfil",
                                tint = MyBoyNeonGreen
                            )
                        }
                    }
                },
                text = {
                    val maxProfilesListHeight = if (isLandscape) 160.dp else 300.dp
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxProfilesListHeight)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (profile in profiles) {
                            val isCurrent = profile.id == activeProfileId
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) GbaCard else Color.Transparent)
                                    .clickable {
                                        onSelectProfile(profile.id)
                                        workingConfig = (if (isLandscape) profile.landscape else profile.portrait).clamped()
                                        selected = null
                                        showProfilesDialog = false
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = isCurrent,
                                    onClick = {
                                        onSelectProfile(profile.id)
                                        workingConfig = (if (isLandscape) profile.landscape else profile.portrait).clamped()
                                        selected = null
                                        showProfilesDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = GbaSecondary,
                                        unselectedColor = Color.Gray
                                    )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.name,
                                        color = Color.White,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = if (profile.isBuiltIn) "Integrado" else "Personalizado",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                                if (!profile.isBuiltIn) {
                                    IconButton(onClick = { onDeleteProfile(profile.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Excluir perfil",
                                            tint = Color(0xFFFF8A80)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showProfilesDialog = false }) {
                        Text("Fechar", color = GbaSecondary)
                    }
                }
            )
        }

        // 6. Diálogo de criação de novo perfil customizado
        if (showNewProfileDialog) {
            AlertDialog(
                onDismissRequest = { showNewProfileDialog = false },
                containerColor = GbaSurface,
                title = { Text("Novo Perfil de Controle", color = Color.White) },
                text = {
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Nome do perfil") },
                        placeholder = { Text("Ex.: Pokémon 1 Mão") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        enabled = newProfileName.trim().isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = GbaPrimary),
                        onClick = {
                            val trimmed = newProfileName.trim()
                            if (trimmed.isNotEmpty()) {
                                onCreateProfile(trimmed)
                            }
                            showNewProfileDialog = false
                            showProfilesDialog = false
                        }
                    ) {
                        Text("Criar", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewProfileDialog = false }) {
                        Text("Cancelar", color = Color.Gray)
                    }
                }
            )
        }
    }
}

@Composable
private fun ResizeCornerHandle(
    modifier: Modifier = Modifier,
    onScaleDrag: (dx: Float, dy: Float) -> Unit
) {
    val latestOnScaleDrag by rememberUpdatedState(onScaleDrag)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(34.dp)
            .zIndex(12f)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    latestOnScaleDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFF0D2618))
                .border(2.dp, MyBoyNeonGreen, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.OpenInFull,
                contentDescription = "Redimensionar",
                tint = MyBoyNeonGreen,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun ControlLayoutEditorPreview() {
    RetroGBATheme {
        val profiles = ControlLayoutPresets.builtInProfiles()
        ControlLayoutEditor(
            initialConfig = profiles.first().portrait,
            defaultConfig = profiles.first().portrait,
            isLandscape = false,
            profiles = profiles,
            activeProfileId = profiles.first().id,
            onSelectProfile = {},
            onCreateProfile = {},
            onDeleteProfile = {},
            onSaveLayout = {},
            onCancel = {},
            modifier = Modifier
                .fillMaxSize()
                .background(GbaBackground),
            screenContent = { mod ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = mod.background(Color(0xFF15192B))
                ) {
                    Text(
                        text = "GAME BOY ADVANCE (3:2)",
                        color = GbaSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }
}

@Preview(showBackground = true, widthDp = 844, heightDp = 390)
@Composable
fun ControlLayoutEditorLandscapePreview() {
    RetroGBATheme {
        val profiles = ControlLayoutPresets.builtInProfiles()
        ControlLayoutEditor(
            initialConfig = profiles.first().landscape,
            defaultConfig = profiles.first().landscape,
            isLandscape = true,
            profiles = profiles,
            activeProfileId = profiles.first().id,
            onSelectProfile = {},
            onCreateProfile = {},
            onDeleteProfile = {},
            onSaveLayout = {},
            onCancel = {},
            modifier = Modifier
                .fillMaxSize()
                .background(GbaBackground),
            screenContent = { mod ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = mod.background(Color(0xFF15192B))
                ) {
                    Text(
                        text = "GAME BOY ADVANCE (3:2)",
                        color = GbaSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }
}
