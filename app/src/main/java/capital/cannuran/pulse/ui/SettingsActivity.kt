package capital.cannuran.pulse.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
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
import capital.cannuran.pulse.core.Impact
import capital.cannuran.pulse.core.Settings
import capital.cannuran.pulse.data.Store
import capital.cannuran.pulse.notify.Notifier
import capital.cannuran.pulse.widget.WidgetRenderer
import capital.cannuran.pulse.work.EventAlarms
import capital.cannuran.pulse.work.Scheduler
import java.time.ZoneId

/** Single-screen control panel: status, live preview, API key, schedule, focus, lock-screen setup. */
class SettingsActivity : Activity() {

    private lateinit var store: Store
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { renderStatus(); handler.postDelayed(this, 3_000) }
    }

    private fun <T : View> v(id: Int): T = findViewById(id)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = Store(this)
        applyInsets()
        bind(store.settings)

        v<RadioButton>(R.id.model_sonnet).text = ClaudeModel.SONNET.label
        v<RadioButton>(R.id.model_haiku).text = ClaudeModel.HAIKU.label
        v<RadioButton>(R.id.model_opus).text = ClaudeModel.OPUS.label
        v<RadioGroup>(R.id.model_group).setOnCheckedChangeListener { _, _ -> renderCost(readForm()) }

        v<Button>(R.id.btn_refresh).setOnClickListener {
            WidgetRenderer.renderAll(this, refreshing = true)
            Scheduler.refreshNow(this, "Manual (app)")
            toast(if (store.hasApiKey) "Refreshing — a brief takes 20–60 s" else "Refreshing calendar & quotes (no API key yet)")
        }
        v<Button>(R.id.btn_save_key).setOnClickListener { saveKey() }
        v<Button>(R.id.btn_clear_key).setOnClickListener {
            store.apiKey = null
            renderKeyState()
            toast("Key removed")
        }
        v<Button>(R.id.btn_console).setOnClickListener { open(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/settings/keys"))) }
        v<Button>(R.id.btn_save).setOnClickListener { saveSettings() }
        v<Button>(R.id.btn_lockscreen_help).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.lockscreen_help_title)
                .setMessage(R.string.lockscreen_help_body)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        v<Button>(R.id.btn_notif).setOnClickListener { requestNotifications() }
        v<Button>(R.id.btn_battery).setOnClickListener { requestBatteryExemption() }
        v<Button>(R.id.btn_exact).setOnClickListener { requestExactAlarms() }

        v<TextView>(R.id.footer_text).text = "Cannuran Pulse ${BuildConfig.VERSION_NAME} · News: Claude API with web search · " +
            "Calendar: ForexFactory weekly feed · Quotes: Yahoo Finance (may be delayed). Information only, not investment advice."

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canPost(this)) requestNotifications()
    }

    override fun onResume() {
        super.onResume()
        renderKeyState()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Notifier.updateCard(this)
        renderStatus()
    }

    /* ---------------- form <-> settings ---------------- */

    private fun bind(s: Settings) {
        v<RadioGroup>(R.id.model_group).check(when (s.model) {
            ClaudeModel.SONNET -> R.id.model_sonnet
            ClaudeModel.HAIKU -> R.id.model_haiku
            ClaudeModel.OPUS -> R.id.model_opus
        })
        setText(R.id.active_start, s.activeStartHour)
        setText(R.id.active_end, s.activeEndHour)
        setText(R.id.active_cadence, s.activeCadenceMin)
        setText(R.id.off_cadence, s.offHoursCadenceMin)
        setText(R.id.weekend_cadence, s.weekendCadenceMin)
        setText(R.id.max_searches, s.maxSearches)
        setText(R.id.pre_alert, s.preEventAlertMin)
        setText(R.id.post_brief, s.postEventBriefMin)
        setText(R.id.lookback, s.lookbackHours)
        setText(R.id.event_cadence, s.eventCadenceMin)
        v<EditText>(R.id.focus).setText(s.focus)
        v<EditText>(R.id.symbols).setText(s.quoteSymbols.joinToString(", "))
        v<EditText>(R.id.currencies).setText(s.calendarCurrencies.joinToString(", "))
        v<EditText>(R.id.cch_keywords).setText(s.cchKeywords.joinToString(", "))
        v<Switch>(R.id.event_mode).isChecked = s.eventMode
        v<Switch>(R.id.include_medium).isChecked = s.includeMedium
        v<Switch>(R.id.ongoing_card).isChecked = s.ongoingCard
        v<Switch>(R.id.alert_high).isChecked = s.alertNewHigh
        v<Switch>(R.id.translucent).isChecked = s.translucentWidget
        renderCost(s)
    }

    private fun setText(id: Int, value: Int) = v<EditText>(id).setText(value.toString())

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
            activeStartHour = int(R.id.active_start, d.activeStartHour),
            activeEndHour = int(R.id.active_end, d.activeEndHour),
            activeCadenceMin = int(R.id.active_cadence, d.activeCadenceMin),
            offHoursCadenceMin = int(R.id.off_cadence, d.offHoursCadenceMin),
            weekendCadenceMin = int(R.id.weekend_cadence, d.weekendCadenceMin),
            maxSearches = int(R.id.max_searches, d.maxSearches),
            preEventAlertMin = int(R.id.pre_alert, d.preEventAlertMin),
            postEventBriefMin = int(R.id.post_brief, d.postEventBriefMin),
            lookbackHours = int(R.id.lookback, d.lookbackHours),
            eventCadenceMin = int(R.id.event_cadence, d.eventCadenceMin),
            eventMode = v<Switch>(R.id.event_mode).isChecked,
            focus = v<EditText>(R.id.focus).text.toString().trim().ifEmpty { Settings().focus },
            quoteSymbols = csv(R.id.symbols),
            calendarCurrencies = csv(R.id.currencies),
            includeMedium = v<Switch>(R.id.include_medium).isChecked,
            cchKeywords = csv(R.id.cch_keywords).ifEmpty { Settings().cchKeywords },
            ongoingCard = v<Switch>(R.id.ongoing_card).isChecked,
            alertNewHigh = v<Switch>(R.id.alert_high).isChecked,
            translucentWidget = v<Switch>(R.id.translucent).isChecked,
        ).sanitized()
    }

    private fun saveSettings() {
        val s = readForm()
        synchronized(Store.LOCK) {
            val old = store.settings
            store.settings = s
            // Calendar filters changed → refetch the feed on the next run.
            if (old.calendarCurrencies != s.calendarCurrencies || old.includeMedium != s.includeMedium) {
                store.snapshot = store.snapshot.copy(calendarFetchedAt = 0)
            }
        }
        bind(s) // show the clamped values
        if (!s.ongoingCard) Notifier.cancelCard(this) else Notifier.updateCard(this)
        WidgetRenderer.renderAll(this)
        EventAlarms.schedule(this, store.snapshot, s)
        Scheduler.refreshNow(this, "Settings changed", force = false)
        toast("Saved")
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
        toast("Key saved — pulling your first brief")
    }

    /* ---------------- status rendering ---------------- */

    private fun renderKeyState() {
        val key = store.apiKey
        v<TextView>(R.id.key_state).text = when {
            key != null -> "✓ Key stored (…${key.takeLast(4)})"
            store.hasApiKey -> "⚠ Stored key can't be decrypted — please re-enter it"
            else -> "No key yet. Calendar and quotes work without one; the news brief needs it."
        }
    }

    private fun renderCost(s: Settings) {
        val ledger = store.ledger.forDay(Store.today())
        val perBrief = if (ledger.calls > 0) ledger.costUsd / ledger.calls else Cost.typicalBrief(s.model)
        val n = Cost.briefsPerWeekday(s)
        v<TextView>(R.id.cost_estimate).text = String.format(
            java.util.Locale.US,
            "≈ %.0f briefs per trading day × %s ≈ %s/day (≈ %s per month of trading days). %s",
            n, Format.usd(perBrief), Format.usd(n * perBrief), Format.usd(n * perBrief * 21),
            if (ledger.calls > 0) "Based on today's measured cost per brief." else "Estimate; refines after the first briefs.",
        )
    }

    private fun renderStatus() {
        val snap = store.snapshot
        val s = store.settings
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val gold = getColor(R.color.pulse_gold)
        val warn = getColor(R.color.stale)
        val decision = Cadence.decide(now, zone, s, snap.events, snap.brief?.generatedAt ?: 0L, store.hasApiKey, false)
        val ledger = store.ledger.forDay(Store.today(zone))

        val st = SpannableStringBuilder()
        fun line(label: String, value: String, color: Int? = null) {
            st.append(label, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE).append("  ")
            if (color != null) st.append(value, ForegroundColorSpan(color), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) else st.append(value)
            st.append('\n')
        }
        val b = snap.brief
        line("Last brief", if (b == null) "none yet" else "${Format.time(b.generatedAt, zone)} (${Format.ago(b.generatedAt, now)}) · ${ClaudeModel.fromId(b.model).shortName}")
        if (b != null && b.reason.isNotEmpty()) line("Trigger", b.reason)
        line("Mode now", "${decision.mode.name.lowercase().replace('_', '-')} · every ${if (decision.cadenceMin > 0) "${decision.cadenceMin}m" else "— (paused)"} · ${decision.reason}")
        CalendarParser.nextHigh(snap.events, now)?.let { line("Next release", Format.eventLine(it, now, zone) + " · " + Format.countdown(it.timeMillis, now), gold) }
        line("Today", "${ledger.calls} briefs · ${ledger.usage.webSearches} searches · ${Format.tokens(ledger.usage.inputTokens + ledger.usage.cacheReadTokens)} in / ${Format.tokens(ledger.usage.outputTokens)} out · ≈ ${Format.usd(ledger.costUsd)}")
        snap.briefError?.let { line("Brief", it, warn) }
        snap.calendarError?.let { line("Calendar", it, warn) }
        snap.quoteError?.let { line("Quotes", it, warn) }
        val perms = buildList {
            if (!Notifier.canPost(this@SettingsActivity)) add("notifications off")
            if (!batteryExempt()) add("battery optimised")
            if (!EventAlarms.canExact(this@SettingsActivity)) add("inexact alarms")
        }
        line("Device", if (perms.isEmpty()) "all set ✓" else perms.joinToString(" · "), if (perms.isEmpty()) null else warn)
        v<TextView>(R.id.status_text).text = st.chompNewline()

        val p = SpannableStringBuilder()
        if (b == null) {
            p.append("Your brief will appear here and on the widget.")
        } else {
            p.append("RISK ${b.risk.name}", StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (b.cchFlag) p.append("  ·  CCH: ${b.cchReason.ifEmpty { "flagged" }}", ForegroundColorSpan(gold), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            p.append("\n").append(b.bias).append("\n")
            for (h in b.headlines) {
                val c = getColor(when (h.impact) { Impact.HIGH -> R.color.impact_high; Impact.MED -> R.color.impact_med; Impact.LOW -> R.color.impact_low })
                p.append("\n● ", ForegroundColorSpan(c), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                p.append(h.title, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                p.append("\n   ${h.direction.short} · ${h.instruments.joinToString(" ")} · ${h.source}")
                if (h.why.isNotEmpty()) p.append(" — ").append(h.why)
            }
            if (b.watch.isNotEmpty()) p.append("\n\nWATCH  ").append(b.watch.joinToString(" · "))
        }
        v<TextView>(R.id.preview_text).text = p
        renderCost(s)
    }

    private fun SpannableStringBuilder.chompNewline(): CharSequence =
        if (isNotEmpty() && this[length - 1] == '\n') subSequence(0, length - 1) else this

    /* ---------------- system permissions ---------------- */

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        } else {
            open(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun batteryExempt(): Boolean =
        getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true

    private fun requestBatteryExemption() {
        if (batteryExempt()) { toast("Already unrestricted ✓"); return }
        val direct = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        if (!open(direct, quiet = true)) open(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun requestExactAlarms() {
        if (EventAlarms.canExact(this)) { toast("Precise alarms already allowed ✓"); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            open(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
    }

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
        val base = content.paddingTop
        content.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(base + bars.left, base + bars.top, base + bars.right, base + bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(base + insets.systemWindowInsetLeft, base + insets.systemWindowInsetTop,
                    base + insets.systemWindowInsetRight, base + insets.systemWindowInsetBottom)
            }
            insets
        }
    }
}
