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
import dk.Android.Cpmonster.mgmanager.saic.NeedsLoginException
import dk.Android.Cpmonster.mgmanager.saic.SaicAuthException
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min

enum class JobType(val maxAgeMs: Long, val maxAttempts: Int) {
    REFRESH_STATUS(2 * 60 * 60_000L, 30),
    CLIMATE(20 * 60_000L, 15),
    SEATS(20 * 60_000L, 15),
    STEERING_WHEEL(20 * 60_000L, 15),
    REAR_WINDOW(20 * 60_000L, 15),
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
    private val demo: dk.Android.Cpmonster.mgmanager.demo.DemoCar,
) {
    private val mutex = Mutex()
    private var timer: Job? = null

    val pendingCount: Flow<Int> = dao.countFlow()

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
                    dao.delete(job.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: NeedsLoginException) {
                    // Stored credentials were rejected – the UI shows the login screen. Keep the jobs.
                    return
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
                    if (e.serverDown) serverDown(job, type, e) else retryLater(job, type, e, immediate = false)
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
        Log.w(TAG, "${job.type} failed (attempt $attempts): ${e.message}")
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

    private suspend fun drop(job: ApiJob, type: JobType?, reason: String?) {
        Log.w(TAG, "Dropping ${job.type}: $reason")
        dao.delete(job.id)
        if (type != null && type != JobType.REFRESH_STATUS) _lastDropped.value = "Couldn't reach the car"
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
            JobType.REFRESH_STATUS -> error("unreachable")
        }
        api.control(vin, command)
    }

    companion object {
        private const val TAG = "CommandQueue"
        /** ~8 tries over roughly 10 minutes (5 s, 10 s, 20 s … backoff). */
        private const val INCOMPLETE_MAX_ATTEMPTS = 8
        /** How often we knock on MG's door while their servers are down. */
        private const val SERVER_DOWN_RETRY_MS = 60_000L
    }
}
