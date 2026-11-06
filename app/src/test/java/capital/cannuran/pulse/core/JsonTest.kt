package capital.cannuran.pulse.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JsonTest {

    @Test fun parsesNestedStructures() {
        val m = Json.parseObject("""{"a":1,"b":[true,false,null],"c":{"d":"x\"y","e":-2.5e2},"f":"é\n"}""")
        assertEquals(1L, m["a"])
        assertEquals(listOf(true, false, null), m["b"])
        assertEquals("x\"y", m.obj("c")!!.str("d"))
        assertEquals(-250.0, m.obj("c")!!.num("e")!!, 1e-9)
        assertEquals("é\n", m.str("f"))
    }

    @Test fun roundTripsThroughStringify() {
        val original = linkedMapOf<String, Any?>("s" to "line\nbreak \"q\" \\ /", "n" to 42L, "d" to 1.25,
            "l" to listOf(1L, "two", null, false), "o" to linkedMapOf("k" to "v"))
        val back = Json.parseObject(Json.stringify(original))
        assertEquals(original, back)
    }

    @Test fun stringifiesWholeDoublesAsIntegersAndNonFiniteAsNull() {
        assertEquals("""{"a":3,"b":null}""", Json.stringify(linkedMapOf("a" to 3.0, "b" to Double.NaN)))
    }

    @Test fun rejectsMalformedInput() {
        for (bad in listOf("{", "{\"a\":}", "[1,]", "{\"a\":1} x", "\"unterminated", "tru", "-")) {
            try { Json.parse(bad); fail("Expected failure for $bad") } catch (e: Json.ParseException) { /* ok */ }
        }
    }

    @Test fun typedAccessorsAreLenient() {
        val m = Json.parseObject("""{"n":"1,234.5","b":"yes","l":"7","x":[1]}""")
        assertEquals(1234.5, m.num("n")!!, 1e-9)
        assertEquals(true, m.bool("b"))
        assertEquals(7L, m.long("l"))
        assertNull(m.obj("x"))
        assertTrue(m.arr("missing").isEmpty())
    }
}
