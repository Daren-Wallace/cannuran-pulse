package capital.cannuran.pulse.core

import java.net.URLEncoder

/**
 * Parses Yahoo Finance's public chart endpoint, e.g.
 * https://query1.finance.yahoo.com/v8/finance/chart/NQ%3DF?range=1d&interval=15m
 *
 * Quotes are a convenience: they may be delayed and the endpoint is unofficial, so every
 * failure degrades to "no quotes" rather than breaking the widget.
 */
object QuoteParser {

    private val LABELS = mapOf(
        "NQ=F" to "NQ", "ES=F" to "ES", "YM=F" to "YM", "RTY=F" to "RTY", "^VIX" to "VIX",
        "CL=F" to "OIL", "BZ=F" to "BRENT", "GC=F" to "GOLD", "DX-Y.NYB" to "DXY", "^TNX" to "10Y",
        "^NDX" to "NDX", "^GSPC" to "SPX", "BTC-USD" to "BTC", "ZN=F" to "ZN", "6E=F" to "EUR",
    )

    fun label(symbol: String): String = LABELS[symbol.uppercase()] ?: symbol.removeSuffix("=F").removePrefix("^").take(5)

    fun url(symbol: String): String =
        "https://query1.finance.yahoo.com/v8/finance/chart/" + URLEncoder.encode(symbol, "UTF-8") +
            "?range=1d&interval=15m&includePrePost=true"

    fun parse(symbol: String, json: String): Quote? {
        val root = Json.parseObject(json)
        val result = root.obj("chart")?.arr("result")?.objects()?.firstOrNull() ?: return null
        val meta = result.obj("meta") ?: return null
        val price = meta.num("regularMarketPrice") ?: lastClose(result) ?: return null
        val prev = meta.num("previousClose") ?: meta.num("chartPreviousClose")
        val pct = if (prev != null && prev != 0.0) (price - prev) / prev * 100.0 else null
        return Quote(symbol = symbol, label = label(symbol), price = price, changePct = pct)
    }

    private fun lastClose(result: Map<String, Any?>): Double? =
        result.obj("indicators")?.arr("quote")?.objects()?.firstOrNull()
            ?.arr("close")?.lastOrNull { it is Number }?.let { (it as Number).toDouble() }
}
