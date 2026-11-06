@file:Suppress("unused", "UNUSED_PARAMETER")
package android.os

class Bundle { fun getInt(k: String, d: Int): Int = d }
object Build { object VERSION { const val SDK_INT: Int = 36 }
  object VERSION_CODES { const val R = 30; const val S = 31; const val TIRAMISU = 33 } }
class Looper { companion object { @JvmStatic fun getMainLooper(): Looper = Looper() } }
class Handler(l: Looper) { fun post(r: Runnable): Boolean = true; fun postDelayed(r: Runnable, d: Long): Boolean = true; fun removeCallbacks(r: Runnable) {} }
class PowerManager { fun isIgnoringBatteryOptimizations(p: String): Boolean = false }
object SystemClock { @JvmStatic fun elapsedRealtime(): Long = 0 }

