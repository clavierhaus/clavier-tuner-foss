package at.clavierhaus.unisonmaster.tuning

import at.clavierhaus.unisonmaster.dsp.PreciseF0
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Live pitch and spectrum of one sounding string.
 *
 * It never stops: it follows the string while the tuner turns the pin.
 *
 *  - A strike (hop energy rising by [ONSET_RATIO] above [ONSET_RMS]) clears
 *    the recent estimates, so a new note is never averaged with the last.
 *  - After a strike, [settleHops] hops are skipped: the attack, plus enough
 *    to flush the pre-strike sound out of the analysis window.
 *  - Each hop: the strongest spectral component of the range set by
 *    [setRange] locates the fundamental; a two-stage phase reading (phase
 *    advance over [phaseBaseline] samples) refines it.
 *    The value shown ([hz]) is the mean of those readings since the strike,
 *    over at most [readingSeconds] — a unison's strings a cent apart, or a
 *    string with a false beat, pull the reading of any single hop back and
 *    forth at the beat rate (0.4 Hz on a D#4 of the 225, with a 0.1 Hz
 *    match window), and a tuner's ear averages the beat as this does. A
 *    strike starts the mean afresh, so a pin turned and struck again is
 *    read at once. (The stored record uses the median of the same
 *    readings.)
 *  - [level] is the bell height: loudness relative to the strike's own peak,
 *    in dB over [RANGE_DB]. Every strike reaches full height whatever the
 *    microphone distance, and the bell sinks as the note decays.
 *  - When the tone dies, the last value is held; [level] falls to zero.
 *  - Each reading also measures partials 1..12 ([partials]), located by the
 *    spectrum peak and then read by phase, like the fundamental; a partial that
 *    stands [AUDIBLE_SNR_DB] above the noise floor at any time during the
 *    current strike is [audible] until the next strike.
 *  - Per strike, every partial's position, peak level and sustain are
 *    gathered; [summary] condenses them into a [NoteMeasurement].
 */
class LiveReference(
    private val sampleRateHz: Int,
    private val windowSize: Int = 16384,
    private val hopSize: Int = 4096,
    minHz: Double = 380.0,
    maxHz: Double = 500.0,
    private val phaseBaseline: Int = 4096,
) {
    companion object {
        const val ONSET_RMS = 0.001      // -60 dBFS: the raw phone input is quiet
        const val RELEASE_RMS = 0.00025  // -72 dBFS: tone considered ended below this
        const val ONSET_RATIO = 2.0      // hop energy jump that counts as a strike
        const val ONSET_MEMORY_HOPS = 4  // ... above the loudest of this many hops (85 ms at hop 1024: longer than C0's period)
        const val RANGE_DB = 48.0        // bell falls from full height to zero over this
        const val AUDIBLE_SNR_DB = 12.0  // a partial this far above the noise floor is audible
        const val DETECT_RUN = 6         // readings in a row that make a key the note (126 ms at hop 1024)
        const val DETECT_SETTLE_S = 1.5  // after this much of a strike the note is held
        const val PARTIALS = 12
        const val SUSTAIN_WINDOW_DB = 30.0 // a partial counts as sounding within this of the loudest
        const val DEFAULT_READING_S = 2.0  // the shown reading is the mean over at most this much of the strike
        const val JUMP_HZ = 1.0            // a reading this far from the mean starts it afresh ...
        const val JUMP_RATIO = 0.003       // ... or 5 cents, where that is more

        /** Display and reference precision: 0.1 Hz. Finer digits are noise. */
        fun roundToTenth(hz: Double): Double = kotlin.math.round(hz * 10.0) / 10.0
    }

    private var minHz = minHz
    private var maxHz = maxHz
    private val settleHops = ceil(windowSize.toDouble() / hopSize).toInt() + 1
    private val ring = FloatArray(windowSize)
    private var filled = 0
    /** Loudness of the last [ONSET_MEMORY_HOPS] hops: a strike is a rise above all of them. */
    private val recentRms = DoubleArray(ONSET_MEMORY_HOPS)
    private var recentAt = 0
    private var settle = -1 // -1: waiting for a strike
    private val estimates = ArrayDeque<Double>()
    private val partialEstimates = HashMap<Int, ArrayDeque<Double>>()

    /**
     * How long the shown reading looks back, at most: the fundamental and
     * every partial are the mean of the readings of the last this many
     * seconds of the current strike. Set from the settings.
     */
    var readingSeconds: Double = DEFAULT_READING_S
    private val readingSpan get() = maxOf(1, (readingSeconds * sampleRateHz / hopSize).toInt())
    private var peakDb = Double.NEGATIVE_INFINITY // loudest hop of the current strike
    private val tracker = PartialTracker(sampleRateHz, windowSize, PARTIALS)
    private val detector = NoteDetector(sampleRateHz, windowSize)
    private var partialPeakDb = Double.NEGATIVE_INFINITY // loudest partial of the current strike

    // per-strike statistics
    private var hopsSinceStrike = 0
    private val centsSeen = HashMap<Int, MutableList<Double>>()
    private val peakSeen = HashMap<Int, Double>()
    private val lastSounding = HashMap<Int, Int>()

    /** One partial as the hub draws it. */
    data class LivePartial(val k: Int, val hz: Double, val cents: Double, val level: Double)

    /**
     * Partials of the current strike; level 0 .. 1 relative to the strike's
     * loudest partial. A partial that has decayed below audibility keeps its
     * last reading at level 0 until the next strike, as the fundamental does
     * after the tone dies — the readout never goes blank mid-note.
     */
    var partials: List<LivePartial> = emptyList()
        private set

    /** Partials that have been audible during the current strike. */
    var audible: Set<Int> = emptySet()
        private set

    /** Current pitch of the string in Hz, or null before the first reading. */
    var hz: Double? = null
        private set

    /**
     * The key struck, over the whole compass, unmuted — by [NoteDetector],
     * the comb of partials scored for every key, named on [a4Hz]. Null
     * before a strike has been read and after the tone has died. What the
     * tuning screen switches notes on.
     *
     * Latched per strike: the first key read [DETECT_RUN] hops in a row
     * after the strike is the note, and within [DETECT_SETTLE_S] of the
     * strike a different key read as long replaces it (the attack of a
     * bass string can read an octave high before its fundamental has
     * come); after that the note is held until release or the next strike,
     * because a decaying tone reads its own upper partials as another note
     * and the tuner did not strike one.
     */
    var detectedMidi: Int? = null
        private set
    private var detectCandidate: Int? = null
    private var detectRun = 0

    /** The reference the detected key is named on; the controller keeps it current. */
    var a4Hz: Double = 440.0

    /** Bell height 0 .. 1: loudness relative to the current strike's peak. */
    var level: Double = 0.0
        private set

    /**
     * Where the fundamental is searched. Changing it clears the readings and
     * the strike's statistics, but keeps the sound already in the window: a
     * note that is sounding when the screen switches to it is read on the
     * next hop, without a second strike. Only the part of the settle still
     * owed to the last strike is waited out.
     */
    fun setRange(minHz: Double, maxHz: Double) {
        require(minHz > 0 && maxHz > minHz)
        this.minHz = minHz
        this.maxHz = maxHz
        estimates.clear(); partialEstimates.clear()
        hz = null
        partials = emptyList()
        audible = emptySet()
        partialPeakDb = Double.NEGATIVE_INFINITY
        val sinceStrike = hopsSinceStrike
        clearStrikeStats()
        hopsSinceStrike = sinceStrike
        if (settle != -1) settle = maxOf(0, settleHops - sinceStrike)
    }

    fun reset() {
        ring.fill(0f)
        filled = 0
        recentRms.fill(0.0)
        settle = -1
        estimates.clear(); partialEstimates.clear()
        hz = null
        level = 0.0
        peakDb = Double.NEGATIVE_INFINITY
        partialPeakDb = Double.NEGATIVE_INFINITY
        partials = emptyList()
        audible = emptySet()
        clearStrikeStats()
    }

    private fun clearStrikeStats() {
        hopsSinceStrike = 0
        centsSeen.clear()
        peakSeen.clear()
        lastSounding.clear()
    }

    fun push(chunk: FloatArray) {
        require(chunk.size == hopSize) { "expected $hopSize samples, got ${chunk.size}" }
        ring.copyInto(ring, 0, hopSize, windowSize)
        chunk.copyInto(ring, windowSize - hopSize)
        if (filled < windowSize) filled += hopSize

        var sq = 0.0
        for (x in chunk) sq += x.toDouble() * x
        val rms = sqrt(sq / chunk.size)
        val db = 20.0 * log10(maxOf(rms, 1e-12))

        // A strike is a rise by ONSET_RATIO above the loudest of the last few
        // hops, not above the last hop alone: a hop is 21 ms and the lowest
        // strings' periods are longer (A0 36 ms, C0 61 ms), so from hop to
        // hop their loudness swings with the phase of the cycle, and against
        // the last hop alone A0 read as a new strike every few hops and was
        // never measured — the settle after each "strike" never ran out.
        var loudest = 0.0
        for (v in recentRms) if (v > loudest) loudest = v
        val strike = rms > ONSET_RMS && rms > loudest * ONSET_RATIO
        recentRms[recentAt] = rms
        recentAt = (recentAt + 1) % ONSET_MEMORY_HOPS
        if (strike) peakDb = db else if (db > peakDb) peakDb = db
        level = if (rms < RELEASE_RMS || peakDb == Double.NEGATIVE_INFINITY) 0.0
        else (1.0 + (db - peakDb) / RANGE_DB).coerceIn(0.0, 1.0)
        if (strike) {
            settle = settleHops
            detectedMidi = null; detectCandidate = null; detectRun = 0
            estimates.clear(); partialEstimates.clear()
            partials = emptyList()
            audible = emptySet()
            partialPeakDb = Double.NEGATIVE_INFINITY
            clearStrikeStats()
            return
        }
        hopsSinceStrike++
        if (settle == -1) return
        if (settle > 0) { settle--; return }
        if (rms < RELEASE_RMS) {
            settle = -1
            partials = partials.map { it.copy(level = 0.0) }
            detectedMidi = null; detectCandidate = null; detectRun = 0
            return
        }
        if (filled < windowSize) return

        val sr = sampleRateHz.toDouble()
        // The fundamental is located in the spectrum: the strongest component
        // of the range, standing clear of the noise floor and refined between
        // bins, is what the tuner hears as the note. When the range holds no
        // such component nothing is read — YIN used to be asked instead, and
        // with only the octave above sounding its difference function has a
        // zero at twice that period too, so it reported a phantom note an
        // octave below whatever rang. The phase reading then sharpens the
        // peak to millihertz, in two stages so a coarse position half a
        // capture off cannot wrap onto the wrong side; and a phase result
        // that has moved off the peak by more than a bin was pulled by a
        // second component in the range — a neighbour still ringing — and
        // the peak is kept instead.
        tracker.spectrum(ring)
        val key = tracker.detectKey(detector, a4Hz)
        if (key != null && key == detectCandidate) detectRun++ else { detectCandidate = key; detectRun = if (key != null) 1 else 0 }
        if (key != null && detectRun >= DETECT_RUN) {
            val fresh = hopsSinceStrike * hopSize < DETECT_SETTLE_S * sampleRateHz
            if (detectedMidi == null || fresh) detectedMidi = key
        }
        // The fundamental: the strongest component in the note's range, read
        // by phase — or, where no such component stands (a wound bass
        // string's fundamental is faint), the fundamental the string's own
        // partials imply, which is how a bass note is measured anyway.
        val peak = tracker.strongestPeak(minHz, maxHz)
        val fine = if (peak != null) {
            val phased = PreciseF0.refineTwoStage(ring, sr, peak, fineHop = phaseBaseline, coarseHop = hopSize.coerceAtMost(phaseBaseline))
            if (abs(phased - peak) <= tracker.binHz) phased else peak
        } else {
            fundamentalFromPartials() ?: return
        }
        if (fine < minHz || fine > maxHz) return

        // a reading that jumps from the mean is a new thing — another string
        // taking over the range, a pin turned — not a beat to average out
        if (estimates.isNotEmpty() && abs(fine - estimates.average()) > jumpHz(fine)) { estimates.clear(); partialEstimates.clear() }
        estimates.addLast(fine)
        while (estimates.size > readingSpan) estimates.removeFirst()
        val f1 = estimates.average()
        hz = f1

        val heard = tracker.partials(f1)
            .filter { it.snrDb >= AUDIBLE_SNR_DB }
            .map { r ->
                // the peak locates the partial; the phase reads it to millihertz;
                // shown, like the fundamental, as the mean over the strike's last readings
                if (r.k == 1) r.copy(hz = f1, cents = 0.0) else {
                    val seen = partialEstimates.getOrPut(r.k) { ArrayDeque() }
                    val read = PreciseF0.refine(ring, sr, r.hz, phaseBaseline)
                    if (seen.isNotEmpty() && abs(read - seen.average()) > jumpHz(read)) seen.clear()
                    seen.addLast(read)
                    while (seen.size > readingSpan) seen.removeFirst()
                    val hz = seen.average()
                    r.copy(hz = hz, cents = 1200.0 * ln(hz / (r.k * f1)) / ln(2.0))
                }
            }
        for (r in heard) if (r.db > partialPeakDb) partialPeakDb = r.db
        audible = audible + heard.map { it.k }
        val fresh = heard.map { r ->
            LivePartial(r.k, r.hz, r.cents, (1.0 + (r.db - partialPeakDb) / RANGE_DB).coerceIn(0.0, 1.0))
        }
        val freshKs = fresh.map { it.k }.toSet()
        partialEstimates.keys.retainAll(freshKs)   // a partial gone quiet starts its mean afresh when it returns
        val held = partials.filter { it.k !in freshKs }.map { it.copy(level = 0.0) }
        partials = (fresh + held).sortedBy { it.k }
        for (r in heard) {
            centsSeen.getOrPut(r.k) { mutableListOf() }.add(r.cents)
            if (r.db > (peakSeen[r.k] ?: Double.NEGATIVE_INFINITY)) peakSeen[r.k] = r.db
            if (r.db >= partialPeakDb - SUSTAIN_WINDOW_DB) lastSounding[r.k] = hopsSinceStrike
        }
    }

    /**
     * The fundamental implied by the partials of the key detected, for a
     * string whose fundamental itself does not stand out: the partials are
     * found from the key's nominal pitch, the stiff-string model fitted to
     * them, and each partial k at hz_k puts the fundamental at
     * hz_k / (k · ratio(k, B)); the level-weighted mean of those is the
     * reading. Null when no key is detected or too few partials are heard.
     */
    /** A change larger than this between a reading and the mean it would join starts the mean afresh: 1 Hz, or 5 cents where that is more. */
    private fun jumpHz(hz: Double) = maxOf(JUMP_HZ, JUMP_RATIO * hz)

    private fun fundamentalFromPartials(): Double? {
        val midi = detectedMidi ?: return null
        val nominal = a4Hz * 2.0.pow((midi - 69) / 12.0)
        if (nominal < minHz || nominal > maxHz) return null
        val heard = tracker.partials(nominal).filter { it.k >= 2 && it.snrDb >= AUDIBLE_SNR_DB }
        if (heard.size < 2) return null
        val fit = Inharmonicity.fit(heard.map { MeasuredPartial(it.k, it.cents, it.db, 0.0) })
        var num = 0.0; var den = 0.0
        for (r in heard) {
            val w = 10.0.pow(r.db / 20.0)
            num += w * r.hz / (r.k * Inharmonicity.ratio(r.k, fit.b)); den += w
        }
        return if (den > 0) num / den else null
    }

    /**
     * The current strike condensed: median position and peak level of every
     * audible partial, how long it stayed within [SUSTAIN_WINDOW_DB] of the
     * loudest partial, and the string's inharmonicity fitted from them.
     * Null before a reading exists.
     */
    fun summary(midi: Int): NoteMeasurement? {
        if (hz == null || estimates.isEmpty()) return null
        val f1 = median(estimates)
        val secondsPerHop = hopSize.toDouble() / sampleRateHz
        val measured = centsSeen.keys.sorted().map { k ->
            MeasuredPartial(
                k = k,
                cents = median(centsSeen.getValue(k)),
                levelDb = (peakSeen[k] ?: Double.NEGATIVE_INFINITY) - partialPeakDb,
                sustainS = (lastSounding[k] ?: 0) * secondsPerHop,
            )
        }
        val fit = Inharmonicity.fit(measured)
        return NoteMeasurement(midi, f1, fit.b, fit.residualCents, measured)
    }

    private fun median(values: Collection<Double>): Double {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }
}
