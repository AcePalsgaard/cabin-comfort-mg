package dk.Android.Cpmonster.mgmanager.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dk.Android.Cpmonster.mgmanager.AppContainer
import dk.Android.Cpmonster.mgmanager.data.db.Profile
import dk.Android.Cpmonster.mgmanager.data.db.ProfileKind
import dk.Android.Cpmonster.mgmanager.data.profile.ActiveProfile
import dk.Android.Cpmonster.mgmanager.ui.home.color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import dk.Android.Cpmonster.mgmanager.saic.CarFeatures
import dk.Android.Cpmonster.mgmanager.saic.SeatHeating
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ProfileEditViewModel(private val c: AppContainer, id: Long) : ViewModel() {
    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile

    /** Detected car features – limits temperature range and seat controls. */
    val car: StateFlow<CarFeatures> = c.vehicle.state
        .map { it?.features ?: CarFeatures(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CarFeatures(null))

    init {
        viewModelScope.launch { _profile.value = c.profiles.get(id) }
    }

    fun edit(f: (Profile) -> Profile) = _profile.update { it?.let(f) }

    fun save(done: () -> Unit) = viewModelScope.launch {
        val p = _profile.value ?: return@launch
        c.profiles.save(p.copy(name = p.name.trim().ifEmpty { if (p.kind == ProfileKind.COLD) "Warm up" else "Chill" }))
        // Re-apply if this profile is running, so the car follows the new settings.
        c.controller.update(p)
        done()
    }
}

@Composable
fun ProfileEditScreen(viewModel: ProfileEditViewModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val p by viewModel.profile.collectAsStateWithLifecycle()
    val car by viewModel.car.collectAsStateWithLifecycle()
    val profile = p ?: run {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val accent = profile.kind.color()
    val sliderColors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = profile.name,
            onValueChange = { v -> viewModel.edit { it.copy(name = v.take(24)) } },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Section("Cabin") {
            val temp = profile.targetTemp.coerceIn(car.minTemp, car.maxTemp)
            LabeledValue("Temperature", "${temp}°C")
            Slider(
                value = temp.toFloat(),
                onValueChange = { v -> viewModel.edit { it.copy(targetTemp = v.roundToInt()) } },
                valueRange = car.minTemp.toFloat()..car.maxTemp.toFloat(),
                steps = car.maxTemp - car.minTemp - 1,
                colors = sliderColors,
            )
            LabeledValue("Fan speed", "${profile.fanSpeed}")
            Slider(
                value = profile.fanSpeed.toFloat(),
                onValueChange = { v -> viewModel.edit { it.copy(fanSpeed = v.roundToInt()) } },
                valueRange = 1f..5f,
                steps = 3,
                colors = sliderColors,
            )
            if (profile.kind == ProfileKind.COLD) {
                SwitchRow("Front defrost", "Max fan on the windscreen", profile.frontDefrost) { on ->
                    viewModel.edit { it.copy(frontDefrost = on) }
                }
            }
        }

        if (profile.kind == ProfileKind.COLD) {
            Section("Heating") {
                when (car.seatHeating) {
                    SeatHeating.NONE -> Unit
                    SeatHeating.ON_OFF -> {
                        SwitchRow("Front left seat", null, profile.seatLeft > 0) { on ->
                            viewModel.edit { it.copy(seatLeft = if (on) 1 else 0) }
                        }
                        SwitchRow("Front right seat", null, profile.seatRight > 0) { on ->
                            viewModel.edit { it.copy(seatRight = if (on) 1 else 0) }
                        }
                        HorizontalDivider()
                    }
                    else -> {
                        SeatLevel("Front left seat", profile.seatLeft) { l -> viewModel.edit { it.copy(seatLeft = l) } }
                        SeatLevel("Front right seat", profile.seatRight) { l -> viewModel.edit { it.copy(seatRight = l) } }
                        HorizontalDivider()
                    }
                }
                SwitchRow("Steering wheel", "Turns off by itself after ~10 min", profile.steeringWheel) { on ->
                    viewModel.edit { it.copy(steeringWheel = on) }
                }
                SwitchRow("Rear window", null, profile.rearWindow) { on ->
                    viewModel.edit { it.copy(rearWindow = on) }
                }
            }
        }

        Section("Duration") {
            val runs = profile.runs.coerceIn(1, 4)
            LabeledValue("Runs", "${runs * ActiveProfile.RUN_MINUTES} min")
            Text(
                "The car stops climate after ${ActiveProfile.RUN_MINUTES} min. Each run restarts it. " +
                    "One minute before the last run ends you're asked whether to continue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Choice(listOf("1", "2", "3", "4"), runs - 1) { i -> viewModel.edit { it.copy(runs = i + 1) } }
        }

        Button(
            onClick = { viewModel.save(onDone) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) { Text("Save") }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SwitchRow(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeatLevel(label: String, level: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Choice(listOf("Off", "1", "2", "3"), level, onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choice(labels: List<String>, selected: Int, onChange: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, l ->
            SegmentedButton(
                selected = selected == i,
                onClick = { onChange(i) },
                shape = SegmentedButtonDefaults.itemShape(i, labels.size),
            ) { Text(l) }
        }
    }
}
