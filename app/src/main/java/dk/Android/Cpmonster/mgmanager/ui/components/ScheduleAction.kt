package dk.Android.Cpmonster.mgmanager.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Toolbar button for the schedule: an alarm clock, with a tick and in colour while a schedule is on. */
@Composable
fun ScheduleAction(scheduleOn: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            if (scheduleOn) Icons.Rounded.AlarmOn else Icons.Rounded.Alarm,
            contentDescription = if (scheduleOn) "Schedule (on)" else "Schedule",
            tint = if (scheduleOn) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}
