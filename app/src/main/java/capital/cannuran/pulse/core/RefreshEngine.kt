package capital.cannuran.pulse.core

import java.time.ZoneId

/** Minimal HTTP abstraction so the whole refresh pipeline runs in plain JVM tests. */
fun interface HttpClient {
    /** POST when [body] is non-null, GET otherwise. Must not throw for HTTP error statuses. */
    fun execute(url: String, headers: Map<String, String>, body: String?): HttpResult
}

data class HttpResult(val status: Int, val body: String)

/**
 * One refresh cycle: calendar (hourly), quotes (every run), and a Claude brief when the
 * cadence says one is due. Returns the new snapshot plus side information for notifications.
 */
class RefreshEngine(
    private val http: HttpClient,
    private val zone: ZoneId,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Outcome(
        val snapshot: Snapshot,
        val decision: Cadence.Decision,
        val briefed: Boolean,
        val usage: Usage?,
        /** HIGH-impact headlines that weren't on screen before — candidates for an alert. */
        val newHighHeadlines: List<Headline>,
    )

    companion object {
        const val CALENDAR_URL = "https://nfs.faireconomy.media/ff_calendar_thisweek.json"
        const val CALENDAR_TTL_MS = 60 * 60_000L
        const val USER_AGENT = "CannuranPulse/1.0 (Android lock-screen widget)"
    }

    fun refresh(prev: Snapshot, settings: Settings, apiKey: String?, force: Boolean, reason: String): Outcome {
        val s = settings.sanitized()
        val now = clock()
        var snap = prev.copy(lastAttemptAt = now)

        // 1. Calendar — free, but the feed asks clients not to hammer it: at most hourly.
        if (force || snap.events.isEmpty() || now - snap.calendarFetchedAt > CALENDAR_TTL_MS) {
            snap = try {
                val r = http.execute(CALENDAR_URL, mapOf("User-Agent" to USER_AGENT), null)
                if (r.status != 200) throw IllegalStateException("Calendar HTTP ${r.status}")
                snap.copy(events = CalendarParser.filter(CalendarParser.parse(r.body), s),
                    calendarFetchedAt = now, calendarError = null)
            } catch (e: Exception) {
                snap.copy(calendarError = "Calendar: ${e.message ?: e.javaClass.simpleName}".take(160))
            }
        }

        // 2. Quotes — best effort, never fatal.
        if (s.quoteSymbols.isNotEmpty()) {
            val quotes = ArrayList<Quote>()
            var failures = 0
            for (sym in s.quoteSymbols) {
                try {
                    val r = http.execute(QuoteParser.url(sym), mapOf("User-Agent" to "Mozilla/5.0 $USER_AGENT"), null)
                    val q = if (r.status == 200) QuoteParser.parse(sym, r.body) else null
                    if (q != null) quotes.add(q) else failures++
                } catch (e: Exception) { failures++ }
            }
            snap = if (quotes.isNotEmpty()) snap.copy(quotes = quotes, quotesFetchedAt = now,
                quoteError = if (failures > 0) "$failures quote(s) unavailable" else null)
            else snap.copy(quoteError = "Quotes unavailable")
        } else {
            snap = snap.copy(quotes = emptyList(), quoteError = null)
        }

        // 3. Claude brief — the only paid step.
        val decision = Cadence.decide(now, zone, s, snap.events, prev.brief?.generatedAt ?: 0L,
            hasKey = !apiKey.isNullOrBlank(), force = force)
        if (!decision.due) {
            val err = if (apiKey.isNullOrBlank()) "Add your Claude API key in Pulse settings" else snap.briefError
            return Outcome(snap.copy(briefError = err), decision, false, null, emptyList())
        }

        val why = if (force) reason else "$reason · ${decision.reason}"
        return try {
            val (brief, usage) = fetchBrief(apiKey!!, s, snap, now, why)
            val newHigh = if (prev.brief == null) emptyList() else brief.headlines.filter { it.isNew && it.impact == Impact.HIGH }
            Outcome(snap.copy(brief = brief, briefError = null), decision, true, usage, newHigh)
        } catch (e: ClaudeProtocol.ApiException) {
            Outcome(snap.copy(briefError = e.message), decision, false, null, emptyList())
        } catch (e: BriefParser.BriefFormatException) {
            Outcome(snap.copy(briefError = "Brief: ${e.message}"), decision, false, null, emptyList())
        } catch (e: Exception) {
            Outcome(snap.copy(briefError = "Network: ${e.message ?: e.javaClass.simpleName}".take(160)), decision, false, null, emptyList())
        }
    }

    private fun fetchBrief(apiKey: String, s: Settings, snap: Snapshot, now: Long, reason: String): Pair<Brief, Usage> {
        val system = PromptBuilder.system(s)
        val messages = mutableListOf(ClaudeProtocol.userMessage(
            PromptBuilder.context(now, zone, snap.events, snap.brief, reason, s)))
        var total = Usage()
        var cited = emptyList<String>()
        var text = ""
        var attempt = 0
        while (true) {
            val body = ClaudeProtocol.requestBody(s.model, system, messages, s.maxSearches)
            val r = http.execute(ClaudeProtocol.ENDPOINT, ClaudeProtocol.headers(apiKey), body)
            if (r.status != 200) throw ClaudeProtocol.ApiException(r.status, ClaudeProtocol.describeHttpError(r.status, r.body))
            val resp = ClaudeProtocol.parseResponse(r.body)
            total += resp.usage
            cited = (cited + resp.citedUrls).distinct()
            text = resp.finalText
            if (resp.stopReason == "pause_turn" && attempt < ClaudeProtocol.MAX_CONTINUATIONS) {
                // Resume: echo the paused assistant turn back unchanged and let Claude continue.
                messages.add(linkedMapOf("role" to "assistant", "content" to resp.content))
                attempt++
                continue
            }
            break
        }
        val brief = BriefParser.parse(text, now, s.model, reason, cited, snap.brief)
        return brief to total
    }
}
