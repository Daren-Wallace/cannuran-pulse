package capital.cannuran.pulse.core

import capital.cannuran.pulse.core.Fixtures.NOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BriefParserTest {

    private fun parse(text: String, cited: List<String> = emptyList(), prev: Brief? = null) =
        BriefParser.parse(text, NOW, ClaudeModel.SONNET, "test", cited, prev)

    @Test fun parsesCleanJson() {
        val b = parse(Fixtures.BRIEF_JSON)
        assertEquals(Risk.ELEVATED, b.risk)
        assertTrue(b.cchFlag)
        assertEquals("CPI and Fed Chair remarks today", b.cchReason)
        assertEquals(3, b.headlines.size) // duplicate merged, untitled dropped
        assertEquals(Impact.HIGH, b.headlines[0].impact)
        assertEquals(Direction.BULL, b.headlines[1].direction) // lowercase normalised
        assertEquals(listOf("NQ"), b.headlines[1].instruments)
        assertEquals(listOf("ES"), b.headlines[2].instruments)
        assertEquals(Fixtures.at("2026-11-12T11:40:00Z"), b.headlines[0].publishedAt)
        assertNull(b.headlines[2].publishedAt)
        assertEquals(3, b.watch.size)
        assertTrue(b.headlines.all { it.isNew })
    }

    @Test fun toleratesFencesAndProse() {
        val wrapped = "Here is the brief you asked for:\n```json\n${Fixtures.BRIEF_JSON}\n```\nLet me know {if} you need more."
        assertEquals(3, parse(wrapped).headlines.size)
    }

    @Test fun skipsUnrelatedBracesBeforeTheBrief() {
        val text = "Searched {3} sources. {\"note\": 1} then " + Fixtures.BRIEF_JSON
        assertEquals(Risk.ELEVATED, parse(text).risk)
    }

    @Test fun bracesInsideStringsDontConfuseExtraction() {
        val text = """{"bias":"Tape is {choppy} into \"CPI\"","risk":"LOW","headlines":[{"title":"Yields } flat","impact":"low"}]}"""
        val b = parse(text)
        assertEquals("Tape is {choppy} into \"CPI\"", b.bias)
        assertEquals("Yields } flat", b.headlines.single().title)
    }

    @Test fun fillsMissingUrlFromMatchingCitation() {
        val b = parse(Fixtures.BRIEF_JSON, cited = listOf("https://www.cnbc.com/x", "https://www.bloomberg.com/news/oil"))
        assertEquals("https://www.bloomberg.com/news/oil", b.headlines[2].url)
    }

    @Test fun rejectsNonUrlsAndFutureTimestamps() {
        val text = """{"bias":"x","headlines":[{"title":"A","url":"javascript:alert(1)","published_utc":"2027-01-01T00:00:00Z"}]}"""
        val h = parse(text).headlines.single()
        assertEquals("", h.url)
        assertNull(h.publishedAt)
        assertEquals("", h.source)
    }

    @Test fun marksNoveltyAgainstPreviousBrief() {
        val prev = parse(Fixtures.BRIEF_JSON)
        val next = parse(Fixtures.BRIEF_JSON.replace("Nvidia extends gains on fresh hyperscaler AI capex commitments",
            "Apple cuts iPhone forecast on weak China demand"), prev = prev)
        assertFalse(next.headlines[0].isNew)
        assertTrue(next.headlines[1].isNew)
    }

    @Test fun failsLoudlyOnGarbage() {
        for (bad in listOf("", "No news today.", "{\"foo\": 1}", "{\"bias\":\"\",\"headlines\":[]}")) {
            try { parse(bad); fail("expected failure for '$bad'") } catch (e: BriefParser.BriefFormatException) { /* ok */ }
        }
    }

    @Test fun capsHeadlineCount() {
        val topics = listOf("Nvidia beats estimates", "Oil spikes on Hormuz", "Jobless claims fall", "Apple guides lower",
            "Yen intervention fears", "Tariff deadline extended", "Microsoft capex raised", "Gold record high",
            "Shutdown vote delayed", "Bond auction tails", "Tesla deliveries miss", "ECB holds rates")
        val many = topics.joinToString(",") { """{"title":"$it","impact":"LOW"}""" }
        assertEquals(PromptBuilder.MAX_HEADLINES, parse("""{"bias":"b","headlines":[$many]}""").headlines.size)
    }

    @Test fun noveltyHeuristic() {
        assertTrue(Novelty.similar("Fed's Waller says December cut still on the table",
            "Waller: December cut still on the table, Fed says"))
        assertFalse(Novelty.similar("Oil jumps on Hormuz tension", "Nvidia beats on data center revenue"))
        assertFalse(Novelty.similar("Nasdaq futures rise as Nvidia gains", "Nasdaq futures rise as Apple gains"))
    }

    @Test fun hostFallback() {
        assertEquals("Reuters", BriefParser.hostOf("https://www.reuters.com/markets/x"))
        assertEquals("", BriefParser.hostOf(""))
    }
}
