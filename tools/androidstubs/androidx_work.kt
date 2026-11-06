@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.work

import android.content.Context
class WorkerParameters
class Data
fun workDataOf(vararg pairs: Pair<String, Any?>): Data = Data()
abstract class ListenableWorkerResult
abstract class CoroutineWorker(appContext: Context, params: WorkerParameters) {
  val applicationContext: Context get() = TODO()
  val inputData: InputData get() = InputData()
  abstract suspend fun doWork(): Result
  open suspend fun getForegroundInfo(): ForegroundInfo = TODO()
  class InputData { fun getBoolean(k: String, d: Boolean): Boolean = d; fun getString(k: String): String? = null }
  abstract class Result { companion object { @JvmStatic fun success(): Result = TODO() } }
}
class ForegroundInfo(id: Int, n: android.app.Notification)
enum class NetworkType { CONNECTED }
class Constraints { class Builder { fun setRequiredNetworkType(t: NetworkType): Builder = this; fun build(): Constraints = Constraints() } }
enum class ExistingWorkPolicy { KEEP, REPLACE }
enum class ExistingPeriodicWorkPolicy { KEEP, UPDATE }
enum class OutOfQuotaPolicy { RUN_AS_NON_EXPEDITED_WORK_REQUEST }
open class WorkRequest
class OneTimeWorkRequest : WorkRequest()
class PeriodicWorkRequest : WorkRequest()
class OneTimeBuilder { fun setConstraints(c: Constraints) = this; fun setExpedited(p: OutOfQuotaPolicy) = this
  fun setInputData(d: Data) = this; fun build(): OneTimeWorkRequest = OneTimeWorkRequest() }
class PeriodicBuilder { fun setConstraints(c: Constraints) = this; fun setInputData(d: Data) = this; fun build(): PeriodicWorkRequest = PeriodicWorkRequest() }
inline fun <reified W : CoroutineWorker> OneTimeWorkRequestBuilder(): OneTimeBuilder = OneTimeBuilder()
inline fun <reified W : CoroutineWorker> PeriodicWorkRequestBuilder(interval: Long, unit: java.util.concurrent.TimeUnit): PeriodicBuilder = PeriodicBuilder()
class WorkManager {
  fun enqueueUniquePeriodicWork(n: String, p: ExistingPeriodicWorkPolicy, r: PeriodicWorkRequest): Any = 0
  fun enqueueUniqueWork(n: String, p: ExistingWorkPolicy, r: OneTimeWorkRequest): Any = 0
  companion object { @JvmStatic fun getInstance(c: Context): WorkManager = WorkManager() }
}
