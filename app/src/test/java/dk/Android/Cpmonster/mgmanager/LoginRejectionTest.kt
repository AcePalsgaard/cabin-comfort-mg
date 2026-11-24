package dk.Android.Cpmonster.mgmanager

import dk.Android.Cpmonster.mgmanager.data.auth.LoginRejection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginRejectionTest {
    private val now = 10_000_000L

    @Test fun firstRejectionDoesNotLogOut() {
        assertFalse(LoginRejection.shouldLogOut(null, now))
    }

    @Test fun quickSecondRejectionDoesNotLogOut() {
        assertFalse(LoginRejection.shouldLogOut(now - 5_000, now))
        assertFalse(LoginRejection.shouldLogOut(now - 59_999, now))
    }

    @Test fun rejectionStillThereAMinuteLaterLogsOut() {
        assertTrue(LoginRejection.shouldLogOut(now - 60_000, now))
        assertTrue(LoginRejection.shouldLogOut(now - 10 * 60_000, now))
    }

    @Test fun noEarlierLoginMeansGoAhead() {
        assertEquals(0L, LoginRejection.nextLoginAt(null, 0))
    }

    @Test fun atLeast30SecondsBetweenLogins() {
        assertEquals(now + 30_000, LoginRejection.nextLoginAt(now, 0))
        assertEquals(now + 30_000, LoginRejection.nextLoginAt(now, 1))
    }

    @Test fun gapDoublesWithRejectionsUpTo10Minutes() {
        assertEquals(now + 60_000, LoginRejection.nextLoginAt(now, 2))
        assertEquals(now + 120_000, LoginRejection.nextLoginAt(now, 3))
        assertEquals(now + 10 * 60_000, LoginRejection.nextLoginAt(now, 6))
        assertEquals(now + 10 * 60_000, LoginRejection.nextLoginAt(now, 100))
    }

    @Test fun threeRejectionsSpreadOverAMinuteAndAHalfLogOut() {
        // 1st no at t0, 2nd allowed at t0+30 s, 3rd at t0+90 s: only then the user is asked.
        val second = LoginRejection.nextLoginAt(now, 1)
        assertFalse(LoginRejection.shouldLogOut(now, second))
        val third = LoginRejection.nextLoginAt(second, 2)
        assertTrue(LoginRejection.shouldLogOut(now, third))
    }
}
