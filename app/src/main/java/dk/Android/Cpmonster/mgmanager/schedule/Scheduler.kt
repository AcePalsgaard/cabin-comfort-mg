package dk.Android.Cpmonster.mgmanager.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dk.Android.Cpmonster.mgmanager.MGApp
import dk.Android.Cpmonster.mgmanager.data.auth.AuthRepository
import dk.Android.Cpmonster.mgmanager.data.db.ProfileKind
import dk.Android.Cpmonster.mgmanager.data.profile.ProfileRepository
import dk.Android.Cpmonster.mgmanager.data.profile.ProfileSuggester
import dk.Android.Cpmonster.mgmanager.data.vehicle.VehicleRepository
import dk.Android.Cpmonster.mgmanager.queue.ProfileController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.ZonedDateTime

/** Stores the schedule and keeps one alarm set for the next start. */
class Scheduler(
    private val context: Context,
    private val store: DataStore<Preferences>,
    private val profiles: ProfileRepository,
    private val vehicle: VehicleRepository,
    private val auth: AuthRepository,
    private val controller: ProfileController,
) {
    private val key = stringPreferencesKey("schedule")

    val schedule: Flow<Schedule> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { Schedule.fromJson(it[key]) }

    suspend fun update(f: (Schedule) -> Schedule) {
        store.edit { it[key] = f(Schedule.fromJson(it[key])).toJson() }
        rearm()
    }

    fun canTimeExactly(): Boolean = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Set (or clear) the alarm for the next start. Call after changes, at app start and after each start. */
    suspend fun rearm() {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending()
        val next = schedule.first().nextStart(ZonedDateTime.now())
        if (next == null) {
            am.cancel(pi)
            return
        }
        val at = next.toInstant().toEpochMilli()
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            // Without "Alarms & reminders" Android may start it some minutes late.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        Log.i(TAG, "Next scheduled start: $next")
    }

    /** The alarm went off: start the profile, unless something is already running. */
    suspend fun onAlarm() {
        try {
            val s = schedule.first()
            val now = ZonedDateTime.now()
            // Late alarm (phone was off, Doze): only start if it's still close to the planned time.
            val planned = s.nextStart(now.minusMinutes(LATE_LIMIT_MIN))
            if (planned == null || planned.isAfter(now.plusMinutes(1))) {
                Log.i(TAG, "Alarm without a start due now – skipped")
                return
            }
            if (!auth.isLoggedIn() || profiles.activeNow() != null) return
            val all = profiles.profiles.first()
            val profile = s.profileId?.let { id -> all.firstOrNull { it.id == id } } ?: run {
                // Auto: like the Auto widget. Unknown temperature -> warm up (the usual reason to pre-heat).
                val t = vehicle.current()?.exteriorTemp
                val kind = if (t == null) ProfileKind.COLD else ProfileSuggester.kindFor(t)
                all.firstOrNull { it.kind == kind }
            } ?: return
            Log.i(TAG, "Scheduled start: ${profile.name}")
            controller.activate(profile)
        } finally {
            rearm()
        }
    }

    private fun pending(): PendingIntent = PendingIntent.getBroadcast(
        context,
        ACTION_START.hashCode(),
        Intent(context, ScheduleReceiver::class.java).setAction(ACTION_START),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val TAG = "Scheduler"
        const val ACTION_START = "dk.Android.Cpmonster.mgmanager.SCHEDULED_START"
        private const val LATE_LIMIT_MIN = 15L
    }
}

/** Alarm entry point; also re-arms after a time zone or clock change. */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = (context.applicationContext as MGApp).container
        val done = goAsync()
        c.scope.launch {
            try {
                if (intent.action == Scheduler.ACTION_START) c.scheduler.onAlarm() else c.scheduler.rearm()
            } finally {
                done.finish()
            }
        }
    }
}
