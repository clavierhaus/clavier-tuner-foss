package at.clavierhaus.unisonmaster.tuning

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SR = 48_000
private const val N = 16384
private const val B = 4.0e-4

private fun partialHz(f0: Double, k: Int) = k * f0 * sqrt(1 + B * k * k)
private fun cents(a: Double, b: Double) = 1200.0 * ln(a / b) / ln(2.0)

/** A stiff string with partials 1..[count], amplitudes falling with k, plus faint noise. */
private fun stiffString(f0: Double, count: Int, samples: Int, amp: Double = 0.2): FloatArray {
    val rnd = Random(7)
    return FloatArray(samples) { i ->
        val t = i.toDouble() / SR
        var v = 0.0
        for (k in 1..count) v += amp / k * sin(2 * PI * partialHz(f0, k) * t + k)
        (v * exp(-t / 3.0) + 1e-4 * (rnd.nextDouble() * 2 - 1)).toFloat()
    }
}

class PartialTrackerTest {

    @Test
    fun findsInharmonicPartialsWhereTheyAre() {
        val f0 = 438.0
        val f1 = partialHz(f0, 1)
        val readings = PartialTracker(SR, N).analyse(stiffString(f0, 10, N), f1)
        for (k in 2..10) {
            val r = readings.first { it.k == k }
            val truth = cents(partialHz(f0, k), k * f1)
            assertTrue(abs(r.cents - truth) < 1.5, "partial $k at ${r.cents} ct, truth $truth ct")
            assertTrue(r.snrDb >= LiveReference.AUDIBLE_SNR_DB, "partial $k should be audible, snr ${r.snrDb}")
        }
        assertEquals(0.0, readings.first { it.k == 1 }.cents)
    }

    @Test
    fun absentPartialsAreNotAudible() {
        val f0 = 438.0
        val readings = PartialTracker(SR, N).analyse(stiffString(f0, 6, N), partialHz(f0, 1))
        for (r in readings.filter { it.k >= 8 }) {
            assertTrue(r.snrDb < LiveReference.AUDIBLE_SNR_DB, "partial ${r.k} is absent but snr ${r.snrDb}")
        }
    }

    @Test
    fun liveReferenceReportsAudiblePartials() {
        val f0 = 438.0
        val live = LiveReference(SR)
        val signal = FloatArray(4096 * 2) + stiffString(f0, 8, SR * 3)
        var pos = 0
        val buf = FloatArray(4096)
        while (pos + 4096 <= signal.size) { signal.copyInto(buf, 0, pos, pos + 4096); live.push(buf); pos += 4096 }
        assertEquals((1..8).toSet(), live.audible, "audible ${live.audible}")
        assertTrue(live.partials.all { it.level in 0.0..1.0 })
        assertTrue(live.partials.first { it.k == 1 }.cents == 0.0)
        val p8 = live.partials.first { it.k == 8 }
        val truth = cents(partialHz(f0, 8), 8 * partialHz(f0, 1))
        assertTrue(abs(p8.cents - truth) < 1.5, "partial 8 at ${p8.cents} ct, truth $truth ct")
    }

    @Test
    fun selectionFollowsTheHubRules() {
        val audible = setOf(1, 2, 3, 5)
        assertEquals(setOf(1), PartialSelection.shown(false, audible, emptySet()))
        assertEquals(audible, PartialSelection.shown(true, audible, emptySet()))
        val hidden = PartialSelection.tap(3, true, audible, emptySet())
        assertEquals(setOf(3), hidden)
        assertEquals(setOf(1, 2, 5), PartialSelection.shown(true, audible, hidden))
        assertEquals(emptySet(), PartialSelection.tap(3, true, audible, hidden))
        assertEquals(emptySet(), PartialSelection.tap(4, true, audible, emptySet()), "inaudible partial ignored")
        assertEquals(emptySet(), PartialSelection.tap(2, false, audible, emptySet()), "no taps in fundamental mode")
    }
}
