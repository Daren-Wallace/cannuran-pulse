package capital.cannuran.pulse.core

/**
 * Minimal, dependency-free JSON reader/writer.
 *
 * The app deliberately avoids org.json in its core so every piece of parsing logic
 * can be unit-tested on a plain JVM without Robolectric or Android stubs.
 *
 * Values map to Kotlin types as follows:
 *   object -> Map<String, Any?> (insertion ordered), array -> List<Any?>,
 *   string -> String, number -> Long or Double, true/false -> Boolean, null -> null.
 */
object Json {

    class ParseException(message: String, val position: Int) : RuntimeException("$message at $position")

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.readValue()
        p.skipWs()
        if (!p.atEnd()) throw ParseException("Trailing characters", p.pos)
        return v
    }

    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?> =
        parse(text) as? Map<String, Any?> ?: throw ParseException("Expected object", 0)

    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> quote(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is Double -> if (v.isFinite()) sb.append(numberText(v)) else sb.append("null")
            is Float -> if (v.isFinite()) sb.append(numberText(v.toDouble())) else sb.append("null")
            is Number -> sb.append(v.toString())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    quote(sb, k.toString())
                    sb.append(':')
                    write(sb, value)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (item in v) {
                    if (!first) sb.append(',')
                    first = false
                    write(sb, item)
                }
                sb.append(']')
            }
            is Array<*> -> write(sb, v.asList())
            is Enum<*> -> quote(sb, v.name)
            else -> quote(sb, v.toString())
        }
    }

    private fun numberText(d: Double): String =
        if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ' || c == ' ' || c == ' ') {
                    sb.append("\\u").append(String.format("%04x", c.code))
                } else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun readValue(): Any? {
            skipWs()
            if (atEnd()) throw ParseException("Unexpected end", pos)
            return when (val c = s[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else throw ParseException("Unexpected '$c'", pos)
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, pos)) throw ParseException("Expected $word", pos)
            pos += word.length
            return value
        }

        private fun readObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            pos++ // {
            skipWs()
            if (peek() == '}') { pos++; return map }
            while (true) {
                skipWs()
                if (peek() != '"') throw ParseException("Expected key", pos)
                val key = readString()
                skipWs()
                expect(':')
                map[key] = readValue()
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    '}' -> { pos++; return map }
                    else -> throw ParseException("Expected , or }", pos)
                }
            }
        }

        private fun readArray(): List<Any?> {
            val list = ArrayList<Any?>()
            pos++ // [
            skipWs()
            if (peek() == ']') { pos++; return list }
            while (true) {
                list.add(readValue())
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    ']' -> { pos++; return list }
                    else -> throw ParseException("Expected , or ]", pos)
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw ParseException("Unterminated string", pos)
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw ParseException("Bad escape", pos)
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw ParseException("Bad unicode escape", pos)
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw ParseException("Bad escape '\\$e'", pos)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): Any {
            val start = pos
            if (peek() == '-') pos++
            while (!atEnd() && s[pos].isDigit()) pos++
            var isDouble = false
            if (!atEnd() && s[pos] == '.') {
                isDouble = true
                pos++
                while (!atEnd() && s[pos].isDigit()) pos++
            }
            if (!atEnd() && (s[pos] == 'e' || s[pos] == 'E')) {
                isDouble = true
                pos++
                if (!atEnd() && (s[pos] == '+' || s[pos] == '-')) pos++
                while (!atEnd() && s[pos].isDigit()) pos++
            }
            val text = s.substring(start, pos)
            if (text == "-" || text.isEmpty()) throw ParseException("Bad number", start)
            return if (isDouble) text.toDouble() else text.toLongOrNull() ?: text.toDouble()
        }

        private fun peek(): Char = if (atEnd()) '\u0000' else s[pos]

        private fun expect(c: Char) {
            if (peek() != c) throw ParseException("Expected '$c'", pos)
            pos++
        }
    }
}

/* ---------- Typed accessors so call sites stay readable ---------- */

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.obj(key: String): Map<String, Any?>? = this[key] as? Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.arr(key: String): List<Any?> = this[key] as? List<Any?> ?: emptyList()

fun Map<String, Any?>.str(key: String): String? = when (val v = this[key]) {
    null -> null
    is String -> v
    is Number, is Boolean -> v.toString()
    else -> null
}

fun Map<String, Any?>.num(key: String): Double? = when (val v = this[key]) {
    is Number -> v.toDouble()
    is String -> v.trim().replace(",", "").toDoubleOrNull()
    else -> null
}

fun Map<String, Any?>.long(key: String): Long? = when (val v = this[key]) {
    is Long -> v
    is Number -> v.toLong()
    is String -> v.trim().toLongOrNull()
    else -> null
}

fun Map<String, Any?>.bool(key: String): Boolean? = when (val v = this[key]) {
    is Boolean -> v
    is String -> when (v.trim().lowercase()) { "true", "yes", "1" -> true; "false", "no", "0" -> false; else -> null }
    is Number -> v.toInt() != 0
    else -> null
}

@Suppress("UNCHECKED_CAST")
fun List<Any?>.objects(): List<Map<String, Any?>> = mapNotNull { it as? Map<String, Any?> }
