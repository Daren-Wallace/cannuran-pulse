package capital.cannuran.pulse.core

/** Geometry for the tiny intraday charts on the quote tiles. Drawing happens in the Android layer. */
object Sparkline {

    /** Evenly samples [values] down to at most [max] points, always keeping the first and last. */
    fun downsample(values: List<Double>, max: Int): List<Double> {
        if (values.size <= max || max < 2) return values
        val step = (values.size - 1).toDouble() / (max - 1)
        return List(max) { i -> values[Math.round(i * step).toInt().coerceAtMost(values.size - 1)] }
    }

    /**
     * Maps [values] onto a [w]×[h] canvas with [pad] inset, y pointing down.
     * Returns interleaved x,y pairs; empty when there's nothing to draw.
     * A flat series is drawn along the vertical middle.
     */
    fun points(values: List<Double>, w: Float, h: Float, pad: Float): FloatArray {
        if (values.size < 2 || w <= 2 * pad || h <= 2 * pad) return FloatArray(0)
        val lo = values.min()
        val hi = values.max()
        val range = hi - lo
        val innerW = w - 2 * pad
        val innerH = h - 2 * pad
        val out = FloatArray(values.size * 2)
        values.forEachIndexed { i, v ->
            out[2 * i] = pad + innerW * i / (values.size - 1)
            out[2 * i + 1] = if (range == 0.0) h / 2 else (pad + innerH * (1 - (v - lo) / range)).toFloat()
        }
        return out
    }

    /** True when the series ended higher than it started (drives the line colour when %change is unknown). */
    fun rising(values: List<Double>): Boolean = values.size >= 2 && values.last() >= values.first()
}
