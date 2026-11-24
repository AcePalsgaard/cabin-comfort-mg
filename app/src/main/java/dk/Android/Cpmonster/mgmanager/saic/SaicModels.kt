package dk.Android.Cpmonster.mgmanager.saic

enum class SaicRegion(val label: String, val baseUri: String, val region: String, val tenantId: String = "459771") {
    EU("Europe", "https://gateway-mg-eu.soimt.com/api.app/v1/", "eu"),
    AU("Australia / NZ", "https://gateway-mg-au.soimt.com/api.app/v1/", "au"),
    TR("Türkiye", "https://gateway-mg-tr.soimt.com/api.app/v1/", "tr"),
    BR("Brazil", "https://gateway-mg-br.soimt.com/api.app/v1/", "br"),

    // DEV-ONLY: hidden and ignored in release builds (see DevOnly).
    /** Fake server from tools/mock-server, running on the PC. 10.0.2.2 = the PC seen from the emulator. */
    TEST("Test server (PC)", "http://10.0.2.2:8080/api.app/v1/", "eu");

    companion object {
        /** Servers the user may pick. DEV-ONLY: TEST is left out of release builds. */
        val selectable: List<SaicRegion>
            get() = entries.filter { it != TEST || dk.Android.Cpmonster.mgmanager.DevOnly.ENABLED }

        /** A stored TEST choice falls back to EU in release builds. */
        fun from(name: String?) = selectable.firstOrNull { it.name == name } ?: EU
    }
}

/** Anything the server or network threw at us. Retryable by default – the API fails a lot. */
open class SaicApiException(message: String, val code: Int = -1, cause: Throwable? = null) : Exception(message, cause)
/** [serverDown] is false when the server answered but the car didn't (not the server's fault). */
class SaicNetworkException(
    message: String,
    cause: Throwable? = null,
    val serverDown: Boolean = true,
) : SaicApiException(message, -1, cause)
class SaicAuthException(message: String) : SaicApiException(message, 401)
class SaicLoginRejectedException(message: String) : SaicApiException(message)
/** The car answered, but key data (temperatures, battery) was missing. Retried a limited number of times. */
class SaicIncompleteDataException(message: String) : SaicApiException(message)
class NeedsLoginException : Exception("Login required")

data class LoginResult(val accessToken: String, val expiresInSec: Long, val userName: String?)

data class VehicleInfo(
    val vin: String,
    val modelName: String?,
    val modelYear: String?,
    val series: String?,
    val colorName: String? = null,
    val isCurrent: Boolean,
    /** Raw `HeatedSeat` value from vehicleModelConfiguration, if the car reported one. */
    val heatedSeat: String? = null,
)

enum class SeatHeating {
    /** Car didn't say – send levels as-is. */
    UNKNOWN, NONE, ON_OFF, LEVELS;

    companion object {
        /** Same rule as saic-python-mqtt-gateway: "1" = levels, "2" = on/off. */
        fun fromConfig(value: String?): SeatHeating = when (value?.trim()?.uppercase()) {
            null, "" -> UNKNOWN
            "1" -> LEVELS
            "2" -> ON_OFF
            else -> NONE
        }

        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}

/** What the car supports, detected from series + model configuration (rules from saic-python-mqtt-gateway). */
data class CarFeatures(val series: String?, val seatHeating: SeatHeating = SeatHeating.UNKNOWN) {
    private val s = series?.trim()?.uppercase().orEmpty()
    private val isMg4 = s.startsWith("EH32")

    val minTemp: Int get() = if (isMg4) 17 else 16
    val maxTemp: Int get() = if (isMg4 || s.startsWith("MZS3E")) 31 else 28
    val hasSeatHeating: Boolean get() = seatHeating != SeatHeating.NONE

    /** Celsius -> index the car expects. */
    fun temperatureIdx(celsius: Int): Int = (if (isMg4) 3 else 2) + celsius.coerceIn(minTemp, maxTemp) - minTemp

    /** Profile level (0–3) -> what this car accepts. */
    fun seatLevel(level: Int): Int = when (seatHeating) {
        SeatHeating.NONE -> 0
        SeatHeating.ON_OFF -> if (level > 0) 1 else 0
        else -> level.coerceIn(0, 3)
    }
}

data class VehicleStatus(
    val exteriorTemp: Int?,
    val interiorTemp: Int?,
    val remoteClimateStatus: Int?,
)

class RvcParam(val id: Int, val value: ByteArray)

data class ControlCommand(val reqType: String, val params: List<RvcParam>)

/** Port of api/vehicle/climate/__init__.py command builders. */
object SaicCommands {
    private const val FAN_SPEED = 19
    private const val TEMPERATURE = 20
    private const val AC_ON_OFF = 22
    private const val REAR_WINDOW_HEAT = 23
    private const val STEERING_WHEEL_HEAT = 24
    private const val SEAT_FRONT_LEFT = 17
    private const val SEAT_FRONT_RIGHT = 18
    private const val PARAMS_MAX = 0xFF

    private val END = RvcParam(PARAMS_MAX, byteArrayOf(0, 0, 0, 0))
    private fun b(v: Int) = byteArrayOf(v.toByte())

    fun climate(fanSpeed: Int, acOn: Boolean?, temperatureIdx: Int): ControlCommand {
        var ac = acOn
        var temp = temperatureIdx
        if (fanSpeed == 0) {
            ac = false
            temp = 8
        }
        val p = mutableListOf(RvcParam(FAN_SPEED, b(fanSpeed)))
        if (fanSpeed > 0 || temp == 0) p += RvcParam(TEMPERATURE, b(temp))
        if (ac != null) p += RvcParam(AC_ON_OFF, b(if (ac) 1 else 0))
        p += END
        return ControlCommand("6", p)
    }

    fun stopClimate() = climate(fanSpeed = 0, acOn = false, temperatureIdx = 0)

    fun heatedSeats(left: Int, right: Int) = ControlCommand(
        "5",
        listOf(RvcParam(SEAT_FRONT_LEFT, b(left.coerceIn(0, 3))), RvcParam(SEAT_FRONT_RIGHT, b(right.coerceIn(0, 3))), END),
    )

    fun steeringWheel(on: Boolean) = ControlCommand("8", listOf(RvcParam(STEERING_WHEEL_HEAT, b(if (on) 1 else 0)), END))

    fun rearWindow(on: Boolean) = ControlCommand("32", listOf(RvcParam(REAR_WINDOW_HEAT, b(if (on) 1 else 0)), END))

}
