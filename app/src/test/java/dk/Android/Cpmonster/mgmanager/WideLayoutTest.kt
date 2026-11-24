package dk.Android.Cpmonster.mgmanager

import dk.Android.Cpmonster.mgmanager.ui.components.isWideLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WideLayoutTest {
    @Test fun phoneLandscapeIsWide() {
        // OnePlus 11: 2772 x 1240 px at 3.5 -> 792 x 354 dp, whichever way it's turned.
        assertTrue(isWideLayout(792f, 354f))
    }

    @Test fun portraitIsNotWide() {
        assertFalse(isWideLayout(354f, 792f))
        assertFalse(isWideLayout(700f, 900f))
    }

    @Test fun smallLandscapeIsNotWide() {
        assertFalse(isWideLayout(560f, 320f))
    }
}
