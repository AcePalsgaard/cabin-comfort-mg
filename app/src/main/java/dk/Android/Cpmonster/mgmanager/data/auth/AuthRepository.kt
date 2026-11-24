package dk.Android.Cpmonster.mgmanager.data.auth

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
) {
    val isLoggedIn get() = isDemo || (hasCredentials && !needsLogin)
}

/** When a background re-login that MG rejected should send the user back to the login screen. */
object LoginRejection {
    /** MG must still reject the login this long after the first "no". */
    const val CONFIRM_AFTER_MS = 60_000L

    fun shouldLogOut(firstRejectedAt: Long?, now: Long): Boolean =
        firstRejectedAt != null && now - firstRejectedAt >= CONFIRM_AFTER_MS
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
        val api = SaicApi(http, s.region)
        val result = try {
            api.login(s.username!!, hash, s.isEmail, s.countryCode)
        } catch (e: SaicLoginRejectedException) {
            // MG sometimes says no while it's struggling. Only give up on the stored login
            // when it still says no a while later; until then the queue retries quietly.
            val since = p[K.REJECTED_SINCE]
            if (LoginRejection.shouldLogOut(since, now)) {
                markNeedsLogin()
                throw NeedsLoginException()
            }
            if (since == null) store.edit { it[K.REJECTED_SINCE] = now }
            throw e
        }
        store.edit {
            it.remove(K.REJECTED_SINCE)
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
