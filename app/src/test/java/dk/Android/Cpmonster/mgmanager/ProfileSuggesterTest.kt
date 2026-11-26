package dk.Android.Cpmonster.mgmanager

import dk.Android.Cpmonster.mgmanager.data.db.Profile
import dk.Android.Cpmonster.mgmanager.data.db.ProfileKind
import dk.Android.Cpmonster.mgmanager.data.profile.ProfileSuggester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileSuggesterTest {
    private val cold = Profile(1, "Cold", ProfileKind.COLD, 24, 3, true, 3, 0, true, true, 2)
    private val warm = Profile(2, "Warm", ProfileKind.WARM, 19, 4, false, 0, 0, false, false, 2)
    private val all = listOf(cold, warm)

    @Test fun coldWeatherSuggestsCold() = assertEquals(cold, ProfileSuggester.suggest(3, all, null))
    @Test fun warmWeatherSuggestsWarm() = assertEquals(warm, ProfileSuggester.suggest(26, all, null))
    @Test fun mildWeatherSplitsAt15() {
        assertEquals(cold, ProfileSuggester.suggest(15, all, null))
        assertEquals(warm, ProfileSuggester.suggest(16, all, null))
    }
    @Test fun unknownTempSuggestsNothing() = assertNull(ProfileSuggester.suggest(null, all, null))
    @Test fun nothingSuggestedWhileAProfileRuns() {
        assertNull(ProfileSuggester.suggest(3, all, 1))
        assertNull(ProfileSuggester.suggest(3, all, 2))
    }
}
