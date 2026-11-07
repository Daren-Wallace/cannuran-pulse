package capital.cannuran.pulse.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SparklineTest {

    @Test fun downsampleKeepsEndsAndLength() {
        val v = (0 until 100).map { it.toDouble() }
        val d = Sparkline.downsample(v, 10)
        assertEquals(10, d.size)
        assertEquals(0.0, d.first(), 0.0)
        assertEquals(99.0, d.last(), 0.0)
        assertEquals(v.take(5), Sparkline.downsample(v.take(5), 10))
    }

    @Test fun pointsFitTheBoxWithYInverted() {
        val p = Sparkline.points(listOf(1.0, 3.0, 2.0), 100f, 20f, 2f)
        assertEquals(6, p.size)
        assertEquals(2f.toDouble(), p[0].toDouble(), 1e-6)   // first x at left pad
        assertEquals(98f.toDouble(), p[4].toDouble(), 1e-6)  // last x at right pad
        assertEquals(18f.toDouble(), p[1].toDouble(), 1e-6)  // min value at bottom
        assertEquals(2f.toDouble(), p[3].toDouble(), 1e-6)   // max value at top
        assertEquals(10f.toDouble(), p[5].toDouble(), 1e-6)  // midpoint
    }

    @Test fun degenerateInputs() {
        assertEquals(0, Sparkline.points(listOf(1.0), 100f, 20f, 2f).size)
        assertEquals(0, Sparkline.points(listOf(1.0, 2.0), 3f, 20f, 2f).size)
        val flat = Sparkline.points(listOf(5.0, 5.0, 5.0), 100f, 20f, 2f)
        assertTrue(flat.filterIndexed { i, _ -> i % 2 == 1 }.all { it == 10f })
        assertTrue(Sparkline.rising(listOf(1.0, 2.0)))
        assertFalse(Sparkline.rising(listOf(2.0, 1.0)))
    }

    @Test fun quoteCarriesSparkAndPersists() {
        val q = QuoteParser.parse("NQ=F", Fixtures.YAHOO_NQ)!!
        assertEquals(listOf(21400.0, 21456.75), q.spark)
        assertEquals(q, Quote.fromMap(Json.parseObject(Json.stringify(q.toMap()))))
        val vix = QuoteParser.parse("^VIX", Fixtures.YAHOO_NO_META_PRICE)!!
        assertEquals(listOf(15.5, 15.2), vix.spark) // nulls skipped
    }

    @Test fun themeParsingAndLegacyMigration() {
        assertEquals(WidgetTheme.DYNAMIC, Settings.fromJson(Settings(theme = WidgetTheme.DYNAMIC).toJson()).theme)
        assertEquals(WidgetTheme.GLASS, Settings.fromJson("""{"translucentWidget":true}""").theme)
        assertEquals(WidgetTheme.MIDNIGHT, Settings.fromJson("""{"translucentWidget":false}""").theme)
        assertEquals(WidgetTheme.MIDNIGHT, WidgetTheme.parse("nonsense"))
    }
}
