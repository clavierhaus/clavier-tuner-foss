package at.clavierhaus.unisonmaster.measure

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A false beat measured from a partial's band power over a window of a strike.
 *
 * A string vibrates in two planes; where they sit apart in frequency by Δf the
 * partial's power swings at Δf -- a1² + a2² + 2·a1·a2·cos(2π·Δf·t) -- on top of the
 * strike's decay. The decay is fitted as a line in the log of the power and
 * divided out, which leaves 1 + m·cos(2π·Δf·t + φ): the rate is the sinusoid that
 * explains most of what is left, and m is its amplitude, the modulation index,
 * which gives the planes' ratio ρ = a2/a1 through m = 2ρ/(1+ρ²).
 *
 * Why this and not the spread of the frequency reading, which decided until
 * 8 October 2026: the ear's criterion (SAMICK findings §8, `false_beats.py`) is
 * a rate of 0.5-8 Hz with the second plane within 10 dB, and the spread in cents
 * is neither -- it read 0.35 c on an audible 0.67 Hz beat with planes 3.5 dB
 * apart and 0.5 c on inaudible polarisation doublets, wrong both ways (§20, §21).
 * On the readers' own series the power swing separates the classes outright:
 * the clean F3's partials give m of 0.02-0.32 on 36 partial-takes, the Samick's
 * left A4 gives m ≥ 1.0 at 0.63-0.67 Hz on both its false partials on all three
 * strikes, and the 16 Sept A3's partials 3 and 4 measure 0.63-0.68 and
 * 0.86-0.92 Hz where ESPRIT found 0.595 and 0.932 (FALSE-BEATS §6). A beat
 * slower than half the window is removed with the decay and is not a beat.
 *
 * The one line it decides by is the findings' own: [WITHIN_10_DB] is
 * 2ρ/(1+ρ²) at ρ = 10^(−10/20). There is no cap on the rate but the ear's.
 */
object BeatMeasure {
    /**
     * What a window measured: the beat rate, its modulation index, how well one sinusoid explains
     * the swing -- and how well a parabola would, which is the floor's own test: a beat slower
     * than the window leaves a bump that a parabola explains as well as any sinusoid, and a
     * beat that fits in the window leaves a swing a parabola cannot explain.
     */
    data class Beat(val rateHz: Double, val index: Double, val fit: Double, val trendFit: Double = 0.0) {
        /** The second plane's level below the first, dB, from the index; 60 dB when there is none to speak of. */
        val depthDb: Double get() {
            val m = index.coerceIn(0.0, 0.9999)
            if (m <= 0.0) return 60.0
            val rho = (1 - sqrt(1 - m * m)) / m
            return (-20 * kotlin.math.log10(rho)).coerceIn(0.0, 60.0)
        }
        /** Audible by the findings' line: 0.5-8 Hz, the second plane within 10 dB, a swing that is one sinusoid, and one a parabola does not explain. */
        val audible: Boolean get() = rateHz >= MIN_RATE_HZ && rateHz <= MAX_RATE_HZ && index >= WITHIN_10_DB && fit >= MIN_FIT && fit - trendFit >= OVER_TREND
    }

    /**
     * [t] seconds and [p] band power, one pair per reading, in time order; at least
     * [MIN_READINGS] of them or null. The window should hold a second's worth of
     * decay after the attack: the attack's transient is not a plane.
     */
    fun measure(t: DoubleArray, p: DoubleArray): Beat? {
        val n = minOf(t.size, p.size)
        if (n < MIN_READINGS) return null
        // the decay: a line through the log of the power
        var st = 0.0; var sl = 0.0; var stt = 0.0; var stl = 0.0
        val logp = DoubleArray(n) { ln(maxOf(p[it], 1e-30)) }
        for (i in 0 until n) { st += t[i]; sl += logp[i]; stt += t[i] * t[i]; stl += t[i] * logp[i] }
        val den = n * stt - st * st
        val slope = if (den == 0.0) 0.0 else (n * stl - st * sl) / den
        // The decay's rate comes from the log fit; its level does not. The log of a modulated
        // signal averages low (the mean of ln(1 + m·cos θ) is negative), so the fitted intercept
        // would sit under the true a1² + a2² and inflate the swing -- planes 8 dB apart read as
        // 6 dB. The level is the arithmetic mean of the de-sloped power instead, which two planes
        // average to exactly a1² + a2². What is left is the swing about one.
        val flat = DoubleArray(n) { p[it] * exp(-slope * t[it]) }
        var level = 0.0; for (v in flat) level += v; level /= n
        if (level <= 0.0) return Beat(0.0, 0.0, 0.0)
        val r = DoubleArray(n) { flat[it] / level - 1.0 }
        var mean = 0.0; for (v in r) mean += v; mean /= n
        var tot = 0.0; for (v in r) tot += (v - mean) * (v - mean)
        if (tot <= 0.0) return Beat(0.0, 0.0, 0.0)
        // what a parabola explains of the same swing: the floor's test, see Beat
        val trendFit = run {
            var s0 = 0.0; var s1 = 0.0; var s2 = 0.0; var s3 = 0.0; var s4 = 0.0; var r0 = 0.0; var r1 = 0.0; var r2 = 0.0
            for (i in 0 until n) { val x = t[i] - t[0]; val x2 = x * x; s0 += 1; s1 += x; s2 += x2; s3 += x2 * x; s4 += x2 * x2; r0 += r[i]; r1 += r[i] * x; r2 += r[i] * x2 }
            val sol = solve3(s0, s1, s2, s1, s2, s3, s2, s3, s4, r0, r1, r2)
            if (sol == null) 0.0 else {
                val (a, b, c) = sol
                var res = 0.0
                for (i in 0 until n) { val x = t[i] - t[0]; val e = r[i] - (a + b * x + c * x * x); res += e * e }
                1.0 - res / tot
            }
        }
        var best = Beat(0.0, 0.0, 0.0, trendFit)
        var f = GRID_LOW_HZ
        while (f <= MAX_RATE_HZ * 1.0001) {
            // least squares of a + b·cos + c·sin at this rate
            var sc = 0.0; var ss = 0.0; var scc = 0.0; var sss = 0.0; var scs = 0.0; var src = 0.0; var srs = 0.0
            for (i in 0 until n) {
                val c = cos(2 * PI * f * t[i]); val s = sin(2 * PI * f * t[i])
                sc += c; ss += s; scc += c * c; sss += s * s; scs += c * s; src += r[i] * c; srs += r[i] * s
            }
            // normal equations for [a b c] with a absorbing the mean: solve the 3x3 by elimination
            val a11 = n.toDouble(); val a12 = sc; val a13 = ss; val a22 = scc; val a23 = scs; val a33 = sss
            val b1 = n * mean; val b2 = src; val b3 = srs
            val sol = solve3(a11, a12, a13, a12, a22, a23, a13, a23, a33, b1, b2, b3)
            if (sol != null) {
                val (a, b, c) = sol
                var res = 0.0
                for (i in 0 until n) { val e = r[i] - (a + b * cos(2 * PI * f * t[i]) + c * sin(2 * PI * f * t[i])); res += e * e }
                val fit = 1.0 - res / tot
                if (fit > best.fit) best = Beat(f, sqrt(b * b + c * c), fit, trendFit)
            }
            f *= GRID_STEP
        }
        return best
    }

    private fun solve3(a11: Double, a12: Double, a13: Double, a21: Double, a22: Double, a23: Double, a31: Double, a32: Double, a33: Double, b1: Double, b2: Double, b3: Double): Triple<Double, Double, Double>? {
        val det = a11 * (a22 * a33 - a23 * a32) - a12 * (a21 * a33 - a23 * a31) + a13 * (a21 * a32 - a22 * a31)
        if (kotlin.math.abs(det) < 1e-18) return null
        val x = (b1 * (a22 * a33 - a23 * a32) - a12 * (b2 * a33 - a23 * b3) + a13 * (b2 * a32 - a22 * b3)) / det
        val y = (a11 * (b2 * a33 - a23 * b3) - b1 * (a21 * a33 - a23 * a31) + a13 * (a21 * b3 - b2 * a31)) / det
        val z = (a11 * (a22 * b3 - b2 * a32) - a12 * (a21 * b3 - b2 * a31) + b1 * (a21 * a32 - a22 * a31)) / det
        return Triple(x, y, z)
    }

    /** The ear's range for a beat (SAMICK findings §8): slower is a wow, faster a roughness. */
    const val MIN_RATE_HZ = 0.5
    const val MAX_RATE_HZ = 8.0
    /** The rate grid starts below the ear's floor so a beat at the floor is not forced up onto it. */
    const val GRID_LOW_HZ = 0.4
    /** Rates are tried at this ratio apart: about 8 %, forty steps from 0.4 to 8 Hz. */
    const val GRID_STEP = 1.08
    /** The modulation index of two planes 10 dB apart: 2ρ/(1+ρ²) at ρ = 10^(−0.5) = 0.3162. */
    val WITHIN_10_DB: Double = run { val rho = 10.0.pow(-0.5); 2 * rho / (1 + rho * rho) }
    /** The sinusoid at the chosen rate must explain this share of the swing: one beat, not scatter. */
    const val MIN_FIT = 0.5
    /**
     * … and this much more of it than a parabola does. A beat slower than the window -- the 0.25 Hz
     * wow of the Samick's A4, the Bösendorfer A3's 0.23 Hz -- leaves a bump a parabola explains
     * as well as the best sinusoid; a beat inside the window does not. On the corpus the real
     * beats stand 0.17-0.92 over the parabola, the wows under 0.2 on every window but a few early
     * ones, and those the tally's repeatability rule refuses.
     */
    const val OVER_TREND = 0.2
    /** Readings a measurement needs: four fifths of a two-second window at hop 1024. */
    const val MIN_READINGS = 75
}
