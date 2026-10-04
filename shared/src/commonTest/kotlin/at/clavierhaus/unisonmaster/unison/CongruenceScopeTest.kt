package at.clavierhaus.unisonmaster.unison

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The zoom-FFT must find the strings where they are. */
class CongruenceScopeTest {
    private val sr = 48000

    /** C4 with two strings at partial 5, split by [d] Hz, plus the fundamental. */
    private fun scope(d: Double, seconds: Double = 2.5): CongruenceScope {
        val f0 = 261.63
        val s = CongruenceScope(sr); s.tune(f0); s.selected[5] = true
        val n = 1024; val b = FloatArray(n); var t = 0L
        val total = (seconds * sr).toLong()
        while (t + n <= total) {
            for (i in 0 until n) {
                val tt = (t + i) / sr.toDouble()
                b[i] = (0.4 * sin(2 * PI * f0 * tt) +
                        0.3 * sin(2 * PI * 5 * f0 * tt) +
                        0.3 * sin(2 * PI * (5 * f0 + d) * tt + 0.9)).toFloat()
            }
            s.push(b); t += n
        }
        return s
    }

    @Test fun twoStringsThreeHertzApartAreTwoPeaksThreeApart() {
        val offs = scope(3.0).combSplit(5)
        assertEquals(2, offs.size, "expected two peaks, got ${offs.toList()}")
        assertTrue(abs((offs[1] - offs[0]) - 3.0) < 0.25, "split ${offs[1] - offs[0]}")
    }

    @Test fun twoStringsEightHertzApartAreTwoPeaksEightApart() {
        val offs = scope(8.0).combSplit(5)
        assertEquals(2, offs.size, "expected two peaks, got ${offs.toList()}")
        assertTrue(abs((offs[1] - offs[0]) - 8.0) < 0.25, "split ${offs[1] - offs[0]}")
    }

    @Test fun oneStringIsOnePeakNearZero() {
        val f0 = 261.63
        val s = CongruenceScope(sr); s.tune(f0); s.selected[5] = true
        val n = 1024; val b = FloatArray(n); var t = 0L
        while (t + n <= (2.5 * sr).toLong()) {
            for (i in 0 until n) { val tt = (t + i) / sr.toDouble()
                b[i] = (0.4 * sin(2 * PI * f0 * tt) + 0.3 * sin(2 * PI * 5 * f0 * tt)).toFloat() }
            s.push(b); t += n
        }
        val offs = s.combSplit(5)
        assertEquals(1, offs.size, "expected one peak, got ${offs.toList()}")
        assertTrue(abs(offs[0]) < 0.25, "peak at ${offs[0]}")
    }

    @Test fun theProfileIsABellWithItsPeakAtTheCentre() {
        val prof = scope(0.0).profile(5, 1.5, 0.05)
        assertTrue(prof.size > 20, "profile too short")
        val peakIdx = prof.indices.maxByOrNull { prof[it] }!!
        assertTrue(abs(peakIdx - prof.size / 2) <= 3, "peak at $peakIdx of ${prof.size}")
        assertTrue(prof.first() < 0.5 && prof.last() < 0.5, "edges should fall: ${prof.first()} ${prof.last()}")
    }
}
