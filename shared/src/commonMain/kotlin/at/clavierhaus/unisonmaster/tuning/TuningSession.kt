package at.clavierhaus.unisonmaster.tuning

import at.clavierhaus.unisonmaster.settings.OctaveType
import at.clavierhaus.unisonmaster.settings.TunerSettings
import kotlin.math.abs
import kotlin.math.exp
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

    /**
     * Targets from the string itself. A single string has one free variable,
     * its tension: once the fundamental is on target, partial k is wherever
     * this string's own ratio f_k / f1 puts it. The ratio is measured on the
     * current strike (median over its hops) and needs no model, so the
     * partial targets and the fundamental target can never disagree, and a
     * partial shows the fundamental's error magnified k times.
     */
    fun ownTargets(targetF1: Double, own: NoteMeasurement): List<PredictedPartial> =
        own.partials.map { p ->
            PredictedPartial(p.k, targetF1 * p.k * 2.0.pow(p.cents / 1200.0), p.levelDb, p.sustainS)
        }

    /** Partials of a note tuned to [targetF1] whose string behaves like [basis]. */
    fun predict(targetF1: Double, basis: NoteMeasurement, maxPartials: Int = LiveReference.PARTIALS, b: Double = basis.b): List<PredictedPartial> =
        basis.partials.filter { it.k <= maxPartials }.map { p ->
            PredictedPartial(p.k, p.k * targetF1 * ratio(p.k, b), p.levelDb, p.sustainS)
        }
}

/**
 * The tuning session: A4 first, then the octave down to A3, one single
 * string per note. Targets for the fundamental come from equal temperament
 * on the A4 reference; targets for the partials come from the nearest note
 * already measured, so each Done improves the next prediction.
 */
class TuningSession(a4Hz: Double, settings: TunerSettings = TunerSettings()) {
    /** The A4 reference. Changing it re-targets every note. */
    var a4Hz: Double = a4Hz
    /** The settings in force: temperament octave, octave types, plain-wire floor. */
    var settings: TunerSettings = settings
    /** Lowest note of the session: the instrument's lowest key (never above its lowest plain string). */
    val lowMidi: Int get() = minOf(settings.lowestKeyMidi, settings.lowestUnwoundMidi).coerceIn(TunerSettings.MIN_LOWEST_KEY, MIDI_A4 - 1)
    /** Highest note of the session: the top of the compass. */
    val highMidi: Int get() = MIDI_C8
    /**
     * The notes of the session in the order they are tuned: A4 first, then
     * down to the plain-wire floor, then up from A#4 to the top. The treble
     * follows the bass because a treble note is linked to one below it,
     * which must already be measured.
     */
    val notes: List<Int> get() = (MIDI_A4 downTo lowMidi) + ((MIDI_A4 + 1)..highMidi)

    /** The notes of the temperament octave, A4 down to its low note. */
    val temperamentNotes: List<Int>
        get() = (TunerSettings.TEMPERAMENT_HIGH downTo settings.temperamentLowMidi).toList()

    /** True once every note of the temperament octave has been measured. */
    val temperamentComplete: Boolean get() = temperamentNotes.all { it in measured }

    /** True while the session is held inside the temperament octave. */
    val gated: Boolean get() = settings.temperamentFirst && !temperamentComplete

    /**
     * True when leaving a note registers it as done with its last
     * measurement, so that the walk needs no Done: everywhere in Pro, and in
     * FOSS once the temperament octave is complete. Inside that octave FOSS
     * asks for Done on every note, because the octave is what everything
     * else is built on.
     */
    val recordsOnLeaving: Boolean get() = !gated

    /** Lowest note the arrows may reach now. */
    val stepLowMidi: Int get() = if (gated) settings.temperamentLowMidi else lowMidi

    /** Highest note the arrows may reach now. */
    val stepHighMidi: Int get() = if (gated) TunerSettings.TEMPERAMENT_HIGH else highMidi

    /** Whether [midi] may be tuned at this point in the session. */
    fun selectable(midi: Int): Boolean =
        midi in notes && (!gated || midi in temperamentNotes)

    /**
     * How a note below the temperament octave gets its target: its partial
     * [type].low must equal [viaHz], the measured partial [type].high of the
     * already tuned note [refMidi] an octave (or two) above. This is what an
     * aural tuner listens for; it needs no inharmonicity model, because the
     * reference partial was measured, not predicted.
     */
    data class OctaveLink(
        val type: OctaveType,
        val refMidi: Int,
        val viaHz: Double,
        /** The partial of the note being tuned that must land on [viaHz]. */
        val ownK: Int,
        /** True when [viaHz] comes from the calculated stretch (Pro, after Stretch Definition), not from a measured string. */
        val calculated: Boolean = false,
    )

    /**
     * The octave link for [midi], or null inside the temperament octave or
     * without a measured reference.
     *
     * Below the temperament octave the reference lies above: partial
     * [OctaveType.low] of this note must meet the measured partial
     * [OctaveType.high] of it. Above the temperament octave the same interval
     * is read the other way round — this note is the upper one, so its partial
     * [OctaveType.high] must meet the measured partial [OctaveType.low] of the
     * note below. Either way the reference partial was measured, not predicted.
     */
    fun octaveLink(midi: Int = current): OctaveLink? {
        val type = settings.octaveTypeFor(midi)
        if (calibrated) return when {
            midi < settings.temperamentLowMidi -> {
                val ref = midi + type.semitones
                OctaveLink(type, ref, type.high * stretchTargetF1(ref) * Inharmonicity.ratio(type.high, predictedB(ref)), type.low, calculated = true)
            }
            midi > TunerSettings.TEMPERAMENT_HIGH -> {
                val ref = midi - type.semitones
                OctaveLink(type, ref, type.low * stretchTargetF1(ref) * Inharmonicity.ratio(type.low, predictedB(ref)), type.high, calculated = true)
            }
            else -> null
        }
        return when {
            midi < settings.temperamentLowMidi -> {
                val ref = measured[midi + type.semitones] ?: return null
                val p = ref.partials.firstOrNull { it.k == type.high } ?: return null
                OctaveLink(type, ref.midi, type.high * ref.f1Hz * 2.0.pow(p.cents / 1200.0), type.low)
            }
            midi > TunerSettings.TEMPERAMENT_HIGH -> {
                val ref = measured[midi - type.semitones] ?: return null
                val p = ref.partials.firstOrNull { it.k == type.low } ?: return null
                OctaveLink(type, ref.midi, type.low * ref.f1Hz * 2.0.pow(p.cents / 1200.0), type.high)
            }
            else -> null
        }
    }

    /**
     * Target of the first partial. Inside the temperament octave: equal
     * temperament on A4. Below it: from the octave link and this string's own
     * ratio f_low / (low · f1) — [ownCentsLow], measured live — or, before the
     * string has sounded, the ratio predicted from the nearest measured note.
     */
    fun target(midi: Int = current, ownCentsLow: Double? = null): Double {
        // Pro after Stretch Definition: the target is the calculated stretch,
        // fixed, whatever the string does — the tuner asked for that pitch.
        if (calibrated) return stretchTargetF1(midi)
        val link = octaveLink(midi) ?: return Companion.targetF1(midi, a4Hz)
        val ratio = ownCentsLow?.let { 2.0.pow(it / 1200.0) }
            ?: Inharmonicity.ratio(link.ownK, predictedB(midi))
        return link.viaHz / (link.ownK * ratio)
    }

    // ---- Stretch Definition (Pro): the calibration that precedes tuning ----

    /**
     * Pro's second step after A4: single strings across the range until
     * [TunerSettings.calibrationNotes] anchors are in (docs/INHARMONICITY.md).
     * FOSS has no such phase — its temperament octave is its sample.
     */
    val calibrating: Boolean get() = !settings.temperamentFirst && sampled().size < settings.calibrationNotes

    /**
     * The notes Stretch Definition counts: every registered note whose
     * partial spectrum was recorded — identified and measured, nothing
     * more asked. The stricter [anchors] are what the curve is fitted on.
     */
    fun sampled(): List<NoteMeasurement> = measured.values.filter { it.partials.size >= 2 }

    /** Pro with Stretch Definition finished: targets come from the calculated stretch. */
    val calibrated: Boolean get() = !settings.temperamentFirst && !calibrating

    /**
     * The calculated stretch: the pitch of [midi]'s first partial such that
     * the octave type's partials coincide with those of the reference an
     * octave (or two) away, both strings' inharmonicity taken from the
     * fitted curve, chained from the temperament octave outward. Inside the
     * temperament octave: the temperament on A4.
     */
    fun stretchTargetF1(midi: Int): Double {
        if (midi >= settings.temperamentLowMidi && midi <= TunerSettings.TEMPERAMENT_HIGH) return Companion.targetF1(midi, a4Hz)
        val type = settings.octaveTypeFor(midi)
        return if (midi < settings.temperamentLowMidi) {
            val ref = midi + type.semitones
            val via = type.high * stretchTargetF1(ref) * Inharmonicity.ratio(type.high, predictedB(ref))
            via / (type.low * Inharmonicity.ratio(type.low, predictedB(midi)))
        } else {
            val ref = midi - type.semitones
            val via = type.low * stretchTargetF1(ref) * Inharmonicity.ratio(type.low, predictedB(ref))
            via / (type.high * Inharmonicity.ratio(type.high, predictedB(midi)))
        }
    }
    companion object {
        const val MIDI_A4 = 69
        const val MIDI_A3 = 57
        /** Top of the compass. */
        const val MIDI_C8 = 108
        /** A basis note whose fit is worse than this is skipped for prediction. */
        const val MAX_BASIS_RESIDUAL_CENTS = 1.5
        /** A recommended partial stays within 30 dB of the loudest this long. */
        const val MIN_SUSTAIN_S = 2.0
        const val MAX_LEVEL_DOWN_DB = 30.0
        /** A partial is matched when it is this close to its target, Hz (the display resolution). */
        const val MATCH_HZ = 0.1
        /** Inharmonicity is sampled up to here; above it B is extrapolated (CURVE.md §3). */
        const val CURVE_TOP_MIDI = 72             // C5
        const val CURVE_MIN_ANCHORS = 3
        const val CURVE_MIN_SPAN = 24             // two octaves
        /** The worst held-out prediction a representative curve may show, cents at partial 4. */
        const val CURVE_TOLERANCE_CENTS = 1.5

        val sequence: List<Int> = (MIDI_A4 downTo MIDI_A3).toList()

        fun targetF1(midi: Int, a4Hz: Double): Double = a4Hz * 2.0.pow((midi - MIDI_A4) / 12.0)

        fun centsOff(hz: Double, targetHz: Double): Double = 1200.0 * ln(hz / targetHz) / ln(2.0)

        /** Green: the live frequency lies within [windowHz] of its target. */
        fun matched(hz: Double?, targetHz: Double?, windowHz: Double = MATCH_HZ): Boolean =
            hz != null && targetHz != null && abs(hz - targetHz) <= windowHz + 1e-9

        /**
         * The partial that gives the finest match: the highest one that is both
         * strong (within [MAX_LEVEL_DOWN_DB] of the loudest) and long-lived
         * (at least [MIN_SUSTAIN_S]). A detuning Δ shows k times larger in Hz
         * at partial k, so higher is better — if it lasts.
         */
        fun recommend(
            vararg sources: List<MeasuredPartial>,
            maxLevelDownDb: Double = MAX_LEVEL_DOWN_DB,
            minSustainS: Double = MIN_SUSTAIN_S,
            maxK: Int = LiveReference.PARTIALS,
        ): Int? =
            sources.asList().flatten()
                .filter { it.k in 2..maxK && it.levelDb >= -maxLevelDownDb && it.sustainS >= minSustainS }
                .maxOfOrNull { it.k }

        /** Highest partial of a note at [f1Hz] that lies at or below [highestPartialHz]. */
        fun highestUsefulPartial(f1Hz: Double, highestPartialHz: Double): Int =
            (highestPartialHz / f1Hz).toInt().coerceIn(1, LiveReference.PARTIALS)
    }

    private val measured = LinkedHashMap<Int, NoteMeasurement>()

    val measurements: Map<Int, NoteMeasurement> get() = measured

    var current: Int = MIDI_A4
        private set

    /**
     * Keeps [m] as the note's measurement. A partial no plain-wire string
     * can produce — flat of its harmonic position, or sharper than
     * stiffness allows ([PartialTracker.plausibleCents]) — is another
     * string's, whatever measured it, and is dropped here: every octave link
     * reads the stored partials, so one such partial in a saved tuning made
     * the target of the note an octave below come out a semitone wrong.
     */
    fun record(m: NoteMeasurement) {
        // A fundamental nearer another key than this one is another note's
        // reading — a neighbour still ringing when the note was left — and
        // cannot stand as this note's measurement; what the note already has
        // stays. (Until the 21st it was removed as well, and one such reading
        // on leaving a note of the finished temperament octave closed the
        // octave again: arrows only, no following, on a piano tuned to A4.)
        if (Notes.nearestMidi(m.f1Hz, a4Hz) != m.midi) return
        measured[m.midi] = m.copy(partials = m.partials.filter { it.k == 1 || PartialTracker.plausibleCents(it.k, it.cents) })
    }

    fun select(midi: Int) {
        require(midi in notes) { "note $midi is outside the session" }
        require(selectable(midi)) { "note $midi is outside the temperament octave, which is not finished" }
        current = midi
    }

    /** Next note down that has no measurement yet, or null when the octave is done. */
    fun nextUnmeasured(): Int? = notes.firstOrNull { it !in measured }

    /** One semitone down from the current note, or null below the session's foot. */
    fun below(): Int? = (current - 1).takeIf { it >= lowMidi }

    /**
     * The note Done moves to: the next one in the tuning order, so the walk
     * runs A4 down to the floor and then up from A#4 to the top. Null at the
     * end of the compass.
     */
    fun next(): Int? {
        // While the temperament octave is unfinished, Done goes back to the
        // first note of it that has no measurement, rather than walking on.
        if (gated) return notes.firstOrNull { it in temperamentNotes && it !in measured }
        return notes.getOrNull(notes.indexOf(current) + 1)
    }

    /**
     * The note [delta] semitones away, kept within the compass. A4 is a
     * note like any other here: its reference was set on the hub, and it
     * can be returned to — a pin settles — without changing that reference.
     */
    fun stepped(delta: Int): Int = (current + delta).coerceIn(stepLowMidi, stepHighMidi)

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
        return Inharmonicity.predict(target(midi), basis, b = predictedB(midi))
    }

    /**
     * The inharmonicity expected of [midi] before its string has sounded:
     * from the fitted curve once it is representative, otherwise from the
     * nearest measured note. Only ever a proposal — a string's own partials
     * replace it the moment they are heard.
     */
    fun predictedB(midi: Int = current): Double {
        // a wound string's inharmonicity is its own physics: the nearest
        // measured wound string stands for it, never the plain-wire curve
        if (settings.isWound(midi)) {
            val wound = measured.values.filter { settings.isWound(it.midi) && it.midi != midi && it.b > 0.0 }
            wound.minByOrNull { abs(it.midi - midi) }?.let { return it.b }
        }
        val report = curve()
        if (report.representative || (calibrated && report.anchors >= 2)) {
            val (slope, intercept) = fitLogB(anchors())
            return exp(intercept + slope * midi)
        }
        return basisFor(midi)?.b ?: 0.0
    }

    /**
     * How well the notes sampled so far stand for the instrument's
     * inharmonicity (docs/CURVE.md §4, docs/INHARMONICITY.md). The anchors
     * are the measured plain-wire notes up to C5 whose partials fit the
     * stiff-string model; log B is fitted against note number, linear, and
     * each anchor is held out in turn and predicted from the rest. The worst
     * of those predictions, as cents at partial 4 — the partial an octave
     * link reads — is the report's error. Representative when at least
     * [CURVE_MIN_ANCHORS] anchors span at least [CURVE_MIN_SPAN] semitones and
     * the worst held-out prediction is within [CURVE_TOLERANCE_CENTS].
     */
    /** The measured notes that qualify as anchors of the inharmonicity curve, lowest first. */
    fun anchors(): List<NoteMeasurement> = measured.values
        .filter { !settings.isWound(it.midi) && it.midi <= CURVE_TOP_MIDI && it.b > 0.0 && it.partials.size >= 3 && it.residualCents <= MAX_BASIS_RESIDUAL_CENTS }
        .sortedBy { it.midi }

    fun curve(): CurveReport {
        val anchors = anchors()
        val n = anchors.size
        if (n == 0) return CurveReport(0, null, null, false)
        val span = anchors.last().midi - anchors.first().midi
        if (n < CURVE_MIN_ANCHORS) return CurveReport(n, span, null, false)
        var worst = 0.0
        for (held in anchors) {
            val rest = anchors.filter { it !== held }
            val (slope, intercept) = fitLogB(rest)
            val predictedB = exp(intercept + slope * held.midi)
            val err = abs(Inharmonicity.centsOf(4, predictedB) - Inharmonicity.centsOf(4, held.b))
            if (err > worst) worst = err
        }
        val ok = span >= CURVE_MIN_SPAN && worst <= CURVE_TOLERANCE_CENTS
        return CurveReport(n, span, worst, ok)
    }

    /** Least squares of ln B against note number: (slope, intercept). */
    private fun fitLogB(notes: List<NoteMeasurement>): Pair<Double, Double> {
        val xs = notes.map { it.midi.toDouble() }
        val ys = notes.map { ln(it.b) }
        val mx = xs.average(); val my = ys.average()
        var sxx = 0.0; var sxy = 0.0
        for (i in xs.indices) { sxx += (xs[i] - mx) * (xs[i] - mx); sxy += (xs[i] - mx) * (ys[i] - my) }
        val slope = if (sxx > 0) sxy / sxx else 0.0
        return slope to (my - slope * mx)
    }
}

/**
 * The state of the inharmonicity sampling: how many anchors, how far apart
 * the lowest and highest are (semitones), the worst held-out prediction
 * (cents at partial 4; null below three anchors), and whether that is
 * enough to call the curve representative.
 */
data class CurveReport(
    val anchors: Int,
    val spanSemitones: Int?,
    val worstCents: Double?,
    val representative: Boolean,
)
