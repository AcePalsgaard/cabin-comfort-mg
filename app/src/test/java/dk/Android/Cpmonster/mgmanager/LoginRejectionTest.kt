package dk.Android.Cpmonster.mgmanager

import dk.Android.Cpmonster.mgmanager.data.auth.LoginRejection
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
}
