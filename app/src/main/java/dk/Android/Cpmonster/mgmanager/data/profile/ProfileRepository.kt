package dk.Android.Cpmonster.mgmanager.data.profile

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dk.Android.Cpmonster.mgmanager.data.db.Profile
import dk.Android.Cpmonster.mgmanager.data.db.ProfileDao
import dk.Android.Cpmonster.mgmanager.data.db.ProfileKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.profileStore by preferencesDataStore(name = "profile_state")

/**
 * A running climate session. The car stops climate by itself after [RUN_MS], so a session
 * is made of [runs] back-to-back runs; [run] is the current one (1-based).
 */
data class ActiveProfile(
    val id: Long,
    val activatedAt: Long,
    val runs: Int = 1,
    val run: Int = 1,
    val runStartedAt: Long = activatedAt,
) {
    val runEndsAt: Long get() = runStartedAt + RUN_MS
    val sessionEndsAt: Long get() = runStartedAt + (runs - run + 1) * RUN_MS
    val isLastRun: Boolean get() = run >= runs

    companion object {
        const val RUN_MINUTES = 10
        const val RUN_MS = RUN_MINUTES * 60_000L
    }
}

class ProfileRepository(context: Context, private val dao: ProfileDao) {
    private val store = context.applicationContext.profileStore
    private val ACTIVE_ID = longPreferencesKey("active_id")
    private val ACTIVATED_AT = longPreferencesKey("activated_at")
    private val RUNS = intPreferencesKey("runs")
    private val RUN = intPreferencesKey("run")
    private val RUN_STARTED_AT = longPreferencesKey("run_started_at")

    val profiles: Flow<List<Profile>> = dao.all()

    val active: Flow<ActiveProfile?> = store.data.map { p ->
        p[ACTIVE_ID]?.let {
            val at = p[ACTIVATED_AT] ?: 0L
            ActiveProfile(it, at, p[RUNS] ?: 1, p[RUN] ?: 1, p[RUN_STARTED_AT] ?: at)
        }
    }

    suspend fun activeNow(): ActiveProfile? = active.first()

    suspend fun get(id: Long) = dao.get(id)

    suspend fun save(profile: Profile) {
        dao.upsert(profile)
    }

    suspend fun setActive(id: Long, runs: Int) = store.edit {
        val now = System.currentTimeMillis()
        it[ACTIVE_ID] = id
        it[ACTIVATED_AT] = now
        it[RUNS] = runs.coerceIn(1, 4)
        it[RUN] = 1
        it[RUN_STARTED_AT] = now
    }

    /** Starts the next run of the current session. */
    suspend fun nextRun() = store.edit {
        it[RUN] = (it[RUN] ?: 1) + 1
        it[RUN_STARTED_AT] = System.currentTimeMillis()
    }

    /** Changes the planned number of runs, never below the run in progress. */
    suspend fun setRuns(runs: Int) = store.edit {
        it[RUNS] = maxOf(runs, it[RUN] ?: 1)
    }

    suspend fun addRun() = store.edit {
        it[RUNS] = (it[RUNS] ?: 1) + 1
    }

    suspend fun clearActive() = store.edit {
        it.remove(ACTIVE_ID)
        it.remove(ACTIVATED_AT)
        it.remove(RUNS)
        it.remove(RUN)
        it.remove(RUN_STARTED_AT)
    }

    suspend fun seedDefaults() {
        if (dao.count() > 0) return
        dao.upsert(
            Profile(
                name = "Warm up", kind = ProfileKind.COLD, targetTemp = 24, fanSpeed = 3, frontDefrost = true,
                seatLeft = 3, seatRight = 0, steeringWheel = true, rearWindow = true,
            ),
        )
        dao.upsert(
            Profile(
                name = "Chill", kind = ProfileKind.WARM, targetTemp = 19, fanSpeed = 4, frontDefrost = false,
                seatLeft = 0, seatRight = 0, steeringWheel = false, rearWindow = false,
            ),
        )
    }
}

/** Guesses a profile from the outside temperature. Never activates anything by itself. */
object ProfileSuggester {
    const val COLD_BELOW = 10
    const val WARM_ABOVE = 20

    fun suggest(outsideTemp: Int?, profiles: List<Profile>, activeId: Long?): Profile? {
        val kind = when {
            outsideTemp == null -> return null
            outsideTemp <= COLD_BELOW -> ProfileKind.COLD
            outsideTemp >= WARM_ABOVE -> ProfileKind.WARM
            else -> return null
        }
        val match = profiles.firstOrNull { it.kind == kind } ?: return null
        return if (match.id == activeId) null else match
    }
}
