package dk.Android.Cpmonster.mgmanager.ui.settings

import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalIconButton
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
import dk.Android.Cpmonster.mgmanager.data.model.CarColor
import dk.Android.Cpmonster.mgmanager.data.db.VehicleState
import dk.Android.Cpmonster.mgmanager.ui.car.BodyStyle
import dk.Android.Cpmonster.mgmanager.ui.car.CarSilhouette
import dk.Android.Cpmonster.mgmanager.ui.car.carColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.TextButton
import androidx.compose.ui.layout.ContentScale
import dk.Android.Cpmonster.mgmanager.ui.car.rememberCarPhoto
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
    vehicle: VehicleState? = null,
    carColorOverride: CarColor? = null,
    onCarColorSelected: (CarColor?) -> Unit = {},
    carPhoto: Long? = null,
    onPhotoPicked: (Uri) -> Unit = {},
    onPhotoRemoved: () -> Unit = {},
) {
    // Landscape / wide screens: the car (preview, photo, colour) on the left, the rest on the right.
    // One full-width column made the car preview huge.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp && maxWidth > maxHeight
        val appearanceSection: @Composable ColumnScope.() -> Unit = {
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
        }
        val carSection: @Composable ColumnScope.() -> Unit = {
            Section("Your car") {
                val detected = CarColor.detect(vehicle?.colorName)
                val photo = rememberCarPhoto(carPhoto)
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    uri?.let(onPhotoPicked)
                }
                if (photo != null) {
                    Image(
                        photo,
                        contentDescription = "Your car",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).align(Alignment.CenterHorizontally),
                    )
                } else {
                    CarSilhouette(
                        style = BodyStyle.of(vehicle),
                        color = carColor(carColorOverride, vehicle),
                        modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(0.8f).align(Alignment.CenterHorizontally),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Rounded.AddPhotoAlternate, if (carPhoto == null) "Use my own photo" else "Change photo")
                    }
                    if (carPhoto != null) {
                        IconButton(onClick = onPhotoRemoved) {
                            Icon(Icons.Rounded.Delete, "Remove photo")
                        }
                    }
                }
                Text(
                    if (carPhoto != null) "Colour (used when there's no photo)" else "Colour",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    when {
                        carColorOverride != null -> "Chosen by you: ${carColorOverride.label}"
                        detected != null -> "Detected from your car: ${vehicle?.colorName}"
                        !vehicle?.colorName.isNullOrBlank() -> "Your car says \"${vehicle?.colorName}\" – pick the closest colour"
                        else -> "Colour not detected yet – pick one"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // "Auto" = use the detected colour
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .border(
                                if (carColorOverride == null) 3.dp else 1.dp,
                                if (carColorOverride == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            )
                            .selectable(selected = carColorOverride == null, onClick = { onCarColorSelected(null) }, role = Role.RadioButton),
                        contentAlignment = Alignment.Center,
                    ) { Text("Auto", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
                    CarColor.entries.forEach { c ->
                        val selected = c == carColorOverride
                        val swatch = Color(c.argb)
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(
                                    if (selected) 3.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                )
                                .selectable(selected = selected, onClick = { onCarColorSelected(c) }, role = Role.RadioButton),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Rounded.Check, c.label, tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White)
                        }
                    }
                }
            }
        }
        val serverSection: @Composable ColumnScope.() -> Unit = {
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
        }
        val accountSection: @Composable ColumnScope.() -> Unit = {
            if (isLoggedIn) {
                Section("Account") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isDemo) "Guest (demo mode)" else username ?: "Logged in",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalIconButton(onClick = onLogout) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, if (isDemo) "Exit demo" else "Log out")
                        }
                    }
                }
            }
        }
        val versionLine: @Composable ColumnScope.() -> Unit = {
            Text(
                "${stringResource(R.string.app_name)} $appVersion",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        val spacing = Arrangement.spacedBy(16.dp)
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = spacing) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                    verticalArrangement = spacing,
                ) { carSection() }
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                    verticalArrangement = spacing,
                ) {
                    appearanceSection()
                    serverSection()
                    accountSection()
                    versionLine()
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = spacing,
            ) {
                appearanceSection()
                carSection()
                serverSection()
                accountSection()
                versionLine()
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
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
