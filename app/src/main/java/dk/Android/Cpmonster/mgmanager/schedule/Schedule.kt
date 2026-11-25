package dk.Android.Cpmonster.mgmanager.schedule

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.ZonedDateTime

/**
 * "2 days 5 hours", "5 hours", "1 day", or "35 min" when under an hour – no dates.
 * Rounded up to the minute so it never says "0 min".
 */
fun timeUntil(from: ZonedDateTime, to: ZonedDateTime): String {
    val minutes = ((java.time.Duration.between(from, to).seconds + 59) / 60).coerceAtLeast(1)
    val days = minutes / (24 * 60)
    val hours = (minutes % (24 * 60)) / 60
    if (days == 0L && hours == 0L) return "$minutes min"
    fun unit(n: Long, one: String) = if (n == 1L) "1 $one" else "$n ${one}s"
    return listOfNotNull(
        unit(days, "day").takeIf { days > 0 },
        unit(hours, "hour").takeIf { hours > 0 },
    ).joinToString(" ")
}

/** One start time: [minutes] after midnight, local time. */
data class DaySlot(val on: Boolean, val minutes: Int) {
    val hour get() = minutes / 60
    val minute get() = minutes % 60
    val label: String get() = "%02d:%02d".format(hour, minute)
}

enum class ScheduleMode {
    /** One time for Monday–Friday, one for Saturday–Sunday. */
    WEEKDAYS_WEEKEND,
    /** Each day has its own time. */
    EACH_DAY,
}

/**
 * Starts a profile automatically at set times. Setting it up is the user's go-ahead,
 * so here (unlike the suggestion on the front page) the profile is started without a tap.
 */
data class Schedule(
    val enabled: Boolean = false,
    val mode: ScheduleMode = ScheduleMode.WEEKDAYS_WEEKEND,
    val weekdays: DaySlot = DaySlot(on = true, minutes = 7 * 60),
    val weekend: DaySlot = DaySlot(on = false, minutes = 9 * 60),
    /** Monday first. */
    val days: List<DaySlot> = List(7) { i -> if (i < 5) DaySlot(true, 7 * 60) else DaySlot(false, 9 * 60) },
    /** null = Auto: pick from the outside temperature at start time. */
    val profileId: Long? = null,
) {
    fun slotFor(day: DayOfWeek): DaySlot = when (mode) {
        ScheduleMode.WEEKDAYS_WEEKEND -> if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) weekend else weekdays
        ScheduleMode.EACH_DAY -> days[day.value - 1]
    }

    /** The first start strictly after [from], or null if nothing is planned. */
    fun nextStart(from: ZonedDateTime): ZonedDateTime? {
        if (!enabled) return null
        for (d in 0..7) {
            val date = from.toLocalDate().plusDays(d.toLong())
            val slot = slotFor(date.dayOfWeek)
            if (!slot.on) continue
            val at = date.atTime(slot.hour, slot.minute).atZone(from.zone)
            if (at.isAfter(from)) return at
        }
        return null
    }

    fun toJson(): String = JSONObject()
        .put("enabled", enabled)
        .put("mode", mode.name)
        .put("weekdays", weekdays.json())
        .put("weekend", weekend.json())
        .put("days", JSONArray().apply { days.forEach { put(it.json()) } })
        .put("profileId", profileId ?: JSONObject.NULL)
        .toString()

    companion object {
        private fun DaySlot.json() = JSONObject().put("on", on).put("minutes", minutes)
        private fun JSONObject.slot(default: DaySlot) = DaySlot(optBoolean("on", default.on), optInt("minutes", default.minutes).coerceIn(0, 24 * 60 - 1))

        fun fromJson(s: String?): Schedule {
            if (s.isNullOrBlank()) return Schedule()
            return runCatching {
                val j = JSONObject(s)
                val d = Schedule()
                val arr = j.optJSONArray("days")
                Schedule(
                    enabled = j.optBoolean("enabled", false),
                    mode = ScheduleMode.entries.firstOrNull { it.name == j.optString("mode") } ?: d.mode,
                    weekdays = j.optJSONObject("weekdays")?.slot(d.weekdays) ?: d.weekdays,
                    weekend = j.optJSONObject("weekend")?.slot(d.weekend) ?: d.weekend,
                    days = List(7) { i -> arr?.optJSONObject(i)?.slot(d.days[i]) ?: d.days[i] },
                    profileId = if (j.isNull("profileId")) null else j.optLong("profileId"),
                )
            }.getOrElse { Schedule() }
        }
    }
}
