package at.clavierhaus.unisonmaster.tuning

import at.clavierhaus.unisonmaster.dsp.Fft
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sqrt

/** One partial of the sounding string, as measured in a single spectrum. */
data class PartialReading(
    val k: Int,
    val hz: Double,
    /** Deviation from the harmonic position k × f1, in cents. */
    val cents: Double,
    /** Peak magnitude, dB (arbitrary reference, comparable within a spectrum). */
    val db: Double,
    /** Peak above the spectrum's noise floor, dB. */
    val snrDb: Double,
)

/**
 * Finds partials 1..[maxPartials] of a stiff string in one Hann-windowed
 * spectrum. The string is inharmonic, so partial k sits sharp of k × f1 by
 * an amount that grows roughly with k². The search therefore walks upward:
 * each found partial refines an inharmonicity estimate B, which predicts
 * where the next partial lies; the peak is taken within ±[SEARCH_SPAN] × f1
 * of that prediction and refined between bins.
 */
class PartialTracker(
    private val sampleRateHz: Int,
    private val windowSize: Int = 16384,
    private val maxPartials: Int = 12,
) {
    companion object {
        const val SEARCH_SPAN = 0.35     // of f1, either side of the prediction
        /**
         * The band around partial k must not reach the same partial of the
         * neighbouring semitone, which stands 5.9 % away: with ±35 % of f1
         * it did, from k = 2 upward, and a louder neighbour — the note just
         * tuned, still ringing — was taken for this string's partial. Its
         * stored cents then went into every octave link that read it, and
         * the target of the next note came out a semitone wrong.
         * ±3 % of the partial is half a semitone: room for the prediction's
         * error, none for the neighbour.
         */
        const val NEIGHBOUR_GUARD = 0.03 // of k·f1, either side of the prediction
        /** No search narrower than this, so the parabola has something to stand on. */
        const val MIN_HALF_BINS = 2
        /**
         * A plain-wire partial is sharp of k·f1 by an amount that grows with
         * k², and never flat by more than measurement noise. A reading outside
         * that is another string's partial, or nothing, and is not returned.
         */
        fun plausibleCents(k: Int, cents: Double): Boolean =
            cents >= -25.0 && cents <= 20.0 + 1.6 * k * k
        const val FLOOR_MIN_HZ = 50.0
        const val FLOOR_MAX_HZ = 12000.0
    }

    init {
        require(windowSize > 0 && windowSize and (windowSize - 1) == 0) { "window must be a power of two" }
    }

    private val hann = DoubleArray(windowSize) { 0.5 - 0.5 * cos(2.0 * PI * it / (windowSize - 1)) }
    private val re = DoubleArray(windowSize)
    private val im = DoubleArray(windowSize)
    private val mag = DoubleArray(windowSize / 2)
    val binHz = sampleRateHz.toDouble() / windowSize

    /** The Hann-windowed magnitude spectrum of [samples]; [strongestPeak] and [partials] read it. */
    fun spectrum(samples: FloatArray) {
        require(samples.size == windowSize) { "expected $windowSize samples" }
        for (i in 0 until windowSize) { re[i] = samples[i] * hann[i]; im[i] = 0.0 }
        Fft.transform(re, im)
        for (i in mag.indices) mag[i] = sqrt(re[i] * re[i] + im[i] * im[i])
    }

    /**
     * The strongest component between [minHz] and [maxHz] in the last
     * [spectrum], refined between bins, or null when nothing stands out
     * there. This locates the fundamental: the loudest thing in the range is
     * what the tuner hears as the note, and a time-domain estimate that
     * disagrees with it has been pulled by a neighbour or a partial.
     */
    fun strongestPeak(minHz: Double, maxHz: Double, minSnrDb: Double = LiveReference.AUDIBLE_SNR_DB): Double? {
        val from = floor(minHz / binHz).toInt().coerceAtLeast(1)
        val to = floor(maxHz / binHz).toInt().coerceAtMost(mag.size - 2)
        if (to <= from) return null
        var best = from
        for (i in from..to) if (mag[i] > mag[best]) best = i
        if (mag[best] <= 0.0) return null
        // nothing sounding in the range: the loudest bin is noise, not a note
        val lo = (FLOOR_MIN_HZ / binHz).toInt().coerceAtLeast(1)
        val hi = (FLOOR_MAX_HZ / binHz).toInt().coerceAtMost(mag.size - 1)
        if (db(mag[best]) - db(median(mag, lo, hi)) < minSnrDb) return null
        val a = db(mag[best - 1]); val c = db(mag[best]); val d = db(mag[best + 1])
        val den = a - 2 * c + d
        val delta = if (den < 0) (0.5 * (a - d) / den).coerceIn(-0.5, 0.5) else 0.0
        return (best + delta) * binHz
    }

    /**
     * The fundamental of the loudest thing sounding between [minHz] and
     * [maxHz]: the strongest peak, unless a peak within [subharmonicDb] of it
     * stands at that frequency divided by 2, 3 or 4 (within 3 %) — then the
     * lowest such peak, because a bass string's upper partials outweigh its
     * first. Null when the strongest peak does not clear the noise floor.
     */
    fun fundamentalOfStrongest(minHz: Double, maxHz: Double, subharmonicDb: Double): Double? {
        val top = strongestPeak(minHz, maxHz) ?: return null
        val topDb = db(mag[(top / binHz).toInt().coerceIn(1, mag.size - 2)])
        var best = top
        for (k in 4 downTo 2) {
            val f = top / k
            if (f < minHz) continue
            // the candidate must itself stand clear of the noise floor: late in a
            // quiet decay the floor lies within a few dB of the top peak, and a
            // noise bin at half its frequency would name a note an octave low
            val sub = strongestPeak(f * 0.97, f * 1.03, minSnrDb = LiveReference.AUDIBLE_SNR_DB) ?: continue
            val subDb = db(mag[(sub / binHz).toInt().coerceIn(1, mag.size - 2)])
            if (topDb - subDb <= subharmonicDb) { best = sub; break }
        }
        return best
    }

    fun analyse(samples: FloatArray, f1: Double): List<PartialReading> {
        spectrum(samples)
        return partials(f1)
    }

    /** The key struck, from the last [spectrum], by [detector]; null when nothing sounds. */
    fun detectKey(detector: NoteDetector, a4Hz: Double): Int? {
        val lo = (FLOOR_MIN_HZ / binHz).toInt().coerceAtLeast(1)
        val hi = (FLOOR_MAX_HZ / binHz).toInt().coerceAtMost(mag.size - 1)
        return detector.detect(mag, db(median(mag, lo, hi)), a4Hz)
    }

    /** Partials 1..maxPartials of the string whose first partial is [f1], in the last [spectrum]. */
    fun partials(f1: Double): List<PartialReading> {
        val lo = (FLOOR_MIN_HZ / binHz).toInt().coerceAtLeast(1)
        val hi = (FLOOR_MAX_HZ / binHz).toInt().coerceAtMost(mag.size - 1)
        val floorDb = db(median(mag, lo, hi))

        val out = ArrayList<PartialReading>(maxPartials)
        var b = 0.0
        val bs = ArrayList<Double>()
        val nyquist = sampleRateHz / 2.0
        for (k in 1..maxPartials) {
            val predicted = k * f1 * sqrt((1 + b * k * k) / (1 + b))
            val halfHz = maxOf(minOf(SEARCH_SPAN * f1, NEIGHBOUR_GUARD * k * f1), MIN_HALF_BINS * binHz)
            if (predicted + halfHz >= nyquist - binHz) break
            val from = floor((predicted - halfHz) / binHz).toInt().coerceAtLeast(1)
            val to = floor((predicted + halfHz) / binHz).toInt().coerceAtMost(mag.size - 2)
            var best = from
            for (i in from..to) if (mag[i] > mag[best]) best = i
            // parabolic refinement on the log magnitude
            val a = db(mag[best - 1]); val c = db(mag[best]); val d = db(mag[best + 1])
            val den = a - 2 * c + d
            val delta = if (den < 0) (0.5 * (a - d) / den).coerceIn(-0.5, 0.5) else 0.0
            val hz = (best + delta) * binHz
            val peakDb = c - 0.25 * (a - d) * delta
            val cents = 1200.0 * ln(hz / (k * f1)) / ln(2.0)
            if (k >= 2 && !plausibleCents(k, cents)) continue
            val reading = PartialReading(k, hz, if (k == 1) 0.0 else cents, peakDb, peakDb - floorDb)
            out.add(reading)
            if (k >= 2 && reading.snrDb >= LiveReference.AUDIBLE_SNR_DB) {
                val r = hz / (k * f1)
                val bk = (r * r - 1) / (k * k - r * r)
                if (bk > 0 && bk < 0.05) {
                    bs.add(bk)
                    b = bs.sorted()[bs.size / 2]
                }
            }
        }
        return out
    }

    private fun db(x: Double): Double = 20.0 * log10(maxOf(x, 1e-12))

    private fun median(a: DoubleArray, from: Int, to: Int): Double {
        val copy = a.copyOfRange(from, to + 1)
        copy.sort()
        return copy[copy.size / 2]
    }
}

/**
 * Which partials the hub shows. Fundamental mode shows partial 1 only.
 * Full Spectrum shows every audible partial the tuner has not switched off.
 */
object PartialSelection {
    fun shown(fullSpectrum: Boolean, audible: Set<Int>, hidden: Set<Int>): Set<Int> =
        if (fullSpectrum) audible - hidden else setOf(1)

    /** A tap only acts in Full Spectrum mode, and only on an audible partial. */
    fun tap(k: Int, fullSpectrum: Boolean, audible: Set<Int>, hidden: Set<Int>): Set<Int> = when {
        !fullSpectrum || k !in audible -> hidden
        k in hidden -> hidden - k
        else -> hidden + k
    }
}
