package dk.Android.Cpmonster.mgmanager.data.db

import androidx.room.ColumnInfo
import dk.Android.Cpmonster.mgmanager.saic.CarFeatures
import dk.Android.Cpmonster.mgmanager.saic.SeatHeating
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ProfileKind { COLD, WARM }

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: ProfileKind,
    /** Cabin target temperature in °C. */
    val targetTemp: Int,
    /** 1–5 */
    val fanSpeed: Int,
    val frontDefrost: Boolean,
    /** 0 = off, 1–3 = level */
    val seatLeft: Int,
    val seatRight: Int,
    val steeringWheel: Boolean,
    val rearWindow: Boolean,
    /** How many times in a row the climate is started (the car stops it after ~10 min). 1–4. */
    @ColumnInfo(defaultValue = "2") val runs: Int = 2,
    /**
     * Work around cars that refuse heating/A/C while plugged in but not charging:
     * start a charge first (see ChargeAssist).
     */
    @ColumnInfo(defaultValue = "1") val chargeAssist: Boolean = true,
)

@Entity(tableName = "jobs")
data class ApiJob(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val payload: String,
    /** Jobs with the same key replace each other (newest wins). */
    val dedupeKey: String?,
    val createdAt: Long,
    val nextRunAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)

@Entity(tableName = "vehicle_state")
data class VehicleState(
    @PrimaryKey val id: Int = 0,
    val vin: String? = null,
    val model: String? = null,
    val modelYear: String? = null,
    val series: String? = null,
    /** Paint name from the API, e.g. "Holborn Blue". Empty = the car didn't say. */
    val colorName: String? = null,
    /** SeatHeating name; null = not detected yet. */
    val seatHeating: String? = null,
    val exteriorTemp: Int? = null,
    val interiorTemp: Int? = null,
    val batterySoc: Double? = null,
    val remoteClimateStatus: Int? = null,
    val updatedAt: Long? = null,
) {
    val features get() = CarFeatures(series, SeatHeating.from(seatHeating))
}
