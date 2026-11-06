package capital.cannuran.pulse.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import capital.cannuran.pulse.R
import capital.cannuran.pulse.core.Cadence
import capital.cannuran.pulse.core.CalendarParser
import capital.cannuran.pulse.core.Direction
import capital.cannuran.pulse.core.Format
import capital.cannuran.pulse.core.Impact
import capital.cannuran.pulse.core.MarketClock
import capital.cannuran.pulse.core.Risk
import capital.cannuran.pulse.core.Settings
import capital.cannuran.pulse.core.Snapshot
import capital.cannuran.pulse.data.Store
import capital.cannuran.pulse.ui.SettingsActivity
import java.time.ZoneId

/**
 * Builds the widget's RemoteViews from the stored snapshot. One layout, three density tiers
 * chosen by the host's actual size — so the same widget works as a slim lock-screen strip
 * and as a full home-screen panel.
 */
object WidgetRenderer {

    enum class Tier(val headlines: Int, val quotes: Boolean, val watch: Boolean, val biasLines: Int) {
        SMALL(1, false, false, 1),
        MEDIUM(3, true, false, 2),
        LARGE(5, true, true, 2),
    }

    private val HEADLINE_IDS = listOf(
        Triple(R.id.h1, R.id.h1_title, R.id.h1_meta),
        Triple(R.id.h2, R.id.h2_title, R.id.h2_meta),
        Triple(R.id.h3, R.id.h3_title, R.id.h3_meta),
        Triple(R.id.h4, R.id.h4_title, R.id.h4_meta),
        Triple(R.id.h5, R.id.h5_title, R.id.h5_meta),
    )
    private val QUOTE_IDS = listOf(R.id.q1, R.id.q2, R.id.q3, R.id.q4)

    /** Chronometer counts live (seconds ticking) when the release is within this window. */
    private const val LIVE_COUNTDOWN_MS = 6 * 3_600_000L

    fun renderAll(ctx: Context, refreshing: Boolean = false) {
        val mgr = AppWidgetManager.getInstance(ctx) ?: return
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, PulseWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val store = Store(ctx)
        val snap = store.snapshot
        val settings = store.settings
        for (id in ids) mgr.updateAppWidget(id, build(ctx, mgr, id, snap, settings, refreshing))
    }

    fun build(ctx: Context, mgr: AppWidgetManager, id: Int, snap: Snapshot, s: Settings, refreshing: Boolean): RemoteViews {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The host picks the largest variant that fits the widget's current size.
            RemoteViews(mapOf(
                SizeF(160f, 90f) to views(ctx, Tier.SMALL, snap, s, refreshing),
                SizeF(200f, 190f) to views(ctx, Tier.MEDIUM, snap, s, refreshing),
                SizeF(240f, 300f) to views(ctx, Tier.LARGE, snap, s, refreshing),
            ))
        } else {
            val h = mgr.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 200)
            views(ctx, when { h < 150 -> Tier.SMALL; h < 280 -> Tier.MEDIUM; else -> Tier.LARGE }, snap, s, refreshing)
        }
    }

    private fun color(ctx: Context, res: Int) = ctx.getColor(res)

    fun views(ctx: Context, tier: Tier, snap: Snapshot, s: Settings, refreshing: Boolean): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_pulse)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val brief = snap.brief

        rv.setInt(R.id.widget_root, "setBackgroundResource",
            if (s.translucentWidget) R.drawable.widget_bg_translucent else R.drawable.widget_bg)

        // Header: session, CCH, risk.
        rv.setTextViewText(R.id.session, "· " + MarketClock.session(now).label)
        val cchToday = CalendarParser.cchCatalystsToday(snap.events, now, s.cchKeywords)
        val cchOn = cchToday.isNotEmpty() || brief?.cchFlag == true
        rv.setViewVisibility(R.id.cch_chip, if (cchOn) View.VISIBLE else View.GONE)
        if (brief != null) {
            rv.setTextViewText(R.id.risk_chip, "RISK ${brief.risk.name}")
            rv.setInt(R.id.risk_chip, "setBackgroundResource", when (brief.risk) {
                Risk.LOW -> R.drawable.chip_low
                Risk.ELEVATED -> R.drawable.chip_elevated
                Risk.HIGH -> R.drawable.chip_high
            })
        } else {
            rv.setTextViewText(R.id.risk_chip, "RISK —")
            rv.setInt(R.id.risk_chip, "setBackgroundResource", R.drawable.chip_neutral)
        }

        // Bias line.
        rv.setTextViewText(R.id.bias, when {
            brief != null && brief.bias.isNotEmpty() -> brief.bias
            brief != null -> "Brief ready — see headlines"
            else -> ctx.getString(R.string.widget_placeholder_bias)
        })
        rv.setInt(R.id.bias, "setMaxLines", tier.biasLines)

        // Quotes.
        if (tier.quotes && snap.quotes.isNotEmpty()) {
            rv.setViewVisibility(R.id.quotes, View.VISIBLE)
            QUOTE_IDS.forEachIndexed { i, qid ->
                val q = snap.quotes.getOrNull(i)
                if (q == null) { rv.setViewVisibility(qid, View.GONE); return@forEachIndexed }
                rv.setViewVisibility(qid, View.VISIBLE)
                val sb = SpannableStringBuilder()
                sb.append(q.label, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.append(' ').append(Format.price(q.price))
                if (q.changePct != null) {
                    sb.append(' ')
                    val c = color(ctx, if (q.changePct >= 0) R.color.up else R.color.down)
                    sb.append(Format.pct(q.changePct), ForegroundColorSpan(c), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                rv.setTextViewText(qid, sb)
            }
        } else rv.setViewVisibility(R.id.quotes, View.GONE)

        // Next high-impact release with a live countdown.
        val next = CalendarParser.nextHigh(snap.events, now)
        if (next != null) {
            rv.setViewVisibility(R.id.event_row, View.VISIBLE)
            val cluster = CalendarParser.cluster(snap.events, next).filter { it.isHigh }
            val names = cluster.joinToString(" + ") { it.title }.ifEmpty { next.title }
            val sb = SpannableStringBuilder()
            sb.append("NEXT ", ForegroundColorSpan(color(ctx, R.color.pulse_gold)), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(names, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(" · ").append(Format.timeOrDay(next.timeMillis, now, zone))
            if (next.forecast.isNotEmpty()) sb.append(" · f ").append(next.forecast)
            rv.setTextViewText(R.id.event_title, sb)
            val until = next.timeMillis - now
            if (until in 0..LIVE_COUNTDOWN_MS) {
                rv.setViewVisibility(R.id.event_countdown, View.VISIBLE)
                rv.setViewVisibility(R.id.event_when, View.GONE)
                rv.setChronometer(R.id.event_countdown, SystemClock.elapsedRealtime() + until, null, true)
                rv.setChronometerCountDown(R.id.event_countdown, true)
            } else {
                rv.setViewVisibility(R.id.event_countdown, View.GONE)
                rv.setViewVisibility(R.id.event_when, View.VISIBLE)
                rv.setChronometer(R.id.event_countdown, SystemClock.elapsedRealtime(), null, false)
                rv.setTextViewText(R.id.event_when, if (until < 0) "printed" else Format.countdown(next.timeMillis, now))
            }
        } else rv.setViewVisibility(R.id.event_row, View.GONE)

        // Headlines.
        val headlines = brief?.headlines.orEmpty()
        HEADLINE_IDS.forEachIndexed { i, (row, title, meta) ->
            val h = headlines.getOrNull(i)
            if (h == null || i >= tier.headlines) {
                rv.setViewVisibility(row, if (i == 0 && headlines.isEmpty()) View.VISIBLE else View.GONE)
                if (i == 0 && headlines.isEmpty()) {
                    rv.setTextViewText(title, snap.briefError ?: "Waiting for the first brief…")
                    rv.setTextViewText(meta, if (snap.briefError != null) "Tap to open settings" else "Tap ⟳ to refresh")
                    rv.setOnClickPendingIntent(row, openApp(ctx))
                }
                return@forEachIndexed
            }
            rv.setViewVisibility(row, View.VISIBLE)
            val dotColor = color(ctx, when (h.impact) {
                Impact.HIGH -> R.color.impact_high; Impact.MED -> R.color.impact_med; Impact.LOW -> R.color.impact_low
            })
            val t = SpannableStringBuilder()
            t.append(Format.impactDot(h.impact) + " ", ForegroundColorSpan(dotColor), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (h.isNew) t.append("NEW ", ForegroundColorSpan(color(ctx, R.color.pulse_gold)), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            t.append(h.title)
            rv.setTextViewText(title, t)
            rv.setInt(title, "setMaxLines", if (tier == Tier.SMALL) 1 else 2)

            val m = SpannableStringBuilder()
            val dirColor = color(ctx, when (h.direction) {
                Direction.BULL -> R.color.up; Direction.BEAR -> R.color.down; Direction.MIXED -> R.color.text_secondary
            })
            m.append(h.direction.short, ForegroundColorSpan(dirColor), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            m.append(" · ").append(h.instruments.joinToString(" "))
            if (h.source.isNotEmpty()) m.append(" · ").append(h.source)
            h.publishedAt?.let { m.append(" · ").append(Format.time(it, zone)) }
            if (tier != Tier.SMALL && h.why.isNotEmpty()) m.append(" — ").append(h.why)
            rv.setTextViewText(meta, m)
            rv.setOnClickPendingIntent(row, openUrl(ctx, h.url, 100 + i))
        }
        rv.setViewVisibility(R.id.divider, if (tier == Tier.SMALL) View.GONE else View.VISIBLE)

        // Watch list.
        val watch = brief?.watch.orEmpty()
        if (tier.watch && watch.isNotEmpty()) {
            rv.setViewVisibility(R.id.watch, View.VISIBLE)
            val w = SpannableStringBuilder()
            w.append("WATCH ", ForegroundColorSpan(color(ctx, R.color.pulse_gold)), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            w.append(watch.joinToString("  ·  "))
            rv.setTextViewText(R.id.watch, w)
        } else rv.setViewVisibility(R.id.watch, View.GONE)

        // Footer: freshness, model, errors.
        val decision = Cadence.decide(now, zone, s, snap.events, brief?.generatedAt ?: 0L, hasKey = true, force = false)
        val footer = SpannableStringBuilder()
        when {
            refreshing -> footer.append("Refreshing…", ForegroundColorSpan(color(ctx, R.color.pulse_gold)), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            brief != null -> {
                val stale = Cadence.isStale(now, brief.generatedAt, decision.cadenceMin)
                val text = "Updated ${Format.time(brief.generatedAt, zone)} ${Format.zoneAbbrev(zone, now)} (${Format.ago(brief.generatedAt, now)})"
                if (stale) footer.append("STALE · $text", ForegroundColorSpan(color(ctx, R.color.stale)), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                else footer.append(text)
                footer.append(" · ").append(capital.cannuran.pulse.core.ClaudeModel.fromId(brief.model).shortName)
                snap.briefError?.let { footer.append(" · ⚠ ").append(it) }
            }
            else -> footer.append(snap.briefError ?: ctx.getString(R.string.widget_placeholder_footer))
        }
        rv.setTextViewText(R.id.footer, footer)

        // Clicks: ⟳ refreshes, header/bias opens the app.
        rv.setOnClickPendingIntent(R.id.refresh, refreshIntent(ctx))
        rv.setOnClickPendingIntent(R.id.widget_root, openApp(ctx))
        return rv
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openUrl(ctx: Context, url: String, rc: Int): PendingIntent =
        if (url.isBlank()) openApp(ctx) else PendingIntent.getActivity(
            ctx, rc, Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun refreshIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, PulseWidgetProvider::class.java).setAction(PulseWidgetProvider.ACTION_REFRESH),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
