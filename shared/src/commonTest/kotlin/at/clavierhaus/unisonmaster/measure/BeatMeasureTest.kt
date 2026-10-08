package at.clavierhaus.unisonmaster.measure

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BeatMeasureTest {
    private val hopS = 1024.0 / 48000

    /** Two planes [depthDb] apart beating at [rateHz] on a decay of [tau] seconds, read for [seconds] at the hop rate, with a little scatter. */
    private fun planes(rateHz: Double, depthDb: Double, seconds: Double = 2.0, tau: Double = 3.0, phase: Double = 0.7, noise: Double = 0.03, seed: Int = 3): Pair<DoubleArray, DoubleArray> {
        val rng = Random(seed)
        val n = (seconds / hopS).toInt()
        val rho = 10.0.pow(-depthDb / 20)
        val t = DoubleArray(n) { 1.0 + it * hopS }
        val p = DoubleArray(n) { i ->
            val base = 1 + rho * rho + 2 * rho * cos(2 * PI * rateHz * t[i] + phase)
            exp(-t[i] / tau) * base * (1 + noise * (rng.nextDouble() * 2 - 1))
        }
        return t to p
    }

    @Test
    fun equalPlanesAtOneHertzAreMeasuredAtOneHertzAndFullDepth() {
        val (t, p) = planes(1.0, 0.0)
        val b = assertNotNull(BeatMeasure.measure(t, p))
        assertTrue(abs(b.rateHz - 1.0) < 0.1, "rate ${b.rateHz}")
        assertTrue(b.index > 0.9, "index ${b.index}")
        assertTrue(b.fit > 0.8, "fit ${b.fit}")
        assertTrue(b.audible)
    }

    @Test
    fun theDepthIsReadFromTheIndex() {
        // 8 dB apart: within the ear's 10 dB -- audible; 14 dB apart: not
        val (t1, p1) = planes(2.0, 8.0)
        val near = assertNotNull(BeatMeasure.measure(t1, p1))
        assertTrue(abs(near.depthDb - 8.0) < 1.5, "8 dB read as ${near.depthDb}")
        assertTrue(near.audible, "8 dB apart at 2 Hz is heard: $near")
        val (t2, p2) = planes(2.0, 14.0)
        val far = assertNotNull(BeatMeasure.measure(t2, p2))
        assertTrue(abs(far.depthDb - 14.0) < 2.0, "14 dB read as ${far.depthDb}")
        assertFalse(far.audible, "14 dB apart is not heard: $far")
    }

    @Test
    fun onePlaneIsNotABeat() {
        val (t, p) = planes(1.0, 60.0, noise = 0.05)
        val b = assertNotNull(BeatMeasure.measure(t, p))
        assertTrue(b.index < 0.2, "a single decaying plane with scatter reads an index of ${b.index}")
        assertFalse(b.audible)
    }

    @Test
    fun aBeatSlowerThanHalfTheWindowIsNotABeat() {
        // 0.3 Hz: a period of 3.3 s inside a 2 s window goes out with the decay
        val (t, p) = planes(0.3, 0.0)
        val b = assertNotNull(BeatMeasure.measure(t, p))
        assertFalse(b.audible, "0.3 Hz called audible: $b")
    }

    @Test
    fun aFastBeatIsMeasuredWhereItIs() {
        val (t, p) = planes(6.0, 3.0)
        val b = assertNotNull(BeatMeasure.measure(t, p))
        assertTrue(abs(b.rateHz - 6.0) < 0.6, "rate ${b.rateHz}")
        assertTrue(b.audible)
    }

    @Test
    fun tooFewReadingsIsNoMeasurement() {
        val (t, p) = planes(1.0, 0.0, seconds = 1.0)
        assertTrue(BeatMeasure.measure(t, p) == null)
    }

    @Test
    fun theTenDecibelLineIsTheFindingsOwn() {
        // 2ρ/(1+ρ²) at ρ = 10^(−10/20): 0.5750
        assertTrue(abs(BeatMeasure.WITHIN_10_DB - 0.5750) < 0.001, "${BeatMeasure.WITHIN_10_DB}")
    }
}
