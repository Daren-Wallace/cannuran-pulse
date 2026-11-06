package capital.cannuran.pulse.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object Scheduler {
    private const val PERIODIC = "pulse-periodic"
    private const val NOW = "pulse-now"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * Every 15 minutes (Android's floor). Each run refreshes free data and lets [capital.cannuran.pulse.core.Cadence]
     * decide whether a paid Claude brief is due, so this interval is not the brief interval.
     */
    fun ensurePeriodic(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(network)
            .setInputData(workDataOf(RefreshWorker.KEY_REASON to "Periodic"))
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Immediate refresh. [force] bypasses the cadence (counts as a paid brief if a key is set). */
    fun refreshNow(ctx: Context, reason: String, force: Boolean = true) {
        val req = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(network)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(RefreshWorker.KEY_FORCE to force, RefreshWorker.KEY_REASON to reason))
            .build()
        // KEEP: tapping refresh while a brief is in flight must not cancel (and re-bill) it.
        WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, req)
    }
}
