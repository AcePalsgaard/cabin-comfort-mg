package dk.Android.Cpmonster.mgmanager

import dk.Android.Cpmonster.mgmanager.data.vehicle.CarMemory
import dk.Android.Cpmonster.mgmanager.queue.CommandQueue
import dk.Android.Cpmonster.mgmanager.queue.JobType
import dk.Android.Cpmonster.mgmanager.saic.CarFeatures
import dk.Android.Cpmonster.mgmanager.saic.SaicNetworkException
import dk.Android.Cpmonster.mgmanager.saic.Support
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteeringWheelMemoryTest {
    private val noAnswer = SaicNetworkException("The car did not answer in time", serverDown = false, carNoAnswer = true)
    private val mg4 = CarFeatures("EH32 S", steeringWheelHeat = Support.NOT_REPORTED)

    @Test fun timeoutOnSteeringWheelIsRemembered() {
        assertTrue(CommandQueue.steeringWheelUnanswered(JobType.STEERING_WHEEL, noAnswer, mg4))
        assertTrue(CommandQueue.steeringWheelUnanswered(JobType.STEERING_WHEEL, noAnswer, CarFeatures(null)))
    }

    @Test fun otherFailuresAreRetriedAsBefore() {
        assertFalse(CommandQueue.steeringWheelUnanswered(JobType.SEATS, noAnswer, mg4))
        assertFalse(CommandQueue.steeringWheelUnanswered(JobType.STEERING_WHEEL, SaicNetworkException("MG down"), mg4))
        assertFalse(
            CommandQueue.steeringWheelUnanswered(
                JobType.STEERING_WHEEL, SaicNetworkException("offline", serverDown = false, phoneOffline = true), mg4,
            ),
        )
    }

    @Test fun confirmedSwhKeepsRetrying() {
        val swh = CarFeatures("MIS3E S", steeringWheelHeat = Support.YES)
        assertFalse(CommandQueue.steeringWheelUnanswered(JobType.STEERING_WHEEL, noAnswer, swh))
    }

    @Test fun rememberedCarGetsNoSteeringWheel() {
        val silent = mg4.copy(steeringWheelUnconfirmed = true)
        assertFalse(silent.hasSteeringWheelHeat)
        assertTrue(silent.steeringWheelSilenced)
        assertTrue(mg4.hasSteeringWheelHeat)
        assertFalse(mg4.steeringWheelSilenced)
    }

    @Test fun swhFromTheCarWinsOverMemory() {
        val yes = CarFeatures("MIS3E S", steeringWheelHeat = Support.YES, steeringWheelUnconfirmed = true)
        assertTrue(yes.hasSteeringWheelHeat)
        assertFalse(yes.steeringWheelSilenced)
        // SWH=0: no retry offered, the car says it has none.
        val no = CarFeatures("AS33 UK", steeringWheelHeat = Support.NO, steeringWheelUnconfirmed = true)
        assertFalse(no.hasSteeringWheelHeat)
        assertFalse(no.steeringWheelSilenced)
    }

    @Test fun vinIsStoredHashed() {
        val key = CarMemory.vinKey("LSJWH4092PN000000")
        assertNotEquals("LSJWH4092PN000000", key)
        assertEquals(64, key.length)
        assertEquals(key, CarMemory.vinKey("LSJWH4092PN000000"))
    }
}
