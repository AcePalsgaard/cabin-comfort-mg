package dk.Android.Cpmonster.mgmanager

import dk.Android.Cpmonster.mgmanager.data.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `fromString parses valid values case insensitively`() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromString("light"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromString("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromString("dark"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromString("DARK"))
        assertEquals(ThemeMode.SYSTEM_DEFAULT, ThemeMode.fromString("system_default"))
    }

    @Test
    fun `fromString defaults to SYSTEM_DEFAULT for null or unknown inputs`() {
        assertEquals(ThemeMode.SYSTEM_DEFAULT, ThemeMode.fromString(null))
        assertEquals(ThemeMode.SYSTEM_DEFAULT, ThemeMode.fromString("UNKNOWN_MODE"))
    }
}
