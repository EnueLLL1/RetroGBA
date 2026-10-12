package com.retro.gba.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.retro.gba.ui.theme.GbaBackground
import com.retro.gba.ui.theme.GbaCard
import com.retro.gba.ui.theme.GbaSecondary
import com.retro.gba.ui.theme.TrophyGold
import com.retro.gba.ui.viewmodel.ProfileViewModel

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
                    text = errorMessage ?: "",
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
                                model = userProfile!!.avatarUrl,
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
                            onCheckedChange = { viewModel.setHardcore(it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = { viewModel.logout() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
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
                        onClick = { viewModel.login(inputUser, inputPass) },
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

