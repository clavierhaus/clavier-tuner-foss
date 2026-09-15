package at.clavierhaus.unisonmaster.tuning

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/** One partial of a finished note. Level is relative to the note's loudest partial. */
data class MeasuredPartial(
    val k: Int,
    /** Deviation from k × f1, cents (median over the strike). */
    val cents: Double,
    /** Peak level relative to the loudest partial, dB (0 = loudest). */
    val levelDb: Double,
    /** Seconds after the strike during which the partial stayed within 30 dB of the loudest. */
    val sustainS: Double,
)

/** Everything kept about one tuned note: the foundation for predicting its neighbours. */
data class NoteMeasurement(
    val midi: Int,
    /** Sounding first partial, Hz. */
    val f1Hz: Double,
    /** Inharmonicity coefficient of the string. */
    val b: Double,
    /** RMS deviation of the measured partials from the stiff-string model, cents. */
    val residualCents: Double,
    val partials: List<MeasuredPartial>,
    /** Wall-clock time of the measurement, ms (0 if unknown). */
    val timeMs: Long = 0L,
)

/** Where a partial of a note about to be tuned should sit, and what to expect of it. */
data class PredictedPartial(
    val k: Int,
    /** Target frequency, Hz. */
    val hz: Double,
    /** Level relative to the loudest partial, dB, as heard on the basis note. */
    val levelDb: Double,
    /** Sustain, s, as heard on the basis note. */
    val sustainS: Double,
)

/**
 * The stiff-string model: f_k = k · f0 · √(1 + B·k²). Measured against
 * k × f1 (f1 being the sounding first partial) partial k lies
 * c_k = 600/ln2 · ln((1 + B·k²)/(1 + B)) cents sharp.
 */
object Inharmonicity {
    private val CENTS_PER_LN = 600.0 / ln(2.0)

    data class Fit(val b: Double, val residualCents: Double, val count: Int)

    fun centsOf(k: Int, b: Double): Double = CENTS_PER_LN * ln((1 + b * k * k) / (1 + b))

    /** Ratio f_k / (k × f1). */
    fun ratio(k: Int, b: Double): Double = sqrt((1 + b * k * k) / (1 + b))

    /**
     * Least-squares B from partials 2.. (partial 1 carries no information),
     * weighted by level: a partial 20 dB down counts a tenth as much.
     */
    fun fit(partials: List<MeasuredPartial>): Fit {
        val use = partials.filter { it.k >= 2 && it.levelDb.isFinite() }
        if (use.isEmpty()) return Fit(0.0, 0.0, 0)
        val w = use.map { 10.0.pow(it.levelDb / 20.0) }
        // linear start: c ≈ CENTS_PER_LN · B · (k² − 1)
        var num = 0.0; var den = 0.0
        for ((i, p) in use.withIndex()) {
            val x = CENTS_PER_LN * (p.k * p.k - 1)
            num += w[i] * x * p.cents; den += w[i] * x * x
        }
        var b = (num / den).coerceIn(0.0, 0.05)
        // Gauss-Newton on the exact model
        repeat(6) {
            var g = 0.0; var h = 0.0
            for ((i, p) in use.withIndex()) {
                val k2 = (p.k * p.k).toDouble()
                val r = p.cents - centsOf(p.k, b)
                val d = CENTS_PER_LN * (k2 / (1 + b * k2) - 1 / (1 + b))
                g += w[i] * d * r; h += w[i] * d * d
            }
            if (h > 0) b = (b + g / h).coerceIn(0.0, 0.05)
        }
        var ss = 0.0; var ws = 0.0
        for ((i, p) in use.withIndex()) {
            val r = p.cents - centsOf(p.k, b)
            ss += w[i] * r * r; ws += w[i]
        }
        return Fit(b, sqrt(ss / ws), use.size)
    }

    /** Partials of a note tuned to [targetF1] whose string behaves like [basis]. */
    fun predict(targetF1: Double, basis: NoteMeasurement, maxPartials: Int = LiveReference.PARTIALS): List<PredictedPartial> =
        basis.partials.filter { it.k <= maxPartials }.map { p ->
            PredictedPartial(p.k, p.k * targetF1 * ratio(p.k, basis.b), p.levelDb, p.sustainS)
        }
}

/**
 * The tuning session: A4 first, then the octave down to A3, one single
 * string per note. Targets for the fundamental come from equal temperament
 * on the A4 reference; targets for the partials come from the nearest note
 * already measured, so each Done improves the next prediction.
 */
class TuningSession(val a4Hz: Double) {
    companion object {
        const val MIDI_A4 = 69
        const val MIDI_A3 = 57
        /** A basis note whose fit is worse than this is skipped for prediction. */
        const val MAX_BASIS_RESIDUAL_CENTS = 1.5
        /** A recommended partial stays within 30 dB of the loudest this long. */
        const val MIN_SUSTAIN_S = 2.0
        const val MAX_LEVEL_DOWN_DB = 30.0
        /** A partial is matched when it is this close to its target, Hz (the display resolution). */
        const val MATCH_HZ = 0.1

        val sequence: List<Int> = (MIDI_A4 downTo MIDI_A3).toList()

        fun targetF1(midi: Int, a4Hz: Double): Double = a4Hz * 2.0.pow((midi - MIDI_A4) / 12.0)

        fun centsOff(hz: Double, targetHz: Double): Double = 1200.0 * ln(hz / targetHz) / ln(2.0)

        /** Green: the live frequency lies within [MATCH_HZ] of its target. */
        fun matched(hz: Double?, targetHz: Double?): Boolean =
            hz != null && targetHz != null && abs(hz - targetHz) <= MATCH_HZ + 1e-9

        /**
         * The partial that gives the finest match: the highest one that is both
         * strong (within [MAX_LEVEL_DOWN_DB] of the loudest) and long-lived
         * (at least [MIN_SUSTAIN_S]). A detuning Δ shows k times larger in Hz
         * at partial k, so higher is better — if it lasts.
         */
        fun recommend(vararg sources: List<MeasuredPartial>): Int? =
            sources.asList().flatten()
                .filter { it.k >= 2 && it.levelDb >= -MAX_LEVEL_DOWN_DB && it.sustainS >= MIN_SUSTAIN_S }
                .maxOfOrNull { it.k }
    }

    private val measured = LinkedHashMap<Int, NoteMeasurement>()

    val measurements: Map<Int, NoteMeasurement> get() = measured

    var current: Int = MIDI_A4
        private set

    fun record(m: NoteMeasurement) {
        measured[m.midi] = m
    }

    fun select(midi: Int) {
        require(midi in sequence) { "note $midi is outside the session" }
        current = midi
    }

    /** Next note down that has no measurement yet, or null when the octave is done. */
    fun nextUnmeasured(): Int? = sequence.firstOrNull { it !in measured }

    /** One semitone down from the current note, or null below A3. */
    fun below(): Int? = (current - 1).takeIf { it >= MIDI_A3 }

    /** The note [delta] semitones away, kept within G#4..A3 (A4 is the reference). */
    fun stepped(delta: Int): Int = (current + delta).coerceIn(MIDI_A3, MIDI_A4 - 1)

    fun targetF1(midi: Int = current): Double = targetF1(midi, a4Hz)

    /**
     * The note whose measurement predicts [midi]: the nearest measured note
     * (ties go to the higher one), skipping notes whose fit was poor; if every
     * candidate is poor, the nearest one after all.
     */
    fun basisFor(midi: Int = current): NoteMeasurement? {
        val candidates = measured.values
            .filter { it.midi != midi }
            .sortedWith(compareBy<NoteMeasurement>({ abs(it.midi - midi) }, { -it.midi }))
        return candidates.firstOrNull { it.residualCents <= MAX_BASIS_RESIDUAL_CENTS && it.partials.size >= 3 }
            ?: candidates.firstOrNull()
    }

    fun predictedPartials(midi: Int = current): List<PredictedPartial> {
        val basis = basisFor(midi) ?: return emptyList()
        return Inharmonicity.predict(targetF1(midi), basis)
    }
}
