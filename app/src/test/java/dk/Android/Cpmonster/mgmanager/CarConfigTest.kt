package dk.Android.Cpmonster.mgmanager

import dk.Android.Cpmonster.mgmanager.saic.CarConfig
import dk.Android.Cpmonster.mgmanager.saic.CarFeatures
import dk.Android.Cpmonster.mgmanager.saic.ConfigItem
import dk.Android.Cpmonster.mgmanager.saic.SeatHeating
import dk.Android.Cpmonster.mgmanager.saic.Support
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarConfigTest {
    private fun items(vararg p: Pair<String, String?>) = p.map { ConfigItem(it.first, it.first, it.second) }

    @Test fun parsesItemsAndSkipsBlankCodes() {
        val arr = JSONArray(
            """[{"itemCode":" SWH ","itemName":"SWH","itemValue":" 1 "},
                {"itemCode":"","itemName":"x","itemValue":"1"},
                {"itemCode":"S35","itemName":"Sunroof","itemValue":null}]""",
        )
        val list = CarConfig.parse(arr)
        assertEquals(listOf(ConfigItem("SWH", "SWH", "1"), ConfigItem("S35", "Sunroof", null)), list)
        assertEquals(emptyList<ConfigItem>(), CarConfig.parse(null))
    }

    @Test fun valueLookupIgnoresCase() {
        assertEquals("1", CarConfig.value(items("HeatedSeat" to "1"), "HEATEDSEAT"))
    }

    @Test fun steeringWheelFromSwh() {
        assertEquals(Support.YES, CarConfig.steeringWheelHeat(items("SWH" to "1")))
        assertEquals(Support.NO, CarConfig.steeringWheelHeat(items("SWH" to "0")))
        assertEquals(Support.NOT_REPORTED, CarConfig.steeringWheelHeat(items("HeatedSeat" to "1")))
        assertEquals(Support.NOT_REPORTED, CarConfig.steeringWheelHeat(items("SWH" to "")))
        assertEquals(Support.UNKNOWN, CarConfig.steeringWheelHeat(emptyList()))
    }

    @Test fun onlyNoHidesTheSteeringWheel() {
        assertFalse(CarFeatures("EH32 S", steeringWheelHeat = Support.NO).hasSteeringWheelHeat)
        assertTrue(CarFeatures("EH32 S", steeringWheelHeat = Support.NOT_REPORTED).hasSteeringWheelHeat)
        assertTrue(CarFeatures("EH32 S", steeringWheelHeat = Support.UNKNOWN).hasSteeringWheelHeat)
        assertTrue(CarFeatures("EH32 S", steeringWheelHeat = Support.YES).hasSteeringWheelHeat)
    }

    @Test fun realMg4ConfigHasNoSwh() {
        // From saic-python-mqtt-gateway issue #343 (MG4 EH32 S), trimmed.
        val mg4 = items("S35" to "0", "HeatedSeat" to "1", "BType" to "1", "BATTERY" to "1", "ROV" to "25857")
        assertEquals(Support.NOT_REPORTED, CarConfig.steeringWheelHeat(mg4))
        assertEquals(SeatHeating.LEVELS, SeatHeating.fromConfig(CarConfig.value(mg4, "HeatedSeat")))
    }

    @Test fun supportFromStoredName() {
        assertEquals(Support.NO, Support.from("NO"))
        assertEquals(Support.UNKNOWN, Support.from(null))
        assertEquals(Support.UNKNOWN, Support.from("garbage"))
    }

    @Test fun describeForLog() {
        assertEquals("(none)", CarConfig.describe(emptyList()))
        assertEquals("SWH=0 (SWH); S35=0 (S35)", CarConfig.describe(items("SWH" to "0", "S35" to "0")))
    }
}
