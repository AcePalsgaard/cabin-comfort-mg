package dk.Android.Cpmonster.mgmanager

import dk.Android.Cpmonster.mgmanager.data.profile.ActiveProfile
import dk.Android.Cpmonster.mgmanager.data.profile.ActiveProfile.Companion.RUN_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveProfileTest {
    @Test
    fun firstOfTwoRuns() {
        val a = ActiveProfile(id = 1, activatedAt = 0, runs = 2, run = 1, runStartedAt = 0)
        assertEquals(RUN_MS, a.runEndsAt)
        assertEquals(2 * RUN_MS, a.sessionEndsAt)
        assertFalse(a.isLastRun)
    }

    @Test
    fun lastRunAfterRestart() {
        val a = ActiveProfile(id = 1, activatedAt = 0, runs = 2, run = 2, runStartedAt = RUN_MS + 5_000)
        assertEquals(2 * RUN_MS + 5_000, a.runEndsAt)
        assertEquals(a.runEndsAt, a.sessionEndsAt)
        assertTrue(a.isLastRun)
    }
}
