package capital.cannuran.pulse.core

import capital.cannuran.pulse.core.Fixtures.NAIROBI
import capital.cannuran.pulse.core.Fixtures.NOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {

    private fun routes(claude: (String?) -> HttpResult) = FakeHttp { url, body ->
        when {
            url.startsWith(RefreshEngine.CALENDAR_URL) -> HttpResult(200, Fixtures.CALENDAR)
            url.contains("finance.yahoo.com") -> HttpResult(200, Fixtures.YAHOO_NQ)
            url == ClaudeProtocol.ENDPOINT -> claude(body)
            else -> HttpResult(404, "")
        }
    }

    private fun engine(http: HttpClient) = RefreshEngine(http, NAIROBI) { NOW }

    @Test fun fullCycleProducesBriefCalendarQuotesAndUsage() {
        val http = routes { HttpResult(200, Fixtures.CLAUDE_RESPONSE) }
        val out = engine(http).refresh(Snapshot(), Settings(), "sk-ant-test", force = false, reason = "periodic")
        assertTrue(out.briefed)
        val b = out.snapshot.brief!!
        assertEquals(3, b.headlines.size)
        assertEquals("https://www.bloomberg.com/news/oil", b.headlines[2].url) // from citations
        assertEquals(4L, out.usage!!.webSearches)
        assertTrue(out.snapshot.events.isNotEmpty())
        assertEquals(4, out.snapshot.quotes.size)
        assertNull(out.snapshot.briefError)
        assertTrue(out.newHighHeadlines.isEmpty()) // first brief never alerts

        // Request shape: model, cached system prompt, web search tool with max_uses, context w/ calendar.
        val req = Json.parseObject(http.calls.last { it.first == ClaudeProtocol.ENDPOINT }.second!!)
        assertEquals("claude-sonnet-5-5", req.str("model"))
        val tool = req.arr("tools").objects().single()
        assertEquals(ClaudeProtocol.WEB_SEARCH_TOOL, tool.str("type"))
        assertEquals(5L, tool.long("max_uses"))
        assertEquals("ephemeral", req.arr("system").objects().single().obj("cache_control")!!.str("type"))
        val ctx = req.arr("messages").objects().first().str("content")!!
        assertTrue(ctx.contains("CPI m/m"))
        assertTrue(ctx.contains("[CCH catalyst]"))
        assertTrue(ctx.contains("Africa/Nairobi"))
    }

    @Test fun resumesAfterPauseTurn() {
        var n = 0
        val http = routes { body ->
            n++
            if (n == 1) HttpResult(200, Fixtures.CLAUDE_PAUSED) else {
                val msgs = Json.parseObject(body!!).arr("messages").objects()
                assertEquals("assistant", msgs.last().str("role")) // paused turn echoed back
                HttpResult(200, Fixtures.CLAUDE_RESPONSE)
            }
        }
        val out = engine(http).refresh(Snapshot(), Settings(), "k", force = true, reason = "manual")
        assertTrue(out.briefed)
        assertEquals(2, n)
        assertEquals(6L, out.usage!!.webSearches)
        assertEquals(30500L, out.usage!!.inputTokens)
    }

    @Test fun apiErrorKeepsPreviousBriefAndExplains() {
        val prior = engine(routes { HttpResult(200, Fixtures.CLAUDE_RESPONSE) })
            .refresh(Snapshot(), Settings(), "k", true, "seed").snapshot
        val http = routes { HttpResult(401, """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}""") }
        val out = engine(http).refresh(prior, Settings(), "bad", force = true, reason = "manual")
        assertFalse(out.briefed)
        assertNotNull(out.snapshot.brief)
        assertTrue(out.snapshot.briefError!!.startsWith("API key rejected"))
        assertTrue(out.snapshot.briefError!!.contains("invalid x-api-key"))
    }

    @Test fun noKeyStillRefreshesFreeData() {
        val http = routes { error("Claude must not be called without a key") }
        val out = engine(http).refresh(Snapshot(), Settings(), null, force = true, reason = "manual")
        assertFalse(out.briefed)
        assertTrue(out.snapshot.events.isNotEmpty())
        assertTrue(out.snapshot.briefError!!.contains("API key"))
    }

    @Test fun calendarIsCachedForAnHour() {
        val http = routes { HttpResult(200, Fixtures.CLAUDE_RESPONSE) }
        val first = engine(http).refresh(Snapshot(), Settings(), null, false, "p").snapshot
        val calls = http.calls.count { it.first == RefreshEngine.CALENDAR_URL }
        engine(http).refresh(first, Settings(), null, false, "p")
        assertEquals(calls, http.calls.count { it.first == RefreshEngine.CALENDAR_URL })
    }

    @Test fun failuresDegradeGracefully() {
        val http = FakeHttp { url, _ ->
            if (url.startsWith(RefreshEngine.CALENDAR_URL)) throw java.io.IOException("timeout") else HttpResult(500, "oops")
        }
        val out = engine(http).refresh(Snapshot(), Settings(), "k", true, "manual")
        assertTrue(out.snapshot.calendarError!!.contains("timeout"))
        assertEquals("Quotes unavailable", out.snapshot.quoteError)
        assertTrue(out.snapshot.briefError!!.startsWith("Anthropic API error"))
    }

    @Test fun newHighImpactHeadlinesAreReportedForAlerts() {
        val first = engine(routes { HttpResult(200, Fixtures.CLAUDE_RESPONSE) })
            .refresh(Snapshot(), Settings(), "k", true, "seed").snapshot
        val swapped = Fixtures.CLAUDE_RESPONSE.replace("Treasury yields ease as traders brace for October CPI",
            "Hot CPI print sends yields surging, Nasdaq futures slide")
        val out = engine(routes { HttpResult(200, swapped) }).refresh(first, Settings(), "k", true, "post-event")
        assertEquals(listOf("Hot CPI print sends yields surging, Nasdaq futures slide"), out.newHighHeadlines.map { it.title })
    }

    @Test fun snapshotSurvivesPersistence() {
        val snap = engine(routes { HttpResult(200, Fixtures.CLAUDE_RESPONSE) })
            .refresh(Snapshot(), Settings(), "k", true, "seed").snapshot
        assertEquals(snap, Snapshot.fromJson(snap.toJson()))
        assertEquals(Snapshot(), Snapshot.fromJson("{corrupt"))
    }

    @Test fun settingsSurvivePersistence() {
        val s = Settings(model = ClaudeModel.HAIKU, activeCadenceMin = 30, focus = "custom \"focus\"",
            quoteSymbols = listOf("NQ=F"), cchKeywords = listOf("FOMC"), weekendCadenceMin = 360)
        assertEquals(s, Settings.fromJson(s.toJson()))
        assertEquals(Settings(), Settings.fromJson("nope"))
    }

    @Test fun usageLedgerRollsDaily() {
        val u = Usage(inputTokens = 1_000_000, outputTokens = 100_000, webSearches = 5)
        val l = UsageLedger().add("2026-11-12", u, ClaudeModel.SONNET)
        assertEquals(2.0 + 1.0 + 0.05, l.costUsd, 1e-9)
        val l2 = l.add("2026-11-12", u, ClaudeModel.SONNET)
        assertEquals(2, l2.calls)
        val l3 = l2.add("2026-11-13", u, ClaudeModel.HAIKU)
        assertEquals(1, l3.calls)
        assertEquals(0.1 + 0.05 + 0.05, l3.costUsd, 1e-9)
        assertEquals(l3, UsageLedger.fromJson(l3.toJson()))
    }
}
