package at.clavierhaus.unisonmaster.measure

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StringMeasureTest {
    private val sr = 48_000

    private fun cents(a: Double, b: Double) = 1200 * ln(a / b) / ln(2.0)

    @Test
    fun theFinderPlacesAPartialAndIgnoresNoise() {
        val sig = SyntheticString.strike(220.0, 3e-4, 1.0)
        val finder = PartialFinder(sig.copyOfRange(sig.size - 16384, sig.size), sr)
        val p3 = SyntheticString.partialHz(220.0, 3e-4, 3)
        val found = assertNotNull(finder.near(p3 * 1.01, 60.0))
        assertTrue(abs(cents(found.hz, p3)) < 3, "placed at %.2f for %.2f".format(found.hz, p3))
        val rng = kotlin.random.Random(5)
        val quiet = FloatArray(16384) { (1e-4 * (rng.nextDouble() * 2 - 1)).toFloat() }
        assertNull(PartialFinder(quiet, sr).near(440.0, 60.0), "noise has no prominent peak")
    }

    @Test
    fun aStringIsMeasuredWithItsInharmonicity() {
        for ((f1, b) in listOf(65.4 to 1.2e-4, 220.0 to 3.5e-4, 523.3 to 9e-4, 1046.5 to 2.5e-3)) {
            val sig = SyntheticString.strike(f1, b, 3.0)
            val m = assertNotNull(StringMeasure.measure(sig, sr, 60, f1 * 1.003), "measured $f1")
            assertTrue(abs(cents(m.f1Hz, f1)) < 0.1, "f1 %.3f for %.3f".format(m.f1Hz, f1))
            assertTrue(abs(m.b - b) / b < 0.1, "B %.2e for %.2e".format(m.b, b))
            for (p in m.partials) {
                val truth = SyntheticString.partialHz(f1, b, p.k)
                val read = p.k * m.f1Hz * 2.0.pow(p.cents / 1200)
                assertTrue(abs(cents(read, truth)) < 0.15, "$f1 partial ${p.k}: %.3f for %.3f".format(read, truth))
            }
            assertTrue(m.partials.size >= 4, "$f1: ${m.partials.size} partials")
        }
    }

    @Test
    fun silenceIsNotAString() {
        assertNull(StringMeasure.measure(FloatArray(sr * 2), sr, 57, 220.0))
    }
}
