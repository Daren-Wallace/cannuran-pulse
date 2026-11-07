package capital.cannuran.pulse.core

/** How much a headline matters for NQ/ES right now. */
enum class Impact { HIGH, MED, LOW;
    companion object {
        fun parse(s: String?): Impact = when (s?.trim()?.uppercase()) {
            "HIGH", "H", "CRITICAL", "RED" -> HIGH
            "MED", "MEDIUM", "M", "MODERATE", "ORANGE" -> MED
            else -> LOW
        }
    }
}

/** Directional read for Nasdaq/S&P futures. */
enum class Direction { BULL, BEAR, MIXED;
    val short: String get() = when (this) { BULL -> "▲ BULL"; BEAR -> "▼ BEAR"; MIXED -> "◆ MIXED" }
    companion object {
        fun parse(s: String?): Direction = when (s?.trim()?.uppercase()) {
            "BULL", "BULLISH", "UP", "RISK-ON", "RISK_ON", "LONG" -> BULL
            "BEAR", "BEARISH", "DOWN", "RISK-OFF", "RISK_OFF", "SHORT" -> BEAR
            else -> MIXED
        }
    }
}

/** Overall tape risk for the session. */
enum class Risk { LOW, ELEVATED, HIGH;
    companion object {
        fun parse(s: String?): Risk = when (s?.trim()?.uppercase()) {
            "HIGH", "EXTREME", "SEVERE" -> HIGH
            "ELEVATED", "MEDIUM", "MED", "MODERATE" -> ELEVATED
            else -> LOW
        }
    }
}

data class Headline(
    val title: String,
    val why: String,
    val impact: Impact,
    val direction: Direction,
    val instruments: List<String>,
    val source: String,
    val url: String,
    /** Publication time in epoch millis, if Claude could establish it. */
    val publishedAt: Long?,
    /** True when this headline was not in the previous brief. Computed locally, never by the model. */
    val isNew: Boolean = false,
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "title" to title, "why" to why, "impact" to impact.name, "direction" to direction.name,
        "instruments" to instruments, "source" to source, "url" to url,
        "publishedAt" to publishedAt, "isNew" to isNew,
    )

    companion object {
        fun fromMap(m: Map<String, Any?>): Headline = Headline(
            title = m.str("title").orEmpty(),
            why = m.str("why").orEmpty(),
            impact = Impact.parse(m.str("impact")),
            direction = Direction.parse(m.str("direction")),
            instruments = m.arr("instruments").mapNotNull { it?.toString() },
            source = m.str("source").orEmpty(),
            url = m.str("url").orEmpty(),
            publishedAt = m.long("publishedAt"),
            isNew = m.bool("isNew") ?: false,
        )
    }
}

data class Brief(
    val generatedAt: Long,
    val bias: String,
    val risk: Risk,
    val cchFlag: Boolean,
    val cchReason: String,
    val headlines: List<Headline>,
    val watch: List<String>,
    val model: String,
    val reason: String,
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "generatedAt" to generatedAt, "bias" to bias, "risk" to risk.name,
        "cchFlag" to cchFlag, "cchReason" to cchReason,
        "headlines" to headlines.map { it.toMap() }, "watch" to watch,
        "model" to model, "reason" to reason,
    )

    companion object {
        fun fromMap(m: Map<String, Any?>): Brief = Brief(
            generatedAt = m.long("generatedAt") ?: 0L,
            bias = m.str("bias").orEmpty(),
            risk = Risk.parse(m.str("risk")),
            cchFlag = m.bool("cchFlag") ?: false,
            cchReason = m.str("cchReason").orEmpty(),
            headlines = m.arr("headlines").objects().map(Headline::fromMap),
            watch = m.arr("watch").mapNotNull { it?.toString() },
            model = m.str("model").orEmpty(),
            reason = m.str("reason").orEmpty(),
        )
    }
}

/** One scheduled economic-calendar release. */
data class CalEvent(
    val title: String,
    val country: String,
    val timeMillis: Long,
    /** "High", "Medium", "Low", or "Holiday" as published by the calendar. */
    val impact: String,
    val forecast: String,
    val previous: String,
    /** True when the event time is a date-only / all-day entry (e.g. holidays, "Tentative"). */
    val allDay: Boolean = false,
) {
    val isHigh: Boolean get() = impact.equals("High", ignoreCase = true)
    val isMediumOrHigher: Boolean get() = isHigh || impact.equals("Medium", ignoreCase = true)

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "title" to title, "country" to country, "timeMillis" to timeMillis, "impact" to impact,
        "forecast" to forecast, "previous" to previous, "allDay" to allDay,
    )

    companion object {
        fun fromMap(m: Map<String, Any?>): CalEvent = CalEvent(
            title = m.str("title").orEmpty(),
            country = m.str("country").orEmpty(),
            timeMillis = m.long("timeMillis") ?: 0L,
            impact = m.str("impact").orEmpty(),
            forecast = m.str("forecast").orEmpty(),
            previous = m.str("previous").orEmpty(),
            allDay = m.bool("allDay") ?: false,
        )
    }
}

data class Quote(
    val symbol: String,
    val label: String,
    val price: Double,
    val changePct: Double?,
    /** Recent intraday closes (oldest → newest), downsampled for the widget sparkline. */
    val spark: List<Double> = emptyList(),
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "symbol" to symbol, "label" to label, "price" to price, "changePct" to changePct, "spark" to spark,
    )

    companion object {
        fun fromMap(m: Map<String, Any?>): Quote = Quote(
            symbol = m.str("symbol").orEmpty(),
            label = m.str("label").orEmpty(),
            price = m.num("price") ?: 0.0,
            changePct = m.num("changePct"),
            spark = m.arr("spark").mapNotNull { (it as? Number)?.toDouble() },
        )
    }
}

/** Token/search usage for a single Claude call. */
data class Usage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
    val webSearches: Long = 0,
) {
    operator fun plus(o: Usage) = Usage(
        inputTokens + o.inputTokens, outputTokens + o.outputTokens,
        cacheReadTokens + o.cacheReadTokens, cacheWriteTokens + o.cacheWriteTokens,
        webSearches + o.webSearches,
    )

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "in" to inputTokens, "out" to outputTokens, "cr" to cacheReadTokens,
        "cw" to cacheWriteTokens, "ws" to webSearches,
    )

    companion object {
        fun fromMap(m: Map<String, Any?>?): Usage = if (m == null) Usage() else Usage(
            m.long("in") ?: 0, m.long("out") ?: 0, m.long("cr") ?: 0, m.long("cw") ?: 0, m.long("ws") ?: 0,
        )
    }
}

/** Everything the widget and notifications render from. Persisted as JSON. */
data class Snapshot(
    val brief: Brief? = null,
    val events: List<CalEvent> = emptyList(),
    val quotes: List<Quote> = emptyList(),
    val briefError: String? = null,
    val calendarError: String? = null,
    val quoteError: String? = null,
    val calendarFetchedAt: Long = 0,
    val quotesFetchedAt: Long = 0,
    val lastAttemptAt: Long = 0,
) {
    fun toJson(): String = Json.stringify(linkedMapOf(
        "brief" to brief?.toMap(),
        "events" to events.map { it.toMap() },
        "quotes" to quotes.map { it.toMap() },
        "briefError" to briefError, "calendarError" to calendarError, "quoteError" to quoteError,
        "calendarFetchedAt" to calendarFetchedAt, "quotesFetchedAt" to quotesFetchedAt,
        "lastAttemptAt" to lastAttemptAt,
    ))

    companion object {
        fun fromJson(text: String?): Snapshot {
            if (text.isNullOrBlank()) return Snapshot()
            return try {
                val m = Json.parseObject(text)
                Snapshot(
                    brief = m.obj("brief")?.let(Brief::fromMap),
                    events = m.arr("events").objects().map(CalEvent::fromMap),
                    quotes = m.arr("quotes").objects().map(Quote::fromMap),
                    briefError = m.str("briefError"),
                    calendarError = m.str("calendarError"),
                    quoteError = m.str("quoteError"),
                    calendarFetchedAt = m.long("calendarFetchedAt") ?: 0,
                    quotesFetchedAt = m.long("quotesFetchedAt") ?: 0,
                    lastAttemptAt = m.long("lastAttemptAt") ?: 0,
                )
            } catch (e: RuntimeException) {
                Snapshot() // corrupt cache: start clean rather than crash the widget
            }
        }
    }
}
