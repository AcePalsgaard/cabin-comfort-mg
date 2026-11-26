package dk.Android.Cpmonster.mgmanager.data.vehicle

import dk.Android.Cpmonster.mgmanager.data.db.VehicleDao
import dk.Android.Cpmonster.mgmanager.data.db.VehicleState
import dk.Android.Cpmonster.mgmanager.saic.CarConfig
import dk.Android.Cpmonster.mgmanager.saic.SaicApi
import dk.Android.Cpmonster.mgmanager.saic.SaicApiException
import dk.Android.Cpmonster.mgmanager.saic.SaicAuthException
import dk.Android.Cpmonster.mgmanager.saic.SaicIncompleteDataException
import dk.Android.Cpmonster.mgmanager.saic.SeatHeating
import dk.Android.Cpmonster.mgmanager.saic.CarFeatures
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Cached car type, temperatures and battery. Only ever updated from the background queue. */
class VehicleRepository(private val dao: VehicleDao, val memory: CarMemory) {

    val state: Flow<VehicleState?> = dao.observe()

    /** What the car can do: its configuration plus what we learned from its answers. */
    val features: Flow<CarFeatures> = combine(state, memory.steeringWheelUnconfirmed) { st, silent ->
        featuresOf(st, silent)
    }

    suspend fun current(): VehicleState? = dao.get()

    suspend fun features(): CarFeatures = featuresOf(dao.get(), memory.steeringWheelUnconfirmed.first())

    private fun featuresOf(st: VehicleState?, silent: Set<String>): CarFeatures {
        val f = st?.features ?: CarFeatures(null)
        val vin = st?.vin ?: return f
        return f.copy(steeringWheelUnconfirmed = CarMemory.vinKey(vin) in silent)
    }

    suspend fun clear() = dao.clear()

    /** Demo mode only: the pretend car writes its state directly. */
    suspend fun save(state: VehicleState) = dao.upsert(state)

    suspend fun ensureVin(api: SaicApi): String {
        dao.get()?.vin?.let { return it }
        return loadVehicle(api, dao.get() ?: VehicleState()).also { dao.upsert(it) }.vin!!
    }

    suspend fun refresh(api: SaicApi): VehicleState {
        var st = dao.get() ?: VehicleState()
        if (st.vin == null || st.model == null || st.seatHeating == null || st.colorName == null ||
            st.steeringWheelHeat == null) {
            st = loadVehicle(api, st)
            dao.upsert(st)
        }
        val vin = st.vin!!

        // Cached status first – it doesn't wake the car.
        var status = try {
            api.vehicleStatusCached(vin)
        } catch (e: SaicAuthException) {
            throw e
        } catch (e: SaicApiException) {
            null
        }
        // Cache missing or without temperatures: ask the car itself.
        if (status == null || status.exteriorTemp == null || status.interiorTemp == null) {
            status = try {
                api.vehicleStatus(vin)
            } catch (e: SaicAuthException) {
                throw e
            } catch (e: SaicApiException) {
                // Keep what the cache gave us, but let the queue retry.
                status ?: throw e
            }
        }
        // Save temperatures first, so they show even if the battery call fails.
        st = st.copy(
            exteriorTemp = status.exteriorTemp ?: st.exteriorTemp,
            interiorTemp = status.interiorTemp ?: st.interiorTemp,
            remoteClimateStatus = status.remoteClimateStatus,
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsert(st)
        // A failure here makes the queue retry the whole refresh, until we have a battery level.
        val soc = api.batterySoc(vin)
        st = st.copy(batterySoc = soc ?: st.batterySoc)
        dao.upsert(st)

        // Fresh values missing: retry (the queue limits how often).
        val missing = mutableListOf<String>()
        if (status.exteriorTemp == null) missing += "outside temperature"
        if (status.interiorTemp == null) missing += "inside temperature"
        if (soc == null) missing += "battery"
        if (missing.isNotEmpty()) throw SaicIncompleteDataException("Car data incomplete: " + missing.joinToString())
        return st
    }

    private suspend fun loadVehicle(api: SaicApi, st: VehicleState): VehicleState {
        val list = api.vehicleList()
        val v = list.firstOrNull { it.isCurrent } ?: list.firstOrNull()
            ?: throw SaicApiException("No vehicle on this account")
        return st.copy(
            vin = v.vin, model = v.modelName, modelYear = v.modelYear, series = v.series,
            seatHeating = SeatHeating.fromConfig(v.heatedSeat).name,
            steeringWheelHeat = CarConfig.steeringWheelHeat(v.config).name,
            colorName = v.colorName ?: "",
        )
    }
}
