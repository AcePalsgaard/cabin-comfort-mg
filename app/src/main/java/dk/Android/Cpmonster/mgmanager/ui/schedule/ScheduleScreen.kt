package dk.Android.Cpmonster.mgmanager.ui.schedule

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import dk.Android.Cpmonster.mgmanager.AppContainer
import dk.Android.Cpmonster.mgmanager.data.db.Profile
import dk.Android.Cpmonster.mgmanager.schedule.DaySlot
import dk.Android.Cpmonster.mgmanager.schedule.Schedule
import dk.Android.Cpmonster.mgmanager.schedule.ScheduleMode
import dk.Android.Cpmonster.mgmanager.schedule.timeUntil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

class ScheduleViewModel(private val c: AppContainer) : ViewModel() {
    val schedule: StateFlow<Schedule?> = c.scheduler.schedule.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val profiles: StateFlow<List<Profile>> = c.profiles.profiles.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun update(f: (Schedule) -> Schedule) = viewModelScope.launch { c.scheduler.update(f) }
    fun canTimeExactly() = c.scheduler.canTimeExactly()
    fun rearm() = viewModelScope.launch { c.scheduler.rearm() }
}

private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel, modifier: Modifier = Modifier) {
    val s by viewModel.schedule.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val schedule = s ?: return
    val context = LocalContext.current

    // Re-check "Alarms & reminders" when coming back from Android's settings.
    var exact by remember { mutableStateOf(viewModel.canTimeExactly()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val now = viewModel.canTimeExactly()
            if (now != exact) viewModel.rearm()
            exact = now
        }
    }

    // Which time is being edited: "weekdays", "weekend" or "0".."6".
    var editing by remember { mutableStateOf<String?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Section("Start automatically") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Scheduled start", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = schedule.enabled, onCheckedChange = { on -> viewModel.update { it.copy(enabled = on) } })
            }
            val next = schedule.nextStart(ZonedDateTime.now())
            Hint(
                when {
                    !schedule.enabled -> "Off. Turn on to start a profile at set times."
                    next == null -> "No days are switched on."
                    else -> "Next start in ${timeUntil(ZonedDateTime.now(), next)}"
                },
            )
            if (schedule.enabled && !exact) {
                Hint("Android may start it a few minutes late. Allow \"Alarms & reminders\" for exact timing.")
                FilledTonalButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                    )
                }) { Text("Allow exact timing") }
            }
        }

        Section("Profile") {
            val options = listOf<Long?>(null) + profiles.map { it.id }
            val labels = listOf("Auto") + profiles.map { it.name }
            val selected = options.indexOf(schedule.profileId).coerceAtLeast(0)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, l ->
                    SegmentedButton(
                        selected = i == selected,
                        onClick = { viewModel.update { it.copy(profileId = options[i]) } },
                        shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                    ) { Text(l) }
                }
            }
            if (selected == 0) Hint("Auto picks Warm up or Chill from the outside temperature.")
        }

        Section("When") {
            val modes = listOf(ScheduleMode.WEEKDAYS_WEEKEND to "Weekdays / weekend", ScheduleMode.EACH_DAY to "Each day")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, (m, l) ->
                    SegmentedButton(
                        selected = schedule.mode == m,
                        onClick = { viewModel.update { it.copy(mode = m) } },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    ) { Text(l) }
                }
            }
            when (schedule.mode) {
                ScheduleMode.WEEKDAYS_WEEKEND -> {
                    SlotRow("Monday–Friday", schedule.weekdays, { editing = "weekdays" }) { on ->
                        viewModel.update { it.copy(weekdays = it.weekdays.copy(on = on)) }
                    }
                    SlotRow("Saturday–Sunday", schedule.weekend, { editing = "weekend" }) { on ->
                        viewModel.update { it.copy(weekend = it.weekend.copy(on = on)) }
                    }
                }
                ScheduleMode.EACH_DAY -> schedule.days.forEachIndexed { i, slot ->
                    SlotRow(DAY_NAMES[i], slot, { editing = "$i" }) { on ->
                        viewModel.update { it.copy(days = it.days.toMutableList().also { d -> d[i] = d[i].copy(on = on) }) }
                    }
                }
            }
            Hint("The car is heated or cooled from this time, for as long as the profile's duration.")
        }
    }

    editing?.let { which ->
        val slot = when (which) {
            "weekdays" -> schedule.weekdays
            "weekend" -> schedule.weekend
            else -> schedule.days[which.toInt()]
        }
        TimeDialog(slot, onDismiss = { editing = null }) { minutes ->
            editing = null
            viewModel.update {
                // Picking a time also switches that day on.
                val new = DaySlot(on = true, minutes = minutes)
                when (which) {
                    "weekdays" -> it.copy(weekdays = new)
                    "weekend" -> it.copy(weekend = new)
                    else -> it.copy(days = it.days.toMutableList().also { d -> d[which.toInt()] = new })
                }
            }
        }
    }
}

@Composable
private fun SlotRow(label: String, slot: DaySlot, onPickTime: () -> Unit, onToggle: (Boolean) -> Unit) {
    // Same one-line layout in both modes, so "Monday–Friday" looks like "Monday".
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        // Clearly a button: outlined, with a clock. Greyed out (but still tappable) when the day is off.
        val tint = if (slot.on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        OutlinedButton(
            onClick = onPickTime,
            border = BorderStroke(1.dp, if (slot.on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(slot.label, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = slot.on, onCheckedChange = onToggle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(slot: DaySlot, onDismiss: () -> Unit, onPicked: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = slot.hour, initialMinute = slot.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPicked(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
