package at.clavierhaus.unisonmaster.tuning

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * The instrument's inharmonicity over the range, from the strings sampled (docs/ENGINE.md §1): one smooth line
 * over the keys, B(m) = a·e^{s(m−60)} + c·e^{−t(m−21)} — a part rising up the plain wire (Young: B grows roughly
 * geometrically with pitch there) and a part falling down the wound bass. No cut at the lowest plain string
 * (29 Oct 2026): on the Steinway B 1886 the strings alone show none there — E2, two wound strings, 1.69e-4; F2,
 * three plain, 1.68e-4 — while the line cut at the floor put a step where the strings have none. Measured on that
 * piano's every string A0–A3 (27 Oct; Pro `research/`): from a tuner's nine samples, the strings not sampled
 * are predicted within 9 % median (p90 23 %), against 19 % (p90 31–42 %) for the old straight line per side of the
 * floor; at partial 10, 1.4 c median against 2.7 c.
 *
 * Breaks are the tuner's: a break the tuner marks (Pro) starts a new line, for a bridge break or a strut that does
 * change the scaling. Within a line: four samples or more fit the two parts; two or three a straight line in ln B;
 * one its value throughout; none borrows the nearest sample. Every string keeps its own measured B once it has
 * one; the curve is for the notes not yet measured.
 *
 * The fit: for given slopes (s, t) the model is linear in (a, c) and is solved by least squares on the relative
 * error, a and c not below zero; the slopes are searched on a grid (0–0.2 and 0–0.3 per key, 0.005) and then
 * finely (0.0002) around the best. A grid and not an iteration, so the result does not depend on a start value.
 */
class InharmonicityCurve(
    samples: Map<Int, Double>,
    /** Notes at which a new line starts (the note itself is in the upper line). */
    breaks: Set<Int> = emptySet(),
) {
    private class Segment(val from: Int, val to: Int, val model: ((Int) -> Double)?)

    private val segments: List<Segment>
    // Ascending by key, as the sorted map it was until 30 Oct 2026; built with common
    // collections (toSortedMap exists only on the JVM), so the fit adds in the same order.
    private val all: Map<Int, Double> = samples.filter { it.value > 0.0 }.entries.sortedBy { it.key }.associate { it.toPair() }

    init {
        val cuts = breaks.filter { it in 1..127 }.sorted()
        val starts = listOf(0) + cuts
        val ends = cuts.map { it - 1 } + listOf(127)
        segments = starts.zip(ends).map { (from, to) -> Segment(from, to, fit(all.filter { it.key in from..to })) }
    }

    /** How many strings the curve stands on. */
    val count: Int get() = all.size

    /** True when at least two strings are in: a line can be drawn. */
    val ready: Boolean get() = all.size >= 2

    /** B expected of [midi]: its own sample, else its line's, else the nearest sample. */
    fun b(midi: Int): Double? {
        all[midi]?.let { return it }
        segments.firstOrNull { midi in it.from..it.to }?.model?.let { return it(midi) }
        val near = all.keys.minByOrNull { abs(it - midi) } ?: return null
        return all[near]
    }

    /** The notes sampled, lowest first. */
    val sampled: List<Int> get() = all.keys.toList()

    private companion object {
        fun fit(inside: Map<Int, Double>): ((Int) -> Double)? = when {
            inside.size >= 4 -> twoParts(inside)
            inside.size >= 2 -> lnLine(inside)
            inside.size == 1 -> inside.values.first().let { v -> { _: Int -> v } }
            else -> null
        }

        fun lnLine(inside: Map<Int, Double>): (Int) -> Double {
            val xs = inside.keys.map { it.toDouble() }; val ys = inside.values.map { ln(it) }
            val mx = xs.average(); val my = ys.average()
            var sxx = 0.0; var sxy = 0.0
            for (i in xs.indices) { sxx += (xs[i] - mx) * (xs[i] - mx); sxy += (xs[i] - mx) * (ys[i] - my) }
            val slope = if (sxx > 0) sxy / sxx else 0.0
            val intercept = my - slope * mx
            return { m -> exp(intercept + slope * m) }
        }

        /** (error, a, c) for slopes [s] and [t]: least squares on B_model/B − 1, a and c not below zero. */
        private fun solve(ks: IntArray, bs: DoubleArray, s: Double, t: Double): DoubleArray {
            val n = ks.size
            val e1 = DoubleArray(n) { exp(s * (ks[it] - 60)) / bs[it] }
            val e2 = DoubleArray(n) { exp(-t * (ks[it] - 21)) / bs[it] }
            var p11 = 0.0; var p12 = 0.0; var p22 = 0.0; var q1 = 0.0; var q2 = 0.0
            for (i in 0 until n) { p11 += e1[i] * e1[i]; p12 += e1[i] * e2[i]; p22 += e2[i] * e2[i]; q1 += e1[i]; q2 += e2[i] }
            val cands = ArrayList<Pair<Double, Double>>(3)
            val det = p11 * p22 - p12 * p12
            if (det > 1e-300) {
                val a = (q1 * p22 - q2 * p12) / det; val c = (p11 * q2 - p12 * q1) / det
                if (a >= 0 && c >= 0) cands += a to c
            }
            if (p11 > 0) cands += (q1 / p11) to 0.0
            if (p22 > 0) cands += 0.0 to (q2 / p22)
            var best = doubleArrayOf(Double.MAX_VALUE, 0.0, 0.0)
            for ((a, c) in cands) {
                var err = 0.0
                for (i in 0 until n) { val r = a * e1[i] + c * e2[i] - 1.0; err += r * r }
                if (err < best[0]) best = doubleArrayOf(err, a, c)
            }
            return best
        }

        fun twoParts(inside: Map<Int, Double>): (Int) -> Double {
            val ks = inside.keys.toIntArray(); val bs = inside.values.toDoubleArray()
            fun search(s0: Double, s1: Double, t0: Double, t1: Double, step: Double): DoubleArray {
                var best = doubleArrayOf(Double.MAX_VALUE, 0.0, 0.0, 0.0, 0.0)
                var i = 0
                while (s0 + i * step <= s1 + 1e-12) {
                    val s = s0 + i * step
                    var j = 0
                    while (t0 + j * step <= t1 + 1e-12) {
                        val t = t0 + j * step
                        val r = solve(ks, bs, s, t)
                        if (r[0] < best[0]) best = doubleArrayOf(r[0], r[1], r[2], s, t)
                        j++
                    }
                    i++
                }
                return best
            }
            val coarse = search(0.0, 0.2, 0.0, 0.3, 0.005)
            val fine = search(maxOf(0.0, coarse[3] - 0.005), coarse[3] + 0.005, maxOf(0.0, coarse[4] - 0.005), coarse[4] + 0.005, 0.0002)
            val a = fine[1]; val c = fine[2]; val s = fine[3]; val t = fine[4]
            return { m -> a * exp(s * (m - 60)) + c * exp(-t * (m - 21)) }
        }
    }
}
