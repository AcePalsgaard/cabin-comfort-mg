package dk.Android.Cpmonster.mgmanager

import dk.Android.Cpmonster.mgmanager.queue.ChargeAssist
import dk.Android.Cpmonster.mgmanager.saic.SaicApi
import dk.Android.Cpmonster.mgmanager.saic.SaicApiException
import dk.Android.Cpmonster.mgmanager.saic.SaicCarBlockedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeAssistTest {
    @Test fun reasonCodeFromMgMessage() {
        assertEquals(40, SaicApi.reasonCode("Request timed out. Please try again later.(40)"))
        assertEquals(71, SaicApi.reasonCode("Too frequent operations. … and then try again.(71)"))
        assertNull(SaicApi.reasonCode("Vehicle not locked"))
        assertNull(SaicApi.reasonCode(null))
    }

    @Test fun reason253PassesByItself() {
        assertTrue(SaicApi.reasonCode("Please confirm that the vehicle status or the overall vehicle network status is normal.(253)") in SaicApi.PASSING_CAR_REASONS)
        assertFalse(SaicApi.reasonCode("Too frequent operations.(71)") in SaicApi.PASSING_CAR_REASONS)
    }

    /** What the real car answered when plugged in and not charging (field test 26-11-2026). */
    @Test fun code8Reason40MayBeThePluggedInFault() =
        assertTrue(ChargeAssist.mayBePluggedInRefusal(SaicCarBlockedException("Request timed out. Please try again later.(40)")))

    @Test fun tooFrequentIsNot() =
        assertFalse(ChargeAssist.mayBePluggedInRefusal(SaicCarBlockedException("Too frequent operations. Please try again.(71)")))

    @Test fun oldMockAnswerStillCounts() =
        assertTrue(ChargeAssist.mayBePluggedInRefusal(SaicApiException("Remote control failed.(40)", 5)))
}
