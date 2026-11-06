package capital.cannuran.pulse.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import capital.cannuran.pulse.R
import capital.cannuran.pulse.core.CalEvent
import capital.cannuran.pulse.core.CalendarParser
import capital.cannuran.pulse.core.Format
import capital.cannuran.pulse.core.Headline
import capital.cannuran.pulse.data.Store
import capital.cannuran.pulse.ui.SettingsActivity
import java.time.ZoneId

/**
 * Three channels:
 *  - Lock-screen card: silent, ongoing, fully visible on the lock screen. The guaranteed way to
 *    keep the brief on the lock screen even where One UI won't host third-party widgets.
 *  - Alerts: heads-up for new HIGH-impact headlines and pre-release warnings.
 *  - Background work: required plumbing for expedited work on old Android versions.
 */
object Notifier {
    const val CH_CARD = "pulse_card"
    const val CH_ALERT = "pulse_alerts"
    const val CH_WORK = "pulse_work"

    const val CARD_ID = 42
    const val WORK_ID = 43
    private const val PRE_EVENT_ID = 44

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val card = NotificationChannel(CH_CARD, ctx.getString(R.string.channel_card), NotificationManager.IMPORTANCE_LOW).apply {
            description = ctx.getString(R.string.channel_card_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        val alert = NotificationChannel(CH_ALERT, ctx.getString(R.string.channel_alert), NotificationManager.IMPORTANCE_HIGH).apply {
            description = ctx.getString(R.string.channel_alert_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        val work = NotificationChannel(CH_WORK, ctx.getString(R.string.channel_work), NotificationManager.IMPORTANCE_MIN).apply {
            setShowBadge(false)
        }
        nm.createNotificationChannels(listOf(card, alert, work))
    }

    fun canPost(ctx: Context): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return nm.areNotificationsEnabled()
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

    private fun refreshAction(ctx: Context): Notification.Action {
        val pi = PendingIntent.getBroadcast(
            ctx, 7, Intent(ctx, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null, ctx.getString(R.string.action_refresh), pi).build()
    }

    /** Posts or updates (or removes) the persistent lock-screen card from the stored snapshot. */
    fun updateCard(ctx: Context) {
        val store = Store(ctx)
        val settings = store.settings
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (!settings.ongoingCard) { nm.cancel(CARD_ID); return }
        if (!canPost(ctx)) return
        val snap = store.snapshot
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val (title, body) = Format.cardText(snap, settings, now, zone)
        val next = CalendarParser.nextHigh(snap.events, now)
        val b = Notification.Builder(ctx, CH_CARD)
            .setSmallIcon(R.drawable.ic_stat_pulse)
            .setColor(ctx.getColor(R.color.pulse_gold))
            .setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull().orEmpty())
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setSubText(ctx.getString(R.string.app_name))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(snap.brief != null)
            .setWhen(snap.brief?.generatedAt ?: now)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(openApp(ctx))
            .addAction(refreshAction(ctx))
        requestPromotion(b, next?.let { "${shortName(it)} ${Format.countdown(it.timeMillis, now).removePrefix("in ")}" })
        nm.notify(CARD_ID, b.build())
    }

    fun cancelCard(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java)?.cancel(CARD_ID)
    }

    fun alertHeadline(ctx: Context, h: Headline) {
        if (!canPost(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val body = buildString {
            append(h.title)
            if (h.why.isNotEmpty()) append("\n").append(h.why)
            if (h.source.isNotEmpty()) append("\n— ").append(h.source)
        }
        val n = Notification.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_pulse)
            .setColor(ctx.getColor(R.color.risk_high))
            .setContentTitle("‼ HIGH impact · ${h.direction.short} · ${h.instruments.joinToString(" ")}")
            .setContentText(h.title)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .setContentIntent(openUrl(ctx, h.url, 2000 + (h.title.hashCode() and 0xffff)))
            .build()
        nm.notify("headline", h.title.hashCode(), n)
    }

    fun preEventAlert(ctx: Context, title: String, events: List<CalEvent>, minutes: Long, cchArmed: Boolean) {
        if (!canPost(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val details = events.joinToString("\n") { e ->
            buildString {
                append(e.title)
                if (e.forecast.isNotEmpty()) append(" · fcst ").append(e.forecast)
                if (e.previous.isNotEmpty()) append(" · prev ").append(e.previous)
            }
        }
        val headline = "$title in ${minutes}m" + if (cchArmed) " · CCH ARMED" else ""
        val n = Notification.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_pulse)
            .setColor(ctx.getColor(R.color.pulse_gold))
            .setContentTitle(headline)
            .setContentText(details.lineSequence().firstOrNull().orEmpty())
            .setStyle(Notification.BigTextStyle().bigText(details.ifEmpty { title }))
            .setAutoCancel(true)
            .setTimeoutAfter((minutes + 15) * 60_000L)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_EVENT)
            .setContentIntent(openApp(ctx))
            .build()
        nm.notify(PRE_EVENT_ID, n)
    }

    fun workNotification(ctx: Context): Notification = Notification.Builder(ctx, CH_WORK)
        .setSmallIcon(R.drawable.ic_stat_pulse)
        .setContentTitle(ctx.getString(R.string.refreshing))
        .setOngoing(true)
        .build()

    private fun shortName(e: CalEvent): String = e.title
        .replace(" m/m", "").replace(" y/y", "").replace(" q/q", "")
        .replace("Federal Funds Rate", "FOMC").replace("Non-Farm Employment Change", "NFP")
        .take(14)

    /**
     * Android 16 Live Updates: ask for the card to be promoted (status-bar chip, and the Now Bar on
     * One UI builds that surface Live Updates). Called reflectively so the app also builds and runs
     * where these APIs are absent; the system silently declines if the user hasn't allowed it.
     */
    private fun requestPromotion(b: Notification.Builder, chip: String?) {
        if (Build.VERSION.SDK_INT < 36) return
        try {
            Notification.Builder::class.java.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                .invoke(b, true)
            if (chip != null) {
                Notification.Builder::class.java.getMethod("setShortCriticalText", String::class.java)
                    .invoke(b, chip)
            }
        } catch (e: ReflectiveOperationException) {
            // Not available on this build — the regular ongoing card still shows on the lock screen.
        }
    }
}
