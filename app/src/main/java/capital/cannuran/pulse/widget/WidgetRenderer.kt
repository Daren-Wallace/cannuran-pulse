package capital.cannuran.pulse.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import capital.cannuran.pulse.R
import capital.cannuran.pulse.core.Cadence
import capital.cannuran.pulse.core.CalendarParser
import capital.cannuran.pulse.core.ClaudeModel
import capital.cannuran.pulse.core.Direction
import capital.cannuran.pulse.core.Format
import capital.cannuran.pulse.core.Impact
import capital.cannuran.pulse.core.MarketClock
import capital.cannuran.pulse.core.Quote
import capital.cannuran.pulse.core.Risk
import capital.cannuran.pulse.core.Settings
import capital.cannuran.pulse.core.Snapshot
import capital.cannuran.pulse.core.Sparkline
import capital.cannuran.pulse.core.WidgetTheme
import capital.cannuran.pulse.data.Store
import capital.cannuran.pulse.ui.SettingsActivity
import java.time.ZoneId

/**
 * Builds the widget's RemoteViews from the stored snapshot. One layout, three density tiers
 * chosen by the host's actual size: a slim lock-screen strip, a standard card, and a full panel.
 */
object WidgetRenderer {

    enum class Tier(val headlines: Int, val meter: Boolean, val quotes: Boolean, val section: Boolean, val watch: Boolean) {
        SMALL(0, false, false, false, false),
        MEDIUM(1, true, true, false, false),
        LARGE(3, true, true, true, true),
    }

    /** Theme-dependent resources and colours. */
    private class Skin(
        val background: Int,
        val accent: Int,
        val eventBg: Int,
        val markBg: Int,
        val badge: Int,
        val newPill: Int,
    )

    private data class Row(val root: Int, val bar: Int, val title: Int, val dir: Int, val meta: Int, val badge: Int)

    private val ROWS = listOf(
        Row(R.id.h1, R.id.h1_bar, R.id.h1_title, R.id.h1_dir, R.id.h1_meta, R.id.h1_new),
        Row(R.id.h2, R.id.h2_bar, R.id.h2_title, R.id.h2_dir, R.id.h2_meta, R.id.h2_new),
        Row(R.id.h3, R.id.h3_bar, R.id.h3_title, R.id.h3_dir, R.id.h3_meta, R.id.h3_new),
        Row(R.id.h4, R.id.h4_bar, R.id.h4_title, R.id.h4_dir, R.id.h4_meta, R.id.h4_new),
        Row(R.id.h5, R.id.h5_bar, R.id.h5_title, R.id.h5_dir, R.id.h5_meta, R.id.h5_new),
    )

    private data class Tile(val root: Int, val label: Int, val price: Int, val pct: Int, val spark: Int)

    private val TILES = listOf(
        Tile(R.id.q1, R.id.q1_label, R.id.q1_price, R.id.q1_pct, R.id.q1_spark),
        Tile(R.id.q2, R.id.q2_label, R.id.q2_price, R.id.q2_pct, R.id.q2_spark),
        Tile(R.id.q3, R.id.q3_label, R.id.q3_price, R.id.q3_pct, R.id.q3_spark),
        Tile(R.id.q4, R.id.q4_label, R.id.q4_price, R.id.q4_pct, R.id.q4_spark),
    )

    private val SEGMENTS = listOf(R.id.seg1, R.id.seg2, R.id.seg3)

    /** The Chronometer ticks live (seconds on the lock screen) when the release is this close. */
    private const val LIVE_COUNTDOWN_MS = 6 * 3_600_000L

    fun renderAll(ctx: Context, refreshing: Boolean = false) {
        val mgr = AppWidgetManager.getInstance(ctx) ?: return
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, PulseWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val store = Store(ctx)
        val snap = store.snapshot
        val settings = store.settings
        val sparks = sparkBitmaps(ctx, snap.quotes) // shared across tiers: one copy in the parcel
        for (id in ids) mgr.updateAppWidget(id, build(ctx, mgr, id, snap, settings, refreshing, sparks))
    }

    private fun build(
        ctx: Context, mgr: AppWidgetManager, id: Int, snap: Snapshot, s: Settings, refreshing: Boolean,
        sparks: List<Bitmap?>,
    ): RemoteViews =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The host picks the largest variant that fits the widget's current size.
            RemoteViews(mapOf(
                SizeF(160f, 80f) to views(ctx, Tier.SMALL, snap, s, refreshing, sparks),
                SizeF(220f, 300f) to views(ctx, Tier.MEDIUM, snap, s, refreshing, sparks),
                SizeF(220f, 440f) to views(ctx, Tier.LARGE, snap, s, refreshing, sparks),
            ))
        } else {
            val h = mgr.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 300)
            views(ctx, when { h < 300 -> Tier.SMALL; h < 440 -> Tier.MEDIUM; else -> Tier.LARGE }, snap, s, refreshing, sparks)
        }

    private fun skin(ctx: Context, theme: WidgetTheme): Skin {
        val dynamic = theme == WidgetTheme.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val accent = if (dynamic) ctx.getColor(android.R.color.system_accent1_200) else ctx.getColor(R.color.pulse_gold)
        return when (theme) {
            WidgetTheme.MIDNIGHT -> Skin(R.drawable.widget_bg_midnight, accent, R.drawable.event_bg_gold,
                R.drawable.mark_bg_gold, R.drawable.badge_outline_gold, R.drawable.pill_new_gold)
            WidgetTheme.GLASS -> Skin(R.drawable.widget_bg_glass, accent, R.drawable.event_bg_gold,
                R.drawable.mark_bg_gold, R.drawable.badge_outline_gold, R.drawable.pill_new_gold)
            WidgetTheme.DYNAMIC -> Skin(R.drawable.widget_bg_dynamic, accent, R.drawable.event_bg_dynamic,
                R.drawable.mark_bg_dynamic, R.drawable.badge_outline_dynamic, R.drawable.pill_new_dynamic)
        }
    }

    /** Public so the app can show a pixel-identical live preview of the widget. */
    fun views(
        ctx: Context, tier: Tier, snap: Snapshot, s: Settings, refreshing: Boolean,
        sparks: List<Bitmap?> = sparkBitmaps(ctx, snap.quotes),
    ): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_pulse)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val brief = snap.brief
        val skin = skin(ctx, s.theme)
        val accent = skin.accent

        // ---- Theme ----
        rv.setInt(R.id.widget_root, "setBackgroundResource", skin.background)
        rv.setInt(R.id.mark, "setBackgroundResource", skin.markBg)
        rv.setInt(R.id.mark, "setColorFilter", accent)
        for (id in listOf(R.id.brand, R.id.event_kicker, R.id.event_countdown, R.id.event_when, R.id.cch_badge, R.id.cch_head)) {
            rv.setTextColor(id, accent)
        }
        rv.setInt(R.id.cch_badge, "setBackgroundResource", skin.badge)
        rv.setInt(R.id.cch_head, "setBackgroundResource", skin.badge)
        rv.setInt(R.id.event_card, "setBackgroundResource", skin.eventBg)

        // ---- Header ----
        val small = tier == Tier.SMALL
        val cchOn = CalendarParser.cchCatalystsToday(snap.events, now, s.cchKeywords).isNotEmpty() || brief?.cchFlag == true
        rv.setTextViewText(R.id.session, sessionShort(now))
        rv.setViewVisibility(R.id.session, if (small) View.GONE else View.VISIBLE)
        rv.setViewVisibility(R.id.risk_pill, if (small && brief != null) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.cch_head, if (small && cchOn) View.VISIBLE else View.GONE)
        if (brief != null) {
            val (pill, fg) = riskStyle(brief.risk)
            rv.setInt(R.id.risk_pill, "setBackgroundResource", pill)
            rv.setTextColor(R.id.risk_pill, ctx.getColor(fg))
            rv.setTextViewText(R.id.risk_pill, "● " + riskWord(brief.risk))
        }

        // ---- Bias ----
        rv.setTextViewText(R.id.bias, when {
            brief != null && brief.bias.isNotEmpty() -> brief.bias
            brief != null -> "Brief ready — see headlines"
            small && snap.briefError != null -> "⚠ " + snap.briefError
            else -> ctx.getString(R.string.widget_placeholder_bias)
        })
        rv.setInt(R.id.bias, "setMaxLines", if (small) 1 else 3)
        rv.setTextViewTextSize(R.id.bias, TypedValue.COMPLEX_UNIT_SP, if (small) 14f else 16f)

        // ---- Risk meter ----
        if (tier.meter) {
            rv.setViewVisibility(R.id.meter_row, View.VISIBLE)
            val level = when (brief?.risk) { Risk.LOW -> 1; Risk.ELEVATED -> 2; Risk.HIGH -> 3; null -> 0 }
            val on = when (brief?.risk) { Risk.LOW -> R.drawable.seg_low; Risk.HIGH -> R.drawable.seg_high; else -> R.drawable.seg_elev }
            SEGMENTS.forEachIndexed { i, seg -> rv.setInt(seg, "setBackgroundResource", if (i < level) on else R.drawable.seg_off) }
            if (brief != null) {
                rv.setTextViewText(R.id.risk_label, riskWord(brief.risk) + " risk")
                rv.setTextColor(R.id.risk_label, ctx.getColor(riskStyle(brief.risk).second))
            } else {
                rv.setTextViewText(R.id.risk_label, "Risk —")
                rv.setTextColor(R.id.risk_label, ctx.getColor(R.color.text_muted))
            }
            rv.setViewVisibility(R.id.cch_badge, if (cchOn) View.VISIBLE else View.GONE)
        } else rv.setViewVisibility(R.id.meter_row, View.GONE)

        // ---- Next release with live countdown ----
        val next = CalendarParser.nextHigh(snap.events, now)
        if (next != null) {
            rv.setViewVisibility(R.id.event_card, View.VISIBLE)
            val names = CalendarParser.cluster(snap.events, next).filter { it.isHigh }
                .joinToString(" + ") { it.title }.ifEmpty { next.title }
            rv.setTextViewText(R.id.event_title, names)
            rv.setViewVisibility(R.id.event_kicker, if (small) View.GONE else View.VISIBLE)
            val detail = buildString {
                append(Format.timeOrDay(next.timeMillis, now, zone))
                if (next.forecast.isNotEmpty()) append(" · fcst ").append(next.forecast)
                if (!small && next.previous.isNotEmpty()) append(" · prev ").append(next.previous)
            }
            rv.setTextViewText(R.id.event_detail, detail)
            val until = next.timeMillis - now
            if (until in 0..LIVE_COUNTDOWN_MS) {
                rv.setViewVisibility(R.id.event_countdown, View.VISIBLE)
                rv.setViewVisibility(R.id.event_when, View.GONE)
                rv.setChronometer(R.id.event_countdown, SystemClock.elapsedRealtime() + until, null, true)
                rv.setChronometerCountDown(R.id.event_countdown, true)
                rv.setTextViewTextSize(R.id.event_countdown, TypedValue.COMPLEX_UNIT_SP, if (small) 20f else 24f)
                rv.setTextViewText(R.id.event_caption, "to print")
            } else {
                rv.setViewVisibility(R.id.event_countdown, View.GONE)
                rv.setChronometer(R.id.event_countdown, SystemClock.elapsedRealtime(), null, false)
                rv.setViewVisibility(R.id.event_when, View.VISIBLE)
                rv.setTextViewText(R.id.event_when, if (until < 0) "Printed" else Format.countdown(next.timeMillis, now).removePrefix("in "))
                rv.setTextViewText(R.id.event_caption, if (until < 0) "awaiting reaction" else "to print")
            }
            rv.setViewVisibility(R.id.event_caption, if (small) View.GONE else View.VISIBLE)
            rv.setOnClickPendingIntent(R.id.event_card, openApp(ctx))
        } else rv.setViewVisibility(R.id.event_card, View.GONE)

        // ---- Quote tiles with sparklines ----
        if (tier.quotes && snap.quotes.isNotEmpty()) {
            rv.setViewVisibility(R.id.quotes, View.VISIBLE)
            TILES.forEachIndexed { i, t ->
                val q = snap.quotes.getOrNull(i)
                if (q == null) { rv.setViewVisibility(t.root, View.GONE); return@forEachIndexed }
                rv.setViewVisibility(t.root, View.VISIBLE)
                rv.setTextViewText(t.label, q.label)
                rv.setTextViewText(t.price, Format.price(q.price))
                rv.setTextViewText(t.pct, Format.pct(q.changePct).replace('-', '−'))
                rv.setTextColor(t.pct, ctx.getColor(if (isUp(q)) R.color.up else R.color.down))
                val bmp = sparks.getOrNull(i)
                if (bmp != null) {
                    rv.setViewVisibility(t.spark, View.VISIBLE)
                    rv.setImageViewBitmap(t.spark, bmp)
                } else rv.setViewVisibility(t.spark, View.GONE)
            }
        } else rv.setViewVisibility(R.id.quotes, View.GONE)

        // ---- Headlines ----
        val headlines = brief?.headlines.orEmpty()
        val shown = minOf(tier.headlines, headlines.size)
        rv.setViewVisibility(R.id.sec_row, if (tier.section && headlines.isNotEmpty()) View.VISIBLE else View.GONE)
        if (tier.section && brief != null) {
            val newCount = headlines.count { it.isNew }
            rv.setTextViewText(R.id.sec_meta, buildString {
                if (newCount > 0) append(newCount).append(" new · ")
                append("updated ").append(Format.time(brief.generatedAt, zone))
            })
        }
        ROWS.forEachIndexed { i, row ->
            val h = headlines.getOrNull(i)
            when {
                i < shown && h != null -> {
                    rv.setViewVisibility(row.root, View.VISIBLE)
                    rv.setInt(row.bar, "setBackgroundResource", when (h.impact) {
                        Impact.HIGH -> R.drawable.bar_high; Impact.MED -> R.drawable.bar_med; Impact.LOW -> R.drawable.bar_low
                    })
                    rv.setTextViewText(row.title, h.title)
                    rv.setViewVisibility(row.dir, View.VISIBLE)
                    rv.setTextViewText(row.dir, when (h.direction) { Direction.BULL -> "▲ Bull"; Direction.BEAR -> "▼ Bear"; Direction.MIXED -> "◆ Mixed" })
                    rv.setInt(row.dir, "setBackgroundResource", when (h.direction) {
                        Direction.BULL -> R.drawable.pill_bull; Direction.BEAR -> R.drawable.pill_bear; Direction.MIXED -> R.drawable.pill_mixed
                    })
                    rv.setTextColor(row.dir, ctx.getColor(when (h.direction) {
                        Direction.BULL -> R.color.up; Direction.BEAR -> R.color.down; Direction.MIXED -> R.color.text_secondary
                    }))
                    rv.setTextViewText(row.meta, buildString {
                        append(h.instruments.joinToString(" "))
                        if (h.source.isNotEmpty()) append(" · ").append(h.source)
                        h.publishedAt?.let { append(" · ").append(Format.time(it, zone)) }
                    })
                    rv.setViewVisibility(row.badge, if (h.isNew) View.VISIBLE else View.GONE)
                    rv.setInt(row.badge, "setBackgroundResource", skin.newPill)
                    rv.setOnClickPendingIntent(row.root, openUrl(ctx, h.url, 100 + i))
                }
                i == 0 && headlines.isEmpty() && !small -> {
                    // Empty / error state lives in the first tile.
                    rv.setViewVisibility(row.root, View.VISIBLE)
                    rv.setInt(row.bar, "setBackgroundResource", if (snap.briefError != null) R.drawable.bar_med else R.drawable.bar_low)
                    rv.setTextViewText(row.title, snap.briefError ?: "Waiting for the first brief…")
                    rv.setViewVisibility(row.dir, View.GONE)
                    rv.setViewVisibility(row.badge, View.GONE)
                    rv.setTextViewText(row.meta, if (snap.briefError != null) "Tap to open Pulse" else "Tap ⟳ to refresh")
                    rv.setOnClickPendingIntent(row.root, openApp(ctx))
                }
                else -> rv.setViewVisibility(row.root, View.GONE)
            }
        }

        // ---- Watch list ----
        val watch = brief?.watch.orEmpty()
        if (tier.watch && watch.isNotEmpty()) {
            rv.setViewVisibility(R.id.watch, View.VISIBLE)
            rv.setTextViewText(R.id.watch, "WATCH   " + watch.joinToString(" · "))
        } else rv.setViewVisibility(R.id.watch, View.GONE)

        // ---- Footer: model / error on the left, freshness on the right ----
        rv.setViewVisibility(R.id.footer_row, if (small) View.GONE else View.VISIBLE)
        val decision = Cadence.decide(now, zone, s, snap.events, brief?.generatedAt ?: 0L, hasKey = true, force = false)
        val warn = ctx.getColor(R.color.stale)
        val muted = ctx.getColor(R.color.text_muted)
        when {
            snap.briefError != null && brief != null -> {
                rv.setTextViewText(R.id.footer_left, "⚠ " + snap.briefError)
                rv.setTextColor(R.id.footer_left, warn)
            }
            brief != null -> {
                rv.setTextViewText(R.id.footer_left, ClaudeModel.fromId(brief.model).shortName)
                rv.setTextColor(R.id.footer_left, muted)
            }
            else -> {
                rv.setTextViewText(R.id.footer_left, snap.briefError ?: ctx.getString(R.string.widget_placeholder_footer))
                rv.setTextColor(R.id.footer_left, if (snap.briefError != null) warn else muted)
            }
        }
        when {
            refreshing -> {
                rv.setTextViewText(R.id.footer_right, "Refreshing…")
                rv.setTextColor(R.id.footer_right, accent)
            }
            brief != null -> {
                val stale = Cadence.isStale(now, brief.generatedAt, decision.cadenceMin)
                val text = "${Format.time(brief.generatedAt, zone)} ${Format.zoneAbbrev(zone, now)} · ${Format.ago(brief.generatedAt, now)}"
                rv.setTextViewText(R.id.footer_right, if (stale) "STALE · $text" else text)
                rv.setTextColor(R.id.footer_right, if (stale) warn else muted)
            }
            else -> rv.setTextViewText(R.id.footer_right, "")
        }

        // ---- Clicks ----
        rv.setOnClickPendingIntent(R.id.refresh, refreshIntent(ctx))
        rv.setOnClickPendingIntent(R.id.widget_root, openApp(ctx))
        return rv
    }

    /* ---------------- helpers ---------------- */

    private fun sessionShort(now: Long): String = when (MarketClock.session(now)) {
        MarketClock.Session.CLOSED -> "Closed"
        MarketClock.Session.MAINTENANCE -> "Maintenance"
        MarketClock.Session.ASIA -> "Asia"
        MarketClock.Session.LONDON -> "London"
        MarketClock.Session.PRE_NY -> "Pre-NY"
        MarketClock.Session.NY_AM -> "NY AM"
        MarketClock.Session.NY_LUNCH -> "NY lunch"
        MarketClock.Session.NY_PM -> "NY PM"
        MarketClock.Session.POST_CLOSE -> "Post-close"
    }

    private fun riskWord(r: Risk) = when (r) { Risk.LOW -> "Low"; Risk.ELEVATED -> "Elevated"; Risk.HIGH -> "High" }

    private fun riskStyle(r: Risk): Pair<Int, Int> = when (r) {
        Risk.LOW -> R.drawable.pill_risk_low to R.color.risk_low_fg
        Risk.ELEVATED -> R.drawable.pill_risk_elev to R.color.risk_elev_fg
        Risk.HIGH -> R.drawable.pill_risk_high to R.color.risk_high_fg
    }

    private fun isUp(q: Quote): Boolean = q.changePct?.let { it >= 0 } ?: Sparkline.rising(q.spark)

    /** One bitmap per quote, drawn once per render and shared by every tier. */
    fun sparkBitmaps(ctx: Context, quotes: List<Quote>): List<Bitmap?> {
        val density = ctx.resources.displayMetrics.density.coerceAtMost(2.5f) // keep the parcel small
        val w = (72 * density).toInt()
        val h = (18 * density).toInt()
        return quotes.take(TILES.size).map { q ->
            val pts = Sparkline.points(q.spark, w.toFloat(), h.toFloat(), 1.5f * density)
            if (pts.isEmpty()) null else drawSpark(pts, w, h, ctx.getColor(if (isUp(q)) R.color.up else R.color.down), density)
        }
    }

    private fun drawSpark(pts: FloatArray, w: Int, h: Int, color: Int, density: Float): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val line = Path().apply {
            moveTo(pts[0], pts[1])
            var i = 2
            while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }
        }
        val fill = Path(line).apply {
            lineTo(pts[pts.size - 2], h.toFloat())
            lineTo(pts[0], h.toFloat())
            close()
        }
        val fade = Color.argb(70, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), fade, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        })
        canvas.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * density
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            this.color = color
        })
        return bmp
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
