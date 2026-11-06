package capital.cannuran.pulse.core

import capital.cannuran.pulse.core.Fixtures.NAIROBI
import capital.cannuran.pulse.core.Fixtures.NOW
import capital.cannuran.pulse.core.Fixtures.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatAndQuoteTest {

    @Test fun parsesYahooChart() {
        val q = QuoteParser.parse("NQ=F", Fixtures.YAHOO_NQ)!!
        assertEquals("NQ", q.label)
        assertEquals(21456.75, q.price, 1e-9)
        assertEquals((21456.75 - 21360.25) / 21360.25 * 100, q.changePct!!, 1e-9) // previousClose preferred
        val v = QuoteParser.parse("^VIX", Fixtures.YAHOO_NO_META_PRICE)!!
        assertEquals(15.2, v.price, 1e-9) // falls back to last non-null close
        assertEquals("VIX", v.label)
        assertNull(QuoteParser.parse("X", """{"chart":{"result":null,"error":{"code":"Not Found"}}}"""))
        assertTrue(QuoteParser.url("^VIX").contains("%5EVIX"))
        assertTrue(QuoteParser.url("NQ=F").contains("NQ%3DF"))
    }

    @Test fun formatsNumbers() {
        assertEquals("21,457", Format.price(21456.75))
        assertEquals("612.4", Format.price(612.43))
        assertEquals("15.20", Format.price(15.2))
        assertEquals("+0.45%", Format.pct(0.4518))
        assertEquals("-1.20%", Format.pct(-1.2))
        assertEquals("", Format.pct(null))
        assertEquals("$0.115", Format.usd(0.1149))
        assertEquals("$3.40", Format.usd(3.4))
        assertEquals("21.5k", Format.tokens(21500))
    }

    @Test fun formatsTimes() {
        assertEquals("16:30", Format.time(at("2026-11-12T13:30:00Z"), NAIROBI))
        assertEquals("16:30", Format.timeOrDay(at("2026-11-12T13:30:00Z"), NOW, NAIROBI))
        assertEquals("Fri 16:30", Format.timeOrDay(at("2026-11-13T13:30:00Z"), NOW, NAIROBI))
        assertEquals("in 30m", Format.countdown(at("2026-11-12T13:30:00Z"), NOW))
        assertEquals("in 2h 05m", Format.countdown(NOW + 125 * 60_000L, NOW))
        assertEquals("now", Format.countdown(NOW + 20_000L, NOW))
        assertEquals("4m ago", Format.countdown(NOW - 4 * 60_000L, NOW))
        assertEquals("12m ago", Format.ago(NOW - 12 * 60_000L, NOW))
        assertEquals("2h ago", Format.ago(NOW - 120 * 60_000L, NOW))
        assertEquals("EAT", Format.zoneAbbrev(NAIROBI, NOW))
    }

    @Test fun lockScreenCardText() {
        val events = CalendarParser.filter(CalendarParser.parse(Fixtures.CALENDAR), Settings())
        val brief = BriefParser.parse(Fixtures.BRIEF_JSON, NOW, ClaudeModel.SONNET, "t")
        val snap = Snapshot(brief = brief, events = events,
            quotes = listOf(Quote("NQ=F", "NQ", 21456.75, 0.45)))
        val (title, body) = Format.cardText(snap, Settings(), NOW, NAIROBI)
        assertEquals("RISK ELEVATED · CCH ARMED · CPI m/m in 30m", title)
        assertTrue(body.startsWith("Risk-on into NY"))
        assertTrue(body.contains("‼ Treasury yields ease"))
        assertTrue(body.contains("NQ 21,457 +0.45%"))
        assertTrue(body.contains("Next: USD CPI m/m · 16:30 · f 0.3% p 0.4%"))
        assertTrue(body.endsWith("Updated 16:00 EAT"))

        val (t2, b2) = Format.cardText(Snapshot(), Settings(), NOW, NAIROBI)
        assertEquals("PULSE", t2)
        assertTrue(b2.contains("API key"))
    }

    @Test fun promptCarriesContext() {
        val events = CalendarParser.filter(CalendarParser.parse(Fixtures.CALENDAR), Settings())
        val prev = BriefParser.parse(Fixtures.BRIEF_JSON, NOW - 3_600_000L, ClaudeModel.SONNET, "t")
        val ctx = PromptBuilder.context(NOW, NAIROBI, events, prev, "manual", Settings())
        assertTrue(ctx.contains("NOW: Thu 2026-11-12 13:00 UTC"))
        assertTrue(ctx.contains("New York Thu 08:00"))
        assertTrue(ctx.contains("Trader local Thu 16:00 (Africa/Nairobi)"))
        assertTrue(ctx.contains("SESSION: Pre-NY"))
        assertTrue(ctx.contains("Thu 08:30 USD CPI m/m (High) fcst 0.3% prev 0.4% [CCH catalyst]"))
        assertTrue(ctx.contains("PREVIOUSLY ON THE LOCK SCREEN (60 min ago)"))
        val sys = PromptBuilder.system(Settings())
        assertTrue(sys.contains("Contingent Catalyst Hedge"))
        assertTrue(sys.contains("3 to 6 items"))
        assertTrue(!sys.contains("\${"))
    }
}
