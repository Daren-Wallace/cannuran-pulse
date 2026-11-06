package capital.cannuran.pulse.core

import capital.cannuran.pulse.core.Fixtures.NOW
import capital.cannuran.pulse.core.Fixtures.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarTest {
    private val all = CalendarParser.parse(Fixtures.CALENDAR)

    @Test fun parsesAndSortsValidEventsOnly() {
        assertEquals(10, all.size) // two malformed rows dropped
        assertEquals(all.sortedBy { it.timeMillis }, all)
        val cpi = all.first { it.title == "CPI m/m" }
        assertEquals(at("2026-11-12T13:30:00Z"), cpi.timeMillis)
        assertEquals("0.3%", cpi.forecast)
        assertTrue(cpi.isHigh)
    }

    @Test fun filtersByCurrencyAndImpact() {
        val usd = CalendarParser.filter(all, Settings())
        assertTrue(usd.all { it.country == "USD" })
        assertFalse(usd.any { it.impact == "Low" })
        assertTrue(usd.any { it.impact == "Holiday" })
        val highOnly = CalendarParser.filter(all, Settings(includeMedium = false))
        assertFalse(highOnly.any { it.impact == "Medium" })
        val everything = CalendarParser.filter(all, Settings(calendarCurrencies = emptyList()))
        assertTrue(everything.any { it.country == "EUR" } || everything.none { it.impact == "Low" })
    }

    @Test fun nextHighSkipsPastAndHolidays() {
        val next = CalendarParser.nextHigh(all, NOW)!!
        assertEquals(at("2026-11-12T13:30:00Z"), next.timeMillis)
        // 1 minute after CPI it is still "next" (grace), 5 minutes after it rolls to Fed Chair.
        assertEquals("CPI m/m", CalendarParser.nextHigh(all, at("2026-11-12T13:31:00Z"))!!.title)
        assertEquals("Fed Chair Speaks", CalendarParser.nextHigh(all, at("2026-11-12T13:35:00Z"))!!.title)
        assertNull(CalendarParser.nextHigh(all, at("2026-11-20T00:00:00Z")))
    }

    @Test fun clustersSimultaneousReleases() {
        val cpi = all.first { it.title == "CPI m/m" }
        assertEquals(setOf("CPI m/m", "Core CPI m/m", "Unemployment Claims"),
            CalendarParser.cluster(all, cpi).map { it.title }.toSet())
    }

    @Test fun cchArmsOnCatalystDaysOnly() {
        val kw = Settings().cchKeywords
        val today = CalendarParser.cchCatalystsToday(all, NOW, kw)
        assertEquals(setOf("CPI m/m", "Core CPI m/m", "Fed Chair Speaks"), today.map { it.title }.toSet())
        // Friday's Retail Sales is High impact but not a CCH catalyst.
        assertTrue(CalendarParser.cchCatalystsToday(all, at("2026-11-13T12:00:00Z"), kw).isEmpty())
        // After 17:00 ET the next trading day begins; Thursday's catalysts drop off.
        assertTrue(CalendarParser.cchCatalystsToday(all, at("2026-11-12T23:30:00Z"), kw).isEmpty())
    }

    @Test fun sessionsFollowNewYorkClock() {
        assertEquals(MarketClock.Session.PRE_NY, MarketClock.session(NOW))
        assertEquals(MarketClock.Session.NY_AM, MarketClock.session(at("2026-11-12T15:00:00Z")))
        assertEquals(MarketClock.Session.LONDON, MarketClock.session(at("2026-11-12T08:00:00Z")))
        assertEquals(MarketClock.Session.MAINTENANCE, MarketClock.session(at("2026-11-12T22:30:00Z")))
        assertEquals(MarketClock.Session.ASIA, MarketClock.session(at("2026-11-13T01:00:00Z")))
        // Friday 17:00 ET → Sunday 18:00 ET closed (EST = UTC-5).
        assertEquals(MarketClock.Session.CLOSED, MarketClock.session(at("2026-11-13T22:30:00Z")))
        assertEquals(MarketClock.Session.CLOSED, MarketClock.session(at("2026-11-15T22:59:00Z")))
        assertEquals(MarketClock.Session.ASIA, MarketClock.session(at("2026-11-15T23:01:00Z")))
    }

    @Test fun tradingDayBoundsHandleEveningRollover() {
        val (s, e) = MarketClock.tradingDayBounds(NOW)
        assertEquals(at("2026-11-11T23:00:00Z"), s)
        assertEquals(at("2026-11-12T22:00:00Z"), e)
        val (s2, _) = MarketClock.tradingDayBounds(at("2026-11-12T23:30:00Z"))
        assertEquals(at("2026-11-12T23:00:00Z"), s2)
        assertNotNull(s2)
    }
}
