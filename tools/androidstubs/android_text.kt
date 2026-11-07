@file:Suppress("unused", "UNUSED_PARAMETER")
package android.text

interface Spanned : CharSequence { companion object { const val SPAN_EXCLUSIVE_EXCLUSIVE = 33 } }
interface Editable : CharSequence
interface TextWatcher {
  fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int)
  fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int)
  fun afterTextChanged(e: Editable?)
}
class SpannableStringBuilder : CharSequence, Spanned {
  override val length: Int get() = 0
  override fun get(index: Int): Char = ' '
  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = this
  fun append(t: CharSequence?): SpannableStringBuilder = this
  fun append(c: Char): SpannableStringBuilder = this
  fun append(t: CharSequence, what: Any, flags: Int): SpannableStringBuilder = this
}
