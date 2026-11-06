package capital.cannuran.pulse.core

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** Turns Claude's text reply into a validated [Brief]. Tolerant of fences, prose and sloppy fields. */
object BriefParser {

    class BriefFormatException(message: String) : RuntimeException(message)

    fun parse(
        text: String,
        now: Long,
        model: ClaudeModel,
        reason: String,
        citedUrls: List<String> = emptyList(),
        previous: Brief? = null,
    ): Brief {
        val obj = extractJsonObject(text) ?: throw BriefFormatException("Claude did not return a JSON brief")
        val cch = obj.obj("cch")
        val headlines = obj.arr("headlines").objects()
            .mapNotNull { h -> toHeadline(h, now) }
            .let { dedupe(it) }
            .map { h -> if (h.url.isEmpty()) h.copy(url = matchCitation(h, citedUrls)) else h }
            .take(PromptBuilder.MAX_HEADLINES)
            .map { h -> h.copy(isNew = previous == null || previous.headlines.none { Novelty.similar(it.title, h.title) }) }
        val bias = obj.str("bias")?.let(::clean).orEmpty()
        if (headlines.isEmpty() && bias.isEmpty()) throw BriefFormatException("Brief was empty")
        return Brief(
            generatedAt = now,
            bias = bias.take(160),
            risk = Risk.parse(obj.str("risk")),
            cchFlag = cch?.bool("flag") ?: obj.bool("cch_flag") ?: false,
            cchReason = (cch?.str("reason") ?: obj.str("cch_reason")).orEmpty().let(::clean).take(100),
            headlines = headlines,
            watch = obj.arr("watch").mapNotNull { it?.toString()?.let(::clean)?.takeIf(String::isNotEmpty)?.take(80) }.take(3),
            model = model.id,
            reason = reason,
        )
    }

    /** Finds the outermost JSON object in [text], skipping markdown fences and surrounding prose. */
    fun extractJsonObject(text: String): Map<String, Any?>? {
        val stripped = text.replace("```json", "```").replace("```JSON", "```")
        // Try each '{' as a start, scanning with string-awareness for its matching '}'.
        var start = stripped.indexOf('{')
        while (start >= 0) {
            val end = matchingBrace(stripped, start)
            if (end > start) {
                try {
                    val candidate = Json.parse(stripped.substring(start, end + 1))
                    @Suppress("UNCHECKED_CAST")
                    if (candidate is Map<*, *> && (candidate.containsKey("headlines") || candidate.containsKey("bias"))) {
                        return candidate as Map<String, Any?>
                    }
                } catch (e: Json.ParseException) { /* keep scanning */ }
            }
            start = stripped.indexOf('{', start + 1)
        }
        return null
    }

    private fun matchingBrace(s: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until s.length) {
            val c = s[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return i }
            }
        }
        return -1
    }

    private fun toHeadline(h: Map<String, Any?>, now: Long): Headline? {
        val title = h.str("title")?.let(::clean).orEmpty()
        if (title.isEmpty()) return null
        val url = h.str("url")?.trim().orEmpty().takeIf { it.startsWith("https://") || it.startsWith("http://") }.orEmpty()
        val published = parseTime(h.str("published_utc") ?: h.str("published") ?: h.str("time_utc"))
            ?.takeIf { it <= now + 10 * 60_000L } // ignore impossible future timestamps
        val instruments = h.arr("instruments").mapNotNull { it?.toString()?.trim()?.uppercase() }
            .filter { it.isNotEmpty() }.distinct().take(4).ifEmpty { listOf("NQ", "ES") }
        return Headline(
            title = title.take(110),
            why = h.str("why")?.let(::clean).orEmpty().take(130),
            impact = Impact.parse(h.str("impact")),
            direction = Direction.parse(h.str("direction")),
            instruments = instruments,
            source = h.str("source")?.let(::clean).orEmpty().take(30).ifEmpty { hostOf(url) },
            url = url,
            publishedAt = published,
        )
    }

    fun parseTime(s: String?): Long? {
        if (s.isNullOrBlank() || s.equals("null", true)) return null
        return try { Instant.parse(s.trim()).toEpochMilli() } catch (e: DateTimeParseException) {
            try { OffsetDateTime.parse(s.trim()).toInstant().toEpochMilli() } catch (e2: DateTimeParseException) { null }
        }
    }

    fun hostOf(url: String): String = url.substringAfter("://", "").substringBefore('/')
        .removePrefix("www.").substringBeforeLast('.').substringAfterLast('.')
        .replaceFirstChar { it.uppercase() }

    private fun clean(s: String): String = s.replace(Regex("\\s+"), " ").trim()

    /** Collapse near-duplicate stories, keeping the more important one in its original position. */
    fun dedupe(items: List<Headline>): List<Headline> {
        val out = ArrayList<Headline>()
        for (h in items) {
            val i = out.indexOfFirst { Novelty.similar(it.title, h.title) }
            if (i < 0) out.add(h) else if (h.impact.ordinal < out[i].impact.ordinal) out[i] = h
        }
        return out
    }

    /** When Claude omits a URL, borrow the cited URL whose host matches the named source. */
    private fun matchCitation(h: Headline, cited: List<String>): String {
        val src = h.source.lowercase().replace(" ", "")
        if (src.isEmpty()) return ""
        return cited.firstOrNull { url -> hostOf(url).lowercase().let { it.isNotEmpty() && (src.contains(it) || it.contains(src)) } }.orEmpty()
    }
}

/** Cheap headline similarity: Jaccard overlap of significant words. */
object Novelty {
    private val STOP = setOf(
        "the", "a", "an", "of", "to", "in", "on", "for", "and", "or", "as", "at", "by", "with", "from",
        "is", "are", "after", "amid", "over", "into", "its", "us", "u.s.", "new", "says", "say",
    )

    fun tokens(s: String): Set<String> = s.lowercase()
        .replace(Regex("[^a-z0-9%.\$ ]"), " ")
        .split(' ')
        .map { it.trim('.') }
        .filter { it.length > 1 && it !in STOP }
        .toSet()

    fun similarity(a: String, b: String): Double {
        val ta = tokens(a); val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        return ta.intersect(tb).size.toDouble() / ta.union(tb).size
    }

    fun similar(a: String, b: String, threshold: Double = 0.7): Boolean =
        a.equals(b, ignoreCase = true) || similarity(a, b) >= threshold
}
