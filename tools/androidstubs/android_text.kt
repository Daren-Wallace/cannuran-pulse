@file:Suppress("unused", "UNUSED_PARAMETER")
package android.text

interface Spanned : CharSequence { companion object { const val SPAN_EXCLUSIVE_EXCLUSIVE = 33 } }
class SpannableStringBuilder : CharSequence, Spanned {
  override val length: Int get() = 0
  override fun get(index: Int): Char = ' '
  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = this
  fun append(t: CharSequence?): SpannableStringBuilder = this
  fun append(c: Char): SpannableStringBuilder = this
  fun append(t: CharSequence, what: Any, flags: Int): SpannableStringBuilder = this
}
