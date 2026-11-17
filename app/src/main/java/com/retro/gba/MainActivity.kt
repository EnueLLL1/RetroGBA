package com.retro.gba

import android.content.res.Configuration
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.retro.gba.emulator.GbaInput
import com.retro.gba.ui.screens.AchievementsScreen
import com.retro.gba.ui.screens.EmulatorScreen
import com.retro.gba.ui.screens.LibraryScreen
import com.retro.gba.ui.screens.ProfileScreen
import com.retro.gba.ui.theme.GbaBackground
import com.retro.gba.ui.theme.GbaSecondary
import com.retro.gba.ui.theme.RetroGBATheme
import com.retro.gba.ui.viewmodel.EmulatorViewModel
import com.retro.gba.ui.viewmodel.ProfileViewModel
import kotlin.math.abs

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Library : Screen("library", "Biblioteca", Icons.Default.Folder)
    object Emulator : Screen("emulator", "Emulador", Icons.Default.SportsEsports)
    object Achievements : Screen("achievements", "Conquistas", Icons.Default.EmojiEvents)
    object Profile : Screen("profile", "Perfil RA", Icons.Default.Person)
}

class MainActivity : ComponentActivity() {

    private val emulatorViewModel: EmulatorViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()

    @Volatile
    private var isEmulatorRouteActive: Boolean = false

    companion object {
        private const val ANALOG_DEADZONE = 0.35f
        private const val HAT_DEADZONE = 0.5f

        internal fun mapKeyCodeToGbaButton(keyCode: Int): Int? = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_X -> GbaInput.BUTTON_A
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_Z -> GbaInput.BUTTON_B
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_Q -> GbaInput.BUTTON_L
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_W -> GbaInput.BUTTON_R
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> GbaInput.BUTTON_START
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_SHIFT_RIGHT -> GbaInput.BUTTON_SELECT
            KeyEvent.KEYCODE_DPAD_UP -> GbaInput.BUTTON_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> GbaInput.BUTTON_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> GbaInput.BUTTON_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> GbaInput.BUTTON_RIGHT
            else -> null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RetroGBATheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                LaunchedEffect(currentRoute) {
                    val onEmulator = currentRoute == Screen.Emulator.route
                    isEmulatorRouteActive = onEmulator
                    if (!onEmulator) {
                        emulatorViewModel.clearPhysicalInputs()
                    }
                }

                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
                    configuration.screenWidthDp >= 600
                val showNavigationRail = isLandscape && currentRoute != Screen.Emulator.route

                val screens = listOf(
                    Screen.Library,
                    Screen.Emulator,
                    Screen.Achievements,
                    Screen.Profile
                )

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = GbaBackground
                ) {
                    if (isLandscape) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (showNavigationRail) {
                                NavigationRail(
                                    containerColor = Color(0xFF161424),
                                    contentColor = Color.White
                                ) {
                                    Spacer(Modifier.weight(1f))
                                    screens.forEach { screen ->
                                        NavigationRailItem(
                                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                                            label = { Text(screen.title) },
                                            selected = currentRoute == screen.route,
                                            colors = NavigationRailItemDefaults.colors(
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
                                    Spacer(Modifier.weight(1f))
                                }
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

    override fun onPause() {
        super.onPause()
        emulatorViewModel.clearPhysicalInputs()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            return super.dispatchKeyEvent(event)
        }
        if (!shouldHandlePhysicalGameInput()) {
            return super.dispatchKeyEvent(event)
        }

        val buttonMask = mapKeyCodeToGbaButton(event.keyCode)
            ?: return super.dispatchKeyEvent(event)

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    emulatorViewModel.onPhysicalButtonEvent(buttonMask, true)
                }
                true
            }
            KeyEvent.ACTION_UP -> {
                emulatorViewModel.onPhysicalButtonEvent(buttonMask, false)
                true
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!shouldHandlePhysicalGameInput()) {
            return super.onGenericMotionEvent(event)
        }
        val isGamepadOrJoystick =
            (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
                (event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        if (!isGamepadOrJoystick || event.action != MotionEvent.ACTION_MOVE) {
            return super.onGenericMotionEvent(event)
        }

        val axisMask = extractAxisButtonsMask(event)
        emulatorViewModel.onPhysicalAxisEvent(axisMask)
        return true
    }

    private fun shouldHandlePhysicalGameInput(): Boolean {
        return isEmulatorRouteActive &&
            !emulatorViewModel.isEditingLayout.value &&
            !emulatorViewModel.isOverlayModalOpen.value &&
            currentFocus !is android.widget.EditText
    }

    private fun extractAxisButtonsMask(event: MotionEvent): Int {
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val axisX = event.getAxisValue(MotionEvent.AXIS_X)
        val axisY = event.getAxisValue(MotionEvent.AXIS_Y)

        val effectiveX = when {
            abs(hatX) > HAT_DEADZONE -> hatX
            abs(axisX) > ANALOG_DEADZONE -> axisX
            else -> 0f
        }
        val effectiveY = when {
            abs(hatY) > HAT_DEADZONE -> hatY
            abs(axisY) > ANALOG_DEADZONE -> axisY
            else -> 0f
        }

        var mask = 0
        if (effectiveX < 0f) mask = mask or GbaInput.BUTTON_LEFT
        else if (effectiveX > 0f) mask = mask or GbaInput.BUTTON_RIGHT

        if (effectiveY < 0f) mask = mask or GbaInput.BUTTON_UP
        else if (effectiveY > 0f) mask = mask or GbaInput.BUTTON_DOWN

        return mask
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
