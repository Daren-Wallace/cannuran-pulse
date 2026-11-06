package capital.cannuran.pulse.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import capital.cannuran.pulse.core.CalendarParser
import capital.cannuran.pulse.core.Settings
import capital.cannuran.pulse.core.Snapshot
import capital.cannuran.pulse.notify.ActionReceiver

/**
 * Wakes the app exactly around the next high-impact release: a heads-up T-minus N minutes and a
 * fresh brief N minutes after the print. Uses exact alarms when the user allows them (Settings →
 * "Allow precise alarms"), otherwise Doze-friendly inexact alarms.
 */
object EventAlarms {
    private const val RC_PRE = 1001
    private const val RC_POST = 1002

    fun schedule(ctx: Context, snap: Snapshot, s: Settings) {
        val now = System.currentTimeMillis()
        val next = CalendarParser.nextHigh(snap.events, now, graceMs = 0)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        if (next == null) { cancel(ctx); return }
        val title = CalendarParser.cluster(snap.events, next).filter { it.isHigh }.joinToString(" + ") { it.title }

        val preAt = next.timeMillis - s.preEventAlertMin * 60_000L
        if (s.preEventAlertMin > 0 && preAt > now) {
            set(am, preAt, pending(ctx, RC_PRE, ActionReceiver.ACTION_PRE_EVENT, next.timeMillis, title))
        } else am.cancel(pending(ctx, RC_PRE, ActionReceiver.ACTION_PRE_EVENT, 0, ""))

        val postAt = next.timeMillis + s.postEventBriefMin * 60_000L
        if (s.postEventBriefMin > 0 && postAt > now) {
            set(am, postAt, pending(ctx, RC_POST, ActionReceiver.ACTION_POST_EVENT, next.timeMillis, title))
        } else am.cancel(pending(ctx, RC_POST, ActionReceiver.ACTION_POST_EVENT, 0, ""))
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(ctx, RC_PRE, ActionReceiver.ACTION_PRE_EVENT, 0, ""))
        am.cancel(pending(ctx, RC_POST, ActionReceiver.ACTION_POST_EVENT, 0, ""))
    }

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    private fun set(am: AlarmManager, at: Long, pi: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pending(ctx: Context, rc: Int, action: String, eventTime: Long, title: String): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(ActionReceiver.EXTRA_EVENT_TIME, eventTime)
            .putExtra(ActionReceiver.EXTRA_EVENT_TITLE, title)
        return PendingIntent.getBroadcast(ctx, rc, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
