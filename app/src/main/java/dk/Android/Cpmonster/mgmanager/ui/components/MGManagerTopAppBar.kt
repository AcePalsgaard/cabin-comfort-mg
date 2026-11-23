package dk.Android.Cpmonster.mgmanager.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import dk.Android.Cpmonster.mgmanager.AppLinks

/** One menu for everything: the three dots. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MGManagerTopAppBar(
    title: String,
    canNavigateBack: Boolean,
    onNavigateBack: () -> Unit,
    showSettingsItem: Boolean,
    onSettings: () -> Unit,
    showAboutItem: Boolean,
    onAbout: () -> Unit,
    isLoggedIn: Boolean,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    logoutLabel: String = "Log out",
) {
    var showMenu by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    TopAppBar(
        title = { Text(title) },
        modifier = modifier,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Menu")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (showSettingsItem) {
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                        onClick = {
                            showMenu = false
                            onSettings()
                        },
                    )
                }
                if (showAboutItem) {
                    DropdownMenuItem(
                        text = { Text("About") },
                        leadingIcon = { Icon(Icons.Rounded.Info, null) },
                        onClick = {
                            showMenu = false
                            onAbout()
                        },
                    )
                }
                // Always reachable, also before login (Google Play requirement).
                DropdownMenuItem(
                    text = { Text("Privacy policy") },
                    leadingIcon = { Icon(Icons.Rounded.PrivacyTip, null) },
                    onClick = {
                        showMenu = false
                        uriHandler.openUri(AppLinks.PRIVACY_POLICY_URL)
                    },
                )
                if (isLoggedIn) {
                    DropdownMenuItem(
                        text = { Text(logoutLabel) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
                        onClick = {
                            showMenu = false
                            onLogout()
                        },
                    )
                }
            }
        },
    )
}
