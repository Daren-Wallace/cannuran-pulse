package capital.cannuran.pulse.core

/** Claude models the app offers, with list prices (USD per million tokens) used for the cost meter. */
enum class ClaudeModel(val id: String, val label: String, val inputPerM: Double, val outputPerM: Double) {
    SONNET("claude-sonnet-5-5", "Sonnet 5.5 — balanced (recommended)", 2.0, 10.0),
    HAIKU("claude-haiku-5-5", "Haiku 5.5 — cheapest", 0.10, 0.50),
    OPUS("claude-opus-5-5", "Opus 5.5 — deepest read", 4.0, 20.0);

    val shortName: String get() = label.substringBefore(" —")

    companion object {
        fun fromId(id: String?): ClaudeModel = entries.firstOrNull { it.id == id } ?: SONNET
        /** Web search is billed per search, independent of model. */
        const val SEARCH_COST_USD = 0.01
    }
}

/** Widget look. DYNAMIC follows the wallpaper's Material You palette on Android 12+. */
enum class WidgetTheme(val label: String) {
    MIDNIGHT("Midnight"), GLASS("Glass"), DYNAMIC("Material You");
    companion object {
        fun parse(s: String?): WidgetTheme = entries.firstOrNull { it.name.equals(s, true) } ?: MIDNIGHT
    }
}

const val DEFAULT_FOCUS = """Solo prop futures trader (Cannuran Capital Trading, Nairobi, UTC+3) trading NQ and ES on funded prop accounts with an ICT discretionary method, mainly London and New York AM sessions.
Runs a NAS100 CFD hedge (Cannuran Hedge Protocol). Its Contingent Catalyst Hedge (CCH) sits at zero lots and activates only for scheduled high-impact catalysts (FOMC, CPI, NFP, PCE, Fed Chair testimony/speeches) and major geopolitical shocks (e.g. Iran / Strait of Hormuz).
Care most about: Fed path and Fed speakers, US data surprises vs forecast, Treasury yields and the dollar, mega-cap tech and semis (NVDA, MSFT, AAPL, AMZN, META, GOOGL, AVGO, TSLA) earnings/guidance/AI capex, tariffs and trade policy, oil and geopolitics, US fiscal/shutdown risk, and anything that changes intraday volatility for NQ/ES."""

data class Settings(
    val model: ClaudeModel = ClaudeModel.SONNET,
    /** Minutes between briefs inside the active window. */
    val activeCadenceMin: Int = 60,
    /** Minutes between briefs outside the active window on trading days. 0 = never. */
    val offHoursCadenceMin: Int = 240,
    /** Minutes between briefs on the weekend (CME closed). 0 = never. */
    val weekendCadenceMin: Int = 0,
    /** Active window in local (device) hours, may wrap midnight. Defaults cover London open → NY close in EAT. */
    val activeStartHour: Int = 10,
    val activeEndHour: Int = 1,
    /** Tighten cadence to [eventCadenceMin] around high-impact releases. */
    val eventMode: Boolean = true,
    val eventCadenceMin: Int = 15,
    val maxSearches: Int = 5,
    val lookbackHours: Int = 12,
    val focus: String = DEFAULT_FOCUS.trim(),
    val quoteSymbols: List<String> = listOf("NQ=F", "ES=F", "^VIX", "CL=F"),
    val calendarCurrencies: List<String> = listOf("USD"),
    /** Include Medium-impact events on the calendar line (High always included). */
    val includeMedium: Boolean = true,
    /** Calendar titles containing any of these mark the day as a CCH catalyst day. */
    val cchKeywords: List<String> = listOf(
        "FOMC", "Federal Funds Rate", "CPI", "Non-Farm", "NFP", "Fed Chair", "PCE", "Core PCE",
    ),
    val ongoingCard: Boolean = true,
    val alertNewHigh: Boolean = true,
    /** Minutes before a high-impact release to post a heads-up. 0 = off. */
    val preEventAlertMin: Int = 10,
    /** Minutes after a high-impact release to pull a fresh brief with the print. 0 = off. */
    val postEventBriefMin: Int = 3,
    val theme: WidgetTheme = WidgetTheme.MIDNIGHT,
) {
    fun toJson(): String = Json.stringify(linkedMapOf(
        "model" to model.id,
        "activeCadenceMin" to activeCadenceMin, "offHoursCadenceMin" to offHoursCadenceMin,
        "weekendCadenceMin" to weekendCadenceMin,
        "activeStartHour" to activeStartHour, "activeEndHour" to activeEndHour,
        "eventMode" to eventMode, "eventCadenceMin" to eventCadenceMin,
        "maxSearches" to maxSearches, "lookbackHours" to lookbackHours, "focus" to focus,
        "quoteSymbols" to quoteSymbols, "calendarCurrencies" to calendarCurrencies,
        "includeMedium" to includeMedium, "cchKeywords" to cchKeywords,
        "ongoingCard" to ongoingCard, "alertNewHigh" to alertNewHigh,
        "preEventAlertMin" to preEventAlertMin, "postEventBriefMin" to postEventBriefMin,
        "theme" to theme.name,
    ))

    /** Clamp everything to sane bounds so a typo in the settings screen can't run up a bill. */
    fun sanitized(): Settings = copy(
        activeCadenceMin = activeCadenceMin.coerceIn(15, 24 * 60),
        offHoursCadenceMin = if (offHoursCadenceMin <= 0) 0 else offHoursCadenceMin.coerceIn(30, 24 * 60),
        weekendCadenceMin = if (weekendCadenceMin <= 0) 0 else weekendCadenceMin.coerceIn(60, 7 * 24 * 60),
        activeStartHour = activeStartHour.mod(24),
        activeEndHour = activeEndHour.mod(24),
        eventCadenceMin = eventCadenceMin.coerceIn(10, 120),
        maxSearches = maxSearches.coerceIn(1, 10),
        lookbackHours = lookbackHours.coerceIn(2, 48),
        focus = focus.take(2000),
        quoteSymbols = quoteSymbols.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(6),
        calendarCurrencies = calendarCurrencies.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct(),
        cchKeywords = cchKeywords.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        preEventAlertMin = preEventAlertMin.coerceIn(0, 60),
        postEventBriefMin = postEventBriefMin.coerceIn(0, 30),
    )

    companion object {
        fun fromJson(text: String?): Settings {
            if (text.isNullOrBlank()) return Settings()
            val d = Settings()
            return try {
                val m = Json.parseObject(text)
                fun list(key: String, def: List<String>) =
                    if (m.containsKey(key)) m.arr(key).mapNotNull { it?.toString() } else def
                Settings(
                    model = ClaudeModel.fromId(m.str("model")),
                    activeCadenceMin = m.long("activeCadenceMin")?.toInt() ?: d.activeCadenceMin,
                    offHoursCadenceMin = m.long("offHoursCadenceMin")?.toInt() ?: d.offHoursCadenceMin,
                    weekendCadenceMin = m.long("weekendCadenceMin")?.toInt() ?: d.weekendCadenceMin,
                    activeStartHour = m.long("activeStartHour")?.toInt() ?: d.activeStartHour,
                    activeEndHour = m.long("activeEndHour")?.toInt() ?: d.activeEndHour,
                    eventMode = m.bool("eventMode") ?: d.eventMode,
                    eventCadenceMin = m.long("eventCadenceMin")?.toInt() ?: d.eventCadenceMin,
                    maxSearches = m.long("maxSearches")?.toInt() ?: d.maxSearches,
                    lookbackHours = m.long("lookbackHours")?.toInt() ?: d.lookbackHours,
                    focus = m.str("focus") ?: d.focus,
                    quoteSymbols = list("quoteSymbols", d.quoteSymbols),
                    calendarCurrencies = list("calendarCurrencies", d.calendarCurrencies),
                    includeMedium = m.bool("includeMedium") ?: d.includeMedium,
                    cchKeywords = list("cchKeywords", d.cchKeywords),
                    ongoingCard = m.bool("ongoingCard") ?: d.ongoingCard,
                    alertNewHigh = m.bool("alertNewHigh") ?: d.alertNewHigh,
                    preEventAlertMin = m.long("preEventAlertMin")?.toInt() ?: d.preEventAlertMin,
                    postEventBriefMin = m.long("postEventBriefMin")?.toInt() ?: d.postEventBriefMin,
                    // v1.0 stored a translucent flag; it maps to the Glass theme.
                    theme = m.str("theme")?.let(WidgetTheme::parse)
                        ?: if (m.bool("translucentWidget") == true) WidgetTheme.GLASS else d.theme,
                ).sanitized()
            } catch (e: RuntimeException) {
                d
            }
        }
    }
}

/** Daily usage ledger, keyed by local date (yyyy-MM-dd). */
data class UsageLedger(val day: String = "", val calls: Int = 0, val usage: Usage = Usage(), val costUsd: Double = 0.0) {
    fun add(today: String, u: Usage, model: ClaudeModel): UsageLedger {
        val base = if (day == today) this else UsageLedger(day = today)
        return base.copy(calls = base.calls + 1, usage = base.usage + u, costUsd = base.costUsd + Cost.of(u, model))
    }

    fun forDay(today: String): UsageLedger = if (day == today) this else UsageLedger(day = today)

    fun toJson(): String = Json.stringify(linkedMapOf(
        "day" to day, "calls" to calls, "usage" to usage.toMap(), "cost" to costUsd,
    ))

    companion object {
        fun fromJson(text: String?): UsageLedger = try {
            if (text.isNullOrBlank()) UsageLedger() else Json.parseObject(text).let {
                UsageLedger(it.str("day").orEmpty(), it.long("calls")?.toInt() ?: 0,
                    Usage.fromMap(it.obj("usage")), it.num("cost") ?: 0.0)
            }
        } catch (e: RuntimeException) { UsageLedger() }
    }
}

object Cost {
    /** Typical cost of one brief before we've measured any (≈20k input incl. search results, 1k output, 4 searches). */
    fun typicalBrief(model: ClaudeModel): Double = of(Usage(inputTokens = 20_000, outputTokens = 1_000, webSearches = 4), model)

    /**
     * Expected briefs per weekday: active window at its cadence, off-hours at theirs, plus
     * extra event-mode briefs (≈ (lead+tail)/eventCadence per high-impact release, ~2 a day).
     */
    fun briefsPerWeekday(s: Settings, highImpactPerDay: Double = 2.0): Double {
        val x = s.sanitized()
        val activeHours = ((x.activeEndHour - x.activeStartHour).mod(24)).let { if (it == 0) 24 else it }
        val offHours = 24 - activeHours
        val active = activeHours * 60.0 / x.activeCadenceMin
        val off = if (x.offHoursCadenceMin <= 0) 0.0 else offHours * 60.0 / x.offHoursCadenceMin
        val eventWindow = (Cadence.EVENT_LEAD_MIN + Cadence.EVENT_TAIL_MIN).toDouble()
        val eventExtra = if (x.eventMode) highImpactPerDay * (eventWindow / x.eventCadenceMin - eventWindow / x.activeCadenceMin).coerceAtLeast(0.0) else 0.0
        val post = if (x.postEventBriefMin > 0) highImpactPerDay * 0.5 else 0.0 // post-release briefs often coincide with event mode
        return active + off + eventExtra + post
    }

    /** Estimated USD for one call. Cache reads ~0.05x input on 5.5 models, cache writes 1.25x. */
    fun of(u: Usage, model: ClaudeModel): Double =
        (u.inputTokens * model.inputPerM +
            u.cacheReadTokens * model.inputPerM * 0.05 +
            u.cacheWriteTokens * model.inputPerM * 1.25 +
            u.outputTokens * model.outputPerM) / 1_000_000.0 +
            u.webSearches * ClaudeModel.SEARCH_COST_USD
}
