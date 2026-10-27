package at.clavierhaus.unisonmaster.tuning

import at.clavierhaus.unisonmaster.measure.FalseBeat
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
    /**
     * The string's false beats, measured while sampling with the string alone and claimed over
     * three strikes at one rate ([at.clavierhaus.unisonmaster.measure.FalseBeatTally]): which
     * partials beat against themselves, how fast, how deep. Kept with f1 and B because, like
     * them, they are the string's and not the strike's -- a false beat repeats within 0.1 Hz
     * across takes (strings-225, 26 Sept 2026) -- and because tuning cannot measure them: with
     * the unison sounding, a beat at one partial is the unison's or the string's and the
     * microphone cannot say which (FALSE-BEATS §15). Empty until sampling has claimed one.
     */
    val falseBeats: List<FalseBeat> = emptyList(),
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
 * strings are **sampled** — single strings across the range, each
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
    /** Highest note of the session: the top of the range. */
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

    /** True once every note of the temperament octave has been tuned ([tuned]); sampled is not tuned. */
    val temperamentComplete: Boolean get() = temperamentNotes.all { it in tuned }

    /** True while the session walks the temperament octave first (the tuner's preference, until the octave is complete). */
    val gated: Boolean get() = !sampling && settings.temperamentFirst && !temperamentComplete

    // ---- Sampling ----

    /**
     * True while the strings are being sampled (docs/ENGINE.md §1): the
     * tuner strikes whatever key he chooses, the key is caught and Accept
     * keeps it; Done ends the sampling once [samplingReady] — any
     * [TunerSettings.MIN_SAMPLES] strings besides A4, in both editions. No
     * note is prescribed (the set of 25 Sept left on 4 Oct 2026).
     */
    var sampling: Boolean = sampling
        private set

    /** How many strings besides A4 have been sampled. */
    val samplesIn: Int get() = measured.keys.count { it != MIDI_A4 }

    /** How many a sampling needs: the minimum, the same for every tuner. */
    val samplesNeeded: Int get() = TunerSettings.MIN_SAMPLES

    /** True once the sampling may end: the minimum is in. */
    val samplingReady: Boolean get() = samplesIn >= TunerSettings.MIN_SAMPLES

    /**
     * Ends the sampling (Done): the inharmonicity sampled so far becomes the basis of every
     * target for the rest of the session ([basis]), and the tuning begins at A4's neighbour.
     * [frozen] is a basis saved earlier, for a session continued; without one, the strings
     * measured now are it.
     */
    fun finishSampling(frozen: Map<Int, Double>? = null) {
        if (!sampling) return
        sampling = false
        sampledB = frozen ?: measured.mapValues { it.value.b }
        select(notes.first { it != MIDI_A4 })
    }

    /**
     * The inharmonicity of the sampled strings when the sampling ended, by key: the basis of
     * every target from then on. The sampling measures the piano's inharmonicity once, and it
     * does not depend on pitch; a note tuned afterwards is recorded ([record]) for the tuning's
     * record -- the progress, the check against its partner -- but never moves a target (Peter,
     * 11 Oct 2026: "What we measure initially in the sampling is a PITCH INDEPENDENT measurement
     * and it is written in stone ... the calculated pitch of C2 needs to be where we tuned it").
     * Until then every note tuned was a new sample of the curve, and a note tuned early stood
     * off its target after twenty more (TuningSessionTest, theSamplingIsTheBasis...). Null while
     * sampling, and in a session that never sampled: then what is measured is the basis.
     */
    private var sampledB: Map<Int, Double>? = null

    /** The sampled inharmonicity saved with the session; null while sampling. */
    val frozenBasis: Map<Int, Double>? get() = sampledB

    /** The inharmonicity every target is computed from: the sampling's, once it has ended. */
    private val basis: Map<Int, Double> get() = sampledB ?: measured.mapValues { it.value.b }

    /** The instrument's inharmonicity curve from the sampling (from what is measured, while sampling). */
    val curve: InharmonicityCurve
        get() = InharmonicityCurve(basis, settings.lowestUnwoundMidi, settings.curveBreaks)

    /**
     * True when leaving a note registers it as done with what was heard of
     * it, so that the walk needs no Done: always, unless the tuner walks the
     * temperament octave first and it is not yet complete — inside that
     * octave the walk asks for Done on every note, because the octave is
     * what everything else is built on.
     */
    val recordsOnLeaving: Boolean get() = !gated && !sampling

    /** Lowest note the arrows may reach: the foot of the session, always. */
    val stepLowMidi: Int get() = lowMidi

    /** Highest note the arrows may reach: the top of the range, always. */
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

    /**
     * An offset in cents per key that an edition may lay over the targets (24 Oct 2026): Pro's pitch
     * raise pulls each string past its target by this much, so that it settles there once the strings
     * after it are raised. Every target the screen reads ([listening]) carries it; the stretch itself
     * ([curveTargetF1]) does not, and neither does the check against a partner, which is read against
     * where the partner was asked to stand. The free edition never sets it. Empty: the targets as computed.
     */
    var targetOffsetCents: Map<Int, Double> = emptyMap()

    private fun offset(midi: Int): Double = targetOffsetCents[midi] ?: 0.0

    /** What is listened to on [midi], and where it must sit: the target as computed, moved by its offset if it has one. */
    fun listening(midi: Int = current): Listening {
        val l = listeningAsComputed(midi)
        val o = offset(midi)
        return if (o == 0.0) l else l.copy(targetHz = l.targetHz * 2.0.pow(o / 1200.0))
    }

    private fun listeningAsComputed(midi: Int): Listening {
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
                val modelledRef = refK * curveTargetF1(refMidi, c) * Inharmonicity.ratio(refK, bOf(refMidi, c)) * 2.0.pow(offset(refMidi) / 1200.0)
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

    /** B for [midi]: its own as sampled, else the curve's. */
    private fun bOf(midi: Int, c: InharmonicityCurve): Double = basis[midi]?.takeIf { it > 0 } ?: c.b(midi) ?: StringMeasure.DEFAULT_B

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
        val key = Triple(a4Hz, settings, basis)
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

    /**
     * Where partials 1..[maxK] of [midi] should sit when it is on target. A
     * string that has been measured is read against its own partial table —
     * each partial where the string actually puts it, not where the B model
     * says — so that a partial the model places a few tenths of a cent off
     * does not read as a detuning (28 Sept); an unmeasured string takes the
     * model, its B predicted from the curve.
     */
    fun predictedPartials(midi: Int = current, maxK: Int = Partials.MAX): List<PredictedPartial> {
        val b = predictedB(midi)
        val f1 = targetF1Of(midi, b)
        val own = measured[midi]
        val basis = own ?: basisFor(midi)
        // The partial ratios are a live measurement and are not stored:
        // what is predicted here is the model's placing, from this note's own B where
        // it has been measured and the curve's otherwise. The moment a string sounds,
        // TuningController replaces this with the partials of that strike. A stored
        // partial table was used here until 1 Oct 2026 and let a strike made hours
        // earlier furnish the targets of the string now sounding.
        val ownB = measured[midi]?.b ?: b
        return (1..maxK).map { k ->
            PredictedPartial(k, k * f1 * Inharmonicity.ratio(k, ownB),
                basis?.partials?.firstOrNull { it.k == k }?.levelDb ?: 0.0)
        }
    }

    /** True when [midi] has been measured: its partials are read against its own table. */
    fun isMeasured(midi: Int = current): Boolean = measured.containsKey(midi)

    companion object {
        const val MIDI_A4 = 69
        /** Top of the range. */
        const val MIDI_C8 = 108
        /**
         * A partial is matched when it stands this close to its target, in cents.
         *
         * Cents, not Hertz. A tenth of a Hertz is a cent at F3 and a twelfth of a
         * cent at C7: the same window meant forty different things across the
         * range, and at the top of the piano it promised a precision no
         * measurement here has (1 Oct 2026 -- "a false illusion of precision").
         *
         * Two numbers set it, and they are different kinds of number. The floor is
         * the measurement's: one string's partials repeat from strike to strike
         * within 0.04-0.11 cents over all twelve (30 Sept, F3, three strikes on
         * each of three strings), so a window under about a tenth of a cent
         * promises what the reading cannot repeat. What the window actually is,
         * above that floor, is the tuner's tolerance, and only the tuner at the
         * piano can set it: 1.5 cents on Peter's word (1 Oct 2026), a tenth of the
         * scale's thirty cents either side, which is the band he can see and aim
         * at. The green band on the scale is this window drawn at its true width,
         * so narrowing it in Settings narrows the band with it.
         */
        const val MATCH_CENTS = 1.5

        fun targetF1(midi: Int, a4Hz: Double): Double = a4Hz * 2.0.pow((midi - MIDI_A4) / 12.0)

        fun centsOff(hz: Double, targetHz: Double): Double = 1200.0 * ln(hz / targetHz) / ln(2.0)

        /** Green: the live reading lies within [windowCents] of its target. */
        fun matched(hz: Double?, targetHz: Double?, windowCents: Double = MATCH_CENTS): Boolean =
            hz != null && targetHz != null && targetHz > 0.0 && hz > 0.0 &&
                abs(centsOff(hz, targetHz)) <= windowCents + 1e-9

        /** Highest partial of a note at [f1Hz] that lies at or below [highestPartialHz]. */
        fun highestUsefulPartial(f1Hz: Double, highestPartialHz: Double): Int =
            (highestPartialHz / f1Hz).toInt().coerceIn(1, Partials.MAX)

        /** A reference pitch to the display's resolution, 0.1 Hz. */
        fun roundToTenth(hz: Double): Double = kotlin.math.round(hz * 10.0) / 10.0
    }

    private val measured = LinkedHashMap<Int, NoteMeasurement>()

    val measurements: Map<Int, NoteMeasurement> get() = measured

    /**
     * The notes tuned in this session: A4, which is tuned to the reference before the sampling, and
     * every note recorded once the sampling has ended. A note only sampled is in [measurements] as the
     * piano was found and is not here (Peter, 25 Oct 2026, the 1970 B: the progress showed the
     * sampled treble as tuned). What the progress, the temperament octave and the walk call done.
     */
    val tuned: Set<Int> get() = if (MIDI_A4 in measured) tunedNotes + MIDI_A4 else tunedNotes.toSet()
    private val tunedNotes = LinkedHashSet<Int>()

    /**
     * The tuned notes of a session saved earlier, continued ([tuned]); notes it no longer holds are
     * left out. Only on a session restored, after its measurements.
     */
    fun restoreTuned(notes: Set<Int>) {
        tunedNotes.clear()
        tunedNotes += notes.filter { it in measured }
    }

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
    fun record(m: NoteMeasurement): Boolean {
        if (Notes.nearestMidi(m.f1Hz, a4Hz) != m.midi) return false
        measured[m.midi] = m.copy(partials = m.partials.filter { it.k == 1 || Partials.plausibleCents(it.k, it.cents) })
        if (!sampling) tunedNotes += m.midi
        return true
    }

    /**
     * Keeps [m] in place of the note's measurement without tuning the note: the facts the strikes
     * claim (its false beats) laid on a string as it stands, sampled or tuned ([tuned] unchanged).
     */
    fun keep(m: NoteMeasurement): Boolean {
        val wasTuned = m.midi in tunedNotes
        if (!record(m)) return false
        if (!wasTuned) tunedNotes -= m.midi
        return true
    }

    /**
     * A sample taken back while sampling (Peter, 4 Oct 2026: every sampled
     * note has its undo — out of the sampling, or struck anew): the string's
     * measurement leaves the session and the curve stands on the rest. A4
     * is the reference and is not taken back here; outside sampling nothing
     * is taken back. True when a sample was removed.
     */
    fun forget(midi: Int): Boolean {
        if (!sampling || midi == MIDI_A4) return false
        return measured.remove(midi) != null
    }

    fun select(midi: Int) {
        require(midi in notes) { "note $midi is outside the session" }
        current = midi
    }

    /** The first note of the walk not yet tuned ([tuned]), or null when every note is. */
    fun nextUntuned(): Int? = notes.firstOrNull { it !in tuned }

    /**
     * The note Done moves to: the next one in the tuning order, so the walk
     * runs A4 down to the floor and then up from A#4 to the top. While the
     * temperament octave is walked first, the first note of it still
     * without a measurement. Null at the end of the range.
     */
    fun next(): Int? {
        if (sampling) return null
        if (gated) return notes.firstOrNull { it in temperamentNotes && it !in tuned }
        return notes.getOrNull(notes.indexOf(current) + 1)
    }

    /** The note [delta] semitones away, kept within the range. */
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
        basis[midi]?.takeIf { it > 0.0 }?.let { return it }
        val c = curve
        if (c.ready) c.b(midi)?.let { return it }
        val withB = measured.values.filter { it.midi != midi && it.b > 0.0 }
        val sameWire = withB.filter { settings.isWound(it.midi) == settings.isWound(midi) }
        return (sameWire.ifEmpty { withB }).minByOrNull { abs(it.midi - midi) }?.b ?: StringMeasure.DEFAULT_B
    }
}
