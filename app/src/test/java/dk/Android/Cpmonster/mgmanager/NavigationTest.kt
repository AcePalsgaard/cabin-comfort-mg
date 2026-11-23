package dk.Android.Cpmonster.mgmanager

import dk.Android.Cpmonster.mgmanager.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTest {

    @Test
    fun `verify screen routes defined correctly`() {
        val mainScreen: Screen = Screen.Main
        val settingsScreen: Screen = Screen.Settings
        val editScreen: Screen = Screen.ProfileEdit(1)

        assertEquals(Screen.Main, mainScreen)
        assertEquals(Screen.Settings, settingsScreen)
        assertEquals(Screen.ProfileEdit(1), editScreen)
        assertEquals(Screen.About, Screen.About as Screen)
    }
}
