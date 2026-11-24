package dk.Android.Cpmonster.mgmanager

import dk.Android.Cpmonster.mgmanager.saic.ServerHealth
import dk.Android.Cpmonster.mgmanager.saic.ServerSnapshot
import dk.Android.Cpmonster.mgmanager.saic.ServerStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerStatusTest {
    private val now = 1_000_000L
    private fun show(s: ServerSnapshot) = ServerStatus.displayHealth(s, now)

    @Test fun lastResultShownWhenIdle() {
        assertEquals(ServerHealth.OK, show(ServerSnapshot(ServerHealth.OK)))
        assertEquals(ServerHealth.SLOW, show(ServerSnapshot(ServerHealth.SLOW)))
        assertEquals(ServerHealth.DOWN, show(ServerSnapshot(ServerHealth.DOWN)))
        assertEquals(ServerHealth.UNKNOWN, show(ServerSnapshot()))
    }

    @Test fun longWaitTurnsYellowThenRedBeforeTimeout() {
        assertEquals(ServerHealth.OK, show(ServerSnapshot(ServerHealth.OK, waitingSince = now - 1_000)))
        assertEquals(ServerHealth.SLOW, show(ServerSnapshot(ServerHealth.OK, waitingSince = now - 4_000)))
        assertEquals(ServerHealth.DOWN, show(ServerSnapshot(ServerHealth.OK, waitingSince = now - 11_000)))
    }

    @Test fun refreshWithoutDataFor10sTurnsGreenToYellow() {
        val ok = ServerSnapshot(ServerHealth.OK)
        assertEquals(ServerHealth.OK, ServerStatus.displayHealth(ok, now, refreshSince = now - 9_000))
        assertEquals(ServerHealth.SLOW, ServerStatus.displayHealth(ok, now, refreshSince = now - 11_000))
        assertEquals(ServerHealth.SLOW, ServerStatus.displayHealth(ServerSnapshot(), now, refreshSince = now - 11_000))
        // Already red stays red.
        assertEquals(ServerHealth.DOWN, ServerStatus.displayHealth(ServerSnapshot(ServerHealth.DOWN), now, refreshSince = now - 11_000))
        assertEquals(true, ServerStatus.isStuck(now, now - 11_000))
        assertEquals(false, ServerStatus.isStuck(now, null))
    }

    @Test fun phoneOfflineIsItsOwnStateAndNotMarkedSlowWhenStuck() {
        val off = ServerSnapshot(ServerHealth.OFFLINE)
        assertEquals(ServerHealth.OFFLINE, ServerStatus.displayHealth(off, now))
        assertEquals(ServerHealth.OFFLINE, ServerStatus.displayHealth(off, now, refreshSince = now - 11_000))
        ServerStatus.reset()
        ServerStatus.end(ServerStatus.begin(), ok = false, offline = true)
        assertEquals(ServerHealth.OFFLINE, ServerStatus.state.value.health)
        ServerStatus.reset()
    }

    @Test fun downStaysRedWhileRetrying() {
        assertEquals(ServerHealth.DOWN, show(ServerSnapshot(ServerHealth.DOWN, waitingSince = now - 500)))
    }

    @Test fun finishedCallsSetHealthFromOutcomeAndSpeed() {
        ServerStatus.reset()
        val a = ServerStatus.begin()
        ServerStatus.end(a, ok = true)
        assertEquals(ServerHealth.OK, ServerStatus.state.value.health)
        val b = ServerStatus.begin()
        assertEquals(true, ServerStatus.state.value.waitingSince != null)
        ServerStatus.end(b, ok = false)
        assertEquals(ServerHealth.DOWN, ServerStatus.state.value.health)
        assertEquals(null, ServerStatus.state.value.waitingSince)
        ServerStatus.reset()
    }
}
