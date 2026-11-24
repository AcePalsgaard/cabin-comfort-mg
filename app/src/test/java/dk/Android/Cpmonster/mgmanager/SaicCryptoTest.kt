package dk.Android.Cpmonster.mgmanager

import dk.Android.Cpmonster.mgmanager.saic.SaicCommands
import dk.Android.Cpmonster.mgmanager.saic.CarFeatures
import dk.Android.Cpmonster.mgmanager.saic.SeatHeating
import dk.Android.Cpmonster.mgmanager.saic.SaicCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Vectors generated with saic-python-client-ng (net/crypto.py). */
class SaicCryptoTest {
    private val tenant = "459771"
    private val ts = "1700000000123"

    @Test
    fun loginRequestMatchesPython() {
        val (body, ver) = SaicCrypto.encryptRequest(
            "/oauth/token", tenant, "", ts, "application/x-www-form-urlencoded",
            "grant_type=password&username=a%40b.com",
        )
        assertEquals("4b2d0fb337ebcde93be64fa7b4c81a2b6f2d2873f2edb7d841ab86d5e1aae47cb74571744c942768894276011f8867fd", body)
        assertEquals("5af6624bbab2d300a2dc376129c5fba0913a82d6daea2d41840bca30793511e1", ver)
    }

    @Test
    fun getRequestMatchesPython() {
        val (body, ver) = SaicCrypto.encryptRequest("/vehicle/list", tenant, "tok123", ts, "application/json", "")
        assertEquals("", body)
        assertEquals("a73eb590dcb3a805a9f825eb8d5f3dc75e2b7a11643399d1d3f9fa7432d793aa", ver)
    }

    @Test
    fun postRequestMatchesPython() {
        val (body, ver) = SaicCrypto.encryptRequest("/vehicle/control", tenant, "tok123", ts, "application/json", "{\"a\":1}")
        assertEquals("f26ac842b3987322c850a3d4ff496bda", body)
        assertEquals("d48da6eb12dab4c4940679c3f20165103596daabd137c0b7e81d18cab05ff1ec", ver)
    }

    @Test
    fun responseDecrypts() {
        val plain = SaicCrypto.decryptResponse(
            "5a7918074831d63b5987038e572c12fe2e5b845b93e9cd16bd5b986ee5ceae59", ts, "application/json",
        )
        assertEquals("{\"code\":0,\"data\":{\"x\":1}}", plain)
    }

    @Test
    fun hashes() {
        assertEquals("5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8", SaicCrypto.sha1Hex("password"))
        assertEquals("cf579d28fd5307043b3c3d5c1019995453986e16b9f8e5a6d818c6373d6950b5", SaicCrypto.sha256Hex("LSJA1234567890123"))
    }

    @Test
    fun climateCommandMatchesPython() {
        val c = SaicCommands.climate(fanSpeed = 2, acOn = null, temperatureIdx = 8)
        assertEquals("6", c.reqType)
        assertEquals(listOf(19, 20, 255), c.params.map { it.id })
        assertArrayEquals(byteArrayOf(8), c.params[1].value)

        val stop = SaicCommands.stopClimate()
        // Python stop_ac: fan 0 forces temperature_idx = 8, so no TEMPERATURE param is sent.
        assertEquals(listOf(19, 22, 255), stop.params.map { it.id })
        assertArrayEquals(byteArrayOf(0), stop.params[0].value)
        assertArrayEquals(byteArrayOf(0), stop.params[1].value)
    }

    @Test
    fun temperatureIndex() {
        assertEquals(8, CarFeatures("ZS11").temperatureIdx(22))
        assertEquals(8, CarFeatures("ZP22").temperatureIdx(22))
        assertEquals(8, CarFeatures("EH32 S").temperatureIdx(22)) // MG4: 3 + 22 - 17
        assertEquals(2, CarFeatures(null).temperatureIdx(10))
        assertEquals(17, CarFeatures("EH32").temperatureIdx(40)) // clamped to 31
        assertEquals(17, CarFeatures("MZS3E").temperatureIdx(31))
    }

    @Test
    fun seatHeatingDetection() {
        assertEquals(SeatHeating.LEVELS, SeatHeating.fromConfig("1"))
        assertEquals(SeatHeating.ON_OFF, SeatHeating.fromConfig(" 2 "))
        assertEquals(SeatHeating.NONE, SeatHeating.fromConfig("0"))
        assertEquals(SeatHeating.UNKNOWN, SeatHeating.fromConfig(null))
        assertEquals(1, CarFeatures(null, SeatHeating.ON_OFF).seatLevel(3))
        assertEquals(0, CarFeatures(null, SeatHeating.NONE).seatLevel(3))
        assertEquals(3, CarFeatures(null, SeatHeating.UNKNOWN).seatLevel(3))
    }
}
