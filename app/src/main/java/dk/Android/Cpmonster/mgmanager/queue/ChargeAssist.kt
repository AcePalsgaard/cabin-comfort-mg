package dk.Android.Cpmonster.mgmanager.queue

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import dk.Android.Cpmonster.mgmanager.saic.SaicApi
import dk.Android.Cpmonster.mgmanager.saic.SaicAuthException
import dk.Android.Cpmonster.mgmanager.saic.SaicCarBlockedException
import dk.Android.Cpmonster.mgmanager.saic.SaicNetworkException
import dk.Android.Cpmonster.mgmanager.saic.TargetSoc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.ceil

/**
 * Many MG4s (mostly older software) refuse remote heating/A/C while plugged in but not charging:
 * the car keeps its main battery switched off then. Charging switches it on, so when the A/C
 * command fails on a plugged-in car we start a charge (raising the charge limit a step if the
 * battery is already at it) and send the A/C again. What we changed is stored and put back
 * when the climate session ends ([JobType.CHARGE_RESTORE]).
 */
class ChargeAssist(private val store: DataStore<Preferences>) {
    private object Keys {
        /** We started a charge that should be stopped again. */
        val STARTED = booleanPreferencesKey("charge_assist_started")
        /** The car's charge-limit code before we raised it. */
        val OLD_TARGET = intPreferencesKey("charge_assist_old_target")
    }

    /** Last time we looked at the charge state for this – not again for a while, every look wakes the car. */
    @Volatile
    private var lastCheckedAt = 0L

    /** Could a failed A/C start be the plugged-in-not-charging problem? (The profile decides whether to try.) */
    fun worthTrying(e: Exception): Boolean = when {
        e is SaicAuthException -> false
        // No internet on the phone: nothing to learn. A flaky MG server is worth one look –
        // a refused command is retried until it times out, and a hiccup then looks like "server down".
        e is SaicNetworkException && e.phoneOffline -> false
        // A real MG4, plugged in and not charging, answered code 8 "Request timed out.(40)": always worth
        // a look, also soon after the last one (the next run of a session, a second tap).
        mayBePluggedInRefusal(e) -> true
        // "Too frequent operations (71)" or "Vehicle is powered on": more commands won't help.
        e is SaicCarBlockedException -> false
        System.currentTimeMillis() - lastCheckedAt < RECHECK_GAP_MS -> false
        else -> true
    }

    /**
     * Start a charge if the car is plugged in but not charging. True when the car is charging
     * now, so the A/C is worth sending again. Never throws (except cancellation): on any problem
     * the caller just carries on with the original error. [onStep] tells the user what's going on.
     */
    suspend fun prepare(api: SaicApi, vin: String, onStep: (String) -> Unit = {}): Boolean = try {
        onStep("The car refused – checking the charging cable…")
        val st = api.chargeState(vin)
        lastCheckedAt = System.currentTimeMillis()
        Log.i(TAG, "A/C failed – charge state: plugged=${st.plugged} charging=${st.charging} soc=${st.soc} target=${st.targetPercent}")
        when {
            !st.plugged || st.charging -> false
            // Full: the car won't start a charge, nothing we can do.
            (st.soc ?: 0.0) >= FULL_SOC -> false
            else -> startCharge(api, vin, st.soc, st.targetPercent, st.targetCode, onStep)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't start a charge for the A/C: ${e.message}")
        false
    }

    private suspend fun startCharge(
        api: SaicApi, vin: String, soc: Double?, target: Int?, targetCode: Int?, onStep: (String) -> Unit,
    ): Boolean {
        onStep("Car is plugged in – starting a charge so it can heat…")
        if (soc != null && target != null && soc >= target - 1) {
            // At the limit: the car won't charge. One step above where the battery is now.
            val pct = (ceil((soc + 2) / 10) * 10).toInt().coerceIn(40, 100)
            store.edit { if (it[Keys.OLD_TARGET] == null && targetCode != null) it[Keys.OLD_TARGET] = targetCode }
            Log.i(TAG, "Raising charge limit $target% -> $pct% so a charge can start")
            api.setTargetSoc(vin, TargetSoc.codeFor(pct))
        }
        store.edit { it[Keys.STARTED] = true }
        Log.i(TAG, "Starting a charge so the car powers the A/C")
        api.controlCharging(vin, start = true)
        onStep("Waiting for the car to start charging…")
        // Each look takes the car a few seconds to answer, so look again right away rather than
        // sleeping first. Gives up after about a minute.
        val until = System.currentTimeMillis() + CHARGE_WAIT_MS
        while (System.currentTimeMillis() < until) {
            if (runCatching { api.chargeState(vin).charging }.getOrDefault(false)) return true
            delay(CHARGE_POLL_MS)
        }
        Log.w(TAG, "Car didn't start charging (charger not supplying power?)")
        return false
    }

    /** Something to put back after the session. */
    suspend fun hasChanges(): Boolean = store.data.first().let { it[Keys.STARTED] == true || it[Keys.OLD_TARGET] != null }

    /** Put the charge limit back and stop the charge we started. Throws so the queue retries. */
    suspend fun restore(api: SaicApi, vin: String) {
        val prefs = store.data.first()
        prefs[Keys.OLD_TARGET]?.let { code ->
            Log.i(TAG, "Restoring charge limit to ${TargetSoc.percent(code)}%")
            api.setTargetSoc(vin, code)
            store.edit { it.remove(Keys.OLD_TARGET) }
        }
        if (prefs[Keys.STARTED] == true) {
            Log.i(TAG, "Stopping the charge we started")
            api.controlCharging(vin, start = false)
            store.edit { it.remove(Keys.STARTED) }
        }
    }

    companion object {
        private const val TAG = "ChargeAssist"
        private const val RECHECK_GAP_MS = 20 * 60_000L
        private const val FULL_SOC = 99.0
        private const val CHARGE_WAIT_MS = 60_000L
        private const val CHARGE_POLL_MS = 2_000L

        /** Reason 40 is what the car gave when the A/C was refused while plugged in and not charging. */
        fun mayBePluggedInRefusal(e: Exception): Boolean = SaicApi.reasonCode(e.message) == 40
    }
}
