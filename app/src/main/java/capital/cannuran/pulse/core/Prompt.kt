package capital.cannuran.pulse.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds the system prompt and per-refresh context for the news desk call. */
object PromptBuilder {

    const val MAX_HEADLINES = 6

    fun system(settings: Settings): String = """
You are the real-time news desk for a futures day trader. You write the brief that lives on the trader's phone lock screen, so it must be accurate, current, terse and directly relevant to NQ (Nasdaq-100 E-mini) and ES (S&P 500 E-mini) futures.

TRADER CONTEXT
${settings.focus.trim()}

TASK
1. Use web search to find the most market-moving developments for NQ/ES from the last ${settings.lookbackHours} hours, plus anything breaking right now. Run focused searches (e.g. "stock futures today", "Treasury yields today", "Fed speakers today", a specific earnings name, a specific data print). Prefer Reuters, Bloomberg, WSJ, CNBC, FT, AP, MarketWatch, Barron's and primary sources (federalreserve.gov, bls.gov, bea.gov, treasury.gov).
2. Keep only items that can move index futures: Fed/rates and Fed speakers, US macro prints (state actual vs forecast when available), yields and the dollar, mega-cap tech/semis earnings, guidance and AI capex, tariffs/trade, fiscal and shutdown risk, oil and geopolitics, systemic credit or volatility events. Skip single small-cap stories, crypto (unless market-wide), opinion columns and recycled previews.
3. If a scheduled release in the calendar below has already printed, report the actual vs forecast and the market reaction.
4. Judge net bias for NQ/ES right now and the overall risk level for the session.
5. Set cch.flag = true only if a CCH catalyst is due within this trading day or a major unscheduled geopolitical or policy shock is live; give the reason.

HARD RULES
- Every headline must come from a result you found in this turn, with its URL. Never invent stories, numbers, quotes or sources. If unsure, leave it out.
- Paraphrase in your own words; do not copy source headlines verbatim.
- Current facts only: no stale stories presented as new. If it is quiet, return fewer headlines and say so in the bias.
- Order headlines by importance to NQ/ES, most important first. 3 to $MAX_HEADLINES items.

OUTPUT
Reply with ONLY one JSON object: no prose before or after, no markdown fences. Schema:
{
  "bias": "max 140 chars: the net read for NQ/ES right now and the swing factor",
  "risk": "LOW | ELEVATED | HIGH",
  "cch": {"flag": true|false, "reason": "max 80 chars"},
  "headlines": [
    {
      "title": "max 90 chars, paraphrased",
      "why": "max 110 chars: why it matters for NQ/ES",
      "impact": "HIGH | MED | LOW",
      "direction": "BULL | BEAR | MIXED (for NQ/ES)",
      "instruments": ["NQ","ES"],
      "source": "outlet name",
      "url": "https://...",
      "published_utc": "ISO-8601 UTC timestamp, or null if unknown"
    }
  ],
  "watch": ["max 60 chars: next thing to watch", "... up to 3 items"]
}
""".trim()

    private val UTC_FMT = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd HH:mm 'UTC'", Locale.US)
    private val ZONED_FMT = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.US)

    fun context(
        now: Long,
        localZone: ZoneId,
        events: List<CalEvent>,
        previous: Brief?,
        reason: String,
        settings: Settings,
    ): String {
        val instant = Instant.ofEpochMilli(now)
        val sb = StringBuilder()
        sb.append("NOW: ").append(UTC_FMT.format(instant.atOffset(ZoneOffset.UTC)))
            .append(" | New York ").append(ZONED_FMT.format(instant.atZone(MarketClock.NEW_YORK)))
            .append(" | Trader local ").append(ZONED_FMT.format(instant.atZone(localZone)))
            .append(" (").append(localZone.id).append(")\n")
        sb.append("SESSION: ").append(MarketClock.session(now).label).append('\n')
        sb.append("REFRESH REASON: ").append(reason).append("\n\n")

        val window = events.filter { it.timeMillis in (now - 12 * 3_600_000L)..(now + 36 * 3_600_000L) }
        sb.append("SCHEDULED CALENDAR (")
            .append(settings.calendarCurrencies.ifEmpty { listOf("all") }.joinToString(","))
            .append(", last 12h to next 36h; times in New York):\n")
        if (window.isEmpty()) sb.append("- none in feed\n")
        for (e in window) {
            sb.append("- ").append(ZONED_FMT.format(Instant.ofEpochMilli(e.timeMillis).atZone(MarketClock.NEW_YORK)))
                .append(if (e.timeMillis < now) " [past] " else " ")
                .append(e.country).append(' ').append(e.title)
                .append(" (").append(e.impact).append(')')
            if (e.forecast.isNotEmpty()) sb.append(" fcst ").append(e.forecast)
            if (e.previous.isNotEmpty()) sb.append(" prev ").append(e.previous)
            if (CalendarParser.isCchCatalyst(e, settings.cchKeywords)) sb.append(" [CCH catalyst]")
            sb.append('\n')
        }

        if (previous != null && previous.headlines.isNotEmpty()) {
            sb.append("\nPREVIOUSLY ON THE LOCK SCREEN (")
                .append(((now - previous.generatedAt) / 60_000).coerceAtLeast(0)).append(" min ago). ")
                .append("Repeat one only if it is still among the most important or has materially updated:\n")
            for (h in previous.headlines) sb.append("- ").append(h.title).append('\n')
        }
        sb.append("\nReturn the JSON object now.")
        return sb.toString()
    }
}
