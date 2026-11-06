package capital.cannuran.pulse.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import capital.cannuran.pulse.data.Store
import capital.cannuran.pulse.notify.Notifier
import capital.cannuran.pulse.widget.WidgetRenderer

/** Restores the lock-screen card and event alarms after reboot, app update, or a time-zone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store(context)
        Scheduler.ensurePeriodic(context)
        EventAlarms.schedule(context, store.snapshot, store.settings)
        WidgetRenderer.renderAll(context)
        Notifier.updateCard(context)
        Scheduler.refreshNow(context, "Restart", force = false)
    }
}
