package at.clavierhaus.unisonmaster

import java.util.Locale
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The common stand-ins of the tests (CommonFormat.kt) against the JVM originals they replace,
 * so the tests read the same on iOS as they did on the JVM before 30 Oct 2026.
 */
class CommonFormatTest {
    private val values = listOf(0.0, -0.0, 1.0, -1.0, 0.5, 1.5, 2.5, -2.5, 0.125, 0.0049, 0.005, 443.12, 439.418,
        -12.3456, 8e-4, 1.234e-4, 9.995, 9.9951, 99.5, 1e6, 123456.789, 3.14159265, -0.0004, 1.69e-4, 0.1, 0.7)

    @Test fun fixedAndScientificAsTheJvm() {
        for (pattern in listOf("%.0f", "%.1f", "%.2f", "%.3f", "%+.0f", "%+.2f", "%+.3f", "%.2e", "%8.2f", "at %.2f s, spread %.2f c")) {
            for (v in values) {
                val args = arrayOf<Any?>(v, v)
                val jvm = java.lang.String.format(Locale.ROOT, pattern, *args)
                assertEquals(jvm, pattern.format(*args), "pattern $pattern, value $v")
            }
        }
        assertEquals(java.lang.String.format(Locale.ROOT, "%d keys, %s, 100%%", 88, "A0"), "%d keys, %s, 100%%".format(88, "A0"))
    }

    @Test fun manyValuesAsTheJvm() {
        val rnd = java.util.Random(30102026)
        repeat(20000) {
            val v = (rnd.nextDouble() * 2 - 1) * Math.pow(10.0, rnd.nextInt(14) - 7.0)
            for (pattern in listOf("%.1f", "%.2f", "%.3f", "%+.2f", "%.2e")) {
                assertEquals(java.lang.String.format(Locale.ROOT, pattern, v), pattern.format(v), "pattern $pattern, value $v")
            }
        }
    }

    @Test fun javaRandomIsJavaUtilRandom() {
        for (seed in listOf(0L, 7L, 42L, -1L, 123456789L)) {
            val jvm = java.util.Random(seed); val common = JavaRandom(seed)
            repeat(2000) { assertEquals(jvm.nextDouble(), common.nextDouble(), "seed $seed, draw $it") }
            repeat(2000) {
                val a = jvm.nextGaussian(); val b = common.nextGaussian()
                assertTrue(abs(a - b) <= 1e-15 * maxOf(1.0, abs(a)), "seed $seed, gaussian $it: $a against $b")
            }
        }
    }
}
