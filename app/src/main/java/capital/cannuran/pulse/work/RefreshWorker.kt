package capital.cannuran.pulse.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import capital.cannuran.pulse.core.RefreshEngine
import capital.cannuran.pulse.data.AndroidHttp
import capital.cannuran.pulse.data.Store
import capital.cannuran.pulse.notify.Notifier
import capital.cannuran.pulse.widget.WidgetRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId

/** Runs one refresh cycle and pushes the result to every surface (widgets, lock-screen card, alerts, alarms). */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_FORCE = "force"
        const val KEY_REASON = "reason"
        private const val TAG = "PulseRefresh"
        /** Periodic and on-demand runs can overlap; serialise them so we never pay for two briefs at once. */
        private val mutex = Mutex()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val force = inputData.getBoolean(KEY_FORCE, false)
        val reason = inputData.getString(KEY_REASON) ?: "Periodic"
        mutex.withLock {
            val store = Store(ctx)
            val settings = store.settings
            val zone = ZoneId.systemDefault()
            val prev = store.snapshot // read inside the lock so a just-finished run is seen
            val outcome = try {
                RefreshEngine(AndroidHttp(), zone).refresh(prev, settings, store.apiKey, force, reason)
            } catch (e: Exception) {
                Log.e(TAG, "Refresh failed", e)
                null
            }
            if (outcome != null) {
                synchronized(Store.LOCK) {
                    store.snapshot = outcome.snapshot
                    outcome.usage?.let { store.ledger = store.ledger.add(Store.today(zone), it, settings.model) }
                }
                Log.i(TAG, "mode=${outcome.decision.mode} briefed=${outcome.briefed} ${outcome.decision.reason}")
                if (settings.alertNewHigh) outcome.newHighHeadlines.take(2).forEach { Notifier.alertHeadline(ctx, it) }
                EventAlarms.schedule(ctx, outcome.snapshot, settings)
            }
            WidgetRenderer.renderAll(ctx)
            Notifier.updateCard(ctx)
        }
        Result.success()
    }

    /** Only used for expedited work on Android 11 and below. */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(Notifier.WORK_ID, Notifier.workNotification(applicationContext))
}
