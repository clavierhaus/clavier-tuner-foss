package at.clavierhaus.unisonmaster.tuning

import at.clavierhaus.unisonmaster.measure.PartialMap
import at.clavierhaus.unisonmaster.measure.Partials
import at.clavierhaus.unisonmaster.measure.StringMeasure
import at.clavierhaus.unisonmaster.settings.OctaveType
import at.clavierhaus.unisonmaster.settings.TunerSettings
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/** One partial of a finished note. Level is relative to the note's loudest partial. */
data class MeasuredPartial(
    val k: Int,
    /** Deviation from k × f1, cents. */
    val cents: Double,
    /** Peak level relative to the loudest partial, dB (0 = loudest). */
    val levelDb: Double,
    /** Kept for saved tunings of earlier versions; not measured any more. */
    val sustainS: Double,
)

/** Everything kept about one tuned note: what the notes after it are tuned against. */
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
) {
    /** The measured frequency of partial [k], or null when it was not measured. */
    fun partialHz(k: Int): Double? =
        partials.firstOrNull { it.k == k }?.let { k * f1Hz * 2.0.pow(it.cents / 1200.0) }
}

/** Where a partial of the note being tuned should sit. */
data class PredictedPartial(
    val k: Int,
    /** Target frequency, Hz. */
    val hz: Double,
    /** Level relative to the loudest partial, dB, as heard on the nearest measured note (0 when unknown). */
    val levelDb: Double = 0.0,
    val sustainS: Double = 0.0,
)

/**
 * The stiff-string model: f_k = k · f0 · √(1 + B·k²). Measured against
 * k × f1 (f1 being the sounding first partial) partial k lies
 * c_k = 600/ln2 · ln((1 + B·k²)/(1 + B)) cents sharp. Used only where
 * nothing was measured: to place partials of a note not yet heard.
 */
object Inharmonicity {
    private val CENTS_PER_LN = 600.0 / ln(2.0)

    fun centsOf(k: Int, b: Double): Double = CENTS_PER_LN * ln((1 + b * k * k) / (1 + b))

    /** Ratio f_k / (k × f1). */
    fun ratio(k: Int, b: Double): Double = sqrt((1 + b * k * k) / (1 + b))
}

/**
 * The tuning session (docs/ENGINE.md). A4 is set on the hub; then the
 * strings are **sampled** — single strings across the compass, each
 * measured for its inharmonicity — and the instrument's curve is fitted
 * ([InharmonicityCurve]); then every note's target is computed from the
 * curve: the temperament octave to equal temperament on A4, every other
 * note by an octave of the region's type and width to the modelled partial
 * of its partner, chained outward. A partner already tuned is the check
 * on that target, shown beside it, never the source.
 *
 * For every note the session says which partial is listened to and where it
 * must sit ([listening]):
 *
 * - **A4** — the first partial, at the reference.
 * - **The temperament octave** — the first partial, at equal temperament.
 * - **Below it** — the note is the lower of an octave of the region's type
 *   (6:3 in the bass, 4:2 in the middle, …): its partial [OctaveType.low]
 *   must meet the *measured* partial [OctaveType.high] of the note
 *   [OctaveType.semitones] above, lowered by the region's octave width.
 * - **Above it** — the note is the upper one: its partial [OctaveType.high]
 *   meets the measured partial [OctaveType.low] of the note below, raised
 *   by the width.
 * - **A partner not tuned yet** — the note is reachable all the same: it
 *   gets equal temperament, read on the partial the register listens to
 *   ([PartialMap]), placed with the inharmonicity of the nearest measured
 *   string, and says so.
 *
 * Done measures the string ([StringMeasure]) and keeps it: its partials are
 * what the notes linked to it are tuned against.
 */
class TuningSession(a4Hz: Double, settings: TunerSettings = TunerSettings(), sampling: Boolean = true) {
    /** The A4 reference. Changing it re-targets every note. */
    var a4Hz: Double = a4Hz
    /** The settings in force: temperament octave, octave types and widths, plain-wire floor. */
    var settings: TunerSettings = settings
    /** Lowest note of the session: the instrument's lowest key. */
    val lowMidi: Int get() = minOf(settings.lowestKeyMidi, settings.lowestUnwoundMidi).coerceIn(TunerSettings.MIN_LOWEST_KEY, MIDI_A4 - 1)
    /** Highest note of the session: the top of the compass. */
    val highMidi: Int get() = MIDI_C8
    /**
     * The notes of the session in the order they are tuned: A4 first, then
     * down to the lowest key, then up from A#4 to the top. The treble
     * follows the bass because a treble note is linked to one below it,
     * which must already be measured.
     */
    val notes: List<Int> get() = (MIDI_A4 downTo lowMidi) + ((MIDI_A4 + 1)..highMidi)

    /** The notes of the temperament octave, A4 down to its low note. */
    val temperamentNotes: List<Int>
        get() = (TunerSettings.TEMPERAMENT_HIGH downTo settings.temperamentLowMidi).toList()

    /** True once every note of the temperament octave has been measured. */
    val temperamentComplete: Boolean get() = temperamentNotes.all { it in measured }

    /** True while the session walks the temperament octave first (FOSS, until it is complete). */
    val gated: Boolean get() = !sampling && settings.temperamentFirst && !temperamentComplete

    // ---- Sampling ----

    /**
     * True while the strings are being sampled. FOSS samples exactly
     * [TunerSettings.sampleNotes] and finishes by itself; Pro proposes them,
     * takes any note, and finishes on [finishSampling].
     */
    var sampling: Boolean = sampling
        private set

    /** The notes proposed for sampling, lowest first. */
    val sampleNotes: List<Int> get() = settings.sampleNotes.filter { it in notes }

    /** The proposed notes not sampled yet. */
    val samplesLeft: List<Int> get() = sampleNotes.filter { it !in measured }

    /** The next note to sample above [midi], wrapping to the lowest; null when all are in. */
    fun nextSample(after: Int = current): Int? =
        if (after in sampleNotes) samplesLeft.firstOrNull { it > after } ?: samplesLeft.firstOrNull() else samplesLeft.firstOrNull()

    /** Ends the sampling: Pro when the tuner says so, FOSS when the set is complete. */
    fun finishSampling() {
        if (!sampling) return
        sampling = false
        select(next() ?: notes.first { it != MIDI_A4 })
    }

    /** True when the FOSS set is complete and sampling ends by itself. */
    fun sampleSetComplete(): Boolean = samplesLeft.isEmpty()

    /** The instrument's inharmonicity curve from what has been measured. */
    val curve: InharmonicityCurve
        get() = InharmonicityCurve(measured.mapValues { it.value.b }, settings.lowestUnwoundMidi, settings.curveBreaks)

    /**
     * True when leaving a note registers it as done with what was heard of
     * it, so that the walk needs no Done: everywhere in Pro, and in FOSS
     * once the temperament octave is complete. Inside that octave FOSS asks
     * for Done on every note, because the octave is what everything else is
     * built on.
     */
    val recordsOnLeaving: Boolean get() = !gated && !sampling

    /** Lowest note the arrows may reach: the foot of the session, always. */
    val stepLowMidi: Int get() = lowMidi

    /** Highest note the arrows may reach: the top of the compass, always. */
    val stepHighMidi: Int get() = highMidi

    /**
     * Whether [midi] may be tuned now: any note of the session, at any time.
     * The temperament octave is the *suggested* order — [next] walks it
     * first — but nothing stops the tuner going elsewhere and back.
     */
    fun selectable(midi: Int): Boolean = midi in notes

    /** Where a note's target comes from. */
    enum class Source {
        /** A4: the reference set on the hub. */
        REFERENCE,
        /** The temperament octave: equal temperament on A4. */
        TEMPERAMENT,
        /** An octave of the region's type and width, computed on the curve from the samples. */
        CURVE,
        /** No curve yet (nothing sampled): an octave to a measured note. */
        OCTAVE,
        /** No curve and the partner not tuned: equal temperament for now. */
        PARTNER_UNTUNED,
    }

    /**
     * What is listened to on [midi]: its partial [k], which must sit at
     * [targetHz]. [source] says why; for an octave, [type] and [refMidi] name
     * it, [refPartialHz] is the partner's measured partial and [widthCents]
     * the width the tuner set for the region. [refModelled]: the partner was
     * measured but that partial of it was not found, so its place follows
     * from the partner's own fit.
     */
    data class Listening(
        val midi: Int,
        val k: Int,
        val targetHz: Double,
        val source: Source,
        val type: OctaveType? = null,
        val refMidi: Int? = null,
        val refPartialHz: Double? = null,
        val widthCents: Double = 0.0,
        val refModelled: Boolean = false,
        /**
         * The check: for a CURVE target whose partner is measured, how far
         * the measured partner's partial stands from where the curve puts it,
         * cents (+ = the partner stands wider than the curve). Null without.
         */
        val checkCents: Double? = null,
    )

    /** The octave partner of [midi] and the partials that meet: (partner, own k, partner's k). Null inside the temperament octave. */
    fun partner(midi: Int): Triple<Int, Int, Int>? {
        val type = settings.octaveTypeFor(midi)
        return when {
            midi < settings.temperamentLowMidi -> Triple(midi + type.semitones, type.low, type.high)
            midi > TunerSettings.TEMPERAMENT_HIGH -> Triple(midi - type.semitones, type.high, type.low)
            else -> null
        }
    }

    /** What is listened to on [midi], and where it must sit. */
    fun listening(midi: Int = current): Listening {
        if (midi == MIDI_A4) return Listening(midi, 1, a4Hz, Source.REFERENCE)
        val p = partner(midi) ?: return Listening(midi, 1, targetF1(midi, a4Hz), Source.TEMPERAMENT)
        val (refMidi, ownK, refK) = p
        val type = settings.octaveTypeFor(midi)
        val c = curve
        if (c.ready) {
            val f1 = curveTargetF1(midi, c)
            val hz = ownK * f1 * Inharmonicity.ratio(ownK, bOf(midi, c))
            val width = settings.widthFor(midi)
            val sign = if (midi < refMidi) -1.0 else 1.0
            // the check: the partner as measured against the partner as the curve has it
            val check = measured[refMidi]?.partialHz(refK)?.let { measuredRef ->
                val modelledRef = refK * curveTargetF1(refMidi, c) * Inharmonicity.ratio(refK, bOf(refMidi, c))
                sign * centsOff(measuredRef, modelledRef)
            }
            return Listening(midi, ownK, hz, Source.CURVE, type, refMidi, null, width, checkCents = check)
        }
        val ref = measured[refMidi]
        if (ref == null) {
            val k = PartialMap.listening(midi)
            val hz = k * targetF1(midi, a4Hz) * Inharmonicity.ratio(k, predictedB(midi))
            return Listening(midi, k, hz, Source.PARTNER_UNTUNED, type, refMidi)
        }
        val measuredHz = ref.partialHz(refK)
        val refHz = measuredHz ?: (refK * ref.f1Hz * Inharmonicity.ratio(refK, ref.b))
        val width = settings.widthFor(midi)
        // wide: the lower note flatter, the upper sharper
        val sign = if (midi < refMidi) -1.0 else 1.0
        val hz = refHz * 2.0.pow(sign * width / 1200.0)
        return Listening(midi, ownK, hz, Source.OCTAVE, type, refMidi, refHz, width, refModelled = measuredHz == null)
    }

    /** B for [midi]: its own measurement, else the curve's. */
    private fun bOf(midi: Int, c: InharmonicityCurve): Double = measured[midi]?.b?.takeIf { it > 0 } ?: c.b(midi) ?: StringMeasure.DEFAULT_B

    private val curveCache = HashMap<Int, Double>()
    private var curveCacheKey: Any? = null

    /**
     * The computed stretch: the first partial of [midi] such that its
     * octave of the region's type, widened by the region's width, meets the
     * modelled partial of its partner, chained outward from the temperament
     * octave (equal temperament on A4). The strings' inharmonicity is the
     * curve's, or their own where measured.
     */
    fun curveTargetF1(midi: Int, c: InharmonicityCurve = curve): Double {
        val key = Triple(a4Hz, settings, measured.size to measured.values.sumOf { it.b })
        if (key != curveCacheKey) { curveCache.clear(); curveCacheKey = key }
        curveCache[midi]?.let { return it }
        val v = if (midi >= settings.temperamentLowMidi && midi <= TunerSettings.TEMPERAMENT_HIGH) targetF1(midi, a4Hz) else {
            val (refMidi, ownK, refK) = partner(midi)!!
            val refHz = refK * curveTargetF1(refMidi, c) * Inharmonicity.ratio(refK, bOf(refMidi, c))
            val sign = if (midi < refMidi) -1.0 else 1.0
            val target = refHz * 2.0.pow(sign * settings.widthFor(midi) / 1200.0)
            target / (ownK * Inharmonicity.ratio(ownK, bOf(midi, c)))
        }
        curveCache[midi] = v
        return v
    }

    /**
     * The first partial [midi] will have when its listened partial is on
     * target, the string's inharmonicity taken as [b] (by default the
     * nearest measured string's). For the screen and the other partials'
     * places; the target itself is [listening]'s.
     */
    fun targetF1Of(midi: Int = current, b: Double = predictedB(midi)): Double {
        val l = listening(midi)
        return l.targetHz / (l.k * Inharmonicity.ratio(l.k, b))
    }

    /** Where partials 1..[maxK] of [midi] should sit when it is on target. */
    fun predictedPartials(midi: Int = current, maxK: Int = Partials.MAX): List<PredictedPartial> {
        val b = predictedB(midi)
        val f1 = targetF1Of(midi, b)
        val basis = basisFor(midi)
        return (1..maxK).map { k ->
            PredictedPartial(k, k * f1 * Inharmonicity.ratio(k, b), basis?.partials?.firstOrNull { it.k == k }?.levelDb ?: 0.0)
        }
    }

    companion object {
        const val MIDI_A4 = 69
        /** Top of the compass. */
        const val MIDI_C8 = 108
        /** A partial is matched when it is this close to its target, Hz (the display resolution). */
        const val MATCH_HZ = 0.1

        fun targetF1(midi: Int, a4Hz: Double): Double = a4Hz * 2.0.pow((midi - MIDI_A4) / 12.0)

        fun centsOff(hz: Double, targetHz: Double): Double = 1200.0 * ln(hz / targetHz) / ln(2.0)

        /** Green: the live frequency lies within [windowHz] of its target. */
        fun matched(hz: Double?, targetHz: Double?, windowHz: Double = MATCH_HZ): Boolean =
            hz != null && targetHz != null && abs(hz - targetHz) <= windowHz + 1e-9

        /** Highest partial of a note at [f1Hz] that lies at or below [highestPartialHz]. */
        fun highestUsefulPartial(f1Hz: Double, highestPartialHz: Double): Int =
            (highestPartialHz / f1Hz).toInt().coerceIn(1, Partials.MAX)

        /** A reference pitch to the display's resolution, 0.1 Hz. */
        fun roundToTenth(hz: Double): Double = kotlin.math.round(hz * 10.0) / 10.0
    }

    private val measured = LinkedHashMap<Int, NoteMeasurement>()

    val measurements: Map<Int, NoteMeasurement> get() = measured

    var current: Int = MIDI_A4
        private set

    /**
     * Keeps [m] as the note's measurement. A partial no plain-wire string
     * can produce — flat of its harmonic position, or sharper than
     * stiffness allows ([Partials.plausibleCents]) — is another string's
     * and is dropped: every octave link reads the stored partials. A
     * measurement whose fundamental lies nearer another key is another
     * note's, and what the note already has stays.
     */
    fun record(m: NoteMeasurement) {
        if (Notes.nearestMidi(m.f1Hz, a4Hz) != m.midi) return
        measured[m.midi] = m.copy(partials = m.partials.filter { it.k == 1 || Partials.plausibleCents(it.k, it.cents) })
    }

    fun select(midi: Int) {
        require(midi in notes) { "note $midi is outside the session" }
        current = midi
    }

    /** The first note of the walk that has no measurement yet, or null when every note has one. */
    fun nextUnmeasured(): Int? = notes.firstOrNull { it !in measured }

    /**
     * The note Done moves to: the next one in the tuning order, so the walk
     * runs A4 down to the floor and then up from A#4 to the top. While the
     * temperament octave is walked first, the first note of it still
     * without a measurement. Null at the end of the compass.
     */
    fun next(): Int? {
        if (sampling) return nextSample()
        if (gated) return notes.firstOrNull { it in temperamentNotes && it !in measured }
        return notes.getOrNull(notes.indexOf(current) + 1)
    }

    /** The note [delta] semitones away, kept within the compass. */
    fun stepped(delta: Int): Int = (current + delta).coerceIn(stepLowMidi, stepHighMidi)

    /**
     * The measured note nearest [midi] (ties to the higher), of the same
     * kind of wire — wound or plain — where there is one: a wound string's
     * stiffness is its own physics.
     */
    fun basisFor(midi: Int = current): NoteMeasurement? {
        val others = measured.values.filter { it.midi != midi }
        val sameWire = others.filter { settings.isWound(it.midi) == settings.isWound(midi) }
        return (sameWire.ifEmpty { others })
            .minWithOrNull(compareBy<NoteMeasurement>({ abs(it.midi - midi) }, { -it.midi }))
    }

    /**
     * The inharmonicity expected of [midi] before its string is measured:
     * the note's own when it has been, else the nearest measured string's
     * ([basisFor]), else a typical value. Only ever used to place a partial
     * that was not measured.
     */
    fun predictedB(midi: Int = current): Double {
        measured[midi]?.b?.takeIf { it > 0.0 }?.let { return it }
        val c = curve
        if (c.ready) c.b(midi)?.let { return it }
        val withB = measured.values.filter { it.midi != midi && it.b > 0.0 }
        val sameWire = withB.filter { settings.isWound(it.midi) == settings.isWound(midi) }
        return (sameWire.ifEmpty { withB }).minByOrNull { abs(it.midi - midi) }?.b ?: StringMeasure.DEFAULT_B
    }
}
