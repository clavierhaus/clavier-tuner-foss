package at.clavierhaus.unisonmaster.tuning

import at.clavierhaus.unisonmaster.dsp.Fft
import at.clavierhaus.unisonmaster.dsp.InharmonicityFit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Which key was struck — the whole compass, unmuted unisons or one string,
 * from one spectrum (docs/DETECTION.md).
 *
 * Not a comb held against the peaks, but a string *fitted* to them: from
 * every key of the compass as a starting point, the peaks near its first
 * partials anchor a fit of the fundamental and the inharmonicity B; the
 * comb is then extended with the fitted model, refitting as it climbs, out
 * to [reachHz] — a wound string's energy lies in partials 5 to 50, and only
 * a model with its own B finds partial 27 where it stands. The fit is
 * scored on what it explains against what stands unexplained in its range,
 * and the key is named from the *fitted* fundamental, so two neighbouring
 * starts that fit the same string name the same key, and a string 40 cents
 * off its key is still that key.
 *
 * What is held against a key and what is not, each a piece of physics:
 * - nothing below a key's fundamental: the room, the action and the
 *   soundboard are not partials of anything
 * - a peak that is not prominent above its surroundings: a broadband hump
 *   is not a partial
 * - a peak nearly as loud as the loudest, beyond the key's reach: another
 *   note's, so it counts
 * - the loudest peak of the spectrum must be a partial of the key
 * - a model whose matched partials all share a factor m has its string at
 *   m·f0 — a sub-harmonic, not a note
 * - a free fit that leaves the physical band of B for its key (two strings
 *   sounding, a false beat) is not trusted: B is then held at the nominal
 *   value and only the fundamental is fitted
 * - a low partial that is there but faint costs a fraction of a gap, not
 *   a whole one: a wound string's third partial can lie 35 dB down
 * - what sounded before the strike — the room, a note still ringing — is
 *   masked out where it has not grown ([background])
 *
 * Checked on five instruments through three microphones (a Bösendorfer
 * 225 and 290, two Steinways, a Kawai upright): one decision per strike
 * names the key below C7 on all but a handful of takes, and those are the
 * takes (a neighbour ringing louder, a mislabelled strike).
 */
class KeyIdentifier(
    private val sampleRateHz: Int,
    private val windowSize: Int,
    private val lowMidi: Int = 21,     // A0
    private val highMidi: Int = 108,   // C8
    /** The comb reaches this far up, in Hz, or at least [MIN_K] partials. */
    private val reachHz: Double = REACH_HZ,
) {
    companion object {
        const val REACH_HZ = 1500.0
        const val MIN_K = 12
        const val MAX_K = 64
        /** The anchor: the first partials, matched with the nominal B, this generously. */
        const val ANCHOR_K = 12
        const val ANCHOR_TOLERANCE = 0.035
        /** Beyond the anchor the fitted model places a partial this closely. */
        const val FIT_TOLERANCE = 0.008
        /** Peaks weigh by amplitude to this power: half, so one loud peak does not outvote ten quiet ones. */
        const val POWER = 0.5
        const val RANGE_DB = 50.0
        /** A peak counts against a key only within this of the loudest ... */
        const val UNEXPLAINED_DB = 25.0
        /** ... and only when it stands this far above the median of ±[PROMINENCE_HZ] around it. */
        const val MIN_PROMINENCE_DB = 8.0
        const val PROMINENCE_HZ = 100.0
        /** Beyond a key's reach a peak counts against it only within this of the loudest. */
        const val ABOVE_REACH_DB = 10.0
        /** Peaks below this fraction of a key's fundamental are not its business. */
        const val BELOW = 0.8
        /** The first partials a note cannot lack: three from [LOW_PARTIAL_HZ] up; above [TREBLE_HZ] one. */
        const val MUST_HAVE = 3
        const val MUST_HAVE_TREBLE = 1
        const val TREBLE_HZ = 700.0
        const val LOW_PARTIAL_HZ = 80.0
        const val GAP_PENALTY = 0.35
        /** A low partial is fully present within this of the loudest peak, and fully absent [PRESENT_DEPTH_DB] further down. */
        const val PRESENT_DB = 30.0
        const val PRESENT_DEPTH_DB = 20.0
        /** B may lie this factor either side of the nominal curve for its key. */
        const val B_RANGE = 8.0
        const val MAX_B = 0.02
        const val MIN_SCORE = 0.5
        /** Fits within this of the best score tie; the simplest model (the highest fundamental) wins. */
        const val TIE = 0.02
        const val MIN_SNR_DB = 12.0
        const val MAX_PEAKS = 64
        const val MIN_PEAK_HZ = 24.0
        const val MAX_PEAK_HZ = 9000.0
        /** A background peak masks the strike's peak at the same place unless the strike's stands this much higher. */
        const val GROWN_DB = 6.0

        /**
         * A typical piano's inharmonicity by key: wound strings near 1e-4,
         * plain wire from E2 rising by e^0.055 per semitone (the 225 of
         * 22 September, within 2 × on the other four instruments).
         */
        fun nominalB(midi: Int): Double = if (midi <= 40) 1e-4 else 1.9e-4 * exp(0.0553 * (midi - 40))
    }

    private val binHz = sampleRateHz.toDouble() / windowSize
    private val nyquist = sampleRateHz / 2.0
    private val hann by lazy { DoubleArray(windowSize) { 0.5 - 0.5 * cos(2.0 * PI * it / (windowSize - 1)) } }

    /** A peak: where, how far below the loudest (0 = the loudest, or absolute dB for the background), how far above its surroundings. */
    class Peak(val hz: Double, val level: Double, val prominence: Double)

    /**
     * One key's fit: [midi] named from the fitted fundamental [f1] (which
     * [startMidi] the fit began from is incidental), [b] the inharmonicity,
     * [score] the explained share less gaps, [matched] how many partials
     * the model stood on.
     */
    class Fit(val startMidi: Int, val midi: Int, val f1: Double, val b: Double, val score: Double, val explained: Double, val unexplained: Double, val gaps: Double, val matched: Int)

    /**
     * The key struck in the [windowSize] samples of [samples] from [off],
     * named on [a4Hz], or null when nothing sounds or nothing fits. With
     * [backgroundEnd] > 0, what sounded before that sample is masked out.
     */
    fun identify(samples: FloatArray, a4Hz: Double, off: Int = samples.size - windowSize, backgroundEnd: Int = -1): Fit? =
        fits(samples, a4Hz, off, backgroundEnd)?.let { best(it) }

    /** As [identify], on the last [windowSize] samples: the key, for following it. */
    fun detectIn(samples: FloatArray, a4Hz: Double, backgroundEnd: Int = -1): Int? =
        identify(samples, a4Hz, samples.size - windowSize, backgroundEnd)?.midi

    /** The best of [fits], or null below [MIN_SCORE]; among ties the simplest model. */
    fun best(fits: Collection<Fit>): Fit? {
        val top = fits.maxOfOrNull { it.score } ?: return null
        if (top < MIN_SCORE) return null
        return fits.filter { it.score >= top - TIE }.maxByOrNull { it.f1 }
    }

    /** Every key's fit, for the probes; null when nothing sounds. */
    fun fits(samples: FloatArray, a4Hz: Double, off: Int = samples.size - windowSize, backgroundEnd: Int = -1): List<Fit>? {
        if (samples.size < windowSize || off < 0 || off + windowSize > samples.size) return null
        val (all, absTop) = peaksOf(samples, off) ?: return null
        val mask = masked(all, absTop, if (backgroundEnd > 0) background(samples, backgroundEnd) else null)
        val peaks = if (mask.isEmpty()) all else all.filterIndexed { i, _ -> i !in mask }
        if (peaks.isEmpty()) return null
        return (lowMidi..highMidi).map { midi -> fit(peaks, midi, a4Hz) }
    }

    /** The peaks of the window at [off], levels relative to the loudest, and the loudest's absolute dB; null when nothing sounds. */
    fun peaksOf(samples: FloatArray, off: Int): Pair<List<Peak>, Double>? {
        val mag = magnitude(samples, off, windowSize)
        val lo = (50.0 / binHz).toInt().coerceAtLeast(1)
        val hi = (12000.0 / binHz).toInt().coerceAtMost(mag.size - 1)
        val floor = mag.copyOfRange(lo, hi).also { it.sort() }[(hi - lo) / 2]
        var topIdx = 1
        for (i in 1 until mag.size) if (mag[i] > mag[topIdx]) topIdx = i
        val top = db(mag[topIdx])
        if (top - db(floor) < MIN_SNR_DB) return null
        val cut = top - RANGE_DB
        val promBins = (PROMINENCE_HZ / binHz).toInt().coerceAtLeast(4)
        val out = ArrayList<Peak>()
        for (i in bins(mag)) {
            val c = mag[i]
            if (c <= mag[i - 1] || c < mag[i + 1]) continue
            val cDb = db(c)
            if (cDb < cut) continue
            val a = db(mag[i - 1]); val d = db(mag[i + 1])
            val den = a - 2 * cDb + d
            val delta = if (den < 0) (0.5 * (a - d) / den).coerceIn(-0.5, 0.5) else 0.0
            val lo2 = (i - promBins).coerceAtLeast(1); val hi2 = (i + promBins).coerceAtMost(mag.size - 2)
            val around = mag.copyOfRange(lo2, hi2 + 1).also { it.sort() }
            out.add(Peak((i + delta) * binHz, cDb - cut, cDb - db(around[around.size / 2])))
        }
        out.sortByDescending { it.level }
        return if (out.isEmpty()) null else out.take(MAX_PEAKS) to top
    }

    /**
     * The background: the peaks (absolute dB) of what sounded in the
     * window before [end] — the room, a previous note still ringing —
     * or null when there is too little of it.
     */
    fun background(samples: FloatArray, end: Int): List<Peak>? {
        if (end <= 0) return null
        val n = minOf(windowSize, end)
        if (n < windowSize / 4) return null
        val seg = FloatArray(windowSize)
        samples.copyInto(seg, windowSize - n, end - n, end)
        val mag = magnitude(seg, 0, windowSize)
        val lo = (50.0 / binHz).toInt().coerceAtLeast(1)
        val hi = (12000.0 / binHz).toInt().coerceAtMost(mag.size - 1)
        val floor = db(mag.copyOfRange(lo, hi).also { it.sort() }[(hi - lo) / 2])
        val out = ArrayList<Peak>()
        for (i in bins(mag)) {
            val c = mag[i]
            if (c <= mag[i - 1] || c < mag[i + 1]) continue
            val cDb = db(c)
            if (cDb - floor >= MIN_SNR_DB) out.add(Peak(i * binHz, cDb, 0.0))
        }
        return out
    }

    /** The indices of [peaks] that stood in [bg] and have not grown by [GROWN_DB]. */
    fun masked(peaks: List<Peak>, absTop: Double, bg: List<Peak>?): Set<Int> {
        if (bg.isNullOrEmpty()) return emptySet()
        val out = HashSet<Int>()
        for ((i, p) in peaks.withIndex()) {
            val abs = p.level + absTop - RANGE_DB
            for (b in bg) if (abs(b.hz - p.hz) <= 1.5 * binHz && abs - b.level < GROWN_DB) { out.add(i); break }
        }
        return out
    }

    private fun bins(mag: DoubleArray): IntRange =
        (MIN_PEAK_HZ / binHz).toInt().coerceAtLeast(1)..(MAX_PEAK_HZ / binHz).toInt().coerceAtMost(mag.size - 2)

    private fun magnitude(samples: FloatArray, off: Int, n: Int): DoubleArray {
        val re = DoubleArray(n) { samples[off + it] * hann[it] }
        val im = DoubleArray(n)
        Fft.transform(re, im)
        return DoubleArray(n / 2) { sqrt(re[it] * re[it] + im[it] * im[it]) }
    }

    private fun w(level: Double) = 10.0.pow(level * POWER / 20.0)

    /** The string fitted from key [startMidi]'s first partials, then scored. */
    fun fit(peaks: List<Peak>, startMidi: Int, a4Hz: Double): Fit {
        val fStart = a4Hz * 2.0.pow((startMidi - 69) / 12.0)
        var b = nominalB(startMidi)
        var f0 = fStart / sqrt(1 + b)
        val kMax = (reachHz / fStart).toInt().coerceIn(MIN_K, MAX_K)
        val matched = HashMap<Int, Int>()          // partial k -> the strongest peak it claims
        fun predicted(k: Int) = k * f0 * sqrt(1 + b * k * k)
        var bHeld = false
        fun refit() {
            val pts = matched.entries.map { (k, i) -> k to peaks[i].hz }
            val wts = matched.entries.map { (_, i) -> w(peaks[i].level) }
            if (matched.size >= 3 && !bHeld) {
                InharmonicityFit.fit(pts, wts)?.let {
                    val nb = nominalB(Notes.nearestMidi(it.f0Hz * sqrt(1 + it.b.coerceIn(0.0, MAX_B)), a4Hz))
                    if (it.f0Hz > 0 && it.b in nb / B_RANGE..nb * B_RANGE) { f0 = it.f0Hz; b = it.b; return }
                    // outside the band: not one string's model — hold B, fit only the fundamental
                    bHeld = true; b = nominalB(Notes.nearestMidi(f0 * sqrt(1 + b), a4Hz))
                }
            }
            if (matched.isNotEmpty()) InharmonicityFit.fitWithFixedB(pts, b, wts)?.let { f0 = it.f0Hz }
        }
        // the anchor with the nominal B, generously; then the fitted model, in steps, tightly
        val stages = listOf(ANCHOR_K, 2 * ANCHOR_K, 3 * ANCHOR_K, kMax).filter { it <= kMax }.distinct()
        var kDone = 0
        for ((s, kEnd) in stages.withIndex()) {
            val tol = if (s == 0) ANCHOR_TOLERANCE else FIT_TOLERANCE
            for (k in (kDone + 1)..kEnd) {
                val centre = predicted(k)
                if (centre > nyquist) break
                val half = max(minOf(tol * centre, 0.45 * f0), 1.5 * binHz)
                var bestI = -1
                for ((i, p) in peaks.withIndex()) if (abs(p.hz - centre) <= half && (bestI < 0 || p.level > peaks[bestI].level)) bestI = i
                if (bestI >= 0) matched[k] = bestI
            }
            kDone = kEnd
            refit()
        }
        val f1 = f0 * sqrt(1 + b)
        val named = Notes.nearestMidi(f1, a4Hz)
        fun failed(gaps: Int) = Fit(startMidi, named, f1, b, -1.0, 0.0, 0.0, gaps.toDouble(), matched.size)
        if (matched.isEmpty()) return failed(MUST_HAVE)
        var g = 0
        for (k in matched.keys) g = gcd(g, k)
        if (g > 1) return failed(MUST_HAVE)
        // what the model explains against what stands in its range unexplained: every peak within
        // tolerance of a matched partial is explained (a unison's spread, a split peak)
        val reach = minOf(predicted(kMax) * (1 + FIT_TOLERANCE), nyquist)
        val below = BELOW * f1
        val claimed = HashSet<Int>()
        for (k in matched.keys) {
            val centre = predicted(k)
            val half = max(minOf(FIT_TOLERANCE * centre, 0.45 * f0), 1.5 * binHz)
            for ((i, p) in peaks.withIndex()) if (abs(p.hz - centre) <= half) claimed.add(i)
        }
        var explained = 0.0; var unexplained = 0.0
        var topExplained = false
        for ((i, p) in peaks.withIndex()) {
            if (p.hz < below) continue
            val counts = p.prominence >= MIN_PROMINENCE_DB
            if (p.hz >= reach) {
                if (i !in claimed && counts && p.level >= RANGE_DB - ABOVE_REACH_DB) unexplained += w(p.level)
                continue
            }
            if (i in claimed) { explained += w(p.level); if (i == 0) topExplained = true }
            else if (counts && p.level >= RANGE_DB - UNEXPLAINED_DB) unexplained += w(p.level)
        }
        if (!topExplained) return failed(MUST_HAVE)
        val firstK = ((LOW_PARTIAL_HZ / f1).toInt() + 1).coerceAtLeast(1)
        val mustHave = if (f1 < TREBLE_HZ) MUST_HAVE else MUST_HAVE_TREBLE
        var gaps = 0.0
        val presentLevel = RANGE_DB - PRESENT_DB
        for (k in firstK until firstK + mustHave) if (k * f1 < nyquist) {
            val i = matched[k]
            gaps += if (i == null || peaks[i].prominence < MIN_PROMINENCE_DB) 1.0
            else ((presentLevel - peaks[i].level) / PRESENT_DEPTH_DB).coerceIn(0.0, 1.0)
        }
        val share = if (explained > 0.0) explained / (explained + unexplained) else 0.0
        return Fit(startMidi, named, f1, b, share - GAP_PENALTY * gaps, explained, unexplained, round(gaps * 100) / 100, matched.size)
    }

    private fun db(x: Double): Double = 20.0 * log10(max(x, 1e-12))
    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}
