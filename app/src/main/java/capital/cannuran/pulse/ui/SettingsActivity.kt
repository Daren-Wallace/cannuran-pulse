package capital.cannuran.pulse.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import capital.cannuran.pulse.BuildConfig
import capital.cannuran.pulse.R
import capital.cannuran.pulse.core.Cadence
import capital.cannuran.pulse.core.CalendarParser
import capital.cannuran.pulse.core.ClaudeModel
import capital.cannuran.pulse.core.Cost
import capital.cannuran.pulse.core.Format
import capital.cannuran.pulse.core.Settings
import capital.cannuran.pulse.core.WidgetTheme
import capital.cannuran.pulse.data.Store
import capital.cannuran.pulse.notify.Notifier
import capital.cannuran.pulse.widget.WidgetRenderer
import capital.cannuran.pulse.work.EventAlarms
import capital.cannuran.pulse.work.Scheduler
import java.time.ZoneId
import java.util.Locale

/**
 * Control panel: live widget preview, at-a-glance stats, an issue banner, and card-based
 * settings that save automatically as you change them.
 */
class SettingsActivity : Activity() {

    private lateinit var store: Store
    private val handler = Handler(Looper.getMainLooper())
    private var binding = false
    private var lastPreviewKey = ""
    private var savePending = false

    private val ticker = object : Runnable {
        override fun run() { renderStatus(); handler.postDelayed(this, 3_000) }
    }
    private val autosave = Runnable { savePending = false; saveSettings() }
    private val restoreSubtitle = Runnable { v<TextView>(R.id.subtitle).text = SUBTITLE }

    companion object {
        private const val SUBTITLE = "Cannuran Capital · NQ/ES news desk"
        private const val KEYS_URL = "https://platform.claude.com/settings/keys"
        private const val BILLING_URL = "https://platform.claude.com/settings/billing"
    }

    private fun <T : View> v(id: Int): T = findViewById(id)

    private val numberFields = listOf(
        R.id.active_start, R.id.active_end, R.id.active_cadence, R.id.off_cadence, R.id.weekend_cadence,
        R.id.event_cadence, R.id.pre_alert, R.id.post_brief, R.id.max_searches, R.id.lookback,
    )
    private val textFields = listOf(R.id.focus, R.id.symbols, R.id.currencies, R.id.cch_keywords)
    private val switches = listOf(R.id.event_mode, R.id.include_medium, R.id.ongoing_card, R.id.alert_high)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = Store(this)
        applyInsets()
        bind(store.settings)
        wireAutosave()

        v<ImageButton>(R.id.btn_refresh).setOnClickListener {
            WidgetRenderer.renderAll(this, refreshing = true)
            Scheduler.refreshNow(this, "Manual (app)")
            flash(if (store.hasApiKey) "Refreshing · a brief takes 20–60 s" else "Refreshing calendar and quotes")
        }
        v<Button>(R.id.btn_save_key).setOnClickListener { saveKey() }
        v<Button>(R.id.btn_clear_key).setOnClickListener {
            store.apiKey = null
            renderKeyState()
            flash("Key removed")
        }
        v<Button>(R.id.btn_console).setOnClickListener { openUrl(KEYS_URL) }
        v<Button>(R.id.btn_billing).setOnClickListener { openUrl(BILLING_URL) }
        v<Button>(R.id.btn_lockscreen_help).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.lockscreen_help_title)
                .setMessage(R.string.lockscreen_help_body)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        v<Button>(R.id.perm_notif).setOnClickListener { requestNotifications() }
        v<Button>(R.id.perm_battery).setOnClickListener { requestBatteryExemption() }
        v<Button>(R.id.perm_exact).setOnClickListener { requestExactAlarms() }

        v<TextView>(R.id.footer_text).text = "Cannuran Pulse ${BuildConfig.VERSION_NAME}\n" +
            "News: Claude API with web search · Calendar: ForexFactory · Quotes: Yahoo Finance (may be delayed)\n" +
            "Information only, not investment advice."

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canPost(this)) requestNotifications()
    }

    override fun onResume() {
        super.onResume()
        renderKeyState()
        lastPreviewKey = ""
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        if (savePending) { handler.removeCallbacks(autosave); savePending = false; saveSettings() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Notifier.updateCard(this)
        renderStatus()
    }

    /* ---------------- form <-> settings ---------------- */

    private fun bind(s: Settings) {
        binding = true
        v<RadioGroup>(R.id.model_group).check(when (s.model) {
            ClaudeModel.SONNET -> R.id.model_sonnet
            ClaudeModel.HAIKU -> R.id.model_haiku
            ClaudeModel.OPUS -> R.id.model_opus
        })
        v<RadioGroup>(R.id.theme_group).check(when (s.theme) {
            WidgetTheme.MIDNIGHT -> R.id.theme_midnight
            WidgetTheme.GLASS -> R.id.theme_glass
            WidgetTheme.DYNAMIC -> R.id.theme_dynamic
        })
        setNum(R.id.active_start, s.activeStartHour)
        setNum(R.id.active_end, s.activeEndHour)
        setNum(R.id.active_cadence, s.activeCadenceMin)
        setNum(R.id.off_cadence, s.offHoursCadenceMin)
        setNum(R.id.weekend_cadence, s.weekendCadenceMin)
        setNum(R.id.event_cadence, s.eventCadenceMin)
        setNum(R.id.pre_alert, s.preEventAlertMin)
        setNum(R.id.post_brief, s.postEventBriefMin)
        setNum(R.id.max_searches, s.maxSearches)
        setNum(R.id.lookback, s.lookbackHours)
        setTextIfChanged(R.id.focus, s.focus)
        setTextIfChanged(R.id.symbols, s.quoteSymbols.joinToString(", "))
        setTextIfChanged(R.id.currencies, s.calendarCurrencies.joinToString(", "))
        setTextIfChanged(R.id.cch_keywords, s.cchKeywords.joinToString(", "))
        v<Switch>(R.id.event_mode).isChecked = s.eventMode
        v<Switch>(R.id.include_medium).isChecked = s.includeMedium
        v<Switch>(R.id.ongoing_card).isChecked = s.ongoingCard
        v<Switch>(R.id.alert_high).isChecked = s.alertNewHigh
        renderCost(s)
        binding = false
    }

    private fun setNum(id: Int, value: Int) = setTextIfChanged(id, value.toString())

    /** Avoids resetting the cursor of a field the user is typing in. */
    private fun setTextIfChanged(id: Int, value: String) {
        val f = v<EditText>(id)
        if (f.text.toString() != value && !(f.hasFocus() && f.text.toString().trim() == value.trim())) f.setText(value)
    }

    private fun int(id: Int, def: Int): Int = v<EditText>(id).text.toString().trim().toIntOrNull() ?: def

    private fun csv(id: Int): List<String> =
        v<EditText>(id).text.toString().split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    private fun readForm(): Settings {
        val d = store.settings
        return Settings(
            model = when (v<RadioGroup>(R.id.model_group).checkedRadioButtonId) {
                R.id.model_haiku -> ClaudeModel.HAIKU
                R.id.model_opus -> ClaudeModel.OPUS
                else -> ClaudeModel.SONNET
            },
            theme = when (v<RadioGroup>(R.id.theme_group).checkedRadioButtonId) {
                R.id.theme_glass -> WidgetTheme.GLASS
                R.id.theme_dynamic -> WidgetTheme.DYNAMIC
                else -> WidgetTheme.MIDNIGHT
            },
            activeStartHour = int(R.id.active_start, d.activeStartHour),
            activeEndHour = int(R.id.active_end, d.activeEndHour),
            activeCadenceMin = int(R.id.active_cadence, d.activeCadenceMin),
            offHoursCadenceMin = int(R.id.off_cadence, d.offHoursCadenceMin),
            weekendCadenceMin = int(R.id.weekend_cadence, d.weekendCadenceMin),
            eventCadenceMin = int(R.id.event_cadence, d.eventCadenceMin),
            preEventAlertMin = int(R.id.pre_alert, d.preEventAlertMin),
            postEventBriefMin = int(R.id.post_brief, d.postEventBriefMin),
            maxSearches = int(R.id.max_searches, d.maxSearches),
            lookbackHours = int(R.id.lookback, d.lookbackHours),
            eventMode = v<Switch>(R.id.event_mode).isChecked,
            focus = v<EditText>(R.id.focus).text.toString().trim().ifEmpty { Settings().focus },
            quoteSymbols = csv(R.id.symbols),
            calendarCurrencies = csv(R.id.currencies),
            includeMedium = v<Switch>(R.id.include_medium).isChecked,
            cchKeywords = csv(R.id.cch_keywords).ifEmpty { Settings().cchKeywords },
            ongoingCard = v<Switch>(R.id.ongoing_card).isChecked,
            alertNewHigh = v<Switch>(R.id.alert_high).isChecked,
        ).sanitized()
    }

    private fun wireAutosave() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(e: Editable?) { if (!binding) scheduleSave(900) }
        }
        (numberFields + textFields).forEach { v<EditText>(it).addTextChangedListener(watcher) }
        switches.forEach { id -> v<Switch>(id).setOnCheckedChangeListener { _, _ -> if (!binding) scheduleSave(150) } }
        v<RadioGroup>(R.id.model_group).setOnCheckedChangeListener { _, _ -> if (!binding) scheduleSave(150) }
        v<RadioGroup>(R.id.theme_group).setOnCheckedChangeListener { _, _ -> if (!binding) scheduleSave(150) }
    }

    private fun scheduleSave(delayMs: Long) {
        handler.removeCallbacks(autosave)
        savePending = true
        handler.postDelayed(autosave, delayMs)
        renderCost(readForm())
    }

    private fun saveSettings() {
        val s = readForm()
        var calendarChanged = false
        synchronized(Store.LOCK) {
            val old = store.settings
            if (old == s) return
            store.settings = s
            calendarChanged = old.calendarCurrencies != s.calendarCurrencies || old.includeMedium != s.includeMedium
            if (calendarChanged) store.snapshot = store.snapshot.copy(calendarFetchedAt = 0)
        }
        if (!s.ongoingCard) Notifier.cancelCard(this) else Notifier.updateCard(this)
        WidgetRenderer.renderAll(this)
        EventAlarms.schedule(this, store.snapshot, s)
        if (calendarChanged) Scheduler.refreshNow(this, "Calendar filters changed", force = false)
        lastPreviewKey = ""
        renderStatus()
        flash("Saved ✓")
    }

    private fun saveKey() {
        val field = v<EditText>(R.id.api_key)
        val key = field.text.toString().trim()
        if (!key.startsWith("sk-ant-")) {
            toast("That doesn't look like an Anthropic key (sk-ant-…)")
            return
        }
        store.apiKey = key
        field.setText("")
        renderKeyState()
        WidgetRenderer.renderAll(this, refreshing = true)
        Scheduler.refreshNow(this, "API key saved")
        flash("Key saved · pulling your first brief")
    }

    /** Briefly swaps the header subtitle for a status message, Material-snackbar style but quieter. */
    private fun flash(msg: String) {
        val sub = v<TextView>(R.id.subtitle)
        sub.text = msg
        handler.removeCallbacks(restoreSubtitle)
        handler.postDelayed(restoreSubtitle, 2_500)
    }

    /* ---------------- rendering ---------------- */

    private fun renderKeyState() {
        val key = store.apiKey
        v<TextView>(R.id.key_state).text = when {
            key != null -> "✓ Key stored (…${key.takeLast(4)})"
            store.hasApiKey -> "⚠ Stored key can't be decrypted. Please paste it again."
            else -> "No key yet. Calendar, countdown and quotes work without one; the news brief needs it."
        }
    }

    private fun renderCost(s: Settings) {
        val ledger = store.ledger.forDay(Store.today())
        val perBrief = if (ledger.calls > 0 && ClaudeModel.fromId(store.snapshot.brief?.model) == s.model) ledger.costUsd / ledger.calls
            else Cost.typicalBrief(s.model)
        val n = Cost.briefsPerWeekday(s)
        v<TextView>(R.id.cost_estimate).text = String.format(
            Locale.US, "≈ %.0f briefs per trading day · ≈ %s/day · ≈ %s/month",
            n, Format.usd(n * perBrief), Format.usd(n * perBrief * 22),
        )
    }

    private fun renderPreview() {
        val snap = store.snapshot
        val s = store.settings
        val key = snap.toJson().hashCode().toString() + s.toJson().hashCode() + (System.currentTimeMillis() / 60_000)
        if (key == lastPreviewKey) return
        lastPreviewKey = key
        val container = v<FrameLayout>(R.id.preview_container)
        try {
            val view = WidgetRenderer.views(this, WidgetRenderer.Tier.LARGE, snap, s, refreshing = false).apply(this, container)
            container.removeAllViews()
            container.addView(view)
        } catch (e: RuntimeException) {
            // A preview must never take the settings screen down.
            container.removeAllViews()
        }
    }

    private fun renderStatus() {
        renderPreview()
        val snap = store.snapshot
        val s = store.settings
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val decision = Cadence.decide(now, zone, s, snap.events, snap.brief?.generatedAt ?: 0L, store.hasApiKey, false)
        val ledger = store.ledger.forDay(Store.today(zone))
        val b = snap.brief

        // Stat tiles
        v<TextView>(R.id.stat_last).text = b?.let { Format.ago(it.generatedAt, now) } ?: "—"
        v<TextView>(R.id.stat_today).text = "${Format.usd(ledger.costUsd)} · ${ledger.calls}"
        v<TextView>(R.id.stat_mode).apply {
            text = when (decision.mode) {
                Cadence.Mode.EVENT -> "Event"; Cadence.Mode.ACTIVE -> "Active"
                Cadence.Mode.OFF_HOURS -> "Off-hours"; Cadence.Mode.WEEKEND -> "Weekend"
            }
            setTextColor(getColor(if (decision.mode == Cadence.Mode.EVENT) R.color.pulse_gold else R.color.text_primary))
        }

        // Device rows
        chip(R.id.perm_notif, Notifier.canPost(this))
        chip(R.id.perm_battery, batteryExempt())
        chip(R.id.perm_exact, EventAlarms.canExact(this))

        renderBanner(snap.briefError)

        // Diagnostics
        val lines = buildList {
            add("Last brief: " + (b?.let { "${Format.time(it.generatedAt, zone)} (${Format.ago(it.generatedAt, now)}) · ${ClaudeModel.fromId(it.model).shortName}" } ?: "none yet"))
            b?.reason?.takeIf { it.isNotEmpty() }?.let { add("Trigger: $it") }
            add("Mode: ${decision.mode.name.lowercase().replace('_', '-')} · every ${if (decision.cadenceMin > 0) "${decision.cadenceMin}m" else "— (paused)"} · ${decision.reason}")
            CalendarParser.nextHigh(snap.events, now)?.let { add("Next release: " + Format.eventLine(it, now, zone) + " · " + Format.countdown(it.timeMillis, now)) }
            add("Today: ${ledger.calls} briefs · ${ledger.usage.webSearches} searches · ${Format.tokens(ledger.usage.inputTokens + ledger.usage.cacheReadTokens)} in / ${Format.tokens(ledger.usage.outputTokens)} out · ≈ ${Format.usd(ledger.costUsd)}")
            snap.briefError?.let { add("⚠ Brief: $it") }
            snap.calendarError?.let { add("⚠ $it") }
            snap.quoteError?.let { add("⚠ Quotes: $it") }
        }
        v<TextView>(R.id.status_text).text = lines.joinToString("\n")
    }

    private fun chip(id: Int, ok: Boolean) {
        v<Button>(id).apply {
            text = if (ok) "✓ On" else "Fix"
            setBackgroundResource(if (ok) R.drawable.chip_ok else R.drawable.chip_fix)
            setTextColor(getColor(if (ok) R.color.up else R.color.on_accent))
        }
    }

    /** Shows the single most important problem, with a one-tap fix. */
    private fun renderBanner(briefError: String?) {
        data class Issue(val text: String, val action: String, val act: () -> Unit)
        val issue = when {
            !store.hasApiKey -> Issue("Add your Claude API key to turn on the news brief.", "Add key") {
                v<EditText>(R.id.api_key).requestFocus()
                v<View>(R.id.scroll).let { (it as android.widget.ScrollView).smoothScrollTo(0, v<View>(R.id.api_key).top) }
            }
            briefError?.contains("credit", true) == true -> Issue("Out of API credits. Top up in the Claude Console, then refresh.", "Add credits") { openUrl(BILLING_URL) }
            briefError?.contains("API key", true) == true -> Issue(briefError, "Fix key") { v<EditText>(R.id.api_key).requestFocus() }
            !Notifier.canPost(this) -> Issue("Notifications are off, so the lock-screen card and alerts can't show.", "Allow") { requestNotifications() }
            !batteryExempt() -> Issue("Battery optimised: One UI may pause refreshes and the widget will go stale.", "Fix") { requestBatteryExemption() }
            briefError != null -> Issue(briefError, "Retry") { Scheduler.refreshNow(this, "Manual (retry)") }
            else -> null
        }
        val banner = v<LinearLayout>(R.id.banner)
        if (issue == null) { banner.visibility = View.GONE; return }
        banner.visibility = View.VISIBLE
        v<TextView>(R.id.banner_text).text = issue.text
        v<Button>(R.id.banner_action).apply {
            text = issue.action
            setOnClickListener { issue.act() }
        }
    }

    /* ---------------- system permissions ---------------- */

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        } else {
            open(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun batteryExempt(): Boolean =
        getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true

    private fun requestBatteryExemption() {
        if (batteryExempt()) { flash("Battery already unrestricted ✓"); return }
        val direct = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        if (!open(direct, quiet = true)) open(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun requestExactAlarms() {
        if (EventAlarms.canExact(this)) { flash("Precise alarms already allowed ✓"); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            open(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
    }

    private fun openUrl(url: String) = open(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun open(intent: Intent, quiet: Boolean = false): Boolean = try {
        startActivity(intent); true
    } catch (e: ActivityNotFoundException) {
        if (!quiet) toast("Couldn't open that screen on this device")
        false
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /** targetSdk 35+ draws edge-to-edge; pad content clear of the status/nav bars and keyboard. */
    private fun applyInsets() {
        val content = v<View>(R.id.content)
        val l = content.paddingLeft; val t = content.paddingTop; val r = content.paddingRight; val b = content.paddingBottom
        content.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(l + insets.systemWindowInsetLeft, t + insets.systemWindowInsetTop,
                    r + insets.systemWindowInsetRight, b + insets.systemWindowInsetBottom)
            }
            insets
        }
    }
}
