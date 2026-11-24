package dk.Android.Cpmonster.mgmanager.data.auth

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import dk.Android.Cpmonster.mgmanager.saic.LoginThrottledException
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dk.Android.Cpmonster.mgmanager.data.model.AuthSession
import dk.Android.Cpmonster.mgmanager.data.repository.SettingsRepository
import dk.Android.Cpmonster.mgmanager.saic.LoginResult
import dk.Android.Cpmonster.mgmanager.saic.NeedsLoginException
import dk.Android.Cpmonster.mgmanager.saic.SaicApi
import dk.Android.Cpmonster.mgmanager.saic.SaicCrypto
import dk.Android.Cpmonster.mgmanager.saic.SaicLoginRejectedException
import dk.Android.Cpmonster.mgmanager.saic.SaicNetworkException
import dk.Android.Cpmonster.mgmanager.saic.SaicRegion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

private val Context.authStore by preferencesDataStore(name = "auth")

data class AuthState(
    val username: String? = null,
    val isEmail: Boolean = true,
    val countryCode: String? = null,
    val region: SaicRegion = SaicRegion.EU,
    val hasCredentials: Boolean = false,
    val needsLogin: Boolean = false,
    /** Guest/demo mode: no account, nothing is ever sent to MG. */
    val isDemo: Boolean = false,
    /**
     * Logged in while MG's servers were down: the password hasn't been checked yet.
     * The background queue checks it as soon as the servers answer.
     */
    val unverified: Boolean = false,
    /** What MG said when it last rejected the stored login in the background. */
    val rejectReason: String? = null,
) {
    val isLoggedIn get() = isDemo || (hasCredentials && !needsLogin)
}

/** Rules for background logins: how often to try, and when to give up on the stored login. */
object LoginRejection {
    /** MG must still reject the login this long after the first "no". */
    const val CONFIRM_AFTER_MS = 60_000L
    /** Never log in more often than this in the background, even after a success. */
    const val MIN_GAP_MS = 30_000L
    /** Longest wait between background logins while MG keeps saying no. */
    const val MAX_GAP_MS = 10 * 60_000L

    fun shouldLogOut(firstRejectedAt: Long?, now: Long): Boolean =
        firstRejectedAt != null && now - firstRejectedAt >= CONFIRM_AFTER_MS

    /**
     * Earliest time the background may log in again. Logging in over and over can make MG
     * block the account, so the gap doubles with each rejection (30 s, 1 min, 2 min … 10 min).
     */
    fun nextLoginAt(lastAttemptAt: Long?, rejections: Int): Long {
        if (lastAttemptAt == null) return 0L
        val gap = if (rejections <= 1) MIN_GAP_MS
        else minOf(MIN_GAP_MS shl minOf(rejections - 1, 10), MAX_GAP_MS)
        return lastAttemptAt + gap
    }
}

/**
 * Owns credentials and the API token. Only the SHA-1 of the password is stored
 * (that is what the SAIC API wants), encrypted with a Keystore key.
 * The token is refreshed silently; the user is only asked again if the
 * server rejects the stored credentials.
 */
class AuthRepository(
    context: Context,
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
) {
    private val store = context.applicationContext.authStore
    private val cipher = KeystoreCipher("mgmanager_credentials")
    private val lock = Mutex()

    @Volatile private var cachedApi: SaicApi? = null
    @Volatile private var cachedExpiry = 0L

    private object K {
        val USERNAME = stringPreferencesKey("username")
        val IS_EMAIL = booleanPreferencesKey("is_email")
        val COUNTRY = stringPreferencesKey("country_code")
        val REGION = stringPreferencesKey("region")
        val PASSWORD = stringPreferencesKey("password_sha1_enc")
        val TOKEN = stringPreferencesKey("token_enc")
        val TOKEN_EXPIRY = longPreferencesKey("token_expiry")
        val NEEDS_LOGIN = booleanPreferencesKey("needs_login")
        val DEMO = booleanPreferencesKey("demo")
        val UNVERIFIED = booleanPreferencesKey("unverified")
        /** When MG first rejected the stored login in the background (cleared on success). */
        val REJECTED_SINCE = longPreferencesKey("rejected_since")
        /** Rejections in a row, and MG's last reason (cleared on success). */
        val REJECT_COUNT = intPreferencesKey("reject_count")
        val REJECT_REASON = stringPreferencesKey("reject_reason")
        /** Last time we asked MG for a token, user or background. */
        val LAST_LOGIN_AT = longPreferencesKey("last_login_at")
    }

    val state: Flow<AuthState> = store.data.map { it.toState() }

    suspend fun isLoggedIn() = state.first().isLoggedIn

    suspend fun isDemo() = state.first().isDemo

    /** Enter guest mode. Logging out leaves it again. */
    suspend fun startDemo() = lock.withLock {
        cachedApi = null
        cachedExpiry = 0L
        store.edit {
            it[K.DEMO] = true
            it[K.NEEDS_LOGIN] = false
        }
    }

    private fun Preferences.toState() = AuthState(
        username = this[K.USERNAME],
        isEmail = this[K.IS_EMAIL] ?: true,
        countryCode = this[K.COUNTRY],
        region = SaicRegion.from(this[K.REGION]),
        hasCredentials = this[K.USERNAME] != null && this[K.PASSWORD] != null,
        needsLogin = this[K.NEEDS_LOGIN] ?: false,
        isDemo = this[K.DEMO] ?: false,
        unverified = this[K.UNVERIFIED] ?: false,
        rejectReason = this[K.REJECT_REASON],
    )

    /**
     * User-initiated login. Retries network hiccups quietly, throws on bad credentials.
     * If MG's servers don't answer at all, the login is accepted for now (returns false) so the
     * user can use the app; the queue checks the password when the servers are back.
     */
    suspend fun login(username: String, password: String, isEmail: Boolean, countryCode: String?): Boolean {
        val region = state.first().region
        val hash = SaicCrypto.sha1Hex(password)
        val api = SaicApi(http, region)
        val result = try {
            retryNetwork(attempts = 2) { api.login(username.trim(), hash, isEmail, countryCode) }
        } catch (e: SaicNetworkException) {
            // No internet on the phone says nothing about MG: don't let an unchecked password in.
            if (!e.serverDown || e.phoneOffline) throw e
            null
        }
        lock.withLock {
            store.edit {
                it[K.USERNAME] = username.trim()
                it[K.IS_EMAIL] = isEmail
                if (countryCode.isNullOrBlank()) it.remove(K.COUNTRY) else it[K.COUNTRY] = countryCode.trim()
                it[K.PASSWORD] = cipher.encrypt(hash)
                it[K.NEEDS_LOGIN] = false
                it.remove(K.DEMO)
                if (result == null) it[K.UNVERIFIED] = true else it.remove(K.UNVERIFIED)
                it.remove(K.REJECTED_SINCE)
                it.remove(K.REJECT_COUNT)
                it.remove(K.REJECT_REASON)
                it[K.LAST_LOGIN_AT] = System.currentTimeMillis()
                it.remove(K.TOKEN)
                it.remove(K.TOKEN_EXPIRY)
            }
            cachedApi = null
            cachedExpiry = 0L
            if (result != null) saveToken(api, result)
        }
        settings.setAuthSession(AuthSession(isLoggedIn = true, username = result?.userName ?: username.trim()))
        return result != null
    }

    /** API client with a valid token. Re-logs in silently when needed. */
    suspend fun authorizedApi(): SaicApi = lock.withLock {
        val p = store.data.first()
        val s = p.toState()
        // Safety net: demo mode must never reach the real API.
        if (s.isDemo) throw IllegalStateException("Demo mode: no API calls")
        if (!s.hasCredentials || s.needsLogin) throw NeedsLoginException()
        val now = System.currentTimeMillis()
        cachedApi?.let { if (now < cachedExpiry - 60_000) return it }

        val storedToken = p[K.TOKEN]
        val storedExpiry = p[K.TOKEN_EXPIRY] ?: 0L
        if (storedToken != null && now < storedExpiry - 60_000) {
            val token = runCatching { cipher.decrypt(storedToken) }.getOrNull()
            if (token != null) {
                return SaicApi(http, s.region).also {
                    it.userToken = token
                    cachedApi = it
                    cachedExpiry = storedExpiry
                }
            }
        }

        val hash = runCatching { cipher.decrypt(p[K.PASSWORD]!!) }.getOrNull()
            ?: run { markNeedsLogin(); throw NeedsLoginException() }

        // Don't hammer MG with logins: that can get the account blocked.
        val rejections = p[K.REJECT_COUNT] ?: 0
        val nextAt = LoginRejection.nextLoginAt(p[K.LAST_LOGIN_AT], rejections)
        if (now < nextAt) {
            throw LoginThrottledException(
                nextAt,
                if (rejections > 0) "Waiting before logging in again (${p[K.REJECT_REASON]})" else "Logged in moments ago",
            )
        }
        store.edit { it[K.LAST_LOGIN_AT] = now }

        val api = SaicApi(http, s.region)
        val result = try {
            api.login(s.username!!, hash, s.isEmail, s.countryCode)
        } catch (e: SaicLoginRejectedException) {
            // MG sometimes says no while it's struggling. Only give up on the stored login
            // when it still says no a while later; until then the queue retries quietly.
            val since = p[K.REJECTED_SINCE]
            store.edit {
                if (since == null) it[K.REJECTED_SINCE] = now
                it[K.REJECT_COUNT] = rejections + 1
                it[K.REJECT_REASON] = e.message ?: "Login rejected"
            }
            if (LoginRejection.shouldLogOut(since, now)) {
                markNeedsLogin()
                throw NeedsLoginException()
            }
            throw e
        }
        store.edit {
            it.remove(K.REJECTED_SINCE)
            it.remove(K.REJECT_COUNT)
            it.remove(K.REJECT_REASON)
            it.remove(K.UNVERIFIED)
        }
        saveToken(api, result)
        api
    }

    /** Picks which MG server to talk to. Callers log out first if the server changes. */
    suspend fun setRegion(region: SaicRegion) {
        store.edit { it[K.REGION] = region.name }
    }

    suspend fun invalidateToken() = lock.withLock {
        cachedApi = null
        cachedExpiry = 0L
        store.edit { it.remove(K.TOKEN); it.remove(K.TOKEN_EXPIRY) }
    }

    suspend fun logout() = lock.withLock {
        cachedApi = null
        cachedExpiry = 0L
        val p = store.data.first()
        val username = p[K.USERNAME]
        val region = p[K.REGION]
        store.edit {
            it.clear()
            // keep the username (prefills the login form) and the chosen server
            if (username != null) it[K.USERNAME] = username
            if (region != null) it[K.REGION] = region
        }
        settings.clearAuthSession()
    }

    private suspend fun saveToken(api: SaicApi, result: LoginResult) {
        val expiry = System.currentTimeMillis() + result.expiresInSec * 1000
        store.edit {
            it[K.TOKEN] = cipher.encrypt(result.accessToken)
            it[K.TOKEN_EXPIRY] = expiry
        }
        cachedApi = api
        cachedExpiry = expiry
    }

    private suspend fun markNeedsLogin() {
        cachedApi = null
        store.edit { it[K.NEEDS_LOGIN] = true }
    }

    private suspend fun <T> retryNetwork(attempts: Int = 4, block: suspend () -> T): T {
        var wait = 1_000L
        repeat(attempts - 1) {
            try {
                return block()
            } catch (e: SaicNetworkException) {
                delay(wait)
                wait *= 2
            }
        }
        return block()
    }
}
