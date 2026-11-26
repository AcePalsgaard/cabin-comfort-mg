package dk.Android.Cpmonster.mgmanager.data.vehicle

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dk.Android.Cpmonster.mgmanager.saic.SaicCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * What we've learned from the car's answers, per car (VIN stored as a hash).
 * Steering wheel: many MG4s never confirm the command (MG answers code 4 until we give up),
 * so after one such timeout we stop sending it until the user asks to try again.
 */
class CarMemory(private val store: DataStore<Preferences>) {
    private val key = stringSetPreferencesKey("steering_wheel_unconfirmed")

    /** Hashed VINs of cars that didn't confirm steering wheel heating. */
    val steeringWheelUnconfirmed: Flow<Set<String>> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[key] ?: emptySet() }

    suspend fun setSteeringWheelUnconfirmed(vin: String, unconfirmed: Boolean) {
        store.edit {
            val now = it[key] ?: emptySet()
            it[key] = if (unconfirmed) now + vinKey(vin) else now - vinKey(vin)
        }
    }

    companion object {
        fun vinKey(vin: String): String = SaicCrypto.sha256Hex(vin)
    }
}
