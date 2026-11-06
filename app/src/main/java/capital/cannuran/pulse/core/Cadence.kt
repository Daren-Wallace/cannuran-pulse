package capital.cannuran.pulse.core

import java.time.Instant
import java.time.ZoneId

/**
 * Decides when a (paid) Claude brief is due. Calendar and quotes are free and refresh on
 * every worker run; Claude calls follow the session-aware cadence below.
 */
object Cadence {

    enum class Mode { EVENT, ACTIVE, OFF_HOURS, WEEKEND }

    data class Decision(val due: Boolean, val mode: Mode, val cadenceMin: Int, val reason: String)

    /** Event mode window: from this long before a High release until this long after it. */
    const val EVENT_LEAD_MIN = 45
    const val EVENT_TAIL_MIN = 30

    fun isInActiveWindow(now: Long, zone: ZoneId, startHour: Int, endHour: Int): Boolean {
        val hour = Instant.ofEpochMilli(now).atZone(zone).hour
        return when {
            startHour == endHour -> true // 24h window
            startHour < endHour -> hour in startHour until endHour
            else -> hour >= startHour || hour < endHour // wraps midnight
        }
    }

    fun mode(now: Long, zone: ZoneId, s: Settings, events: List<CalEvent>): Mode {
        if (MarketClock.isWeekendClosed(now)) return Mode.WEEKEND
        if (s.eventMode && nearHighImpact(now, events)) return Mode.EVENT
        return if (isInActiveWindow(now, zone, s.activeStartHour, s.activeEndHour)) Mode.ACTIVE else Mode.OFF_HOURS
    }

    fun nearHighImpact(now: Long, events: List<CalEvent>): Boolean = events.any {
        it.isHigh && !it.allDay &&
            now >= it.timeMillis - EVENT_LEAD_MIN * 60_000L && now <= it.timeMillis + EVENT_TAIL_MIN * 60_000L
    }

    fun decide(
        now: Long,
        zone: ZoneId,
        settings: Settings,
        events: List<CalEvent>,
        lastBriefAt: Long,
        hasKey: Boolean,
        force: Boolean,
    ): Decision {
        val s = settings.sanitized()
        val m = mode(now, zone, s, events)
        val cadence = when (m) {
            Mode.EVENT -> minOf(s.eventCadenceMin, s.activeCadenceMin)
            Mode.ACTIVE -> s.activeCadenceMin
            Mode.OFF_HOURS -> s.offHoursCadenceMin
            Mode.WEEKEND -> s.weekendCadenceMin
        }
        if (!hasKey) return Decision(false, m, cadence, "No API key set")
        if (force) return Decision(true, m, cadence, "Manual refresh")
        if (cadence <= 0) return Decision(false, m, cadence, "Briefs paused in ${m.name.lowercase()} mode")
        // Two-minute slack so a 15-minute periodic job doesn't keep missing a 15-minute cadence.
        val elapsedMin = (now - lastBriefAt) / 60_000.0
        val due = lastBriefAt <= 0 || elapsedMin >= cadence - 2
        return Decision(due, m, cadence,
            if (due) "Scheduled (${m.name.lowercase()}, every ${cadence}m)" else "Next brief in ~${(cadence - elapsedMin).toInt().coerceAtLeast(1)}m")
    }

    /** A brief is stale on screen once it is older than twice its cadence (or 3h if paused). */
    fun isStale(now: Long, generatedAt: Long, cadenceMin: Int): Boolean {
        val limitMin = if (cadenceMin <= 0) 180 else cadenceMin * 2
        return now - generatedAt > limitMin * 60_000L
    }
}
