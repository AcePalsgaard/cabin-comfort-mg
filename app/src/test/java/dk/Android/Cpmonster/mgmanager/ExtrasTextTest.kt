package dk.Android.Cpmonster.mgmanager

import dk.Android.Cpmonster.mgmanager.queue.CommandQueue
import dk.Android.Cpmonster.mgmanager.queue.JobType
import dk.Android.Cpmonster.mgmanager.ui.home.extrasText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtrasTextTest {
    @Test fun nothingWaiting() {
        assertNull(extrasText(emptySet()))
        // Heating and status refresh aren't extras.
        assertNull(extrasText(setOf(JobType.CLIMATE, JobType.REFRESH_STATUS)))
    }

    @Test fun oneExtra() {
        assertEquals("Seat heating on the way…", extrasText(setOf(JobType.SEATS)))
    }

    @Test fun severalExtras() {
        assertEquals(
            "Seat heating, steering wheel and rear window on the way…",
            extrasText(setOf(JobType.REAR_WINDOW, JobType.SEATS, JobType.STEERING_WHEEL)),
        )
        assertEquals("Steering wheel and rear window on the way…", extrasText(setOf(JobType.STEERING_WHEEL, JobType.REAR_WINDOW)))
    }

    @Test fun carBlockedNoticeKeepsMgsReasonUnlessItsTheLockout() {
        assertEquals(
            CommandQueue.CAR_BLOCKED_NOTICE,
            CommandQueue.carBlockedNotice("Too frequent operations. Please use the physical key to restart the vehicle…(71)"),
        )
        assertEquals(
            "Vehicle is powered on. Please turn it off and try again.(1)",
            CommandQueue.carBlockedNotice("Vehicle is powered on. Please turn it off and try again.(1)"),
        )
    }
}
