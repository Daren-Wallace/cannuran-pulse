@file:Suppress("unused", "UNUSED_PARAMETER")
package android.view

open class View(ctx: android.content.Context? = null) {
  val paddingTop: Int get() = 0
  val paddingLeft: Int get() = 0
  val paddingRight: Int get() = 0
  val paddingBottom: Int get() = 0
  val top: Int get() = 0
  var visibility: Int = 0
  fun setPadding(l: Int, t: Int, r: Int, b: Int) {}
  fun setOnApplyWindowInsetsListener(l: (View, WindowInsets) -> WindowInsets) {}
  fun setOnClickListener(l: (View) -> Unit) {}
  fun setBackgroundResource(id: Int) {}
  fun hasFocus(): Boolean = false
  fun requestFocus(): Boolean = true
  companion object { const val VISIBLE = 0; const val GONE = 8 }
}
open class ViewGroup : View() { fun removeAllViews() {}; fun addView(v: View) {} }
class WindowInsets {
  fun getInsets(t: Int): Insets = Insets()
  val systemWindowInsetLeft: Int get() = 0
  val systemWindowInsetTop: Int get() = 0
  val systemWindowInsetRight: Int get() = 0
  val systemWindowInsetBottom: Int get() = 0
  class Insets { val left = 0; val top = 0; val right = 0; val bottom = 0 }
  object Type { @JvmStatic fun systemBars(): Int = 1; @JvmStatic fun ime(): Int = 2 }
}
