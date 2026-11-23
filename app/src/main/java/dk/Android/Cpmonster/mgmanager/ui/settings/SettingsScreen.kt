package dk.Android.Cpmonster.mgmanager.ui.settings

import androidx.compose.ui.res.stringResource
import dk.Android.Cpmonster.mgmanager.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dk.Android.Cpmonster.mgmanager.data.model.ThemeMode
import dk.Android.Cpmonster.mgmanager.saic.SaicRegion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    server: SaicRegion,
    showTestServer: Boolean,
    username: String?,
    isLoggedIn: Boolean,
    appVersion: String,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onServerSelected: (SaicRegion) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    isDemo: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Section("Appearance") {
            val modes = listOf(ThemeMode.SYSTEM_DEFAULT to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { onThemeModeSelected(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    ) { Text(label) }
                }
            }
        }

        Section("Server") {
            Text(
                "Changing server logs you out.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.selectableGroup()) {
                SaicRegion.selectable // DEV-ONLY: TEST only in debug builds
                    .forEach { r ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = r == server, onClick = { onServerSelected(r) }, role = Role.RadioButton)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = r == server, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(r.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (r == SaicRegion.TEST) {
                                Icon(
                                    Icons.Rounded.Science,
                                    contentDescription = "Developer",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
            }
            if (showTestServer) { // DEV-ONLY
                Text(
                    "Test server: run tools/mock-server/start_mock_server.bat on your PC. Password: test",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (isLoggedIn) {
            Section("Account") {
                Text(if (isDemo) "Guest (demo mode)" else username ?: "Logged in", style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isDemo) "Exit demo" else "Log out")
                }
            }
        }

        Text(
            "${stringResource(R.string.app_name)} $appVersion",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
