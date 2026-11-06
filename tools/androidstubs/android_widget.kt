@file:Suppress("unused", "UNUSED_PARAMETER")
package android.widget

import android.view.View
open class TextView : View() { @set:JvmName("setTextProp") var text: CharSequence = "" ; fun setText(s: CharSequence) {} }
open class Button : TextView()
class EditText : TextView()
open class CompoundButton : Button() { var isChecked: Boolean = false }
class Switch : CompoundButton()
class RadioButton : CompoundButton()
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
  fun setViewVisibility(id: Int, v: Int) {}
  fun setInt(id: Int, method: String, v: Int) {}
  fun setOnClickPendingIntent(id: Int, p: android.app.PendingIntent) {}
  fun setChronometer(id: Int, base: Long, format: String?, started: Boolean) {}
  fun setChronometerCountDown(id: Int, down: Boolean) {}
}
