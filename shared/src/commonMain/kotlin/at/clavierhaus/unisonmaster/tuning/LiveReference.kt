package at.clavierhaus.unisonmaster.tuning

import at.clavierhaus.unisonmaster.dsp.PreciseF0
import at.clavierhaus.unisonmaster.dsp.Yin
import kotlin.math.ceil
import kotlin.math.log10
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
 *  - Each hop: YIN coarse, phase-refined fine estimate, searched within the
 *    range set by [setRange]. The value shown is the median of the last
 *    [recent] estimates — steady, yet it follows a turning pin.
 *  - [level] is the bell height: loudness relative to the strike's own peak,
 *    in dB over [RANGE_DB]. Every strike reaches full height whatever the
 *    microphone distance, and the bell sinks as the note decays.
 *  - When the tone dies, the last value is held; [level] falls to zero.
 *  - Each reading also measures partials 1..12 ([partials]); a partial that
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
    private val recent: Int = 5,
) {
    companion object {
        const val ONSET_RMS = 0.001      // -60 dBFS: the raw phone input is quiet
        const val RELEASE_RMS = 0.00025  // -72 dBFS: tone considered ended below this
        const val ONSET_RATIO = 2.0      // hop energy jump that counts as a strike
        const val RANGE_DB = 48.0        // bell falls from full height to zero over this
        const val AUDIBLE_SNR_DB = 12.0  // a partial this far above the noise floor is audible
        const val PARTIALS = 12
        const val SUSTAIN_WINDOW_DB = 30.0 // a partial counts as sounding within this of the loudest

        /** Display and reference precision: 0.1 Hz. Finer digits are noise. */
        fun roundToTenth(hz: Double): Double = kotlin.math.round(hz * 10.0) / 10.0
    }

    private var minHz = minHz
    private var maxHz = maxHz
    private val settleHops = ceil(windowSize.toDouble() / hopSize).toInt() + 1
    private val ring = FloatArray(windowSize)
    private var filled = 0
    private var prevRms = 0.0
    private var settle = -1 // -1: waiting for a strike
    private val estimates = ArrayDeque<Double>()
    private var peakDb = Double.NEGATIVE_INFINITY // loudest hop of the current strike
    private val tracker = PartialTracker(sampleRateHz, windowSize, PARTIALS)
    private var partialPeakDb = Double.NEGATIVE_INFINITY // loudest partial of the current strike

    // per-strike statistics
    private var hopsSinceStrike = 0
    private val centsSeen = HashMap<Int, MutableList<Double>>()
    private val peakSeen = HashMap<Int, Double>()
    private val lastSounding = HashMap<Int, Int>()

    /** One partial as the hub draws it. */
    data class LivePartial(val k: Int, val hz: Double, val cents: Double, val level: Double)

    /** Partials of the current reading; level 0 .. 1 relative to the strike's loudest partial. */
    var partials: List<LivePartial> = emptyList()
        private set

    /** Partials that have been audible during the current strike. */
    var audible: Set<Int> = emptySet()
        private set

    /** Current pitch of the string in Hz, or null before the first reading. */
    var hz: Double? = null
        private set

    /** Bell height 0 .. 1: loudness relative to the current strike's peak. */
    var level: Double = 0.0
        private set

    /** Where the fundamental is searched. Changing it starts afresh. */
    fun setRange(minHz: Double, maxHz: Double) {
        require(minHz > 0 && maxHz > minHz)
        this.minHz = minHz
        this.maxHz = maxHz
        reset()
    }

    fun reset() {
        ring.fill(0f)
        filled = 0
        prevRms = 0.0
        settle = -1
        estimates.clear()
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

        val strike = rms > ONSET_RMS && rms > prevRms * ONSET_RATIO
        prevRms = rms
        if (strike) peakDb = db else if (db > peakDb) peakDb = db
        level = if (rms < RELEASE_RMS || peakDb == Double.NEGATIVE_INFINITY) 0.0
        else (1.0 + (db - peakDb) / RANGE_DB).coerceIn(0.0, 1.0)
        if (strike) {
            settle = settleHops
            estimates.clear()
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
            return
        }
        if (filled < windowSize) return

        val sr = sampleRateHz.toDouble()
        val coarse = Yin.estimateF0(ring, sr, minHz = minHz, maxHz = maxHz) ?: return
        val fine = PreciseF0.refine(ring, sr, coarse, hopSize)
        if (fine < minHz || fine > maxHz) return

        estimates.addLast(fine)
        while (estimates.size > recent) estimates.removeFirst()
        val f1 = median(estimates)
        hz = f1

        val readings = tracker.analyse(ring, f1)
        val heard = readings.filter { it.snrDb >= AUDIBLE_SNR_DB }
        for (r in heard) if (r.db > partialPeakDb) partialPeakDb = r.db
        audible = audible + heard.map { it.k }
        partials = heard.map { r ->
            LivePartial(r.k, r.hz, r.cents, (1.0 + (r.db - partialPeakDb) / RANGE_DB).coerceIn(0.0, 1.0))
        }
        for (r in heard) {
            centsSeen.getOrPut(r.k) { mutableListOf() }.add(r.cents)
            if (r.db > (peakSeen[r.k] ?: Double.NEGATIVE_INFINITY)) peakSeen[r.k] = r.db
            if (r.db >= partialPeakDb - SUSTAIN_WINDOW_DB) lastSounding[r.k] = hopsSinceStrike
        }
    }

    /**
     * The current strike condensed: median position and peak level of every
     * audible partial, how long it stayed within [SUSTAIN_WINDOW_DB] of the
     * loudest partial, and the string's inharmonicity fitted from them.
     * Null before a reading exists.
     */
    fun summary(midi: Int): NoteMeasurement? {
        val f1 = hz ?: return null
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
