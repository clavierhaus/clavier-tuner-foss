package at.clavierhaus.unisonmaster.dsp

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Beat-rate estimation from the envelope of a single partial.
 *
 * Why this exists. While a unison is being tuned, two strings sound
 * together. Their components are a fraction of a hertz apart — far closer
 * than any window can resolve — so a spectral measurement returns one
 * amplitude-weighted composite frequency, not two. Comparing that composite
 * against a stored model yields a pitch difference, which is not what the
 * tuner hears and not what is being nulled.
 *
 * What the ear uses is the amplitude modulation: two components at f and
 * f+Δ beat at exactly Δ, and the envelope of the partial rises and falls at
 * that rate. Measuring the envelope's period measures the beat directly,
 * with both strings sounding, which is the situation the tuner is actually
 * in. It also degrades gracefully: as the unison closes, Δ falls and the
 * modulation slows, which is precisely the signal being tuned toward.
 *
 * Method: mean-removed normalised autocorrelation of the envelope series,
 * first significant peak beyond the minimum lag, parabolic interpolation
 * for sub-frame resolution.
 *
 * Bounds. With envelope samples at [frameRateHz] the highest measurable
 * beat is frameRateHz/2; with N samples the lowest needs roughly two
 * periods, i.e. 2*frameRateHz/N. At 11.7 Hz and 96 samples that is about
 * 0.24 Hz to 5.8 Hz — the range in which unison work happens.
 */
object BeatRate {

    /** A peak this close to the global maximum counts as the true period. */
    private const val PEAK_FRACTION = 0.82

    /**
     * [levelsDb] in chronological order, oldest first; NaN entries are gaps.
     * Returns beats per second, or null if no periodic modulation is found.
     */
    fun estimate(
        levelsDb: DoubleArray,
        frameRateHz: Double,
        maxBeatHz: Double = 6.0,
        minCorrelation: Double = 0.35,
    ): Double? {
        // Work in linear amplitude: beating is multiplicative in amplitude,
        // and a dB series exaggerates the deep troughs of a near-null beat.
        val amps = ArrayList<Double>(levelsDb.size)
        for (v in levelsDb) {
            if (v.isNaN()) return null // a gap breaks the periodicity estimate
            amps += 10.0.pow(v / 20.0)
        }
        val n = amps.size
        if (n < 24) return null

        // Remove the decay trend: a decaying partial is linear in dB, so a
        // straight-line fit in dB removes decay without touching modulation.
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        for (i in 0 until n) {
            val x = i.toDouble()
            sx += x; sy += levelsDb[i]; sxx += x * x; sxy += x * levelsDb[i]
        }
        val denom = n * sxx - sx * sx
        if (denom == 0.0) return null
        val slope = (n * sxy - sx * sy) / denom
        val intercept = (sy - slope * sx) / n

        val x = DoubleArray(n) { i -> levelsDb[i] - (intercept + slope * i) }
        var mean = 0.0
        for (v in x) mean += v
        mean /= n
        for (i in 0 until n) x[i] -= mean

        var energy = 0.0
        for (v in x) energy += v * v
        if (energy <= 1e-9) return null // flat envelope: no beat to find

        val minLag = (frameRateHz / maxBeatHz).toInt().coerceAtLeast(2)
        val maxLag = n / 2
        if (maxLag <= minLag + 1) return null

        val ac = DoubleArray(maxLag + 1)
        var globalMax = 0.0
        for (lag in minLag..maxLag) {
            var acc = 0.0
            for (i in 0 until n - lag) acc += x[i] * x[i + lag]
            val norm = acc / energy
            ac[lag] = norm
            if (norm > globalMax) globalMax = norm
        }
        if (globalMax < minCorrelation) return null

        // Take the FIRST strong peak, not the global maximum. A periodic
        // envelope correlates at the period and again at every multiple of
        // it, often more strongly at 2x because fewer samples are wasted by
        // the lag; taking the maximum therefore reports half the true beat
        // rate. (This test caught exactly that: a 1.8 Hz beat read as 0.9.)
        var bestLag = -1
        for (lag in (minLag + 1) until maxLag) {
            val v = ac[lag]
            if (v >= PEAK_FRACTION * globalMax && v >= minCorrelation &&
                v >= ac[lag - 1] && v >= ac[lag + 1]
            ) {
                bestLag = lag
                break
            }
        }
        if (bestLag < 0) return null

        // Parabolic interpolation around the peak
        val lag = if (bestLag in (minLag + 1) until maxLag) {
            val a = ac[bestLag - 1]; val b = ac[bestLag]; val c = ac[bestLag + 1]
            val d = 2.0 * (2.0 * b - a - c)
            if (abs(d) < 1e-12) bestLag.toDouble() else bestLag + (c - a) / d
        } else bestLag.toDouble()

        return frameRateHz / lag
    }

    /** Root-mean-square of a series, for callers needing a level check. */
    fun rms(values: DoubleArray): Double {
        var acc = 0.0
        for (v in values) acc += v * v
        return sqrt(acc / values.size)
    }
}
