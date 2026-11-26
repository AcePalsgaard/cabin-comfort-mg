package dk.Android.Cpmonster.mgmanager.saic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Base64

/**
 * Minimal Kotlin port of saic-python-client-ng: login, vehicle list, status,
 * battery SoC and remote control commands.
 */
class SaicApi(
    private val http: OkHttpClient,
    private val region: SaicRegion,
) {
    @Volatile
    var userToken: String = ""

    // ---------- Public calls ----------

    suspend fun login(username: String, passwordSha1: String, isEmail: Boolean, countryCode: String?): LoginResult {
        val deviceId = "simulator" + "*".repeat(45) + (System.currentTimeMillis() / 1000) + "###com.saicmotor.europecar"
        val form = linkedMapOf(
            "grant_type" to "password",
            "username" to username,
            "password" to passwordSha1,
            "scope" to "all",
            "deviceId" to deviceId,
            "deviceType" to "0",
            "language" to "EN",
        )
        if (isEmail) {
            form["loginType"] = "2"
        } else {
            form["loginType"] = "1"
            form["countryCode"] = countryCode?.trim()?.removePrefix("+")
                ?: throw SaicLoginRejectedException("Country code is required for phone login")
        }
        val body = form.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }
        val data = try {
            unwrap(
                call(
                    method = "POST",
                    path = "oauth/token",
                    body = body,
                    contentType = FORM,
                    headers = mapOf("Authorization" to "Basic c3dvcmQ6c3dvcmRfc2VjcmV0"),
                    token = "",
                ),
                requestEventId = null,
            )
        } catch (e: SaicNetworkException) {
            throw e
        } catch (e: SaicAuthException) {
            // HTTP 401/403 on the login itself: wrong password, or MG blocking the account for a while.
            throw SaicLoginRejectedException("MG didn't accept the login (${e.message})")
        } catch (e: SaicApiException) {
            throw SaicLoginRejectedException(e.message ?: "Login failed")
        }
        val token = data?.optString("access_token").orEmpty()
        val expires = data?.optLong("expires_in", 0L) ?: 0L
        if (token.isEmpty() || expires <= 0) throw SaicLoginRejectedException("Login failed – check your credentials")
        userToken = token
        return LoginResult(token, expires, data?.optString("user_name")?.ifBlank { null })
    }

    suspend fun vehicleList(): List<VehicleInfo> {
        val data = unwrap(call("GET", "vehicle/list"), null) ?: return emptyList()
        val arr = data.optJSONArray("vinList") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val vin = o.optString("vin").ifBlank { return@mapNotNull null }
            val config = CarConfig.parse(o.optJSONArray("vehicleModelConfiguration"))
            if (!configLogged) {
                configLogged = true
                // No VIN in the log. Read with: adb logcat -s SaicApi
                runCatching {
                    android.util.Log.i(
                        "SaicApi",
                        "Vehicle config for ${o.str("modelName")} ${o.str("modelYear")} series ${o.str("series")}: " +
                            CarConfig.describe(config),
                    )
                }
            }
            VehicleInfo(
                vin = vin,
                modelName = o.str("modelName") ?: o.str("name"),
                modelYear = o.str("modelYear"),
                series = o.str("series"),
                colorName = o.str("colorName"),
                isCurrent = o.optBoolean("isCurrentVehicle", false),
                heatedSeat = CarConfig.value(config, "HeatedSeat"),
                config = config,
            )
        }
    }

    /** Last status SAIC has stored – does not wake the car. */
    suspend fun vehicleStatusCached(vin: String): VehicleStatus =
        parseStatus(unwrap(call("GET", "vehicle/status/cache", statusQuery(vin)), null))

    /** Live status – wakes the car, may take a while. */
    suspend fun vehicleStatus(vin: String): VehicleStatus =
        parseStatus(callWithEventId("GET", "vehicle/status", statusQuery(vin)))

    /** Battery state of charge in percent, or null if the car doesn't report it. */
    suspend fun batterySoc(vin: String): Double? = chargeState(vin).soc

    /** Plugged in, charging, battery level and charge limit (one call; wakes the car). */
    suspend fun chargeState(vin: String): ChargeState {
        val data = callWithEventId("GET", "vehicle/charging/mgmtData", listOf("vin" to SaicCrypto.sha256Hex(vin)))
        val m = data?.optJSONObject("chrgMgmtData")
        val rvs = data?.optJSONObject("rvsChargeStatus")
        val raw = m?.optInt("bmsPackSOCDsp", -1) ?: -1
        return ChargeState(
            plugged = (rvs?.optInt("chargingGunState", 0) ?: 0) != 0,
            // Same rule as saic-python-client-ng is_bms_charging.
            charging = (m?.optInt("bmsChrgSts", 0) ?: 0) in CHARGING_STATES,
            soc = if (raw in 0..1000) raw / 10.0 else null,
            targetCode = m?.optInt("bmsOnBdChrgTrgtSOCDspCmd", 0)?.takeIf { it in 1..7 },
        )
    }

    /** Start or stop charging (port of control_charging). */
    suspend fun controlCharging(vin: String, start: Boolean) {
        val body = JSONObject()
            .put("chrgCtrlReq", if (start) 1 else 2)
            .put("tboxEleccLckCtrlReq", 0)
            .put("tboxV2XReq", 0)
            .put("vin", SaicCrypto.sha256Hex(vin))
        callWithEventId("POST", "vehicle/charging/control", body = body.toString(), contentType = JSON, pollDelayMs = 2_000)
    }

    /** Set the charge limit, [code] from [TargetSoc] (port of set_target_battery_soc). */
    suspend fun setTargetSoc(vin: String, code: Int) {
        val body = JSONObject()
            .put("onBdChrgTrgtSOCReq", code)
            .put("altngChrgCrntReq", 0)
            .put("tboxV2XSpSOCReq", 0)
            .put("vin", SaicCrypto.sha256Hex(vin))
        callWithEventId("POST", "vehicle/charging/setting", body = body.toString(), contentType = JSON, pollDelayMs = 2_000)
    }

    suspend fun control(vin: String, command: ControlCommand) {
        val params = JSONArray()
        command.params.forEach {
            params.put(JSONObject().put("paramId", it.id).put("paramValue", Base64.getEncoder().encodeToString(it.value)))
        }
        val body = JSONObject()
            .put("rvcParams", params)
            .put("rvcReqType", command.reqType)
            .put("vin", SaicCrypto.sha256Hex(vin))
        callWithEventId("POST", "vehicle/control", body = body.toString(), contentType = JSON, pollDelayMs = 2_000)
    }

    // ---------- Plumbing ----------

    private class Envelope(val json: JSONObject, val eventId: String?)
    private class EventPending(val eventId: String) : Exception()

    private fun statusQuery(vin: String) = listOf("vin" to SaicCrypto.sha256Hex(vin), "vehStatusReqType" to "2")

    private fun parseStatus(data: JSONObject?): VehicleStatus {
        val b = data?.optJSONObject("basicVehicleStatus")
        fun temp(key: String): Int? = b?.takeIf { it.has(key) && !it.isNull(key) }?.optInt(key)?.takeIf { it in -60..80 }
        return VehicleStatus(
            exteriorTemp = temp("exteriorTemperature"),
            interiorTemp = temp("interiorTemperature"),
            remoteClimateStatus = b?.takeIf { it.has("remoteClimateStatus") }?.optInt("remoteClimateStatus"),
        )
    }

    /** Port of execute_api_call_with_event_id: repeat with the server's event-id until data arrives. */
    private suspend fun callWithEventId(
        method: String,
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        body: String? = null,
        contentType: String? = null,
        // Same as saic-python-client-ng: a car that hasn't answered in 30 s rarely does.
        timeoutMs: Long = 30_000,
        pollDelayMs: Long = 3_000,
    ): JSONObject? {
        var eventId = "0"
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            try {
                val env = call(method, path, query, body, contentType, mapOf("event-id" to eventId))
                return unwrap(env, eventId)
            } catch (p: EventPending) {
                eventId = p.eventId
                if (System.currentTimeMillis() > deadline) throw SaicNetworkException("The car did not answer in time", serverDown = false, carNoAnswer = true)
                delay(pollDelayMs)
            }
        }
    }

    /** Port of AbstractSaicApi.__deserialize. */
    private fun unwrap(env: Envelope, requestEventId: String?): JSONObject? {
        val code = env.json.optInt("code", -1)
        val msg = env.json.optString("message", "Unknown error")
        if (code == 401 || code == 403) throw SaicAuthException(msg)
        // Final answers: polling the same event again won't change them.
        if (code == CODE_CAR_BLOCKED) {
            // Code 8 covers several reasons. Some pass by themselves: retry those like any failure.
            if (reasonCode(msg) in PASSING_CAR_REASONS) throw SaicApiException(msg, code)
            throw SaicCarBlockedException(msg)
        }
        if (code in FINAL_CODES) throw SaicApiException(msg, code)
        if (env.eventId != null && !env.json.has("data")) throw EventPending(env.eventId)
        if (code != 0) {
            if (requestEventId != null && requestEventId != "0") throw EventPending(requestEventId)
            throw SaicApiException(msg, code)
        }
        return env.json.optJSONObject("data")
    }

    private suspend fun call(
        method: String,
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        body: String? = null,
        contentType: String? = null,
        headers: Map<String, String> = emptyMap(),
        token: String = userToken,
    ): Envelope = withContext(Dispatchers.IO) {
        val qs = if (query.isEmpty()) "" else "?" + query.joinToString("&") { enc(it.first) + "=" + enc(it.second) }
        val requestPath = "/$path$qs"
        val ts = System.currentTimeMillis().toString()
        val ct = when {
            contentType == null -> JSON
            "x-www-form-urlencoded" in contentType -> FORM
            else -> JSON
        }
        val (encBody, verification) = SaicCrypto.encryptRequest(
            requestPath, region.tenantId, token, ts, ct, body.orEmpty(),
        )
        val rb = Request.Builder()
            .url(region.baseUri + path + qs)
            .header("User-Agent", "Europe/2.1.0 (iPad; iOS 18.5; Scale/2.00)")
            .header("Accept", "application/json")
            .header("REGION", region.region)
            .header("APP-SEND-DATE", ts)
            .header("APP-CONTENT-ENCRYPTED", "1")
            .header("tenant-id", region.tenantId)
            .header("User-Type", "app")
            .header("APP-LANGUAGE-TYPE", "en")
            .header("APP-VERIFICATION-STRING", verification)
            .header("ORIGINAL-CONTENT-TYPE", ct)
        if (token.isNotEmpty()) rb.header("blade-auth", token)
        headers.forEach { (k, v) -> rb.header(k, v) }
        if (method == "POST") {
            rb.post(encBody.toRequestBody("$ct;charset=utf-8".toMediaType()))
        } else {
            rb.get().header("Content-Type", "$ct;charset=utf-8")
        }

        val probe = ServerStatus.begin()
        val response = try {
            http.newCall(rb.build()).execute()
        } catch (e: IOException) {
            val offline = isPhoneOffline(e)
            ServerStatus.end(probe, ok = false, offline = offline)
            throw SaicNetworkException("Network error: ${e.message}", e, phoneOffline = offline)
        }
        val envelope = try {
            parse(response)
        } catch (e: SaicNetworkException) {
            ServerStatus.end(probe, ok = false)
            throw e
        } catch (e: Exception) {
            // A real answer (auth error etc.): the server is up.
            ServerStatus.end(probe, ok = true)
            throw e
        }
        ServerStatus.end(probe, ok = true)
        // Every call and MG's answer (no data, no tokens): needed to read field tests in release builds.
        val j = envelope.json
        android.util.Log.i(
            "SaicApi",
            "$method $path event-id=${headers["event-id"]} -> code=${j.opt("code")} " +
                "msg=${j.optString("message")} eventId=${envelope.eventId} data=${j.has("data")}",
        )
        envelope
    }

    /**
     * The request never left the phone: no network, or the name lookup failed
     * (no internet, or a home DNS filter). Timeouts and refused connections are MG's side.
     */
    private fun isPhoneOffline(e: IOException): Boolean =
        generateSequence<Throwable>(e) { it.cause }.take(5).any {
            it is java.net.UnknownHostException ||
                it is java.net.NoRouteToHostException ||
                it.message.orEmpty().let { m -> "ENETUNREACH" in m || "Network is unreachable" in m }
        }

    private fun parse(response: okhttp3.Response): Envelope {
        return response.use { r ->
            val raw = try {
                r.body.string().trim()
            } catch (e: IOException) {
                throw SaicNetworkException("Network error: ${e.message}", e)
            }
            val sendDate = r.header("APP-SEND-DATE")
            val originalCt = r.header("ORIGINAL-CONTENT-TYPE")
            fun decrypted(): String = if (raw.isNotEmpty() && !raw.startsWith("{") && sendDate != null && originalCt != null) {
                SaicCrypto.decryptResponse(raw, sendDate, originalCt)
            } else raw
            if (r.code == 401 || r.code == 403) {
                // Keep MG's own reason when there is one: it's what we show the user.
                val reason = runCatching { JSONObject(decrypted()).optString("message") }.getOrNull()
                throw SaicAuthException(reason?.ifBlank { null } ?: "HTTP ${r.code}")
            }
            if (!r.isSuccessful) throw SaicNetworkException("HTTP ${r.code}")
            val text = try {
                decrypted()
            } catch (e: Exception) {
                throw SaicNetworkException("Could not decrypt response", e)
            }
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw SaicNetworkException("Invalid response", e)
            }
            Envelope(json, r.header("event-id"))
        }
    }

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        /** The vehicle config is logged once per app start. */
        @Volatile private var configLogged = false

        const val JSON = "application/json"
        const val FORM = "application/x-www-form-urlencoded"
        /** "Too frequent operations. Please use the physical key to restart the vehicle…" */
        const val CODE_CAR_BLOCKED = 8
        /**
         * Code 8 reasons that pass by themselves, so they don't block the car. 253 "Please confirm that the
         * vehicle status or the overall vehicle network status is normal": right after the car woke up;
         * the same command a minute later worked (field test 26-11-2026).
         */
        val PASSING_CAR_REASONS = setOf(253)

        /** MG's reason number at the end of a message, e.g. "Request timed out. Please try again later.(40)" -> 40. */
        fun reasonCode(message: String?): Int? =
            message?.let { Regex("""\((\d+)\)\s*$""").find(it)?.groupValues?.get(1)?.toIntOrNull() }

        /** Codes saic-python-client-ng never retries. */
        val FINAL_CODES = setOf(2, 3, 7)
        /** bmsChrgSts values that mean "charging". */
        private val CHARGING_STATES = setOf(1, 3, 10, 12)
    }
}
