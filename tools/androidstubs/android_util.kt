@file:Suppress("unused", "UNUSED_PARAMETER")
package android.util

object Base64 { const val NO_WRAP = 2
  @JvmStatic fun encodeToString(b: ByteArray, f: Int): String = ""
  @JvmStatic fun decode(s: String, f: Int): ByteArray = ByteArray(0) }
object Log { @JvmStatic fun e(t: String, m: String, e: Throwable): Int = 0; @JvmStatic fun i(t: String, m: String): Int = 0 }
class SizeF(w: Float, h: Float)
object TypedValue { const val COMPLEX_UNIT_SP = 2 }

