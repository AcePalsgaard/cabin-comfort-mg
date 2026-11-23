package dk.Android.Cpmonster.mgmanager.demo

import dk.Android.Cpmonster.mgmanager.data.db.VehicleState
import dk.Android.Cpmonster.mgmanager.data.vehicle.VehicleRepository
import dk.Android.Cpmonster.mgmanager.queue.JobType
import dk.Android.Cpmonster.mgmanager.saic.SeatHeating
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.random.Random

/**
 * Guest/demo mode: a pretend car that lives only on the phone.
 * The command queue hands jobs here instead of to the SAIC API, so nothing is ever sent.
 */
class DemoCar(private val vehicle: VehicleRepository) {

    /** Celsius the (pretend) climate is heading for; null = off. */
    @Volatile private var target: Int? = null

    suspend fun execute(type: JobType, p: JSONObject): VehicleState? {
        delay(Random.nextLong(600, 1_400)) // feel like a real round-trip
        return when (type) {
            JobType.REFRESH_STATUS -> refresh()
            JobType.CLIMATE -> {
                target = if (p.optBoolean("stop")) null else celsiusFromIdx(p.optInt("tempIdx"))
                val st = current()
                vehicle.save(st.copy(remoteClimateStatus = if (target == null) 0 else 2))
                null
            }
            else -> null // seats, steering wheel, rear window: nothing to show
        }
    }

    private suspend fun refresh(): VehicleState {
        val st = current()
        val outside = st.exteriorTemp ?: OUTSIDE
        val inside = st.interiorTemp ?: (outside + 2)
        val t = target
        val newInside = when {
            t == null -> inside + (outside + 2 - inside).coerceIn(-1, 1)
            else -> inside + (t - inside).coerceIn(-4, 4)
        }
        return st.copy(
            exteriorTemp = outside,
            interiorTemp = newInside,
            remoteClimateStatus = if (t == null) 0 else 2,
            updatedAt = System.currentTimeMillis(),
        ).also { vehicle.save(it) }
    }

    private suspend fun current(): VehicleState = vehicle.current()?.takeIf { it.vin == VIN } ?: VehicleState(
        vin = VIN,
        model = "MG4 Electric (demo)",
        modelYear = "2024",
        series = "EH32 S",
        colorName = "Holborn Blue",
        seatHeating = SeatHeating.LEVELS.name,
        exteriorTemp = OUTSIDE,
        interiorTemp = OUTSIDE + 2,
        batterySoc = 78.0,
        remoteClimateStatus = 0,
    )

    /** Inverse of CarFeatures.temperatureIdx for an MG4. */
    private fun celsiusFromIdx(idx: Int) = idx - 3 + 17

    fun reset() { target = null }

    companion object {
        const val VIN = "DEMO0000000000000"
        /** Chilly, so the app suggests the cold profile straight away. */
        const val OUTSIDE = 4
    }
}
