package capital.cannuran.pulse.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import capital.cannuran.pulse.core.CalendarParser
import capital.cannuran.pulse.data.Store
import capital.cannuran.pulse.widget.WidgetRenderer
import capital.cannuran.pulse.work.Scheduler

/** Handles the card's Refresh button and the pre/post-release alarms. */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_REFRESH = "capital.cannuran.pulse.action.CARD_REFRESH"
        const val ACTION_PRE_EVENT = "capital.cannuran.pulse.action.PRE_EVENT"
        const val ACTION_POST_EVENT = "capital.cannuran.pulse.action.POST_EVENT"
        const val EXTRA_EVENT_TIME = "event_time"
        const val EXTRA_EVENT_TITLE = "event_title"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> {
                WidgetRenderer.renderAll(context, refreshing = true)
                Scheduler.refreshNow(context, "Manual (lock-screen card)")
            }
            ACTION_PRE_EVENT -> {
                val time = intent.getLongExtra(EXTRA_EVENT_TIME, 0L)
                val title = intent.getStringExtra(EXTRA_EVENT_TITLE).orEmpty()
                if (time <= 0) return
                val store = Store(context)
                val settings = store.settings
                val snap = store.snapshot
                val cluster = snap.events.filter { it.timeMillis == time && it.isMediumOrHigher }
                val cch = cluster.any { CalendarParser.isCchCatalyst(it, settings.cchKeywords) }
                val minutes = ((time - System.currentTimeMillis()) / 60_000L).coerceAtLeast(1)
                if (store.markAlerted(time)) Notifier.preEventAlert(context, title.ifEmpty { "High-impact release" }, cluster, minutes, cch)
                WidgetRenderer.renderAll(context)
                Notifier.updateCard(context)
                // Not forced: event-mode cadence decides whether a pre-release brief is worth paying for.
                Scheduler.refreshNow(context, "Pre-release: $title", force = false)
            }
            ACTION_POST_EVENT -> {
                val title = intent.getStringExtra(EXTRA_EVENT_TITLE).orEmpty()
                Scheduler.refreshNow(context, "Post-release: $title — report actual vs forecast and the reaction")
            }
        }
    }
}
