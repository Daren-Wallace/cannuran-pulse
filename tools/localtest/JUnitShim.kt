// Local-only stand-in for JUnit 4 so the core tests run with nothing but a JDK and the
// Kotlin compiler bundled in Gradle. CI and Android Studio use real JUnit instead.
package org.junit

@Target(AnnotationTarget.FUNCTION) @Retention(AnnotationRetention.RUNTIME) annotation class Test

class AssertionFailed(msg: String) : AssertionError(msg)

object Assert {
    @JvmStatic fun assertEquals(expected: Any?, actual: Any?) { if (expected != actual) throw AssertionFailed("expected:<$expected> but was:<$actual>") }
    @JvmStatic fun assertEquals(msg: String, expected: Any?, actual: Any?) { if (expected != actual) throw AssertionFailed("$msg expected:<$expected> but was:<$actual>") }
    @JvmStatic fun assertEquals(expected: Double, actual: Double, delta: Double) { if (Math.abs(expected - actual) > delta) throw AssertionFailed("expected:<$expected> but was:<$actual>") }
    @JvmStatic fun assertEquals(expected: Long, actual: Long) { if (expected != actual) throw AssertionFailed("expected:<$expected> but was:<$actual>") }
    @JvmStatic fun assertTrue(c: Boolean) { if (!c) throw AssertionFailed("expected true") }
    @JvmStatic fun assertFalse(c: Boolean) { if (c) throw AssertionFailed("expected false") }
    @JvmStatic fun assertNull(v: Any?) { if (v != null) throw AssertionFailed("expected null but was:<$v>") }
    @JvmStatic fun assertNotNull(v: Any?) { if (v == null) throw AssertionFailed("expected non-null") }
    @JvmStatic fun fail(msg: String): Nothing = throw AssertionFailed(msg)
}
