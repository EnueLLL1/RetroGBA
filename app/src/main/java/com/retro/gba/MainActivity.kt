package com.retro.gba

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.retro.gba.ui.screens.AchievementsScreen
import com.retro.gba.ui.screens.EmulatorScreen
import com.retro.gba.ui.screens.LibraryScreen
import com.retro.gba.ui.screens.ProfileScreen
import com.retro.gba.ui.theme.GbaBackground
import com.retro.gba.ui.theme.GbaSecondary
import com.retro.gba.ui.theme.RetroGBATheme
import com.retro.gba.ui.viewmodel.EmulatorViewModel
import com.retro.gba.ui.viewmodel.ProfileViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Library : Screen("library", "Biblioteca", Icons.Default.Folder)
    object Emulator : Screen("emulator", "Emulador", Icons.Default.SportsEsports)
    object Achievements : Screen("achievements", "Conquistas", Icons.Default.EmojiEvents)
    object Profile : Screen("profile", "Perfil RA", Icons.Default.Person)
}

class MainActivity : ComponentActivity() {

    private val emulatorViewModel: EmulatorViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RetroGBATheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val screens = listOf(
                    Screen.Library,
                    Screen.Emulator,
                    Screen.Achievements,
                    Screen.Profile
                )

                Scaffold(
                    containerColor = GbaBackground,
                    bottomBar = {
                        NavigationBar(
                            containerColor = Color(0xFF161424)
                        ) {
                            screens.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                                    label = { Text(screen.title) },
                                    selected = currentRoute == screen.route,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.Black,
                                        selectedTextColor = GbaSecondary,
                                        indicatorColor = GbaSecondary,
                                        unselectedIconColor = Color.Gray,
                                        unselectedTextColor = Color.Gray
                                    ),
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Library.route,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        composable(Screen.Library.route) {
                            LibraryScreen(
                                emulatorViewModel = emulatorViewModel,
                                onGameSelected = {
                                    navController.navigate(Screen.Emulator.route) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable(Screen.Emulator.route) {
                            EmulatorScreen(
                                viewModel = emulatorViewModel,
                                onNavigateToAchievements = {
                                    navController.navigate(Screen.Achievements.route) {
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable(Screen.Achievements.route) {
                            AchievementsScreen(
                                viewModel = emulatorViewModel
                            )
                        }
                        composable(Screen.Profile.route) {
                            ProfileScreen(
                                viewModel = profileViewModel
                            )
                        }
                    }
                }
            }
        }
    }
}

