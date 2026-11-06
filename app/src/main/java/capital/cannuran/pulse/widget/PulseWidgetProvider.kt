package capital.cannuran.pulse.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import capital.cannuran.pulse.data.Store
import capital.cannuran.pulse.work.Scheduler

class PulseWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "capital.cannuran.pulse.action.REFRESH"
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetRenderer.renderAll(context)
        Scheduler.ensurePeriodic(context)
        // First placement: pull calendar/quotes (and a brief if a key exists) right away.
        if (Store(context).snapshot.lastAttemptAt == 0L) Scheduler.refreshNow(context, "Widget added")
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        WidgetRenderer.renderAll(context) // re-pick density tier after a resize
    }

    override fun onEnabled(context: Context) {
        Scheduler.ensurePeriodic(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            WidgetRenderer.renderAll(context, refreshing = true)
            Scheduler.refreshNow(context, "Manual (widget)")
        }
    }
}
