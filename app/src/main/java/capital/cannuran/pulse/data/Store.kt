package capital.cannuran.pulse.data

import android.content.Context
import android.content.SharedPreferences
import capital.cannuran.pulse.core.Settings
import capital.cannuran.pulse.core.Snapshot
import capital.cannuran.pulse.core.UsageLedger
import java.time.LocalDate
import java.time.ZoneId

/** App-private persistence for settings, the last snapshot, usage, and the encrypted API key. */
class Store(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("pulse", Context.MODE_PRIVATE)

    companion object {
        private const val K_SETTINGS = "settings"
        private const val K_SNAPSHOT = "snapshot"
        private const val K_LEDGER = "ledger"
        private const val K_API_KEY = "api_key_enc"
        private const val K_ALERTED = "alerted_events"
        /** Guards read-modify-write cycles between the worker and the settings screen. */
        val LOCK = Any()

        fun today(zone: ZoneId = ZoneId.systemDefault()): String = LocalDate.now(zone).toString()
    }

    var settings: Settings
        get() = Settings.fromJson(prefs.getString(K_SETTINGS, null))
        set(value) { prefs.edit().putString(K_SETTINGS, value.sanitized().toJson()).apply() }

    var snapshot: Snapshot
        get() = Snapshot.fromJson(prefs.getString(K_SNAPSHOT, null))
        set(value) { prefs.edit().putString(K_SNAPSHOT, value.toJson()).commit() }

    var ledger: UsageLedger
        get() = UsageLedger.fromJson(prefs.getString(K_LEDGER, null))
        set(value) { prefs.edit().putString(K_LEDGER, value.toJson()).apply() }

    var apiKey: String?
        get() = SecretBox.decrypt(prefs.getString(K_API_KEY, null))?.takeIf { it.isNotBlank() }
        set(value) {
            val v = value?.trim()
            if (v.isNullOrEmpty()) prefs.edit().remove(K_API_KEY).apply()
            else prefs.edit().putString(K_API_KEY, SecretBox.encrypt(v)).apply()
        }

    val hasApiKey: Boolean get() = prefs.contains(K_API_KEY)

    /** Event timestamps we've already posted a pre-release heads-up for (so reschedules don't double-alert). */
    fun markAlerted(eventTime: Long): Boolean = synchronized(LOCK) {
        val set = prefs.getStringSet(K_ALERTED, emptySet())!!.toMutableSet()
        val cutoff = System.currentTimeMillis() - 3 * 24 * 3_600_000L
        set.removeAll { (it.toLongOrNull() ?: 0L) < cutoff }
        val added = set.add(eventTime.toString())
        prefs.edit().putStringSet(K_ALERTED, set).apply()
        added
    }
}
