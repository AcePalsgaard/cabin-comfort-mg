package dk.Android.Cpmonster.mgmanager.queue

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dk.Android.Cpmonster.mgmanager.data.auth.AuthRepository
import dk.Android.Cpmonster.mgmanager.data.db.ApiJob
import dk.Android.Cpmonster.mgmanager.data.db.JobDao
import dk.Android.Cpmonster.mgmanager.data.db.VehicleState
import dk.Android.Cpmonster.mgmanager.data.vehicle.VehicleRepository
import dk.Android.Cpmonster.mgmanager.saic.LoginThrottledException
import dk.Android.Cpmonster.mgmanager.saic.NeedsLoginException
import dk.Android.Cpmonster.mgmanager.saic.SaicApi
import dk.Android.Cpmonster.mgmanager.saic.SaicApiException
import dk.Android.Cpmonster.mgmanager.saic.SaicAuthException
import dk.Android.Cpmonster.mgmanager.saic.SaicCarBlockedException
import dk.Android.Cpmonster.mgmanager.saic.SaicCommands
import dk.Android.Cpmonster.mgmanager.saic.SaicIncompleteDataException
import dk.Android.Cpmonster.mgmanager.saic.SaicNetworkException
import dk.Android.Cpmonster.mgmanager.work.QueueWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min

enum class JobType(val maxAgeMs: Long, val maxAttempts: Int) {
    REFRESH_STATUS(2 * 60 * 60_000L, 30),
    // Few tries: every re-send counts against the car's limit on remote commands, and when that
    // is used up the car blocks them until it's started with the key ("Too frequent operations").
    CLIMATE(20 * 60_000L, 4),
    SEATS(20 * 60_000L, 4),
    STEERING_WHEEL(20 * 60_000L, 4),
    REAR_WINDOW(20 * 60_000L, 4),
    /** Put back the charge limit / stop the charge [ChargeAssist] started for the A/C. */
    CHARGE_RESTORE(6 * 60 * 60_000L, 6);

    val isCommand get() = this != REFRESH_STATUS
}

/**
 * Every API call goes through here. Jobs are persisted in Room, executed one at a time,
 * retried with exponential backoff and silently dropped when stale. WorkManager picks
 * the queue up again if the app is closed.
 */
class CommandQueue(
    private val context: Context,
    private val dao: JobDao,
    private val auth: AuthRepository,
    private val vehicle: VehicleRepository,
    private val scope: CoroutineScope,
    private val afterRefresh: suspend (VehicleState) -> Unit,
    /** The car blocks remote commands: end the running session so it doesn't send more. */
    private val onCarBlocked: suspend () -> Unit,
    private val demo: dk.Android.Cpmonster.mgmanager.demo.DemoCar,
    private val chargeAssist: ChargeAssist,
) {
    private val mutex = Mutex()
    private var timer: Job? = null

    val pendingCount: Flow<Int> = dao.countFlow()
    /** Kinds of job still waiting. */
    private val waitingTypes: Flow<Set<JobType>> =
        dao.typesFlow().map { names -> names.mapNotNull { runCatching { JobType.valueOf(it) }.getOrNull() }.toSet() }
    /** Car data is being fetched (MG's servers are what we wait for). */
    val refreshing: Flow<Boolean> = waitingTypes.map { JobType.REFRESH_STATUS in it }
    /** The heating/A/C command is waiting for the car. Slow cars are not MG being down. */
    val sending: Flow<Boolean> = waitingTypes.map { JobType.CLIMATE in it }
    /** Seats, steering wheel, rear window still on their way – shown discreetly, the cabin is already heating. */
    val extrasWaiting: Flow<Set<JobType>> = waitingTypes.map { t -> t.filterTo(mutableSetOf()) { it.isCommand && it != JobType.CLIMATE } }

    private val _lastDropped = MutableStateFlow<String?>(null)
    /** Set when a command gave up – shown discreetly, never as a popup. */
    val lastDropped: StateFlow<String?> = _lastDropped

    suspend fun enqueue(type: JobType, payload: JSONObject = JSONObject(), dedupeKey: String? = type.name) {
        val now = System.currentTimeMillis()
        dao.replace(ApiJob(type = type.name, payload = payload.toString(), dedupeKey = dedupeKey, createdAt = now, nextRunAt = now))
        _lastDropped.value = null
        kick()
    }

    fun kick() {
        scope.launch { drain() }
    }

    suspend fun clear() = dao.clear()

    /** MG's servers answer again: stop waiting out the backoff. */
    suspend fun retryNow() {
        dao.runAllNow(System.currentTimeMillis())
        kick()
    }

    suspend fun drain() {
        if (!mutex.tryLock()) return
        try {
            while (true) {
                val now = System.currentTimeMillis()
                val job = dao.nextDue(now) ?: break
                val type = runCatching { JobType.valueOf(job.type) }.getOrNull()
                if (type == null || now - job.createdAt > type.maxAgeMs) {
                    drop(job, type, "expired")
                    continue
                }
                try {
                    execute(type, JSONObject(job.payload))
                    Log.i(TAG, "${job.type} done")
                    dao.delete(job.id)
                    if (type == JobType.CLIMATE) {
                        // Heating/A/C confirmed: fetch the car's status straight away, before the
                        // extras, so the screen shows it running without waiting for seats etc.
                        dao.replace(
                            ApiJob(
                                type = JobType.REFRESH_STATUS.name, payload = "{}", dedupeKey = JobType.REFRESH_STATUS.name,
                                createdAt = System.currentTimeMillis(), nextRunAt = 0L,
                            ),
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: NeedsLoginException) {
                    // Stored credentials were rejected – the UI shows the login screen. Keep the jobs.
                    return
                } catch (e: LoginThrottledException) {
                    // Nothing was sent: wait for the next allowed login without using up an attempt.
                    Log.w(TAG, "${job.type} waiting for login: ${e.message}")
                    dao.reschedule(job.id, job.attempts, e.retryAt, e.message)
                } catch (e: SaicCarBlockedException) {
                    if (type.isCommand) carBlocked(e) else retryLater(job, type, e, immediate = false)
                } catch (e: SaicAuthException) {
                    auth.invalidateToken()
                    retryLater(job, type, e, immediate = true)
                } catch (e: SaicIncompleteDataException) {
                    // Partial data is already saved. Try a few more times, then settle for it quietly.
                    if (job.attempts + 1 >= INCOMPLETE_MAX_ATTEMPTS) {
                        Log.w(TAG, "Giving up on missing data: ${e.message}")
                        dao.delete(job.id)
                    } else {
                        retryLater(job, type, e, immediate = false)
                    }
                } catch (e: SaicNetworkException) {
                    when {
                        e.phoneOffline -> phoneOffline(job, e)
                        e.serverDown -> serverDown(job, type, e)
                        else -> retryLater(job, type, e, immediate = false)
                    }
                } catch (e: SaicApiException) {
                    if (type.isCommand && e.code in SaicApi.FINAL_CODES) {
                        // MG's final word, e.g. "Vehicle not locked. Please lock it and try again.(2)":
                        // sending it again won't help, so tell the user instead.
                        Log.w(TAG, "Dropping ${job.type}: ${e.message}")
                        dao.delete(job.id)
                        _lastDropped.value = e.message
                    } else {
                        retryLater(job, type, e, immediate = false)
                    }
                } catch (e: Exception) {
                    retryLater(job, type, e, immediate = false)
                }
            }
        } finally {
            mutex.unlock()
            scheduleNext()
        }
    }

    private suspend fun retryLater(job: ApiJob, type: JobType, e: Exception, immediate: Boolean) {
        val attempts = job.attempts + 1
        Log.w(TAG, "${job.type} failed (attempt $attempts): ${e::class.simpleName}: ${e.message}")
        if (e.message == null) Log.w(TAG, "  details", e)
        if (attempts >= type.maxAttempts) {
            drop(job, type, e.message)
            return
        }
        val backoff = if (immediate) 1_000L else min(5_000L shl min(job.attempts, 6), 5 * 60_000L)
        dao.reschedule(job.id, attempts, System.currentTimeMillis() + backoff, e.message)
    }

    /**
     * MG's servers aren't answering. That's not this job's fault, so status refreshes keep
     * trying (about once a minute, until they expire) instead of using up their attempts.
     * Commands still give up after their normal number of tries.
     */
    private suspend fun serverDown(job: ApiJob, type: JobType, e: Exception) {
        if (type != JobType.REFRESH_STATUS) return retryLater(job, type, e, immediate = false)
        Log.w(TAG, "${job.type}: MG servers not responding (${e.message})")
        val backoff = min(5_000L shl min(job.attempts, 4), SERVER_DOWN_RETRY_MS)
        dao.reschedule(job.id, job.attempts + 1, System.currentTimeMillis() + backoff, e.message)
    }

    /**
     * The phone has no internet, so nothing reached MG or the car: keep the job without using up an
     * attempt (above all a "turn off" after Stop) and try again soon. Ends when the job gets too old.
     * Wi-Fi without internet doesn't always tell Android when it's back, so don't rely on that alone.
     */
    private suspend fun phoneOffline(job: ApiJob, e: Exception) {
        Log.w(TAG, "${job.type}: phone offline, waiting (${e.message})")
        val waited = System.currentTimeMillis() - job.createdAt
        val backoff = (waited / 4).coerceIn(5_000L, OFFLINE_RETRY_MS)
        dao.reschedule(job.id, job.attempts, System.currentTimeMillis() + backoff, e.message)
    }

    /** The car refuses remote commands for now: drop every waiting command and tell the user what to do. */
    private suspend fun carBlocked(e: SaicCarBlockedException) {
        Log.w(TAG, "Car blocks remote commands, dropping all commands: ${e.message}")
        JobType.entries.filter { it.isCommand }.forEach { dao.deleteByKey(it.name) }
        // Off the queue's lock: the controller may enqueue a status refresh.
        scope.launch { runCatching { onCarBlocked() } }
        _lastDropped.value = carBlockedNotice(e.message)
    }

    private suspend fun drop(job: ApiJob, type: JobType?, reason: String?) {
        Log.w(TAG, "Dropping ${job.type}: $reason")
        dao.delete(job.id)
        _lastDropped.value = when (type) {
            null, JobType.REFRESH_STATUS -> return
            JobType.CHARGE_RESTORE -> "Couldn't set the charge limit back – check it in the car"
            else -> "Couldn't reach the car"
        }
    }

    private suspend fun scheduleNext() {
        val next = dao.nextRunAt() ?: return
        val wait = (next - System.currentTimeMillis()).coerceAtLeast(0)
        timer?.cancel()
        timer = scope.launch {
            delay(wait)
            drain()
        }
        // Survives the app being closed.
        WorkManager.getInstance(context).enqueueUniqueWork(
            QueueWorker.NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<QueueWorker>()
                .setInitialDelay(wait + 2_000, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    private suspend fun execute(type: JobType, p: JSONObject) {
        if (auth.isDemo()) {
            // Guest mode: the pretend car answers, nothing leaves the phone.
            demo.execute(type, p)?.let { afterRefresh(it) }
            return
        }
        val api = auth.authorizedApi()
        if (type == JobType.REFRESH_STATUS) {
            afterRefresh(vehicle.refresh(api))
            return
        }
        val vin = vehicle.ensureVin(api)
        val command = when (type) {
            JobType.CLIMATE -> if (p.optBoolean("stop")) SaicCommands.stopClimate()
            else SaicCommands.climate(
                fanSpeed = p.getInt("fan"),
                acOn = if (p.has("ac")) p.getBoolean("ac") else null,
                temperatureIdx = p.getInt("tempIdx"),
            )
            JobType.SEATS -> SaicCommands.heatedSeats(p.getInt("left"), p.getInt("right"))
            JobType.STEERING_WHEEL -> SaicCommands.steeringWheel(p.getBoolean("on"))
            JobType.REAR_WINDOW -> SaicCommands.rearWindow(p.getBoolean("on"))
            JobType.CHARGE_RESTORE -> return chargeAssist.restore(api, vin)
            JobType.REFRESH_STATUS -> error("unreachable")
        }
        if (type == JobType.CLIMATE && !p.optBoolean("stop")) {
            startClimate(api, vin, command, allowCharge = p.optBoolean("chargeAssist", true))
        } else {
            api.control(vin, command)
        }
    }

    /**
     * Heating/A/C start. If the car refuses it while plugged in but not charging (a known MG4
     * fault), start a charge so the car powers the A/C, then send it once more – if the profile allows it.
     */
    private suspend fun startClimate(
        api: SaicApi,
        vin: String,
        command: dk.Android.Cpmonster.mgmanager.saic.ControlCommand,
        allowCharge: Boolean,
    ) {
        try {
            api.control(vin, command)
        } catch (e: SaicApiException) {
            if (!allowCharge || !chargeAssist.worthTrying(e)) throw e
            if (!chargeAssist.prepare(api, vin)) {
                // Tried and it didn't work out: put back what we changed.
                if (chargeAssist.hasChanges()) enqueueRestore()
                throw e
            }
            Log.i(TAG, "Car is charging now, sending the A/C again")
            api.control(vin, command)
        }
    }

    /** Queue putting the car's charging back as it was (no-op if nothing was changed). */
    suspend fun enqueueRestore() {
        if (!chargeAssist.hasChanges()) return
        val now = System.currentTimeMillis()
        // Not through enqueue(): that would clear a "couldn't reach the car" notice.
        dao.replace(
            ApiJob(
                type = JobType.CHARGE_RESTORE.name, payload = "{}", dedupeKey = JobType.CHARGE_RESTORE.name,
                createdAt = now, nextRunAt = now,
            ),
        )
        kick()
    }

    companion object {
        private const val TAG = "CommandQueue"
        const val CAR_BLOCKED_NOTICE =
            "The car is refusing remote commands (too many in a short time). " +
                "Start it with the key, lock it and turn it off, then try again."

        /**
         * MG uses code 8 for more than one reason ("Too frequent operations…(71)",
         * "Vehicle is powered on…(1)"). Our wording for the lockout, MG's own otherwise.
         */
        fun carBlockedNotice(mgMessage: String?): String = when {
            mgMessage.isNullOrBlank() -> "The car is refusing remote commands right now."
            mgMessage.contains("frequent", ignoreCase = true) -> CAR_BLOCKED_NOTICE
            else -> mgMessage
        }
        /** ~8 tries over roughly 10 minutes (5 s, 10 s, 20 s … backoff). */
        private const val INCOMPLETE_MAX_ATTEMPTS = 8
        /** How often we knock on MG's door while their servers are down. */
        private const val SERVER_DOWN_RETRY_MS = 60_000L
        /** Longest wait between tries while the phone has no internet. */
        private const val OFFLINE_RETRY_MS = 30_000L
    }
}
