package at.clavierhaus.unisonmaster.tuning

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Which key was struck, from one magnitude spectrum — the whole compass,
 * unmuted unisons, no string muted.
 *
 * Not the strongest peak: in the bass the fundamental is weak and the
 * second to fifth partials carry the note, and at 2.9 Hz per bin the
 * fundamental of F1 (43.65 Hz) lies within a bin of its neighbours. What
 * separates the keys is the whole comb of partials: the key struck is the
 * one whose partial series *explains the peaks that are there* and has
 * *no gap where a partial should be*.
 *
 * So the spectrum is first reduced to its peaks — local maxima within
 * [RANGE_DB] of the loudest — and every key of the compass is scored:
 * the levels of the peaks that lie within [TOLERANCE] of one of its first
 * [MAX_K] partials are added (a peak explained), and for each of its first
 * [MUST_HAVE] partials that has no peak near it, [GAP_PENALTY] is taken
 * off. A key a twelfth or an octave below the true one explains fewer
 * peaks; a key an octave below explains them all as even partials but has
 * gaps at its odd ones; a key in the bass scored against a treble note
 * explains nothing and has every gap. The best score is the note.
 */
class NoteDetector(
    sampleRateHz: Int,
    private val windowSize: Int,
    private val lowMidi: Int = 21,     // A0
    private val highMidi: Int = 108,   // C8
) {
    companion object {
        /** Partials a key may claim: 12 keeps a stiff string's highest claimed partial within the tolerance. */
        const val MAX_K = 12
        /** A peak this close (relative) to k·f belongs to partial k: inharmonicity plus a unison's spread. */
        const val TOLERANCE = 0.035
        /** Peaks this far below the loudest bin still count. */
        const val RANGE_DB = 50.0
        /** The first partials a real note cannot lack — three; above [TREBLE_HZ], where strings have few, two ... */
        const val MUST_HAVE = 3
        const val MUST_HAVE_TREBLE = 1
        const val TREBLE_HZ = 1000.0
        /** The must-have partials start at the first one at or above this: a wound string's fundamental below it may be faint. */
        const val LOW_PARTIAL_HZ = 80.0
        /** ... and what each lacking one costs, in units of the explained share (0..1). */
        const val GAP_PENALTY = 0.35
        /** A low partial counts as present only within this of the note's strongest partial. */
        const val PRESENT_DB = 30.0
        /** Below this explained share (less gaps) no key is named: a decayed tail, a thump, sympathetic ringing. */
        const val MIN_SCORE = 0.5
        /** The loudest bin must stand this far above the noise floor for anything to be a note. */
        const val MIN_SNR_DB = 12.0
        /** Peaks kept, strongest first. */
        const val MAX_PEAKS = 48
        /** Nothing below this is a partial of a key of the compass ... */
        const val MIN_PEAK_HZ = 24.0
        /** ... and nothing above this tells the keys apart (C8's second partial is 8.4 kHz). */
        const val MAX_PEAK_HZ = 9000.0
        /** A peak counts against a key only within this of the loudest peak. */
        const val UNEXPLAINED_DB = 25.0
    }

    private val binHz = sampleRateHz.toDouble() / windowSize
    private val nyquist = sampleRateHz / 2.0

    private class Peak(val hz: Double, val level: Double)

    /**
     * The key struck, or null when nothing sounds: [mag] is a magnitude
     * spectrum of [windowSize] / 2 bins, [floorDb] its noise floor in dB,
     * [a4Hz] the reference the keys are named on.
     */
    fun detect(mag: DoubleArray, floorDb: Double, a4Hz: Double): Int? {
        val peaks = peaks(mag, floorDb) ?: return null
        var bestMidi = -1
        var best = Double.NEGATIVE_INFINITY
        var bestPresent = 0
        for (midi in lowMidi..highMidi) {
            val r = score(peaks, a4Hz * 2.0.pow((midi - 69) / 12.0))
            if (r.score > best) { best = r.score; bestMidi = midi; bestPresent = r.present }
        }
        // a note is a note only with its low partials in place; a decayed
        // tail or a noise burst names nothing
        return if (bestMidi < 0 || best < MIN_SCORE || bestPresent < 1) null else bestMidi
    }

    class Score(val score: Double, val present: Int, val explained: Double = 0.0, val unexplained: Double = 0.0, val gaps: Int = 0)

    /** For the probes: the peaks as (hz, level) and the full score of [midi]. */
    fun probe(mag: DoubleArray, floorDb: Double, a4Hz: Double, midi: Int): Pair<List<Pair<Double, Double>>, Score?> {
        val peaks = peaks(mag, floorDb) ?: return emptyList<Pair<Double, Double>>() to null
        return peaks.map { it.hz to it.level } to score(peaks, a4Hz * 2.0.pow((midi - 69) / 12.0))
    }

    /** The score of every key, for the probes. */
    fun scores(mag: DoubleArray, floorDb: Double, a4Hz: Double): Map<Int, Double> {
        val peaks = peaks(mag, floorDb) ?: return emptyMap()
        return (lowMidi..highMidi).associateWith { midi -> score(peaks, a4Hz * 2.0.pow((midi - 69) / 12.0)).score }
    }

    /** Local maxima within [RANGE_DB] of the loudest bin, refined between bins; null when nothing sounds. */
    private fun peaks(mag: DoubleArray, floorDb: Double): List<Peak>? {
        var topIdx = 1
        for (i in 1 until mag.size) if (mag[i] > mag[topIdx]) topIdx = i
        val top = db(mag[topIdx])
        if (top - floorDb < MIN_SNR_DB) return null
        val cut = top - RANGE_DB
        val minBin = (MIN_PEAK_HZ / binHz).toInt().coerceAtLeast(1)
        val maxBin = (MAX_PEAK_HZ / binHz).toInt().coerceAtMost(mag.size - 2)
        val out = ArrayList<Peak>()
        for (i in minBin..maxBin) {
            val c = mag[i]
            if (c <= mag[i - 1] || c < mag[i + 1]) continue
            val cDb = db(c)
            if (cDb < cut) continue
            val a = db(mag[i - 1]); val d = db(mag[i + 1])
            val den = a - 2 * cDb + d
            val delta = if (den < 0) (0.5 * (a - d) / den).coerceIn(-0.5, 0.5) else 0.0
            out.add(Peak((i + delta) * binHz, cDb - cut))
        }
        out.sortByDescending { it.level }
        return if (out.isEmpty()) null else out.take(MAX_PEAKS)
    }

    /**
     * Within the key's reach — its first [MAX_K] partials — every peak is
     * either explained by a partial or held against the key. The score is
     * the explained share of the peak amplitude in that band (levels taken
     * out of dB, so the strong peaks decide and the clutter near the floor
     * does not),
     * less [GAP_PENALTY] for each of the first [MUST_HAVE] partials that has
     * no peak. A key an octave below explains everything as even partials
     * but has gaps at its odd ones; a key an octave or a twelfth above
     * leaves the odd partials unexplained; a key deep in the bass, scored
     * against a treble note, has nothing in its reach and every gap.
     */
    private fun score(peaks: List<Peak>, f: Double): Score {
        var explained = 0.0
        var unexplained = 0.0
        // the partials a real note cannot lack: the first three from
        // LOW_PARTIAL_HZ upward — a wound bass string's fundamental is weak
        // and slow to come, and is not held against it — and above
        // TREBLE_HZ only the fundamental, since treble strings have few
        val firstK = ((LOW_PARTIAL_HZ / f).toInt() + 1).coerceAtLeast(1)
        val mustHave = if (f < TREBLE_HZ) MUST_HAVE else MUST_HAVE_TREBLE
        val lastK = firstK + mustHave - 1
        val partialLevel = DoubleArray(lastK + 1)
        val reach = minOf(MAX_K * f * (1 + TOLERANCE), nyquist)
        var strongest = 0.0
        var topExplained = false
        for ((i, p) in peaks.withIndex()) {
            if (p.hz >= reach) continue
            val k = (p.hz / f + 0.5).toInt()
            val centre = k * f
            val half = max(TOLERANCE * centre, binHz)
            val w = 10.0.pow(p.level / 20.0)          // amplitude: the loud peaks decide, the clutter near the floor does not
            if (k in 1..MAX_K && abs(p.hz - centre) <= half) {
                explained += w
                if (i == 0) topExplained = true
                if (p.level > strongest) strongest = p.level
                if (k <= lastK && p.level > partialLevel[k]) partialLevel[k] = p.level
            } else if (p.level >= RANGE_DB - UNEXPLAINED_DB) {
                // only what stands within UNEXPLAINED_DB of the loudest peak
                // is held against a key: the room's clutter deep below is not
                unexplained += w
            }
        }
        // the loudest peak of the spectrum is a partial of the note struck;
        // a key that cannot account for it is not that note
        if (!topExplained) return Score(-1.0, 0, explained, unexplained, mustHave)
        // a low partial is present only if it stands within PRESENT_DB of the
        // note's strongest partial: the room's rumble is not partial 1
        var gaps = 0; var present = 0
        for (k in firstK..lastK) if (k * f < nyquist) {
            if (partialLevel[k] >= strongest - PRESENT_DB) present++ else gaps++
        }
        val share = if (explained > 0.0) explained / (explained + unexplained) else 0.0
        return Score(share - GAP_PENALTY * gaps, present, explained, unexplained, gaps)
    }

    private fun db(x: Double): Double = 20.0 * log10(max(x, 1e-12))
}
