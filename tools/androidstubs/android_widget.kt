@file:Suppress("unused", "UNUSED_PARAMETER")
package android.widget

import android.view.View
import android.view.ViewGroup
open class TextView : View() { @set:JvmName("setTextProp") var text: CharSequence = "" ; fun setText(s: CharSequence) {}
  fun setTextColor(c: Int) {} ; fun addTextChangedListener(w: android.text.TextWatcher) {} }
open class Button : TextView()
class ImageButton : View()
class EditText : TextView()
open class CompoundButton : Button() { var isChecked: Boolean = false
  fun setOnCheckedChangeListener(l: (CompoundButton, Boolean) -> Unit) {} }
class Switch : CompoundButton()
class RadioButton : CompoundButton()
class FrameLayout : ViewGroup()
class LinearLayout : ViewGroup()
class ScrollView : ViewGroup() { fun smoothScrollTo(x: Int, y: Int) {} }
class RadioGroup : View() {
  fun check(id: Int) {}
  val checkedRadioButtonId: Int get() = 0
  fun setOnCheckedChangeListener(l: (RadioGroup, Int) -> Unit) {}
}
class Toast { fun show() {} companion object { const val LENGTH_SHORT = 0
  @JvmStatic fun makeText(c: android.content.Context, t: CharSequence, d: Int): Toast = Toast() } }
class RemoteViews {
  constructor(pkg: String, layout: Int)
  constructor(map: Map<android.util.SizeF, RemoteViews>)
  fun setTextViewText(id: Int, t: CharSequence) {}
  fun setTextViewTextSize(id: Int, unit: Int, size: Float) {}
  fun setTextColor(id: Int, c: Int) {}
  fun setImageViewBitmap(id: Int, b: android.graphics.Bitmap) {}
  fun setViewVisibility(id: Int, v: Int) {}
  fun setInt(id: Int, method: String, v: Int) {}
  fun setOnClickPendingIntent(id: Int, p: android.app.PendingIntent) {}
  fun setChronometer(id: Int, base: Long, format: String?, started: Boolean) {}
  fun setChronometerCountDown(id: Int, down: Boolean) {}
  fun apply(c: android.content.Context, parent: ViewGroup): View = View()
}
