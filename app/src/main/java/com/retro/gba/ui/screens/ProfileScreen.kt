package com.retro.gba.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.retro.gba.data.model.UserProfile
import com.retro.gba.ui.theme.GbaBackground
import com.retro.gba.ui.theme.GbaCard
import com.retro.gba.ui.theme.GbaSecondary
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
        onHardcoreChange = { viewModel.setHardcore(it) },
        onControllerOpacityChange = { viewModel.setControllerOpacity(it) },
        onHapticFeedbackChange = { viewModel.setHapticFeedback(it) },
        onFastForwardSpeedChange = { viewModel.setFastForwardSpeed(it) },
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputUser by remember { mutableStateOf("") }
    var inputPass by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GbaBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
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
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
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
            // Card de Perfil do Jogador
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
                        if (!userProfile?.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = userProfile.avatarUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
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
                            value = "${userProfile?.totalPoints ?: 0}"
                        )
                        ProfileStat(
                            title = "Ranking Global",
                            value = if ((userProfile?.rank ?: 0) > 0) "#${userProfile?.rank}" else "-"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Configuração do Modo Hardcore
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
                                text = "Desativa save states e cheats para validar suas conquistas nos rankings globais oficiais do RetroAchievements.",
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

                    // Opacidade
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

                    // Haptic Feedback
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Feedback Háptico (Vibração)", fontSize = 13.sp, color = Color.LightGray)
                            Text(text = "Vibrar ao pressionar botões virtuais e destravar conquistas", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = hapticFeedback,
                            onCheckedChange = onHapticFeedbackChange
                        )
                    }

                    // Velocidade Fast Forward
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
                        text = "🐛 Diagnóstico & Envio de Erros",
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
        } else {
            // Formulário de Login
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
    }
}

@Composable
private fun ProfileStat(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TrophyGold)
        Text(text = title, fontSize = 11.sp, color = Color.Gray)
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenLoggedInPreview() {
    RetroGBATheme {
        ProfileScreenContent(
            isLoggedIn = true,
            username = "RetroGamer",
            userProfile = UserProfile(
                username = "RetroGamer",
                totalPoints = 1250,
                hardcorePoints = 850,
                rank = 42,
                avatarUrl = ""
            ),
            isHardcore = true,
            isLoading = false,
            errorMessage = null,
            hapticFeedback = true,
            controllerOpacity = 0.8f,
            fastForwardSpeed = 2.0f,
            onHardcoreChange = {},
            onControllerOpacityChange = {},
            onHapticFeedbackChange = {},
            onFastForwardSpeedChange = {},
            onLogout = {},
            onLogin = { _, _ -> }
        )
    }
}

@Preview(showBackground = true)
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
            controllerOpacity = 0.8f,
            fastForwardSpeed = 2.0f,
            onHardcoreChange = {},
            onControllerOpacityChange = {},
            onHapticFeedbackChange = {},
            onFastForwardSpeedChange = {},
            onLogout = {},
            onLogin = { _, _ -> }
        )
    }
}
