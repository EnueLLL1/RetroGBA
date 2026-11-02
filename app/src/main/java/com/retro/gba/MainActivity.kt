package com.retro.gba

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
    object Profile : Screen("profile", "Ajustes", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val emulatorViewModel: EmulatorViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val currentAppTheme by profileViewModel.appTheme.collectAsState()
            RetroGBATheme(appTheme = currentAppTheme) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val isEmulatorRoute = currentRoute == Screen.Emulator.route

                val view = LocalView.current
                LaunchedEffect(isEmulatorRoute) {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (isEmulatorRoute) {
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }

                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600

                val screens = listOf(
                    Screen.Library,
                    Screen.Emulator,
                    Screen.Achievements,
                    Screen.Profile
                )

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isEmulatorRoute) {
                        // Modo Tela Cheia Imersiva na Emulação (sem NavigationBar/NavigationRail)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.displayCutout)
                        ) {
                            MainNavHost(
                                navController = navController,
                                emulatorViewModel = emulatorViewModel,
                                profileViewModel = profileViewModel
                            )
                        }
                    } else if (isLandscape) {
                        // Layout Adaptativo em Modo Paisagem / Telas Grandes (NavigationRail na Lateral)
                        Row(modifier = Modifier.fillMaxSize()) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                Spacer(Modifier.weight(1f))
                                screens.forEach { screen ->
                                    NavigationRailItem(
                                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                                        label = { Text(screen.title) },
                                        selected = currentRoute == screen.route,
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
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
                                Spacer(Modifier.weight(1f))
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .windowInsetsPadding(WindowInsets.safeDrawing)
                            ) {
                                MainNavHost(
                                    navController = navController,
                                    emulatorViewModel = emulatorViewModel,
                                    profileViewModel = profileViewModel
                                )
                            }
                        }
                    } else {
                        // Layout Retrato Padrão (NavigationBar na Parte Inferior)
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.background,
                            bottomBar = {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    screens.forEach { screen ->
                                        NavigationBarItem(
                                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                                            label = { Text(screen.title) },
                                            selected = currentRoute == screen.route,
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
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
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                MainNavHost(
                                    navController = navController,
                                    emulatorViewModel = emulatorViewModel,
                                    profileViewModel = profileViewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainNavHost(
    navController: androidx.navigation.NavHostController,
    emulatorViewModel: EmulatorViewModel,
    profileViewModel: ProfileViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Library.route,
        modifier = Modifier.fillMaxSize()
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
                },
                onNavigateToLibrary = {
                    navController.navigate(Screen.Library.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
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
