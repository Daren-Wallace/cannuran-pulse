@file:Suppress("unused", "UNUSED_PARAMETER")
package android.app

import android.content.Context
open class Application : Context() { open fun onCreate() {} }
open class Activity : Context() {
  open fun onCreate(savedInstanceState: android.os.Bundle?) {}
  open fun onResume() {}
  open fun onPause() {}
  open fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {}
  fun setContentView(id: Int) {}
  fun <T : android.view.View> findViewById(id: Int): T = TODO()
  fun requestPermissions(p: Array<String>, rc: Int) {}
}
class AlertDialog { class Builder(c: Context) {
  fun setTitle(id: Int): Builder = this; fun setMessage(id: Int): Builder = this
  fun setPositiveButton(id: Int, l: Any?): Builder = this; fun show(): AlertDialog = AlertDialog() } }
class PendingIntent { companion object {
  const val FLAG_IMMUTABLE = 1; const val FLAG_UPDATE_CURRENT = 2
  @JvmStatic fun getActivity(c: Context, rc: Int, i: android.content.Intent, f: Int): PendingIntent = PendingIntent()
  @JvmStatic fun getBroadcast(c: Context, rc: Int, i: android.content.Intent, f: Int): PendingIntent = PendingIntent() } }
class AlarmManager {
  fun canScheduleExactAlarms(): Boolean = true
  fun setExactAndAllowWhileIdle(t: Int, at: Long, p: PendingIntent) {}
  fun setAndAllowWhileIdle(t: Int, at: Long, p: PendingIntent) {}
  fun cancel(p: PendingIntent) {}
  companion object { const val RTC_WAKEUP = 0 }
}
class NotificationChannel(id: String, name: CharSequence, importance: Int) {
  var description: String? = null
  var lockscreenVisibility: Int = 0
  fun setShowBadge(b: Boolean) {}
  fun enableVibration(b: Boolean) {}
}
class NotificationManager {
  fun createNotificationChannels(l: List<NotificationChannel>) {}
  fun areNotificationsEnabled(): Boolean = true
  fun notify(id: Int, n: Notification) {}
  fun notify(tag: String, id: Int, n: Notification) {}
  fun cancel(id: Int) {}
  companion object { const val IMPORTANCE_LOW = 2; const val IMPORTANCE_HIGH = 4; const val IMPORTANCE_MIN = 1 }
}
class Icon
class Notification {
  companion object { const val VISIBILITY_PUBLIC = 1; const val CATEGORY_STATUS = "s"; const val CATEGORY_RECOMMENDATION = "r"; const val CATEGORY_EVENT = "e" }
  abstract class Style
  class BigTextStyle : Style() { fun bigText(t: CharSequence): BigTextStyle = this }
  class Action { class Builder(icon: Icon?, title: CharSequence, intent: PendingIntent) { fun build(): Action = Action() } }
  class Builder(c: Context, channel: String) {
    fun setSmallIcon(i: Int): Builder = this
    fun setColor(c: Int): Builder = this
    fun setContentTitle(t: CharSequence): Builder = this
    fun setContentText(t: CharSequence): Builder = this
    fun setStyle(s: Style): Builder = this
    fun setSubText(t: CharSequence): Builder = this
    fun setOngoing(b: Boolean): Builder = this
    fun setOnlyAlertOnce(b: Boolean): Builder = this
    fun setShowWhen(b: Boolean): Builder = this
    fun setWhen(w: Long): Builder = this
    fun setVisibility(v: Int): Builder = this
    fun setCategory(c: String): Builder = this
    fun setContentIntent(p: PendingIntent): Builder = this
    fun addAction(a: Action): Builder = this
    fun setAutoCancel(b: Boolean): Builder = this
    fun setTimeoutAfter(ms: Long): Builder = this
    fun build(): Notification = Notification()
  }
}
