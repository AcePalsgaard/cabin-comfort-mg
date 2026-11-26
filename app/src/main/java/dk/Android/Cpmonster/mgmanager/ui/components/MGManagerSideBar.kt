package dk.Android.Cpmonster.mgmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dk.Android.Cpmonster.mgmanager.AppLinks

/**
 * Landscape replacement for [MGManagerTopAppBar]: a slim vertical toolbar on the right,
 * so the screen's height goes to the content. Same three-dot menu as the top bar
 * (Settings, About, privacy policy, log out), at the top; back arrow below it.
 */
@Composable
fun MGManagerSideBar(
    canNavigateBack: Boolean,
    onNavigateBack: () -> Unit,
    onSettingsScreen: Boolean,
    onSettings: () -> Unit,
    onAboutScreen: Boolean,
    onAbout: () -> Unit,
    showScheduleItem: Boolean,
    onSchedule: () -> Unit,
    scheduleOn: Boolean = false,
    isLoggedIn: Boolean,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    logoutLabel: String = "Log out",
    serverDown: Boolean = false,
) {
    var showMenu by remember { mutableStateOf(false) }
    var explainServer by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    if (explainServer) {
        AlertDialog(
            onDismissRequest = { explainServer = false },
            icon = { Icon(Icons.Rounded.CloudOff, null) },
            title = { Text("MG isn't responding") },
            text = {
                Text(
                    "MG's servers (or your connection) aren't answering right now. " +
                        "You can still use the app – we keep trying in the background " +
                        "and send your commands as soon as MG is back."
                )
            },
            confirmButton = { TextButton(onClick = { explainServer = false }) { Text("OK") } },
        )
    }

    // Same background as the screen, like the portrait top bar: just icons, no separate panel.
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxHeight()
            // Keep clear of a camera cutout when the phone is turned the other way.
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End)),
    ) {
        Column(
            Modifier.fillMaxHeight().width(72.dp).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (!onSettingsScreen) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                            onClick = {
                                showMenu = false
                                onSettings()
                            },
                        )
                    }
                    if (!onAboutScreen) {
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
            }
            if (showScheduleItem) ScheduleAction(scheduleOn, onSchedule)
            if (canNavigateBack) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
            if (serverDown) {
                IconButton(onClick = { explainServer = true }) {
                    Icon(Icons.Rounded.CloudOff, contentDescription = "MG servers not responding", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
