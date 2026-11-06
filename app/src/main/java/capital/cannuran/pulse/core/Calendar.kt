package capital.cannuran.pulse.core

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * Parses the public ForexFactory weekly calendar feed
 * (https://nfs.faireconomy.media/ff_calendar_thisweek.json), which looks like:
 *
 * [{"title":"CPI m/m","country":"USD","date":"2026-11-12T08:30:00-05:00",
 *   "impact":"High","forecast":"0.3%","previous":"0.4%"}, ...]
 */
object CalendarParser {

    fun parse(json: String): List<CalEvent> {
        val root = Json.parse(json) as? List<*> ?: throw IllegalArgumentException("Calendar feed is not an array")
        return root.mapNotNull { item ->
            @Suppress("UNCHECKED_CAST")
            val m = item as? Map<String, Any?> ?: return@mapNotNull null
            val title = m.str("title")?.trim().orEmpty()
            val dateText = m.str("date")?.trim().orEmpty()
            if (title.isEmpty() || dateText.isEmpty()) return@mapNotNull null
            val (millis, allDay) = parseDate(dateText) ?: return@mapNotNull null
            CalEvent(
                title = title,
                country = m.str("country")?.trim()?.uppercase().orEmpty(),
                timeMillis = millis,
                impact = m.str("impact")?.trim().orEmpty(),
                forecast = m.str("forecast")?.trim().orEmpty(),
                previous = m.str("previous")?.trim().orEmpty(),
                allDay = allDay,
            )
        }.sortedBy { it.timeMillis }
    }

    private fun parseDate(s: String): Pair<Long, Boolean>? = try {
        Pair(OffsetDateTime.parse(s).toInstant().toEpochMilli(), false)
    } catch (e: DateTimeParseException) {
        try {
            // Date-only entries: anchor at US/Eastern midnight and flag as all-day.
            Pair(LocalDate.parse(s.take(10)).atStartOfDay(MarketClock.NEW_YORK).toInstant().toEpochMilli(), true)
        } catch (e2: DateTimeParseException) {
            null
        }
    }

    /** Events that pass the user's currency + impact filters. Holidays for watched currencies are kept. */
    fun filter(events: List<CalEvent>, settings: Settings): List<CalEvent> {
        val currencies = settings.calendarCurrencies.toSet()
        return events.filter { e ->
            (currencies.isEmpty() || e.country in currencies) &&
                (e.isHigh || (settings.includeMedium && e.isMediumOrHigher) || e.impact.equals("Holiday", true))
        }
    }

    /** Next timed High-impact release that hasn't printed more than [graceMs] ago. */
    fun nextHigh(events: List<CalEvent>, now: Long, graceMs: Long = 2 * 60_000L): CalEvent? =
        events.filter { it.isHigh && !it.allDay && it.timeMillis >= now - graceMs }.minByOrNull { it.timeMillis }

    /** Releases sharing the same timestamp as [event] (e.g. CPI m/m + Core CPI m/m + CPI y/y at 08:30). */
    fun cluster(events: List<CalEvent>, event: CalEvent): List<CalEvent> =
        events.filter { it.timeMillis == event.timeMillis && it.isMediumOrHigher }

    fun isCchCatalyst(e: CalEvent, keywords: List<String>): Boolean =
        e.isHigh && keywords.any { k -> e.title.contains(k, ignoreCase = true) }

    /**
     * CCH catalysts in the current CME trading day (18:00 ET → 17:00 ET), still ahead of or
     * within 30 minutes after [now]. This is the deterministic "CCH ARMED" signal on the widget.
     */
    fun cchCatalystsToday(events: List<CalEvent>, now: Long, keywords: List<String>): List<CalEvent> {
        val (start, end) = MarketClock.tradingDayBounds(now)
        return events.filter {
            isCchCatalyst(it, keywords) && it.timeMillis in start until end && it.timeMillis >= now - 30 * 60_000L
        }
    }
}

/** Session/market-hours helpers anchored on New York time (CME equity index futures). */
object MarketClock {
    val NEW_YORK: ZoneId = ZoneId.of("America/New_York")

    enum class Session(val label: String) {
        CLOSED("Weekend · CME closed"),
        MAINTENANCE("CME maintenance break"),
        ASIA("Asia session"),
        LONDON("London session"),
        PRE_NY("Pre-NY / London–NY overlap"),
        NY_AM("NY AM session"),
        NY_LUNCH("NY lunch"),
        NY_PM("NY PM session"),
        POST_CLOSE("Post-close Globex"),
    }

    fun session(now: Long): Session {
        val t = java.time.Instant.ofEpochMilli(now).atZone(NEW_YORK)
        val minutes = t.hour * 60 + t.minute
        val dow = t.dayOfWeek.value // Mon=1..Sun=7
        val closedWeekend = (dow == 5 && minutes >= 17 * 60) || dow == 6 || (dow == 7 && minutes < 18 * 60)
        if (closedWeekend) return Session.CLOSED
        if (minutes in (17 * 60) until (18 * 60)) return Session.MAINTENANCE
        return when (minutes) {
            in (18 * 60) until (24 * 60), in 0 until 2 * 60 -> Session.ASIA
            in (2 * 60) until (7 * 60) -> Session.LONDON
            in (7 * 60) until (9 * 60 + 30) -> Session.PRE_NY
            in (9 * 60 + 30) until (12 * 60) -> Session.NY_AM
            in (12 * 60) until (13 * 60 + 30) -> Session.NY_LUNCH
            in (13 * 60 + 30) until (16 * 60) -> Session.NY_PM
            else -> Session.POST_CLOSE
        }
    }

    fun isWeekendClosed(now: Long): Boolean = session(now) == Session.CLOSED

    /** [start, end) of the CME trading day containing [now]: 18:00 ET previous day → 17:00 ET. */
    fun tradingDayBounds(now: Long): Pair<Long, Long> {
        val t = java.time.Instant.ofEpochMilli(now).atZone(NEW_YORK)
        val tradeDate = if (t.hour >= 18) t.toLocalDate().plusDays(1) else t.toLocalDate()
        val start = tradeDate.minusDays(1).atTime(18, 0).atZone(NEW_YORK).toInstant().toEpochMilli()
        val end = tradeDate.atTime(17, 0).atZone(NEW_YORK).toInstant().toEpochMilli()
        return start to end
    }
}
