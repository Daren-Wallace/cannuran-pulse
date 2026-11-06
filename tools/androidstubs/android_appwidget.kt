@file:Suppress("unused", "UNUSED_PARAMETER")
package android.appwidget

import android.content.Context
class AppWidgetManager {
  fun getAppWidgetIds(c: android.content.ComponentName): IntArray = IntArray(0)
  fun updateAppWidget(id: Int, rv: android.widget.RemoteViews) {}
  fun getAppWidgetOptions(id: Int): android.os.Bundle = android.os.Bundle()
  companion object { const val OPTION_APPWIDGET_MAX_HEIGHT = "h"
    @JvmStatic fun getInstance(c: Context): AppWidgetManager? = null }
}
open class AppWidgetProvider : android.content.BroadcastReceiver() {
  open fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {}
  open fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle) {}
  open fun onEnabled(context: Context) {}
  override fun onReceive(context: Context, intent: android.content.Intent) {}
}
