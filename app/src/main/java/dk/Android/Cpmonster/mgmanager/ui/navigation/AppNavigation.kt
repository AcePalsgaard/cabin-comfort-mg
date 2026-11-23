package dk.Android.Cpmonster.mgmanager.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dk.Android.Cpmonster.mgmanager.MGApp
import dk.Android.Cpmonster.mgmanager.data.auth.AuthState
import dk.Android.Cpmonster.mgmanager.navigation.Screen
import dk.Android.Cpmonster.mgmanager.saic.SaicRegion
import dk.Android.Cpmonster.mgmanager.ui.about.AboutScreen
import dk.Android.Cpmonster.mgmanager.ui.components.MGManagerTopAppBar
import dk.Android.Cpmonster.mgmanager.ui.home.HomeScreen
import dk.Android.Cpmonster.mgmanager.ui.home.HomeViewModel
import dk.Android.Cpmonster.mgmanager.ui.login.LoginScreen
import dk.Android.Cpmonster.mgmanager.ui.login.LoginViewModel
import dk.Android.Cpmonster.mgmanager.ui.profile.ProfileEditScreen
import dk.Android.Cpmonster.mgmanager.ui.profile.ProfileEditViewModel
import dk.Android.Cpmonster.mgmanager.ui.settings.SettingsScreen
import dk.Android.Cpmonster.mgmanager.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val container = (LocalContext.current.applicationContext as MGApp).container
    val auth by container.auth.state.collectAsStateWithLifecycle(initialValue = null as AuthState?)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val a = auth
        if (a == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            MainNav(viewModel, a, modifier)
        }
    }
}

@Composable
private fun MainNav(viewModel: SettingsViewModel, auth: AuthState, modifier: Modifier) {
    val context = LocalContext.current
    val container = (context.applicationContext as MGApp).container
    val scope = rememberCoroutineScope()
    val isLoggedIn = auth.isLoggedIn

    // Login and the front page are separate screens: NavDisplay keeps an entry's content per key,
    // so sharing one key left the login screen on display after a successful login.
    val home: Screen = if (isLoggedIn) Screen.Main else Screen.Login
    val backStack = rememberNavBackStack(home)
    val currentScreen = (backStack.lastOrNull() as? Screen) ?: home
    val canNavigateBack = backStack.size > 1

    // Logging in or out always lands on that state's start screen.
    LaunchedEffect(isLoggedIn) {
        if (backStack.size == 1 && backStack[0] == home) return@LaunchedEffect
        backStack.add(home)
        while (backStack.size > 1) backStack.removeAt(0)
    }

    var confirmLogout by remember { mutableStateOf(false) }
    var pendingServer by remember { mutableStateOf<SaicRegion?>(null) }

    fun navigateTo(screen: Screen) {
        if (currentScreen != screen) backStack.add(screen)
    }

    fun navigateBack() {
        if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
    }

    BackHandler(enabled = canNavigateBack) { navigateBack() }

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val vehicleState by container.vehicle.state.collectAsStateWithLifecycle(null)
    val carColorOverride by container.settings.carColor.collectAsStateWithLifecycle(null)
    val carPhoto by container.settings.carPhoto.collectAsStateWithLifecycle(null)
    val showTestServer = dk.Android.Cpmonster.mgmanager.DevOnly.ENABLED // DEV-ONLY
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            text = { Text("You'll need to log in again to control your car.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    scope.launch { container.logout() }
                }) { Text("Log out") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
        )
    }

    pendingServer?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingServer = null },
            title = { Text("Switch to ${target.label}?") },
            text = { Text("You'll be logged out and need to log in on the new server.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingServer = null
                    scope.launch { container.changeServer(target) }
                }) { Text("Switch") }
            },
            dismissButton = { TextButton(onClick = { pendingServer = null }) { Text("Cancel") } },
        )
    }

    val title = when (currentScreen) {
        Screen.Main -> "Cabin Comfort for MG"
        Screen.Login -> ""
        Screen.Settings -> "Settings"
        Screen.About -> "About"
        is Screen.ProfileEdit -> "Edit profile"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MGManagerTopAppBar(
                title = title,
                canNavigateBack = canNavigateBack,
                onNavigateBack = { navigateBack() },
                showSettingsItem = currentScreen != Screen.Settings,
                onSettings = { navigateTo(Screen.Settings) },
                showAboutItem = currentScreen != Screen.About,
                onAbout = { navigateTo(Screen.About) },
                isLoggedIn = isLoggedIn,
                onLogout = {
                    if (auth.isDemo) {
                        scope.launch { container.logout() }
                    } else {
                        confirmLogout = true
                    }
                },
                logoutLabel = if (auth.isDemo) "Exit demo" else "Log out",
            )
        },
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(innerPadding),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        ) { key ->
            NavEntry(key) {
                when (key) {
                    Screen.Settings -> SettingsScreen(
                        themeMode = themeMode,
                        server = auth.region,
                        showTestServer = showTestServer,
                        username = auth.username,
                        isLoggedIn = isLoggedIn,
                        appVersion = appVersion,
                        onThemeModeSelected = { viewModel.setThemeMode(it) },
                        onServerSelected = { r ->
                            if (r == auth.region) return@SettingsScreen
                            if (isLoggedIn) pendingServer = r else scope.launch { container.changeServer(r) }
                        },
                        onLogout = {
                    if (auth.isDemo) {
                        scope.launch { container.logout() }
                    } else {
                        confirmLogout = true
                    }
                },
                        isDemo = auth.isDemo,
                        vehicle = vehicleState,
                        carColorOverride = carColorOverride,
                        onCarColorSelected = { c -> scope.launch { container.settings.setCarColor(c) } },
                        carPhoto = carPhoto,
                        onPhotoPicked = { uri ->
                            scope.launch {
                                if (dk.Android.Cpmonster.mgmanager.ui.car.CarPhoto.save(context, uri)) {
                                    container.settings.setCarPhoto(System.currentTimeMillis())
                                } else {
                                    android.widget.Toast.makeText(context, "Couldn't read that picture", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onPhotoRemoved = {
                            scope.launch {
                                container.settings.setCarPhoto(null)
                                dk.Android.Cpmonster.mgmanager.ui.car.CarPhoto.delete(context)
                            }
                        },
                    )

                    Screen.About -> AboutScreen(appVersion = appVersion)

                    is Screen.ProfileEdit -> ProfileEditScreen(
                        viewModel = viewModel(
                            key = "profile-${key.id}",
                            factory = viewModelFactory { initializer { ProfileEditViewModel(container, key.id) } },
                        ),
                        onDone = { navigateBack() },
                    )

                    Screen.Login -> LoginScreen(
                        viewModel = viewModel(
                            key = "login",
                            factory = viewModelFactory { initializer { LoginViewModel(container) } },
                        ),
                    )

                    else -> HomeScreen(
                        viewModel = viewModel(
                            key = "home",
                            factory = viewModelFactory { initializer { HomeViewModel(container) } },
                        ),
                        onEditProfile = { navigateTo(Screen.ProfileEdit(it)) },
                        isDemo = auth.isDemo,
                        onExitDemo = { scope.launch { container.logout() } },
                    )
                }
            }
        }
    }
}
