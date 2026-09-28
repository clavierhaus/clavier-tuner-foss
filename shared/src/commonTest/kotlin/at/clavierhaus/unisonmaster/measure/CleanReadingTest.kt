package at.clavierhaus.unisonmaster.measure

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CleanReadingTest {
    private val sr = 48_000
    private val hop = 1024

    /**
     * A struck string whose partial [falseK] is two components [splitHz]
     * apart at equal strength — a false beat — the rest clean; the whole
     * string [detuneCents] from the targets it is read against.
     */
    private fun string(f1: Double, b: Double, detuneCents: Double, falseK: Int, splitHz: Double, seconds: Double = 3.0): FloatArray {
        val n = (seconds * sr).toInt()
        val out = FloatArray(n)
        val f1d = f1 * 2.0.pow(detuneCents / 1200)
        val phase = DoubleArray(10)
        val phase2 = DoubleArray(10)
        val start = (0.3 * sr).toInt()
        for (i in 0 until n) {
            var x = 0.0
            if (i >= start) {
                val t = (i - start).toDouble() / sr
                val env = exp(-t / 3.0) * (1 - exp(-t / 0.002))
                for (k in 1..8) {
                    val hz = SyntheticString.partialHz(f1d, b, k)
                    val a = 0.2 * env * 10.0.pow(-3.0 * (k - 1) / 20)
                    if (k == falseK) {
                        phase[k] += 2 * PI * (hz - splitHz / 2) / sr; phase2[k] += 2 * PI * (hz + splitHz / 2) / sr
                        x += a / 2 * sin(phase[k]) + a / 2 * sin(phase2[k])
                    } else {
                        phase[k] += 2 * PI * hz / sr
                        x += a * sin(phase[k])
                    }
                }
            }
            out[i] = x.toFloat()
        }
        return out
    }

    private fun targets(f1: Double, b: Double) = (1..8).associateWith { SyntheticString.partialHz(f1, b, it) }

    private fun readAll(x: FloatArray, r: CleanReading, anchorK: Int, combine: Boolean): CleanReading.Result {
        var res: CleanReading.Result? = null
        var i = 0
        while (i + hop <= x.size) { res = r.push(x.copyOfRange(i, i + hop), anchorK, combine); i += hop }
        return res!!
    }

    @Test
    fun aMeasuredStringIsReadOnItsCleanPartialsAndTheFalseOneIsNamed() {
        val f1 = 440.0; val b = 4e-4
        val r = CleanReading(sr)
        r.target(targets(f1, b), reset = true)
        val res = readAll(string(f1, b, detuneCents = -3.0, falseK = 3, splitHz = 1.5), r, anchorK = 2, combine = true)
        assertEquals(-3.0, res.cents!!, 0.1)
        assertTrue(3 in res.falseBeating, "partial 3 beats on its own: ${res.partials}")
        assertTrue(3 !in res.used, "the false partial is not in the reading")
        assertTrue(res.used.size >= 4, "four clean partials at least: ${res.used}")
    }

    @Test
    fun anUnmeasuredStringIsReadOnThePartialBeingTunedAlone() {
        val f1 = 440.0; val b = 4e-4
        val r = CleanReading(sr)
        r.target(targets(f1, b), reset = true)
        val res = readAll(string(f1, b, detuneCents = 2.0, falseK = 6, splitHz = 2.0), r, anchorK = 2, combine = false)
        assertEquals(listOf(2), res.used)
        assertEquals(2.0, res.cents!!, 0.1)
    }

    @Test
    fun whenThePartialBeingTunedBeatsOnItsOwnAnotherStandsIn() {
        val f1 = 440.0; val b = 4e-4
        val r = CleanReading(sr)
        r.target(targets(f1, b), reset = true)
        val res = readAll(string(f1, b, detuneCents = 1.0, falseK = 2, splitHz = 1.2), r, anchorK = 2, combine = false)
        assertTrue(2 in res.falseBeating, "${res.partials}")
        assertEquals(1, res.used.size)
        assertTrue(res.used.single() != 2)
        assertEquals(1.0, res.cents!!, 0.15)
    }
}
