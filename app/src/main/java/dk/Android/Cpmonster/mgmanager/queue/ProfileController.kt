package dk.Android.Cpmonster.mgmanager.queue

import android.content.Context
import android.util.Log
import dk.Android.Cpmonster.mgmanager.climate.ClimateSession
import dk.Android.Cpmonster.mgmanager.data.db.Profile
import dk.Android.Cpmonster.mgmanager.data.db.ProfileKind
import dk.Android.Cpmonster.mgmanager.data.db.VehicleState
import dk.Android.Cpmonster.mgmanager.data.profile.ProfileRepository
import dk.Android.Cpmonster.mgmanager.data.vehicle.VehicleRepository
import dk.Android.Cpmonster.mgmanager.saic.CarFeatures
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Turns a profile into queued car commands and runs the session (runs, prompt, notification). */
class ProfileController(
    private val context: Context,
    private val profiles: ProfileRepository,
    private val vehicle: VehicleRepository,
    private val queue: CommandQueue,
) {
    private val lock = Mutex()

    private companion object {
        const val TAG = "ProfileController"
    }

    suspend fun activate(profile: Profile) = lock.withLock {
        Log.i(TAG, "Start ${profile.name}")
        val previous = profiles.activeNow()?.let { profiles.get(it.id) }
        profiles.setActive(profile.id, profile.runs)
        // Turn off extras the previous profile enabled but this one doesn't.
        if (previous != null && previous.id != profile.id) stopExtras(previous, keep = profile)
        ClimateSession.clearPrompt(context)
        apply(profile)
        sessionChanged()
    }

    /** Profile was edited while running: re-send settings without restarting the session. */
    suspend fun update(profile: Profile) = lock.withLock {
        if (profiles.activeNow()?.id != profile.id) return@withLock
        profiles.setRuns(profile.runs)
        apply(profile)
        sessionChanged()
    }

    suspend fun deactivate() = lock.withLock {
        Log.i(TAG, "Stop")
        val current = profiles.activeNow()?.let { profiles.get(it.id) }
        profiles.clearActive()
        ClimateSession.cancelAlarms(context)
        ClimateSession.clearAll(context)
        queue.enqueue(JobType.CLIMATE, JSONObject().put("stop", true))
        if (current != null) stopExtras(current, keep = null)
        queue.enqueueRestore()
        queue.enqueue(JobType.REFRESH_STATUS)
    }

    /**
     * App started or opened with a session already running: put the notification and alarms
     * back (they're lost when the app is killed, the phone reboots or the user swipes it away).
     */
    suspend fun resume() = lock.withLock {
        val a = profiles.activeNow() ?: return@withLock
        if (System.currentTimeMillis() >= a.runEndsAt) runEnded() else sessionChanged()
    }

    /** Alarm: the current run is over – start the next one or finish. */
    suspend fun onRunEnd() = lock.withLock { runEnded() }

    private suspend fun runEnded() {
        val a = profiles.activeNow() ?: return
        val p = profiles.get(a.id) ?: return finish(null)
        val now = System.currentTimeMillis()
        if (now < a.runEndsAt - 15_000L) {
            // Stale alarm (session changed meanwhile) – just re-arm.
            ClimateSession.schedule(context, a)
            return
        }
        if (a.isLastRun || now >= a.sessionEndsAt) {
            // Also covers the app having been dead past the planned end.
            finish(p)
        } else {
            profiles.nextRun()
            ClimateSession.clearPrompt(context)
            apply(p)
            sessionChanged()
        }
    }

    /** Alarm: one minute before the last run ends. */
    suspend fun onPrompt() = lock.withLock {
        val a = profiles.activeNow() ?: return@withLock
        if (!a.isLastRun) return@withLock
        profiles.get(a.id)?.let { ClimateSession.showPrompt(context, it) }
    }

    /**
     * The car refuses remote commands: end the session quietly without sending anything
     * (a stop command would be refused too and only extend the block).
     */
    suspend fun abort() = lock.withLock {
        if (profiles.activeNow() == null) return@withLock
        profiles.clearActive()
        ClimateSession.cancelAlarms(context)
        ClimateSession.clearAll(context)
    }

    /** "Continue" from the prompt: one more run after the current one. */
    suspend fun continueOneMore() = lock.withLock {
        ClimateSession.clearPrompt(context)
        if (profiles.activeNow() == null) return@withLock
        profiles.addRun()
        sessionChanged()
    }

    private suspend fun finish(p: Profile?) {
        profiles.clearActive()
        ClimateSession.cancelAlarms(context)
        if (p != null) ClimateSession.showFinished(context, p) else ClimateSession.clearAll(context)
        queue.enqueueRestore()
        queue.enqueue(JobType.REFRESH_STATUS)
    }

    private suspend fun sessionChanged() {
        val a = profiles.activeNow() ?: return
        val p = profiles.get(a.id) ?: return
        ClimateSession.schedule(context, a)
        ClimateSession.showOngoing(context, p, a, vehicle.current()?.interiorTemp)
    }

    private suspend fun apply(profile: Profile) {
        val car = vehicle.features()
        val climate = JSONObject()
            .put("tempIdx", car.temperatureIdx(profile.targetTemp))
            .put("chargeAssist", profile.chargeAssist)
        when {
            profile.kind == ProfileKind.COLD && profile.frontDefrost -> climate.put("fan", 5).put("ac", true)
            profile.kind == ProfileKind.WARM -> climate.put("fan", profile.fanSpeed).put("ac", true)
            else -> climate.put("fan", profile.fanSpeed)
        }
        queue.enqueue(JobType.CLIMATE, climate)
        val left = car.seatLevel(profile.seatLeft)
        val right = car.seatLevel(profile.seatRight)
        if (left > 0 || right > 0) {
            queue.enqueue(JobType.SEATS, JSONObject().put("left", left).put("right", right))
        }
        // Not sent when the car says it doesn't have it.
        if (profile.steeringWheel && car.hasSteeringWheelHeat) queue.enqueue(JobType.STEERING_WHEEL, JSONObject().put("on", true))
        if (profile.rearWindow) queue.enqueue(JobType.REAR_WINDOW, JSONObject().put("on", true))
        queue.enqueue(JobType.REFRESH_STATUS)
    }

    private suspend fun stopExtras(old: Profile, keep: Profile?) {
        val hasSeats = vehicle.current()?.features?.hasSeatHeating != false
        if (hasSeats && (old.seatLeft > 0 || old.seatRight > 0) && (keep == null || (keep.seatLeft == 0 && keep.seatRight == 0))) {
            queue.enqueue(JobType.SEATS, JSONObject().put("left", 0).put("right", 0))
        }
        val car = vehicle.features()
        if (old.steeringWheel && keep?.steeringWheel != true && car.hasSteeringWheelHeat) {
            queue.enqueue(JobType.STEERING_WHEEL, JSONObject().put("on", false))
        }
        if (old.rearWindow && keep?.rearWindow != true) {
            queue.enqueue(JobType.REAR_WINDOW, JSONObject().put("on", false))
        }
    }

    /** New car data: refresh the notification, and clean up if an alarm was missed. */
    suspend fun reconcile(state: VehicleState) = lock.withLock {
        val a = profiles.activeNow() ?: return@withLock
        val p = profiles.get(a.id) ?: return@withLock
        val now = System.currentTimeMillis()
        if (now > a.sessionEndsAt + 5 * 60_000L && state.remoteClimateStatus == 0) {
            finish(p)
            return@withLock
        }
        ClimateSession.showOngoing(context, p, a, state.interiorTemp)
    }
}
