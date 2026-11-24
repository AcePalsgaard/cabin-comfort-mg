package dk.Android.Cpmonster.mgmanager.saic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * OFFLINE = the phone itself can't get out (no network, or the address lookup fails –
 * e.g. a home DNS filter). Shown differently from DOWN, where MG doesn't answer.
 */
enum class ServerHealth { UNKNOWN, OK, SLOW, DOWN, OFFLINE }

/** What we know about MG's servers right now. */
data class ServerSnapshot(
    /** Result of the last finished call. */
    val health: ServerHealth = ServerHealth.UNKNOWN,
    /** How long the last finished call took. */
    val lastLatencyMs: Long? = null,
    /** Start time of the oldest call still waiting for an answer, or null. */
    val waitingSince: Long? = null,
    /** Android says the phone has a network connection. Fed by AppContainer. */
    val phoneOnline: Boolean = true,
)

/**
 * Whether MG's servers answer, and how fast. Fed by every API call in [SaicApi]:
 * any real answer (even an error) means "up"; timeouts, connection errors,
 * HTTP errors and garbage responses mean "down". The car not answering is not
 * the server's fault and doesn't count.
 *
 * Calls still in flight are tracked too, so the UI can turn yellow/red while
 * waiting instead of only after the (long) network timeout.
 */
object ServerStatus {
    /** Slower than this is "slow" (yellow). */
    const val SLOW_MS = 3_000L
    /** Waiting longer than this is as good as down (red). */
    const val STALLED_MS = 10_000L

    private val _state = MutableStateFlow(ServerSnapshot())
    val state: StateFlow<ServerSnapshot> = _state

    private val inFlight = mutableMapOf<Long, Long>()
    private var nextId = 0L

    /** The last finished call's verdict. Kept for callers that only care about up/down. */
    val health: ServerHealth get() = _state.value.health

    @Synchronized
    fun begin(): Long {
        val id = nextId++
        inFlight[id] = System.currentTimeMillis()
        publishWaiting()
        return id
    }

    @Synchronized
    fun end(id: Long, ok: Boolean, offline: Boolean = false) {
        val started = inFlight.remove(id) ?: System.currentTimeMillis()
        val latency = System.currentTimeMillis() - started
        val h = when {
            offline -> ServerHealth.OFFLINE
            !ok -> ServerHealth.DOWN
            latency > SLOW_MS -> ServerHealth.SLOW
            else -> ServerHealth.OK
        }
        _state.update { it.copy(health = h, lastLatencyMs = latency, waitingSince = inFlight.values.minOrNull()) }
    }

    /** Demo mode, logout: we don't know anything about the server any more. */
    @Synchronized
    fun reset() {
        inFlight.clear()
        _state.update { ServerSnapshot(phoneOnline = it.phoneOnline) }
    }

    /** From Android's connectivity callbacks. */
    fun setPhoneOnline(online: Boolean) {
        _state.update { it.copy(phoneOnline = online) }
    }

    private fun publishWaiting() {
        _state.update { it.copy(waitingSince = inFlight.values.minOrNull()) }
    }

    /**
     * What to show at time [now]: a long wait counts before the call finally times out.
     * [refreshSince] is when the current refresh started (null when idle). MG often answers
     * with errors or "still waiting" for ages, which is technically "up" – so a refresh that
     * hasn't delivered car data after [NO_DATA_MS] is shown as at least slow.
     */
    fun displayHealth(s: ServerSnapshot, now: Long, refreshSince: Long? = null): ServerHealth {
        if (!s.phoneOnline) return ServerHealth.OFFLINE
        val waited = s.waitingSince?.let { now - it } ?: 0L
        val base = when {
            waited > STALLED_MS -> ServerHealth.DOWN
            s.health == ServerHealth.DOWN || s.health == ServerHealth.OFFLINE -> s.health
            waited > SLOW_MS -> ServerHealth.SLOW
            else -> s.health
        }
        val failed = base == ServerHealth.DOWN || base == ServerHealth.OFFLINE
        return if (!failed && isStuck(now, refreshSince)) ServerHealth.SLOW else base
    }

    /** A refresh has run for [NO_DATA_MS] without new car data. */
    fun isStuck(now: Long, refreshSince: Long?) = refreshSince != null && now - refreshSince > NO_DATA_MS

    /** A refresh without new car data for this long counts as "MG not responding". */
    const val NO_DATA_MS = 10_000L
}
