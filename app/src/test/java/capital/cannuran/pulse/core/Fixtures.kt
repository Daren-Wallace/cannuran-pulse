package capital.cannuran.pulse.core

import java.time.Instant
import java.time.ZoneId

object Fixtures {
    val NAIROBI: ZoneId = ZoneId.of("Africa/Nairobi")

    /** Thu 2026-11-12 13:00 UTC = 08:00 ET (EST) = 16:00 EAT. */
    val NOW: Long = Instant.parse("2026-11-12T13:00:00Z").toEpochMilli()

    fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()

    const val CALENDAR = """[
      {"title":"Bank Holiday","country":"JPY","date":"2026-11-12T00:00:00-05:00","impact":"Holiday","forecast":"","previous":""},
      {"title":"German Final CPI m/m","country":"EUR","date":"2026-11-12T02:00:00-05:00","impact":"Low","forecast":"0.2%","previous":"0.2%"},
      {"title":"CPI m/m","country":"USD","date":"2026-11-12T08:30:00-05:00","impact":"High","forecast":"0.3%","previous":"0.4%"},
      {"title":"Core CPI m/m","country":"USD","date":"2026-11-12T08:30:00-05:00","impact":"High","forecast":"0.3%","previous":"0.3%"},
      {"title":"Unemployment Claims","country":"USD","date":"2026-11-12T08:30:00-05:00","impact":"Medium","forecast":"221K","previous":"218K"},
      {"title":"Natural Gas Storage","country":"USD","date":"2026-11-12T10:30:00-05:00","impact":"Low","forecast":"35B","previous":"41B"},
      {"title":"Fed Chair Speaks","country":"USD","date":"2026-11-12T14:00:00-05:00","impact":"High","forecast":"","previous":""},
      {"title":"30-y Bond Auction","country":"USD","date":"2026-11-12T13:01:00-05:00","impact":"Medium","forecast":"","previous":"4.71|2.4"},
      {"title":"Retail Sales m/m","country":"USD","date":"2026-11-13T08:30:00-05:00","impact":"High","forecast":"0.4%","previous":"0.6%"},
      {"title":"Bank Holiday","country":"USD","date":"2026-11-11T00:00:00-05:00","impact":"Holiday","forecast":"","previous":""},
      {"title":"","country":"USD","date":"2026-11-13T09:00:00-05:00","impact":"High"},
      {"title":"Broken","country":"USD","date":"not-a-date","impact":"High"}
    ]"""

    const val YAHOO_NQ = """{"chart":{"result":[{"meta":{"currency":"USD","symbol":"NQ=F","regularMarketPrice":21456.75,
      "chartPreviousClose":21300.0,"previousClose":21360.25},"timestamp":[1,2],
      "indicators":{"quote":[{"close":[21400.0,21456.75]}]}}],"error":null}}"""

    const val YAHOO_NO_META_PRICE = """{"chart":{"result":[{"meta":{"symbol":"^VIX","chartPreviousClose":16.0},
      "indicators":{"quote":[{"close":[15.5,null,15.2,null]}]}}],"error":null}}"""

    const val BRIEF_JSON = """{
      "bias": "Risk-on into NY: yields slipping ahead of CPI; NVDA strength lifting NQ. CPI 08:30 ET is the swing factor.",
      "risk": "ELEVATED",
      "cch": {"flag": true, "reason": "CPI and Fed Chair remarks today"},
      "headlines": [
        {"title":"Treasury yields ease as traders brace for October CPI","why":"Lower 10Y supports long-duration tech multiples","impact":"HIGH","direction":"BULL","instruments":["NQ","ES"],"source":"Reuters","url":"https://www.reuters.com/markets/us/yields-cpi-2026-11-12/","published_utc":"2026-11-12T11:40:00Z"},
        {"title":"Nvidia extends gains on fresh hyperscaler AI capex commitments","why":"Largest NQ weight; drives index breadth","impact":"MED","direction":"bull","instruments":["nq"],"source":"CNBC","url":"https://www.cnbc.com/2026/11/12/nvidia.html","published_utc":"2026-11-12T10:05:00Z"},
        {"title":"Oil slips as Strait of Hormuz tanker traffic normalises","why":"Eases inflation-expectations pressure","impact":"LOW","direction":"MIXED","instruments":["ES"],"source":"Bloomberg","url":"","published_utc":null},
        {"title":"Treasury yields ease as traders brace for the October CPI","why":"dup","impact":"LOW","direction":"BULL","source":"Reuters","url":"https://www.reuters.com/dup"},
        {"title":"","why":"no title -> dropped"}
      ],
      "watch": ["08:30 ET CPI vs 0.3% fcst", "14:00 ET Fed Chair remarks", "30Y auction tail"]
    }"""

    /** A realistic Messages API reply: searches, results, then citation-split text blocks. */
    val CLAUDE_RESPONSE: String = Json.stringify(linkedMapOf(
        "id" to "msg_01", "type" to "message", "role" to "assistant", "model" to "claude-sonnet-5-5",
        "stop_reason" to "end_turn",
        "content" to listOf(
            linkedMapOf("type" to "text", "text" to "I'll search for the latest."),
            linkedMapOf("type" to "server_tool_use", "id" to "srvtoolu_1", "name" to "web_search",
                "input" to linkedMapOf("query" to "stock futures today")),
            linkedMapOf("type" to "web_search_tool_result", "tool_use_id" to "srvtoolu_1", "content" to listOf(
                linkedMapOf("type" to "web_search_result", "url" to "https://www.bloomberg.com/news/oil",
                    "title" to "Oil", "encrypted_content" to "x", "page_age" to "1 hour ago"))),
            linkedMapOf("type" to "text", "text" to BRIEF_JSON.substring(0, 200)),
            linkedMapOf("type" to "text", "text" to BRIEF_JSON.substring(200), "citations" to listOf(
                linkedMapOf("type" to "web_search_result_location", "url" to "https://www.bloomberg.com/news/oil",
                    "title" to "Oil", "encrypted_index" to "e", "cited_text" to "Oil slipped..."))),
        ),
        "usage" to linkedMapOf("input_tokens" to 21500, "output_tokens" to 900,
            "cache_read_input_tokens" to 1800, "cache_creation_input_tokens" to 0,
            "server_tool_use" to linkedMapOf("web_search_requests" to 4)),
    ))

    val CLAUDE_PAUSED: String = Json.stringify(linkedMapOf(
        "type" to "message", "stop_reason" to "pause_turn",
        "content" to listOf(
            linkedMapOf("type" to "server_tool_use", "id" to "srvtoolu_9", "name" to "web_search",
                "input" to linkedMapOf("query" to "Fed speakers today")),
        ),
        "usage" to linkedMapOf("input_tokens" to 9000, "output_tokens" to 50,
            "server_tool_use" to linkedMapOf("web_search_requests" to 2)),
    ))
}

/** Scriptable fake HTTP client that records requests. */
class FakeHttp(private val handler: (url: String, body: String?) -> HttpResult) : HttpClient {
    val calls = mutableListOf<Pair<String, String?>>()
    override fun execute(url: String, headers: Map<String, String>, body: String?): HttpResult {
        calls.add(url to body)
        return handler(url, body)
    }
}
