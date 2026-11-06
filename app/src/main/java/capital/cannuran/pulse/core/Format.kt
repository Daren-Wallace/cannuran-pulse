package capital.cannuran.pulse.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Display formatting shared by the widget, the lock-screen card and the settings screen. */
object Format {
    private val HM = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val DAY_HM = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.US)

    fun time(millis: Long, zone: ZoneId): String = HM.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** "15:30" if today in [zone], otherwise "Thu 15:30". */
    fun timeOrDay(millis: Long, now: Long, zone: ZoneId): String {
        val a = Instant.ofEpochMilli(millis).atZone(zone)
        val b = Instant.ofEpochMilli(now).atZone(zone)
        return if (a.toLocalDate() == b.toLocalDate()) HM.format(a) else DAY_HM.format(a)
    }

    fun zoneAbbrev(zone: ZoneId, at: Long): String {
        val name = DateTimeFormatter.ofPattern("zzz", Locale.US).format(Instant.ofEpochMilli(at).atZone(zone))
        // JDKs render Africa/Nairobi as "EAT"; fall back to a GMT offset if a raw id comes back.
        return if (name.contains('/')) zone.getDisplayName(TextStyle.SHORT, Locale.US) else name
    }

    fun ago(then: Long, now: Long): String {
        val min = ((now - then) / 60_000).coerceAtLeast(0)
        return when {
            min < 1 -> "just now"
            min < 60 -> "${min}m ago"
            min < 48 * 60 -> "${min / 60}h ${min % 60}m ago".replace(" 0m", "")
            else -> "${min / (24 * 60)}d ago"
        }
    }

    /** Compact countdown, e.g. "in 2h 05m", "in 9m", "now", "4m ago". */
    fun countdown(target: Long, now: Long): String {
        val diffMin = (target - now) / 60_000.0
        return when {
            diffMin < -1 -> "${(-diffMin).toLong()}m ago"
            diffMin < 1 -> "now"
            diffMin < 60 -> "in ${diffMin.toLong()}m"
            else -> { val m = diffMin.toLong(); "in ${m / 60}h ${"%02d".format(m % 60)}m" }
        }
    }

    fun price(p: Double): String = when {
        abs(p) >= 1000 -> String.format(Locale.US, "%,d", p.roundToLong())
        abs(p) >= 100 -> String.format(Locale.US, "%.1f", p)
        else -> String.format(Locale.US, "%.2f", p)
    }

    fun pct(p: Double?): String = if (p == null) "" else String.format(Locale.US, "%+.2f%%", p)

    fun usd(v: Double): String = if (v < 0.995) String.format(Locale.US, "$%.3f", v) else String.format(Locale.US, "$%.2f", v)

    fun tokens(n: Long): String = when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
        n >= 1000 -> String.format(Locale.US, "%.1fk", n / 1000.0)
        else -> n.toString()
    }

    fun impactDot(i: Impact): String = when (i) { Impact.HIGH -> "●"; Impact.MED -> "●"; Impact.LOW -> "○" }

    fun eventLine(e: CalEvent, now: Long, zone: ZoneId): String {
        val sb = StringBuilder()
        sb.append(e.country).append(' ').append(e.title).append(" · ").append(timeOrDay(e.timeMillis, now, zone))
        if (e.forecast.isNotEmpty()) sb.append(" · f ").append(e.forecast)
        if (e.previous.isNotEmpty()) sb.append(" p ").append(e.previous)
        return sb.toString()
    }

    /** Text body for the persistent lock-screen card (BigTextStyle). */
    fun cardText(snap: Snapshot, settings: Settings, now: Long, zone: ZoneId): Pair<String, String> {
        val brief = snap.brief
        val next = CalendarParser.nextHigh(snap.events, now)
        val cch = CalendarParser.cchCatalystsToday(snap.events, now, settings.cchKeywords)
        val title = buildString {
            append(brief?.let { "RISK ${it.risk.name}" } ?: "PULSE")
            if (cch.isNotEmpty() || brief?.cchFlag == true) append(" · CCH ARMED")
            if (next != null) append(" · ").append(next.title).append(' ').append(countdown(next.timeMillis, now))
        }
        val body = buildString {
            if (brief != null) {
                if (brief.bias.isNotEmpty()) append(brief.bias).append('\n')
                brief.headlines.take(4).forEach { h ->
                    append(if (h.impact == Impact.HIGH) "‼ " else "• ")
                    append(h.title)
                    append(" (").append(h.direction.short.substringAfter(' ')).append(")\n")
                }
            } else {
                append(snap.briefError ?: "Add your Claude API key in Pulse settings to enable the news brief.").append('\n')
            }
            if (snap.quotes.isNotEmpty()) {
                append(snap.quotes.joinToString("  ") { "${it.label} ${price(it.price)} ${pct(it.changePct)}".trim() })
                append('\n')
            }
            if (next != null) append("Next: ").append(eventLine(next, now, zone)).append('\n')
            if (brief != null) append("Updated ").append(time(brief.generatedAt, zone)).append(' ').append(zoneAbbrev(zone, now))
        }.trim()
        return title to body
    }
}
