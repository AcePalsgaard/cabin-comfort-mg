package dk.Android.Cpmonster.mgmanager.climate

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dk.Android.Cpmonster.mgmanager.MGApp
import dk.Android.Cpmonster.mgmanager.R
import dk.Android.Cpmonster.mgmanager.data.db.Profile
import dk.Android.Cpmonster.mgmanager.data.db.ProfileKind
import dk.Android.Cpmonster.mgmanager.data.profile.ActiveProfile
import kotlinx.coroutines.launch

/**
 * Timing and notifications for a climate session. Alarms drive the run changes so it keeps
 * working with the app closed; the ongoing notification counts down by itself.
 */
object ClimateSession {
    const val PROMPT_BEFORE_MS = 60_000L

    private const val CH_ACTIVE = "climate_active"
    private const val CH_PROMPT = "climate_prompt"
    private const val ID_ACTIVE = 1001
    private const val ID_PROMPT = 1002

    const val ACTION_RUN_END = "dk.Android.Cpmonster.mgmanager.RUN_END"
    const val ACTION_PROMPT = "dk.Android.Cpmonster.mgmanager.PROMPT"
    const val ACTION_CONTINUE = "dk.Android.Cpmonster.mgmanager.CONTINUE"
    const val ACTION_STOP = "dk.Android.Cpmonster.mgmanager.STOP"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ACTIVE, "A/C running", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows progress while the car is being heated or cooled"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_PROMPT, "Continue A/C?", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Asks one minute before the A/C ends if it should keep going"
            },
        )
    }

    // ---- Alarms ----

    fun schedule(context: Context, a: ActiveProfile) {
        cancelAlarms(context)
        set(context, a.runEndsAt, ACTION_RUN_END)
        if (a.isLastRun) {
            val promptAt = a.runEndsAt - PROMPT_BEFORE_MS
            if (promptAt > System.currentTimeMillis()) set(context, promptAt, ACTION_PROMPT)
        }
    }

    fun cancelAlarms(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        listOf(ACTION_RUN_END, ACTION_PROMPT).forEach { am.cancel(pending(context, it)) }
    }

    private fun set(context: Context, at: Long, action: String) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, action)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pending(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, ClimateReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // ---- Notifications ----

    fun showOngoing(context: Context, profile: Profile, a: ActiveProfile, insideTemp: Int?) {
        val verb = if (profile.kind == ProfileKind.COLD) "Heating" else "Cooling"
        val text = buildList {
            add("Run ${a.run} of ${a.runs}")
            insideTemp?.let { add("inside $it°") }
            add("target ${profile.targetTemp}°")
        }.joinToString(" · ")
        val total = a.runs * ActiveProfile.RUN_MS
        val elapsed = (a.run - 1) * ActiveProfile.RUN_MS + (System.currentTimeMillis() - a.runStartedAt)

        val n = NotificationCompat.Builder(context, CH_ACTIVE)
            .setSmallIcon(R.drawable.ic_stat_climate)
            .setContentTitle("$verb car · ${profile.name}")
            .setContentText(text)
            .setColor(if (profile.kind == ProfileKind.COLD) 0xFFE8590C.toInt() else 0xFF2F6FEB.toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setWhen(a.sessionEndsAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setProgress(total.toInt(), elapsed.coerceIn(0, total).toInt(), false)
            .setContentIntent(openApp(context))
            .addAction(0, "Stop", pending(context, ACTION_STOP))
            .apply { if (a.isLastRun) addAction(0, "+1 run", pending(context, ACTION_CONTINUE)) }
            .build()
        notify(context, ID_ACTIVE, n)
    }

    fun showPrompt(context: Context, profile: Profile) {
        val n = NotificationCompat.Builder(context, CH_PROMPT)
            .setSmallIcon(R.drawable.ic_stat_climate)
            .setContentTitle("${profile.name} ends in 1 minute")
            .setContentText("Keep going for another ${ActiveProfile.RUN_MINUTES} minutes?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(PROMPT_BEFORE_MS + 30_000L)
            .setContentIntent(openApp(context))
            .addAction(0, "Continue", pending(context, ACTION_CONTINUE))
            .addAction(0, "Stop", pending(context, ACTION_STOP))
            .build()
        notify(context, ID_PROMPT, n)
    }

    fun showFinished(context: Context, profile: Profile) {
        clearPrompt(context)
        val n = NotificationCompat.Builder(context, CH_ACTIVE)
            .setSmallIcon(R.drawable.ic_stat_climate)
            .setContentTitle("${profile.name} finished")
            .setContentText("The car has stopped the A/C")
            .setAutoCancel(true)
            .setTimeoutAfter(30 * 60_000L)
            .setContentIntent(openApp(context))
            .build()
        notify(context, ID_ACTIVE, n)
    }

    fun clearPrompt(context: Context) = NotificationManagerCompat.from(context).cancel(ID_PROMPT)

    fun clearAll(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(ID_ACTIVE)
        nm.cancel(ID_PROMPT)
    }

    private fun notify(context: Context, id: Int, n: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(id, n)
    }

    private fun openApp(context: Context): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
}

/** Alarm and notification-button entry point. */
class ClimateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = (context.applicationContext as MGApp).container
        val done = goAsync()
        c.scope.launch {
            try {
                when (intent.action) {
                    ClimateSession.ACTION_RUN_END -> c.controller.onRunEnd()
                    ClimateSession.ACTION_PROMPT -> c.controller.onPrompt()
                    ClimateSession.ACTION_CONTINUE -> c.controller.continueOneMore()
                    ClimateSession.ACTION_STOP -> c.controller.deactivate()
                }
            } finally {
                done.finish()
            }
        }
    }
}
