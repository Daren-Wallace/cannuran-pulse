package capital.cannuran.pulse.core

import capital.cannuran.pulse.core.Fixtures.NAIROBI
import capital.cannuran.pulse.core.Fixtures.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CadenceTest {
    private val events = CalendarParser.filter(CalendarParser.parse(Fixtures.CALENDAR), Settings())
    private val s = Settings()

    @Test fun activeWindowWrapsMidnight() {
        // 10:00 → 01:00 EAT
        assertTrue(Cadence.isInActiveWindow(at("2026-11-12T07:00:00Z"), NAIROBI, 10, 1)) // 10:00 EAT
        assertTrue(Cadence.isInActiveWindow(at("2026-11-12T21:30:00Z"), NAIROBI, 10, 1)) // 00:30 EAT
        assertFalse(Cadence.isInActiveWindow(at("2026-11-12T22:00:00Z"), NAIROBI, 10, 1)) // 01:00 EAT
        assertFalse(Cadence.isInActiveWindow(at("2026-11-12T03:00:00Z"), NAIROBI, 10, 1)) // 06:00 EAT
        assertTrue(Cadence.isInActiveWindow(at("2026-11-12T03:00:00Z"), NAIROBI, 5, 5)) // 24h
        assertTrue(Cadence.isInActiveWindow(at("2026-11-12T09:00:00Z"), NAIROBI, 9, 17))
    }

    @Test fun eventModeKicksInAroundHighImpact() {
        // 30 min before CPI (13:30Z)
        assertEquals(Cadence.Mode.EVENT, Cadence.mode(at("2026-11-12T13:00:00Z"), NAIROBI, s, events))
        // 2h before CPI, inside active window
        assertEquals(Cadence.Mode.ACTIVE, Cadence.mode(at("2026-11-12T11:30:00Z"), NAIROBI, s, events))
        // disabled
        assertEquals(Cadence.Mode.ACTIVE, Cadence.mode(at("2026-11-12T13:00:00Z"), NAIROBI, s.copy(eventMode = false), events))
        // weekend beats everything
        assertEquals(Cadence.Mode.WEEKEND, Cadence.mode(at("2026-11-14T13:00:00Z"), NAIROBI, s, events))
        // 05:00 EAT on a weekday
        assertEquals(Cadence.Mode.OFF_HOURS, Cadence.mode(at("2026-11-12T02:00:00Z"), NAIROBI, s, events))
    }

    @Test fun decidesDueness() {
        val now = at("2026-11-12T11:30:00Z")
        fun d(last: Long, key: Boolean = true, force: Boolean = false) =
            Cadence.decide(now, NAIROBI, s, events, last, key, force)
        assertTrue(d(0).due)
        assertFalse(d(now - 30 * 60_000L).due)
        assertTrue(d(now - 58 * 60_000L).due) // within 2-minute slack of 60m
        assertFalse(d(0, key = false).due)
        assertTrue(d(now - 60_000L, force = true).due)
        assertFalse(d(now - 60_000L, key = false, force = true).due)
    }

    @Test fun weekendPausedByDefaultButConfigurable() {
        val sat = at("2026-11-14T10:00:00Z")
        assertFalse(Cadence.decide(sat, NAIROBI, s, events, 0, true, false).due)
        assertTrue(Cadence.decide(sat, NAIROBI, s.copy(weekendCadenceMin = 360), events, 0, true, false).due)
    }

    @Test fun eventCadenceIsTight() {
        val now = at("2026-11-12T13:20:00Z")
        val dec = Cadence.decide(now, NAIROBI, s, events, now - 16 * 60_000L, true, false)
        assertEquals(Cadence.Mode.EVENT, dec.mode)
        assertEquals(15, dec.cadenceMin)
        assertTrue(dec.due)
    }

    @Test fun sanitizeClampsAbuse() {
        val x = Settings(activeCadenceMin = 1, maxSearches = 99, offHoursCadenceMin = 5, activeStartHour = 25).sanitized()
        assertEquals(15, x.activeCadenceMin)
        assertEquals(10, x.maxSearches)
        assertEquals(30, x.offHoursCadenceMin)
        assertEquals(1, x.activeStartHour)
    }

    @Test fun staleness() {
        assertFalse(Cadence.isStale(at("2026-11-12T12:00:00Z"), at("2026-11-12T11:00:00Z"), 60))
        assertTrue(Cadence.isStale(at("2026-11-12T13:01:00Z"), at("2026-11-12T11:00:00Z"), 60))
    }
}

class CostTest {
    @Test fun dailyBriefEstimateIsSane() {
        val n = Cost.briefsPerWeekday(Settings())
        // 15h active @60m = 15, 9h off @240m = 2.25, event extra 2*(75/15-75/60)=7.5, post 1 → 25.75
        org.junit.Assert.assertEquals(25.75, n, 1e-9)
        val cheap = Cost.briefsPerWeekday(Settings(activeCadenceMin = 120, offHoursCadenceMin = 0, eventMode = false, postEventBriefMin = 0))
        org.junit.Assert.assertEquals(7.5, cheap, 1e-9)
        org.junit.Assert.assertTrue(Cost.typicalBrief(ClaudeModel.HAIKU) < Cost.typicalBrief(ClaudeModel.SONNET))
        org.junit.Assert.assertEquals(0.05 + 0.04, Cost.typicalBrief(ClaudeModel.SONNET), 1e-9)
    }
}
