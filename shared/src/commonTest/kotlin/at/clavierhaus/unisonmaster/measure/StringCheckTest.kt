package at.clavierhaus.unisonmaster.measure

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The one-strike check takes the strike that sounds (11 Nov 2026). On the Steinway B 1886's F4-G#4 take (Pro
 * data/recordings/2026-11-11/session_20261111-142850, research/ProbeNameDiag.kt) a click of one hop, -68.7 dBFS, 9 dB
 * over the room, opened a check at 73.41 s; the key was looked for up to 1.5 s after it and only silence was found; G4's
 * strike at 75.66 s fell inside the three seconds being kept and went unnamed with them. Here the same: a click, then a
 * string 2.25 s later.
 */
class StringCheckTest {
    private val sr = 48_000
    private val hop = 1024

    /** A click of two hops at about -68 dBFS at [clickS], then G4 (B 7e-4) struck at [strikeS], [seconds] long. */
    private fun take(clickS: Double, strikeS: Double, seconds: Double): FloatArray {
        val n = (seconds * sr).toInt(); val out = FloatArray(n); val rnd = Random(1)
        val f1 = 392.0; val b = 7e-4
        val phase = DoubleArray(9)
        for (i in 0 until n) {
            var x = 1e-4 * (rnd.nextDouble() - 0.5)                                      // the room, about -85 dBFS
            val c = i - (clickS * sr).toInt()
            if (c in 0 until 2 * hop) x += 7e-4 * (rnd.nextDouble() - 0.5) * 2               // the click
            val s = i - (strikeS * sr).toInt()
            if (s >= 0) {
                val t = s.toDouble() / sr
                val env = exp(-t / 3.0) * (1 - exp(-t / 0.002))
                for (k in 1..8) {
                    phase[k] += 2 * PI * SyntheticString.partialHz(f1, b, k) / sr
                    x += 0.2 * env * 10.0.pow(-3.0 * (k - 1) / 20) * sin(phase[k])
                }
            }
            out[i] = x.toFloat()
        }
        return out
    }

    @Test
    fun aClickBeforeTheStrikeDoesNotCostTheString() {
        val x = take(clickS = 1.0, strikeS = 3.25, seconds = 9.0)
        val check = StringCheck(sr, { 440.0 })
        check.begin()
        var at = 0
        while (at + hop <= x.size) { check.push(x.copyOfRange(at, at + hop)); at += hop }
        check.publishNow()
        val st = check.state.value
        assertEquals(67, st.midi, "the strike after the click named G4; the check stood ${st.phase}")
    }
}
