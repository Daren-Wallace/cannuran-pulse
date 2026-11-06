@file:Suppress("unused", "UNUSED_PARAMETER")
package android.content

open class Context {
    open val packageName: String get() = ""
    open val applicationContext: Context get() = this
    open fun getString(id: Int): String = ""
    open fun getColor(id: Int): Int = 0
    open fun <T> getSystemService(c: Class<T>): T? = null
    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences = TODO()
    open fun checkSelfPermission(p: String): Int = 0
    open fun startActivity(i: Intent) {}
    companion object { const val MODE_PRIVATE = 0 }
}
class Intent() {
    constructor(ctx: Context, c: Class<*>) : this()
    constructor(action: String) : this()
    constructor(action: String, uri: android.net.Uri) : this()
    var action: String? = null
    fun setAction(a: String): Intent = this
    fun addFlags(f: Int): Intent = this
    fun putExtra(k: String, v: Long): Intent = this
    fun putExtra(k: String, v: String): Intent = this
    fun getLongExtra(k: String, d: Long): Long = d
    fun getStringExtra(k: String): String? = null
    companion object {
        const val ACTION_VIEW = "v"; const val FLAG_ACTIVITY_NEW_TASK = 1; const val FLAG_ACTIVITY_CLEAR_TOP = 2
    }
}
class ComponentName(ctx: Context, c: Class<*>)
class ActivityNotFoundException : RuntimeException()
abstract class BroadcastReceiver { abstract fun onReceive(context: Context, intent: Intent) }
interface SharedPreferences {
    fun getString(k: String, d: String?): String?
    fun getStringSet(k: String, d: Set<String>?): Set<String>?
    fun contains(k: String): Boolean
    fun edit(): Editor
    interface Editor {
        fun putString(k: String, v: String?): Editor
        fun putStringSet(k: String, v: Set<String>?): Editor
        fun remove(k: String): Editor
        fun apply()
        fun commit(): Boolean
    }
}

