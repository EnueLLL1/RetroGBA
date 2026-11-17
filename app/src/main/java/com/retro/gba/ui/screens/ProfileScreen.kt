package com.retro.gba.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.retro.gba.data.model.UserProfile
import com.retro.gba.model.ControlLayoutPresets
import com.retro.gba.model.ControlLayoutProfile
import com.retro.gba.ui.theme.GbaBackground
import com.retro.gba.ui.theme.GbaCard
import com.retro.gba.ui.theme.GbaPrimary
import com.retro.gba.ui.theme.GbaSecondary
import com.retro.gba.ui.theme.GbaSurface
import com.retro.gba.ui.theme.RetroGBATheme
import com.retro.gba.ui.theme.TrophyGold
import com.retro.gba.ui.viewmodel.ProfileViewModel
import com.retro.gba.util.AppLogger

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    modifier: Modifier = Modifier
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val username by viewModel.username.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val isHardcore by viewModel.isHardcore.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val hapticFeedback by viewModel.hapticFeedback.collectAsState()
    val controllerOpacity by viewModel.controllerOpacity.collectAsState()
    val fastForwardSpeed by viewModel.fastForwardSpeed.collectAsState()
    val hideControls by viewModel.hideControls.collectAsState()
    val layoutProfiles by viewModel.layoutProfiles.collectAsState()
    val activeLayoutProfile by viewModel.activeLayoutProfile.collectAsState()

    ProfileScreenContent(
        isLoggedIn = isLoggedIn,
        username = username,
        userProfile = userProfile,
        isHardcore = isHardcore,
        isLoading = isLoading,
        errorMessage = errorMessage,
        hapticFeedback = hapticFeedback,
        controllerOpacity = controllerOpacity,
        fastForwardSpeed = fastForwardSpeed,
        hideControls = hideControls,
        layoutProfiles = layoutProfiles,
        activeProfileId = activeLayoutProfile.id,
        onHardcoreChange = { viewModel.setHardcore(it) },
        onControllerOpacityChange = { viewModel.setControllerOpacity(it) },
        onHapticFeedbackChange = { viewModel.setHapticFeedback(it) },
        onFastForwardSpeedChange = { viewModel.setFastForwardSpeed(it) },
        onHideControlsChange = { viewModel.setHideControls(it) },
        onSelectLayoutProfile = { viewModel.selectLayoutProfile(it) },
        onCreateLayoutProfile = { viewModel.createCustomLayoutProfile(it) },
        onDeleteLayoutProfile = { viewModel.deleteCustomLayoutProfile(it) },
        onLogout = { viewModel.logout() },
        onLogin = { user, pass -> viewModel.login(user, pass) },
        modifier = modifier
    )
}

@Composable
fun ProfileScreenContent(
    isLoggedIn: Boolean,
    username: String,
    userProfile: UserProfile?,
    isHardcore: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    hapticFeedback: Boolean,
    controllerOpacity: Float,
    fastForwardSpeed: Float,
    onHardcoreChange: (Boolean) -> Unit,
    onControllerOpacityChange: (Float) -> Unit,
    onHapticFeedbackChange: (Boolean) -> Unit,
    onFastForwardSpeedChange: (Float) -> Unit,
    onLogout: () -> Unit,
    onLogin: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    hideControls: Boolean = false,
    layoutProfiles: List<ControlLayoutProfile> = ControlLayoutPresets.builtInProfiles(),
    activeProfileId: String = "default",
    onHideControlsChange: (Boolean) -> Unit = {},
    onSelectLayoutProfile: (String) -> Unit = {},
    onCreateLayoutProfile: (String) -> Unit = {},
    onDeleteLayoutProfile: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current
    var inputUser by remember { mutableStateOf("") }
    var inputPass by remember { mutableStateOf("") }
    var showCreateProfileDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GbaBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "RetroAchievements",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TrophyGold
        )
        Text(
            text = "Conquistas retrô oficiais nos seus jogos de GBA",
            color = Color.Gray,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF441820)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFFF8A80),
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp
                )
            }
        }

        if (isLoggedIn) {
            var avatarFailed by remember(userProfile?.avatarUrl) { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = GbaCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .border(2.dp, TrophyGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val avatarUrl = userProfile?.avatarUrl
                        if (!avatarUrl.isNullOrBlank() && !avatarFailed && !isInspection) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                onError = { avatarFailed = true },
                                onSuccess = { avatarFailed = false }
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = TrophyGold,
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = username,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProfileStat(
                            title = "Pontos Hardcore",
                            value = "${userProfile?.hardcorePoints ?: 0}"
                        )
                        ProfileStat(
                            title = "Pontos Softcore",
                            value = "${userProfile?.softcorePoints ?: 0}"
                        )
                        ProfileStat(
                            title = "Ranking Global",
                            value = if ((userProfile?.rank ?: 0) > 0) "#${userProfile?.rank}" else "-"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = GbaCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Modo Hardcore",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Bloqueia Load State e cheats para validar conquistas nos rankings oficiais (Save State continua liberado).",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = isHardcore,
                            onCheckedChange = onHardcoreChange
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = GbaCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Entrar com sua conta",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )

                    OutlinedTextField(
                        value = inputUser,
                        onValueChange = { inputUser = it },
                        label = { Text("Nome de Usuário") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputPass,
                        onValueChange = { inputPass = it },
                        label = { Text("Senha ou Web API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )

                    Button(
                        onClick = { onLogin(inputUser, inputPass) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && inputUser.isNotBlank() && inputPass.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = GbaSecondary)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
                        } else {
                            Text("Entrar", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = "Não tem uma conta? Crie gratuitamente em retroachievements.org para começar a colecionar troféus retrô!",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Seção Completa: Perfis de Controle (Estilo My Boy!)
        ControlProfilesSectionCard(
            profiles = layoutProfiles,
            activeProfileId = activeProfileId,
            onSelectProfile = onSelectLayoutProfile,
            onOpenCreateDialog = {
                newProfileName = ""
                showCreateProfileDialog = true
            },
            onDeleteProfile = onDeleteLayoutProfile
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Configurações Globais de Controles & Gameplay
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GbaCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Preferências de Controles & Jogabilidade",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Opacidade Padrão dos Controles", fontSize = 13.sp, color = Color.LightGray)
                        Text(
                            text = "${(controllerOpacity * 100).toInt()}%",
                            fontSize = 13.sp,
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
                        Text(text = "Feedback Háptico (Vibração)", fontSize = 13.sp, color = Color.LightGray)
                        Text(
                            text = "Vibrar ao pressionar botões virtuais e destravar conquistas",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = hapticFeedback,
                        onCheckedChange = onHapticFeedbackChange
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Ocultar Controles Virtuais", fontSize = 13.sp, color = Color.LightGray)
                        Text(
                            text = "Ideal ao jogar com gamepad Bluetooth/USB ou teclado físico",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = hideControls,
                        onCheckedChange = onHideControlsChange
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "Velocidade Padrão do Turbo", fontSize = 13.sp, color = Color.LightGray)
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
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Diagnóstico & Relatório de Erros
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GbaCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Diagnóstico & Envio de Erros",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )

                Text(
                    text = "Encontrou um bug ou problema no jogo? Copie ou envie o relatório de diagnósticos para ajudar a corrigir.",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { AppLogger.copyReportToClipboard(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copiar", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { AppLogger.shareReport(context) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = GbaSecondary, contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enviar", fontSize = 12.sp)
                    }
                }
            }
        }

        if (isLoggedIn) {
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onLogout,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Desconectar do RetroAchievements")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showCreateProfileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateProfileDialog = false },
            containerColor = GbaSurface,
            title = {
                Text(
                    text = "Novo Perfil de Controle",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "O novo perfil será criado clonando o layout Portrait e Landscape atual para personalização livre.",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text("Nome do perfil") },
                        placeholder = { Text("Ex.: Pokémon 1 Mão") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = newProfileName.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = GbaPrimary),
                    onClick = {
                        val trimmed = newProfileName.trim()
                        if (trimmed.isNotEmpty()) {
                            onCreateLayoutProfile(trimmed)
                        }
                        showCreateProfileDialog = false
                    }
                ) {
                    Text("Criar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateProfileDialog = false }) {
                    Text("Cancelar", color = Color.LightGray)
                }
            }
        )
    }
}

@Composable
private fun ControlProfilesSectionCard(
    profiles: List<ControlLayoutProfile>,
    activeProfileId: String,
    onSelectProfile: (String) -> Unit,
    onOpenCreateDialog: () -> Unit,
    onDeleteProfile: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GbaCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Perfis de Controle (Estilo My Boy!)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Gerencie perfis para Portrait e Landscape; ajuste posições no editor do emulador.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onOpenCreateDialog,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Novo", fontSize = 12.sp)
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                profiles.forEach { profile ->
                    val isSelected = profile.id == activeProfileId
                    val portraitCount = profile.portrait.controls.count { it.visible }
                    val landscapeCount = profile.landscape.controls.count { it.visible }

                    Surface(
                        color = if (isSelected) Color(0xFF332A52) else Color(0xFF1E1A2E),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) GbaSecondary else Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectProfile(profile.id) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectProfile(profile.id) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = GbaSecondary,
                                    unselectedColor = Color.Gray
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.name,
                                    color = Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${if (profile.isBuiltIn) "Integrado" else "Personalizado"} • $portraitCount Portrait · $landscapeCount Landscape",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                            if (!profile.isBuiltIn) {
                                IconButton(onClick = { onDeleteProfile(profile.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Excluir perfil ${profile.name}",
                                        tint = Color(0xFFFF8A80)
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

@Composable
private fun ProfileStat(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TrophyGold)
        Text(text = title, fontSize = 11.sp, color = Color.Gray)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 980)
@Composable
fun ProfileScreenLoggedInPreview() {
    val builtIns = ControlLayoutPresets.builtInProfiles()
    val sampleCustom = builtIns.first().copy(
        id = "custom_onehand",
        name = "Pokémon 1 Mão",
        isBuiltIn = false
    )
    RetroGBATheme {
        ProfileScreenContent(
            isLoggedIn = true,
            username = "RetroGamer",
            userProfile = UserProfile(
                username = "RetroGamer",
                totalPoints = 1250,
                hardcorePoints = 850,
                rank = 42,
                avatarUrl = "",
                softcorePoints = 400
            ),
            isHardcore = true,
            isLoading = false,
            errorMessage = null,
            hapticFeedback = true,
            controllerOpacity = 0.8f,
            fastForwardSpeed = 2.0f,
            hideControls = false,
            layoutProfiles = builtIns + sampleCustom,
            activeProfileId = sampleCustom.id,
            onHardcoreChange = {},
            onControllerOpacityChange = {},
            onHapticFeedbackChange = {},
            onFastForwardSpeedChange = {},
            onHideControlsChange = {},
            onSelectLayoutProfile = {},
            onCreateLayoutProfile = {},
            onDeleteLayoutProfile = {},
            onLogout = {},
            onLogin = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
fun ProfileScreenLoggedOutPreview() {
    RetroGBATheme {
        ProfileScreenContent(
            isLoggedIn = false,
            username = "",
            userProfile = null,
            isHardcore = false,
            isLoading = false,
            errorMessage = null,
            hapticFeedback = true,
            controllerOpacity = 0.75f,
            fastForwardSpeed = 2.0f,
            hideControls = false,
            layoutProfiles = ControlLayoutPresets.builtInProfiles(),
            activeProfileId = "default",
            onHardcoreChange = {},
            onControllerOpacityChange = {},
            onHapticFeedbackChange = {},
            onFastForwardSpeedChange = {},
            onHideControlsChange = {},
            onSelectLayoutProfile = {},
            onCreateLayoutProfile = {},
            onDeleteLayoutProfile = {},
            onLogout = {},
            onLogin = { _, _ -> }
        )
    }
}
