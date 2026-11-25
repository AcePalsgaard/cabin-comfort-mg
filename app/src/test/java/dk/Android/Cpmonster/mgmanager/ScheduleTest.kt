package dk.Android.Cpmonster.mgmanager

import dk.Android.Cpmonster.mgmanager.schedule.DaySlot
import dk.Android.Cpmonster.mgmanager.schedule.Schedule
import dk.Android.Cpmonster.mgmanager.schedule.ScheduleMode
import dk.Android.Cpmonster.mgmanager.schedule.timeUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {
    private val zone = ZoneId.of("Europe/Copenhagen")
    // Wednesday 25-11-2026
    private fun at(day: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone)

    private val split = Schedule(
        enabled = true,
        weekdays = DaySlot(true, 7 * 60),
        weekend = DaySlot(true, 9 * 60 + 30),
    )

    @Test fun laterTodayOnAWeekday() = assertEquals(at(25, 7), split.nextStart(at(25, 6)))

    @Test fun tomorrowWhenTodaysTimeHasPassed() = assertEquals(at(26, 7), split.nextStart(at(25, 7)))

    @Test fun fridayEveningGoesToSaturdaysTime() = assertEquals(at(28, 9, 30), split.nextStart(at(27, 20)))

    @Test fun weekendOffSkipsToMonday() =
        assertEquals(at(30, 7), split.copy(weekend = DaySlot(false, 0)).nextStart(at(27, 20)))

    @Test fun disabledHasNoStart() = assertNull(split.copy(enabled = false).nextStart(at(25, 6)))

    @Test fun allDaysOffHasNoStart() =
        assertNull(split.copy(weekdays = DaySlot(false, 0), weekend = DaySlot(false, 0)).nextStart(at(25, 6)))

    @Test fun eachDayUsesItsOwnTime() {
        val s = Schedule(
            enabled = true,
            mode = ScheduleMode.EACH_DAY,
            days = List(7) { i -> DaySlot(on = i == 3, minutes = 6 * 60 + 45) }, // Thursday only
        )
        assertEquals(at(26, 6, 45), s.nextStart(at(25, 12)))
        // A week later when Thursday's time has just passed.
        assertEquals(at(26, 6, 45).plusWeeks(1), s.nextStart(at(26, 6, 45)))
    }

    @Test fun timeUntilText() {
        assertEquals("35 min", timeUntil(at(25, 19), at(25, 19, 35)))
        assertEquals("1 hour", timeUntil(at(25, 19), at(25, 20)))
        assertEquals("11 hours", timeUntil(at(25, 19, 30), at(26, 7)))
        assertEquals("1 day", timeUntil(at(25, 7), at(26, 7)))
        assertEquals("2 days 5 hours", timeUntil(at(25, 7), at(27, 12, 30)))
        assertEquals("1 min", timeUntil(at(25, 7), at(25, 7)))
    }

    @Test fun jsonRoundTrip() {
        val s = split.copy(mode = ScheduleMode.EACH_DAY, profileId = 2L)
        assertEquals(s, Schedule.fromJson(s.toJson()))
        assertEquals(Schedule(), Schedule.fromJson("not json"))
    }
}
