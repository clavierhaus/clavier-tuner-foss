package at.clavierhaus.unisonmaster

import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.measure.LivePartial
import at.clavierhaus.unisonmaster.measure.ReadingSpread
import at.clavierhaus.unisonmaster.measure.PartialFinder
import at.clavierhaus.unisonmaster.measure.CleanReading
import at.clavierhaus.unisonmaster.measure.FalseBeat
import at.clavierhaus.unisonmaster.measure.FalseBeatTally
import at.clavierhaus.unisonmaster.measure.PhaseReader
import at.clavierhaus.unisonmaster.measure.StringMeasure
import at.clavierhaus.unisonmaster.persistence.SessionSnapshot
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.tuning.EqualTemperament
import at.clavierhaus.unisonmaster.tuning.Inharmonicity
import at.clavierhaus.unisonmaster.tuning.MeasuredPartial
import at.clavierhaus.unisonmaster.tuning.KeyIdentifier
import at.clavierhaus.unisonmaster.tuning.NoteMeasurement
import at.clavierhaus.unisonmaster.tuning.Notes
import at.clavierhaus.unisonmaster.tuning.PredictedPartial
import at.clavierhaus.unisonmaster.tuning.Temperament
import at.clavierhaus.unisonmaster.tuning.TuningSession
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The tuning engine behind the one main screen (docs/ENGINE.md). Platform
 * UIs observe the StateFlows and call the functions; no platform types in
 * here.
 *
 * Every hop of input (1024 samples, 21 ms at 48 kHz) goes to one
 * [PhaseReader] set on the partial being listened to, and into a ring of
 * the last [RING_S] seconds. On the hub the reader is set on the A4 string
 * wherever [PartialFinder] places it; on the tuning screen it is set on the
 * target of the note's listened partial ([TuningSession.listening]), and
 * the finder only says how far off a string is while it lies outside the
 * reader's band. Done measures the string from the ring ([StringMeasure]).
 */
class TuningController(
    private val audioSource: AudioSource,
    /** Wall clock for measurement timestamps, ms. */
    private val clock: () -> Long = { 0L },
) {
    companion object {
        const val MIN_REFERENCE_HZ = 415.0
        const val MAX_REFERENCE_HZ = 450.0
        const val DEFAULT_REFERENCE_HZ = 440.0
        const val HOP = 1024
        /** What the ring keeps: a strike and two seconds of its decay, with room. */
        const val RING_S = 3.0
        /** The finder runs every this many hops, over the last [FINDER_SAMPLES]. */
        const val FINDER_EVERY = 8
        const val FINDER_SAMPLES = 16384
        /** On the hub, the reader moves to the A4 string when the finder places it this far from where it reads. */
        const val HUB_RETARGET_CENTS = 12.0
        /** How far either side of the target the finder looks for a string that is off: less than half the distance between neighbouring partials at k ≤ 6. */
        const val COARSE_CENTS = 120.0
        /** Leaving a note keeps it when its partial was read this close to target... */
        const val LEAVE_CENTS = 30.0
        /** ... within this many hops before (about 3 s). */
        const val LEAVE_HOPS = 140
        /** The level bar: silence below this (−72 dBFS)... */
        const val SILENCE_RMS = 0.00025
        /** ... and full height at the strike's peak, falling to zero over this. */
        const val LEVEL_RANGE_DB = 48.0
        /** Partials shown at once in Full Spectrum: what the readout column has room for. */
        const val MAX_SHOWN = 6
        /** The key struck is named on this many samples ([KeyIdentifier]) ... */
        const val DETECT_SAMPLES = 16384
        /** ... from this many hops after the strike (half the window of it: the treble is gone in half a second) to this many (1.5 s) ... */
        const val DETECT_FROM_HOPS = 8L
        const val DETECT_UNTIL_HOPS = 70L
        /** ... every other hop, and the same key this many times in a row. */
        const val DETECT_EVERY = 2L
        /**
         * How long a reading stands after the partial was last read. Longer
         * and a decayed partial's last number sits on the screen looking like
         * a measurement; shorter and the display flickers between the hops
         * the gate skips. Derived from the hop, not written as a count: a
         * changed hop or sample rate would break a constant silently.
         */
        const val HOLD_S = 0.5

        /** After a strike, the ring holds enough of it to measure the string's partials. */
        const val LIVE_PARTIALS_S = 1.5

        fun livePartialsHops(sampleRate: Int): Long =
            (LIVE_PARTIALS_S * sampleRate / HOP).toLong().coerceAtLeast(1L)

        fun holdHops(sampleRate: Int): Long = (HOLD_S * sampleRate / HOP).toLong().coerceAtLeast(1L)
        const val DETECT_RUN = 3
        /** After this many hops (half a second) only a lower key may replace the key named: a decaying strike never becomes a higher note. */
        const val DETECT_SETTLED_HOPS = 24L
        /** Nothing is named once the level has fallen this far below the strike: what is left is the room. */
        const val DETECT_DECAY_DB = 30.0
        /** A strike's own rising hops are the same strike: a new one is at least this many hops on. */
        const val STRIKE_HOPS = 3L
        /**
         * Sampling: the key struck is decided once per strike, on a long window
         * past the hammer's thump ([SAMPLE_LONG_SAMPLES] from [SAMPLE_LONG_AT_S]
         * after the strike) or, for the treble that is gone before it fills,
         * on a short window at the onset — the better fit decides, the onset
         * only for a key above [SAMPLE_EARLY_MIN_HZ].
         */
        const val SAMPLE_LONG_SAMPLES = 32768
        const val SAMPLE_LONG_AT_S = 0.15
        const val SAMPLE_EARLY_SAMPLES = 16384
        const val SAMPLE_EARLY_AT_S = 0.02
        const val SAMPLE_EARLY_MIN_HZ = 1500.0
        /**
         * The caught string is measured when its key is decided and again every this many hops, a
         * quarter second, as the sound comes in, until more sound no longer changes it (Peter,
         * 18 Oct 2026, 15:52: "Why hard-code a fixed value at all? A note is done when the software
         * can say: I have captured all measurable partials"). Until then it was measured once more
         * at a fixed 2.5 s, and an Accept followed by the next strike sooner kept the measurement of
         * the decision: in the bass B 10.6 % off the full one (median of 209 takes), where a
         * string's strikes agree to 1.3 % (SAMICK findings §29).
         */
        const val SAMPLE_STEP_HOPS = 12L
        /**
         * Complete: [SAMPLE_STILL_HOPS] more sound (half a second, a choice, not a measurement)
         * moved the partial the string's octaves meet on (the octave type's higher partial, 6 for
         * 6:3, 4 for 4:2) by less than [SAMPLE_STILL_CENTS]. On the 209 takes this completes the
         * middle at 1.4 s, the bass at 1.9 s, the top at 2.4 s, where the measurement's own decay
         * window ends it, with B as close to the 2.5 s measurement as the string's strikes are to
         * each other -- in C3-B4 0.7 % against 0.3 %, 0.04 c at the octave's partial (§30). A
         * criterion on the fit's own uncertainty was tried beside it and failed: it called the
         * middle complete at the decision with B 1.5 % off.
         */
        const val SAMPLE_STILL_HOPS = 24L
        /** One string's strike-to-strike repeat at its partials, cents: the floor under [TuningSession.MATCH_CENTS]. */
        const val SAMPLE_STILL_CENTS = 0.1
    }

    // ---- Reference and settings ----

    private val _referenceA4Hz = MutableStateFlow(DEFAULT_REFERENCE_HZ)
    val referenceA4Hz: StateFlow<Double> = _referenceA4Hz.asStateFlow()

    private val _temperament = MutableStateFlow<Temperament>(EqualTemperament)
    val temperament: StateFlow<Temperament> = _temperament.asStateFlow()

    fun setReference(hz: Double) {
        _referenceA4Hz.value = hz.coerceIn(MIN_REFERENCE_HZ, MAX_REFERENCE_HZ)
        val s = session ?: return
        s.a4Hz = _referenceA4Hz.value
        publish(s)
    }

    private val _settings = MutableStateFlow(TunerSettings())
    /** The settings in force (see [applySettings]). */
    val settings: StateFlow<TunerSettings> = _settings.asStateFlow()

    /** Called by the app whenever the settings change; re-targets the session if there is one. */
    fun applySettings(s: TunerSettings) {
        _settings.value = s
        val session = session ?: return
        session.settings = s
        if (session.current !in session.notes) session.select(session.notes.last())
        publish(session)
    }

    /** Highest partial worth offering for a note whose first partial is at [targetHz] (frequency cap from the settings). */
    fun highestPartial(targetHz: Double): Int =
        TuningSession.highestUsefulPartial(targetHz, TuningSession.targetF1(_settings.value.highestPartialMidi, _referenceA4Hz.value))

    // ---- What the screen shows ----

    private val _live = MutableStateFlow(false)
    val live: StateFlow<Boolean> = _live.asStateFlow()

    private val _liveHz = MutableStateFlow<Double?>(null)
    /**
     * The reading of the partial listened to — on the hub the A4 string's
     * first partial — held after the tone dies; null until the first reading
     * that could be shown.
     */
    val liveHz: StateFlow<Double?> = _liveHz.asStateFlow()

    private val _liveLevel = MutableStateFlow(0.0)
    /** Loudness 0..1 relative to the last strike's peak; 0 in silence. */
    val liveLevel: StateFlow<Double> = _liveLevel.asStateFlow()

    /**
     * The reading on the tuning screen: partial [k] of the note, read at
     * [hz] (the last reading that could be shown, held), against [targetHz].
     * [live]: shown now — the partial stands above the noise beside it and
     * no strike is settling. [coarseCents]: the string lies outside the
     * reader's band, this far from the target by the finder; null when it
     * is inside or nothing is found.
     */
    data class Reading(
        val k: Int,
        val hz: Double?,
        val targetHz: Double,
        val live: Boolean,
        val settling: Boolean,
        val coarseCents: Double?,
        /** The partials the reading is made of when the clean partials are read together; empty when [k] alone is read. */
        val usedPartials: List<Int> = emptyList(),
        /** Partials present but beating on their own, left out of the reading. */
        val falsePartials: List<Int> = emptyList(),
    ) {
        /** Deviation from target, cents: of the clean partials together, or of [k] alone. */
        val cents: Double? get() = hz?.let { TuningSession.centsOff(it, targetHz) }
        val combined: Boolean get() = usedPartials.isNotEmpty()
    }

    private val _reading = MutableStateFlow<Reading?>(null)
    val reading: StateFlow<Reading?> = _reading.asStateFlow()

    private val _livePartials = MutableStateFlow<List<LivePartial>>(emptyList())
    /** Partials of the sounding string, placed by the finder (Full Spectrum only). */
    val livePartials: StateFlow<List<LivePartial>> = _livePartials.asStateFlow()

    private val _liveAudible = MutableStateFlow<Set<Int>>(emptySet())
    /** Partials the finder hears on the sounding string (Full Spectrum only). */
    val liveAudible: StateFlow<Set<Int>> = _liveAudible.asStateFlow()

    private val _fullSpectrum = MutableStateFlow(false)
    /** false = the listened partial only, true = all audible partials. */
    val fullSpectrum: StateFlow<Boolean> = _fullSpectrum.asStateFlow()

    private val _hiddenPartials = MutableStateFlow<Set<Int>>(emptySet())
    /**
     * Partials the tuner has switched off: on the hub's Full Spectrum, and on the tuning screen, where a
     * partial taken off stays off through every strike of the note until a tap brings it back (Peter,
     * 22 Oct 2026: "Tapping a partial button is a deliberate choice and should not automagically be
     * changed back"). A new note starts with none.
     */
    val hiddenPartials: StateFlow<Set<Int>> = _hiddenPartials.asStateFlow()

    private val _shownPartials = MutableStateFlow(setOf(1))
    /** Partials displayed on the tuning screen's Full Spectrum; the listened one is always among them. */
    val shownPartials: StateFlow<Set<Int>> = _shownPartials.asStateFlow()

    private val _activePartial = MutableStateFlow(1)
    /** The partial being read: the note's listened partial, or one the tuner tapped for this note. */
    val activePartial: StateFlow<Int> = _activePartial.asStateFlow()

    private val _suggested = MutableStateFlow<Int?>(null)
    /** The note's listened partial: what the partial row points to. */
    val suggested: StateFlow<Int?> = _suggested.asStateFlow()

    private val _targetHz = MutableStateFlow(DEFAULT_REFERENCE_HZ)
    /** The first partial's place when the note is on target (on the hub: the reference). */
    val targetHz: StateFlow<Double> = _targetHz.asStateFlow()

    private val _targets = MutableStateFlow<List<PredictedPartial>>(emptyList())
    /** Where the current note's partials sit when it is on target. */
    val targets: StateFlow<List<PredictedPartial>> = _targets.asStateFlow()

    fun toggleFullSpectrum() {
        _fullSpectrum.value = !_fullSpectrum.value
        _hiddenPartials.value = emptySet()
        val t = _tuning.value ?: return
        val k = t.listening.k
        // what fits the readout column: the listened partial and the lowest of the rest
        _shownPartials.value = if (_fullSpectrum.value) (_targets.value.map { it.k }.filter { it != k }.take(MAX_SHOWN - 1) + k).toSet() else setOf(k)
        setActive(k)
        wantShown()
    }

    fun tapPartial(k: Int) {
        val t = _tuning.value
        if (t == null) {
            // the A4 page: every partial the strike showed is drawn, and a tap takes one out or brings it
            // back; partial 1 is the pitch itself and always stays (Peter, 22 Oct 2026)
            val hidden = _hiddenPartials.value
            if (k != 1 && k in _liveAudible.value) _hiddenPartials.value = if (k in hidden) hidden - k else hidden + k
            return
        }
        val listened = t.listening.k
        val shown = _shownPartials.value
        when {
            // the listened partial is always shown; a tap only makes it active
            k == listened -> setActive(k)
            // one tap adds a partial and reads it ...
            k !in shown -> {
                val available = _targets.value.map { it.k }.toSet() + _liveAudible.value
                if (k !in available) return
                _hiddenPartials.value = _hiddenPartials.value - k
                _shownPartials.value = shown + k
                setActive(k)
                wantShown()
            }
            // ... and one tap removes it again, and it stays off: the next strike builds the shown
            // partials less the hidden ones (until 22 Oct 2026 it came back with the next strike)
            else -> {
                _hiddenPartials.value = _hiddenPartials.value + k
                _shownPartials.value = shown - k
                if (_activePartial.value == k) setActive(listened)
                wantShown()
            }
        }
    }

    /** Readout column: the tapped line's partial becomes the one read. */
    fun activatePartial(k: Int) {
        if (k in _shownPartials.value) { setActive(k); wantShown() }
    }

    /**
     * Full Spectrum: every shown partial is read by its own phase reader on
     * its own target (docs/ENGINE.md §2) — never by the finder, whose place
     * for a partial jumps by tenths of a hertz from one window to the next
     * on three unmuted strings. The finder only says which partials are
     * there to be tapped.
     */
    /**
     * The shown partials: what each aims at ([targets]) and where its reader
     * listens ([listen]). The two are the same until the strike says the
     * string stands somewhere else — see [heardHz].
     */
    private class WantedSet(val targets: Map<Int, Double>, val generation: Int, val listen: Map<Int, Double> = targets)
    @Volatile private var wantedSet: WantedSet? = null
    private var shownGen = 0

    private fun wantShown() {
        val t = _tuning.value ?: run { wantedSet = null; return }
        val active = _activePartial.value
        val map = _shownPartials.value.filter { it != active }.associateWith { targetOf(t, it) }
        wantedSet = WantedSet(map, ++shownGen, map.mapValues { (k, hz) -> heardHz[k] ?: hz })
    }

    private fun setActive(k: Int) {
        _activePartial.value = k
        val t = _tuning.value ?: return
        val target = targetOf(t, k)
        wanted = Wanted(target, k, ++generation, heardHz[k] ?: target)
    }

    /**
     * Where this note's partials were last heard to stand, by k — not what
     * they are tuned to. A reader has to listen where the string is or it
     * hears nothing: at 45 cents below its target a string lies outside every
     * reader's band, and the screen had nothing to draw but the coarse word
     * "flat" (Peter at the piano, 4 Oct 2026, 17:11). This is a place to
     * listen and never a reading: what the tuner sees is read from the sound
     * now sounding, and these places are forgotten when the note changes.
     */
    private var heardHz: Map<Int, Double> = emptyMap()

    /**
     * Which partials of this note have been heard beating, and on how many
     * separate strikes. A single strike's judgement is not a false beat
     * (FalseBeatTally): emptied when the note changes.
     */
    private val falseBeats = FalseBeatTally()
    /** The tally's claims as last kept on the note's measurement, so a claim is written once when it appears. */
    private var keptClaims: List<FalseBeat> = emptyList()
    /** The note [keptClaims] are of. */
    private var keptClaimsMidi = -1

    /**
     * The string being tuned sounds alone, its partners muted: a beat it makes is its own, and the
     * tuning screen tallies its false beats as sampling does and keeps them with the note. Set by
     * an edition that knows (Pro, 23 Oct 2026: the reference string is tuned alone, and its false
     * beats are what the unison then sets aside); off, a beat while tuning may be the unison's and
     * nothing is tallied (FALSE-BEATS §15).
     */
    @Volatile var referenceAlone: Boolean = false

    /**
     * The string's false beats as sampling has claimed them so far, for [midi] when it is the
     * note the tally is kept on; nothing for any other.
     */
    private fun factsOf(midi: Int): List<FalseBeat> =
        if (_tuning.value?.midi == midi) falseBeats.claims() else emptyList()

    /**
     * The claims made while sampling become the string's facts the moment they are made. The
     * caught string's measurement carries them (Accept keeps it), and a note the session already
     * holds -- an accepted sample, or A4 the reference, which the strikes do not re-measure --
     * is kept again with them on its own f1 and B. Nothing else of the measurement changes.
     */
    private fun keepFacts(s: TuningSession, midi: Int, claims: List<FalseBeat>) {
        keptClaimsMidi = midi
        caught?.takeIf { it.midi == midi }?.let { c -> c.measurement = c.measurement?.copy(falseBeats = claims) }
        s.measurements[midi]?.let { s.keep(it.copy(falseBeats = claims)) }
        onSessionChanged?.let { save -> snapshot()?.let(save) }
    }

    /**
     * The predicted partials moved bodily so that partial [k] lands on [targetHz]:
     * the string's own partial ratios kept, the note placed where it is being tuned.
     * Scaling every partial by one factor keeps the ratios exact, which is what a
     * string is; recomputing them from a shifted f1 would not.
     */
    private fun anchored(partials: List<PredictedPartial>, k: Int, targetHz: Double): List<PredictedPartial> {
        val at = partials.firstOrNull { it.k == k }?.hz ?: return partials
        if (at <= 0.0 || targetHz <= 0.0) return partials
        val factor = targetHz / at
        if (abs(1200.0 * kotlin.math.ln(factor) / kotlin.math.ln(2.0)) < 0.001) return partials
        return partials.map { it.copy(hz = it.hz * factor) }
    }

    /** The target of partial [k] of the note on screen: the listened partial's own, any other placed by the string's model. */
    private fun targetOf(t: TuningView, k: Int): Double =
        if (k == t.listening.k) t.listening.targetHz
        else _targets.value.firstOrNull { it.k == k }?.hz ?: (k * t.targetHz * Inharmonicity.ratio(k, t.b))

    // ---- The session ----

    /** What the tuning screen shows for the current note. */
    data class TuningView(
        val midi: Int,
        /** What is listened to and where it must sit. */
        val listening: TuningSession.Listening,
        /** The first partial's place when the note is on target, Hz. */
        val targetHz: Double,
        /** The inharmonicity the other partials are placed with. */
        val b: Double,
        /** Lowest note of the session. */
        val lowestMidi: Int,
        /** Notes already measured: sampled or tuned. */
        val measured: Set<Int>,
        /** Notes tuned ([TuningSession.tuned]): A4 and what was recorded after the sampling; the progress's green. */
        val tuned: Set<Int> = emptySet(),
        /**
         * Every tuned note's first partial, as cents from equal
         * temperament on this session's A4: the tuning as executed.
         */
        val deviations: Map<Int, Double>,
        /** The curve this piano's measured inharmonicity asks for: every key, cents from equal temperament. */
        val curve: Map<Int, Double> = emptyMap(),
        /** True once the temperament octave is tuned throughout. */
        val temperamentComplete: Boolean,
        /**
         * True when A4 was measured from the piano in this session. False means the
         * reference is DEFAULT_REFERENCE_HZ, assumed, and every target on the screen
         * stands wherever that assumption puts it -- on a piano at 439.4 Hz, 2.3
         * cents sharp of the instrument, which is of the order a tuner is working
         * to. An assumed reference is not a measurement and the screen must say so.
         */
        val referenceMeasured: Boolean = false,
        /** True when moving to another note registers this one as done. */
        val recordsOnLeaving: Boolean,
        /** Lowest and highest note the arrows may reach. */
        val stepLowMidi: Int,
        val stepHighMidi: Int,
        /** True once every note of the session is tuned. */
        val complete: Boolean,
        /** Sampling: single strings across the compass, before tuning. */
        val sampling: Boolean = false,
        /** Sampling: how many strings besides A4 are in, how many the sampling needs, and whether Done may end it. */
        val samplesIn: Int = 0,
        val samplesNeeded: Int = 0,
        val samplingReady: Boolean = false,
        /** Sampling: the key caught from the last strike, and whether Accept has kept it. */
        val caught: Int? = null,
        val caughtAccepted: Boolean = false,
        /** Sampling: how far the caught string's measurement has got (TuningController.SAMPLE_STEP_HOPS). */
        val caughtMeasured: Measured = Measured.MEASURING,
        /** Sampling: kept strings whose measurement the next strike cut short. */
        val sampledShort: Set<Int> = emptySet(),
        /** True once the curve stands on enough strings for computed targets. */
        val curveReady: Boolean = false,
        /** The strings sampled so far (for the sampling screen's row). */
        val sampled: Set<Int> = emptySet(),
        /** True when a curve break is marked at this note (Pro). */
        val breakHere: Boolean = false,
    )

    private var session: TuningSession? = null

    private val _tuning = MutableStateFlow<TuningView?>(null)
    /** Null on the hub, before A4 is set; the note being tuned after. */
    val tuning: StateFlow<TuningView?> = _tuning.asStateFlow()

    /** The session's measurements so far (A4 first). */
    fun measurements(): Map<Int, NoteMeasurement> = session?.measurements ?: emptyMap()

    /** Called whenever the session changes (Done, a step, a new A4); the app saves it. */
    var onSessionChanged: ((SessionSnapshot) -> Unit)? = null

    /** The session as it stands, or null on the hub before A4 is set. */
    fun snapshot(): SessionSnapshot? {
        val s = session ?: return null
        return SessionSnapshot(
            savedAtMs = clock(),
            a4Hz = _referenceA4Hz.value,
            currentMidi = s.current,
            measurements = s.measurements.values.toList(),
            sampling = s.sampling,
            basis = s.frozenBasis,
            offsets = s.targetOffsetCents,
            tuned = s.tuned,
        )
    }

    /**
     * Lays an offset in cents per key over the targets (TuningSession.targetOffsetCents): the strobe then
     * stands still where the edition asks, on every note it names. An empty map takes them off. The free
     * edition never calls this; Pro's pitch raise does (24 Oct 2026).
     */
    fun setTargetOffsets(offsets: Map<Int, Double>) {
        val s = session ?: return
        s.targetOffsetCents = offsets
        publish(s)
    }

    /**
     * The first partial [midi] is to have when tuned, as the stretch computes it from the sampling, without
     * any offset; null while sampling or without a session. Pro's pitch raise measures how far each sampled
     * string stands from it (24 Oct 2026).
     */
    fun computedTargetF1(midi: Int): Double? {
        val s = session ?: return null
        if (s.sampling || !s.curve.ready) return null
        return s.curveTargetF1(midi)
    }

    /** The offsets in force over the targets, cents by key; empty without a session or an edition's offsets. */
    fun targetOffsets(): Map<Int, Double> = session?.targetOffsetCents ?: emptyMap()

    /** "New Tuning": no session; the next Done on the hub defines A4 afresh. */
    fun resetSession() {
        caught = null
        hubA4 = null
        a4Kept = null
        _a4Status.value = A4Status(null, false)
        shortSamples.clear()
        session = null
        _tuning.value = null
        _reading.value = null
        _liveHz.value = null
        _shownPartials.value = setOf(1)
        _activePartial.value = 1
        _fullSpectrum.value = false
        _hiddenPartials.value = emptySet()
        _suggested.value = null
        _targets.value = emptyList()
        _targetHz.value = _referenceA4Hz.value
        wanted = null
        wantedSet = null
        generation++
    }

    /** Continues a saved session: A4, every measured note, the note the tuner was on. */
    fun restore(snap: SessionSnapshot) {
        session = null
        shortSamples.clear()
        setReference(snap.a4Hz)
        val s = TuningSession(_referenceA4Hz.value, _settings.value)
        for (m in snap.measurements) if (m.midi in s.notes) s.record(m)
        // the basis saved with the session; a file from before 11 Oct 2026 has none, and its
        // measurements, tuned notes among them, become the basis -- the best that file can say
        if (!snap.sampling) s.finishSampling(snap.basis)
        // the notes tuned; a file from before 25 Oct 2026 does not say, and the notes measured outside
        // the sampling's basis are taken for them -- a sampled note tuned since is recorded when struck again
        s.restoreTuned(snap.tuned ?: (s.measurements.keys - snap.basis.orEmpty().keys))
        s.targetOffsetCents = snap.offsets
        val resume = snap.currentMidi.takeIf { s.selectable(it) }
            ?: s.next()?.takeIf { s.selectable(it) }
            ?: TuningSession.MIDI_A4
        s.select(resume)
        session = s
        publish(s)
    }

    /** Tuning screen: tune [midi] next (any note of the session, A4 included). */
    fun selectNote(midi: Int) {
        val s = session ?: return
        if (!s.selectable(midi) || midi == s.current) return
        leaveCurrent(s)
        s.select(midi)
        publish(s)
    }

    /** Arrows: one semitone down (-1) or up (+1), within the compass. */
    fun stepNote(delta: Int) {
        val s = session ?: return
        val to = s.stepped(delta)
        if (to == s.current) return
        leaveCurrent(s)
        s.select(to)
        publish(s)
    }

    /** After Done: the next note of the walk. */
    private fun advance(s: TuningSession) {
        s.next()?.let { s.select(it) }
        publish(s)
    }

    /**
     * The reader goes where the string is: a string as it stands may lie
     * outside the band around the place its target asks for, and a reader
     * aimed at the target then hears nothing at all.
     *
     * While sampling there is no target — the string is what is being
     * measured — so the whole aim moves to it. While tuning the target
     * stands, because the target is what the tuner is pulling the string
     * towards, and only the place the readers listen at moves; the cents on
     * screen are worked out from the two absolute frequencies and so say how
     * far the string is from its target, however far away it stands. Every
     * partial of a string standing this far flat stands that far flat
     * (Rule 1: a bell is not pitch dependent), so they are all moved by the
     * one ratio, and the strike's own measured series replaces these places
     * with where it actually heard each partial as soon as it arrives. A
     * place to listen is not a reading and never reaches the screen.
     *
     * Earned at the Samick, 4 Oct 2026, 17:11 (Peter): "In the beginning
     * there was nothing except 'flat'. Only when tuning up did the bells
     * appear" — a string 45 cents below its target was outside every one of
     * the ±25 cent bands, so the picture had nothing to draw.
     */
    private fun followCoarse(w: Wanted, coarseCents: Double) {
        val t = _tuning.value ?: return
        val f = 2.0.pow(coarseCents / 1200.0)
        if (t.sampling) {
            val hz = w.hz * f
            wanted = Wanted(hz, w.k, ++generation)
            _reading.value = Reading(w.k, null, hz, live = false, settling = false, coarseCents = null)
            return
        }
        // and only once the engine has named the sounding key as the note on screen.
        // COARSE_CENTS is 120, wider than a semitone, so a peak that far from the target is
        // as likely to be the neighbouring key sounding as this string standing flat: left
        // to follow it the reader locks onto the neighbour, reads it as this note
        // (Amendment 3), and the screen stops following to the key actually struck, because
        // it follows only while the note on screen is not being read. The key identifier is
        // what tells the two apart, so nothing moves until it has positively named this
        // note. Measured on the 22 Sept walk across the compass: 79 of 80 keys followed
        // before 4 Oct, 65 with the readers following any coarse peak, 79 again with this.
        if (_heardMidi.value != session?.current) return
        val place = w.hz * f
        // already listening where the string is: nothing to move, and moving every hop
        // would restart the readers' memory for as long as the string stood off its target
        if (abs(TuningSession.centsOff(place, w.listenHz)) <= PhaseReader.DEFAULT_BAND_CENTS / 2) return
        heardHz = (_shownPartials.value + w.k).associateWith { targetOf(t, it) * f }
        wanted = Wanted(w.hz, w.k, ++generation, place)
        wantShown()
    }

    /**
     * A note recorded from the still strobe reaches the view -- measured, its deviation, the
     * compass complete or not -- without a publish: a publish would mint a generation and place
     * this note's targets from its own measured B while the tuner is still on it, moving the
     * listened partial's target by the B difference (8.5 c on the F3 of the test) and resetting
     * the shown readers mid-strike. The measured B takes effect when the note is next selected,
     * as every measured note's does.
     */
    private fun recorded(s: TuningSession) {
        _tuning.value = _tuning.value?.copy(
            measured = s.measurements.keys.toSet(),
            tuned = s.tuned,
            deviations = deviationsOf(s),
            complete = s.nextUntuned() == null,
        )
        onSessionChanged?.let { save -> snapshot()?.let(save) }
    }

    /** The tuned notes' first partials, cents from equal temperament: the piano as tuned, never as sampled (25 Oct 2026). */
    private fun deviationsOf(s: TuningSession): Map<Int, Double> =
        s.measurements.filterKeys { it in s.tuned }.mapValues { (m, v) -> TuningSession.centsOff(v.f1Hz, TuningSession.targetF1(m, s.a4Hz)) }

    private fun publish(s: TuningSession) {
        val midi = s.current
        val listening = s.listening(midi)
        val b = s.predictedB(midi)
        val f1 = s.targetF1Of(midi, b)
        val noteChanged = midi != _tuning.value?.midi
        val view = TuningView(
            midi = midi,
            listening = listening,
            targetHz = f1,
            b = b,
            lowestMidi = s.lowMidi,
            measured = s.measurements.keys.toSet(),
            tuned = s.tuned,
            deviations = deviationsOf(s),
            // The tuning this piano's own inharmonicity asks for, every key of the
            // compass, in cents from equal temperament. It is the curve the targets
            // are already taken from -- built from the B of the strings that were
            // sampled, not from a stretch table -- and until now it was computed for
            // every note and shown for none, while Progress drew only how far the
            // notes measured so far stand from equal temperament, which is a result
            // and not a target. Empty until the curve has enough samples to stand on.
            curve = if (!s.curve.ready) emptyMap() else
                (s.lowMidi..TuningSession.MIDI_C8).associateWith { m ->
                    TuningSession.centsOff(s.curveTargetF1(m), TuningSession.targetF1(m, s.a4Hz))
                },
            temperamentComplete = s.temperamentComplete,
            referenceMeasured = s.measurements.containsKey(TuningSession.MIDI_A4),
            recordsOnLeaving = s.recordsOnLeaving,
            stepLowMidi = s.stepLowMidi,
            stepHighMidi = s.stepHighMidi,
            complete = s.nextUntuned() == null,
            sampling = s.sampling,
            samplesIn = s.samplesIn,
            samplesNeeded = s.samplesNeeded,
            samplingReady = s.samplingReady,
            caught = caught?.midi,
            caughtAccepted = caught?.accepted == true,
            caughtMeasured = caught?.state ?: Measured.MEASURING,
            sampledShort = shortSamples.filter { it in s.measurements }.toSet(),
            curveReady = s.curve.ready,
            sampled = s.measurements.keys.filter { it != TuningSession.MIDI_A4 }.toSet(),
            breakHere = midi in s.settings.curveBreaks,
        )
        _targetHz.value = f1
        // Every partial of the note is placed from the one being tuned, not from the
        // curve's f1 for the note. The two are not the same number: the listened
        // partial's target comes from the interval it must make (a beatless 4:2 to
        // the octave below, say), the curve's f1 from the note's own place on the
        // curve, and they differ by the width the tuner asked for. Left unanchored
        // the table contradicted the bell -- partial 4 shown 0.8 Hz from the target
        // it was actually being tuned to -- and the partials of one clean string
        // scattered over 2.5 cents (30 Sept, F3 on the Samick, where the same
        // string's partials agree to 0.24 c once anchored).
        _targets.value = anchored(
            s.predictedPartials(midi, highestPartial(f1).coerceAtLeast(listening.k)),
            listening.k, listening.targetHz,
        )
        _tuning.value = view
        _suggested.value = listening.k
        if (noteChanged) {
            _liveHz.value = null
            _fullSpectrum.value = false
            _hiddenPartials.value = emptySet()
            // every partial the note can carry is read, on every note: the tuning screen is
            // one picture of them all (docs/UI.md), not a mode that shows one and a mode that
            // shows six. Until 3 Oct 2026 only the listened partial was read outside Full
            // Spectrum, and the picture of a sounding string stood empty.
            _shownPartials.value = (_targets.value.map { it.k } + listening.k).toSet()
            _activePartial.value = listening.k
        } else if (_activePartial.value !in _targets.value.map { it.k } && _activePartial.value != listening.k) {
            _activePartial.value = listening.k
        }
        val k = _activePartial.value
        if (noteChanged) { heardHz = emptyMap(); falseBeats.clear(); keptClaims = emptyList() }
        val target = targetOf(view, k)
        wanted = Wanted(target, k, ++generation, heardHz[k] ?: target)
        _reading.value = Reading(k, null, target, live = false, settling = false, coarseCents = null)
        if (noteChanged) { _livePartials.value = emptyList(); _liveAudible.value = emptySet(); levels = emptyMap() }
        wantShown()
        onSessionChanged?.let { save -> snapshot()?.let(save) }
    }

    // ---- Done and leaving ----

    /**
     * Leaving the current note registers it as done with what was heard of
     * it, where the session allows that ([TuningSession.recordsOnLeaving]):
     * only if its partial was read in the last few seconds, near its target.
     * A note passed by without being struck, or a neighbour ringing, keeps
     * whatever the note had.
     */
    private fun leaveCurrent(s: TuningSession) {
        if (!s.recordsOnLeaving) return
        val t = _tuning.value ?: return
        val last = lastShown ?: return
        if (last.generation != generation || hops - last.hop > LEAVE_HOPS) return
        if (abs(TuningSession.centsOff(last.hz, last.targetHz)) > LEAVE_CENTS) return
        // measured up to that reading: a key struck since is not this string
        measureNote(t, last.k, last.hz, upToHop = last.hop + 2)?.let { s.record(it) }
    }

    /**
     * "Done".
     *
     * On the hub: the A4 string's reading, to 0.1 Hz, becomes the reference;
     * the string is measured and kept, and the session moves on. Returns the
     * reference.
     *
     * While tuning: only while the partial read matches its target. The
     * string is measured and kept; the session moves to the next note.
     * Returns the reading, or null if refused.
     */
    fun acceptLive(): Double? {
        val t = _tuning.value
        if (t == null) {
            // the A4 string as its last strike measured it in full, sounding now or not (HubA4); nothing
            // until one is measured
            val m = a4Kept ?: return null
            // the reference is the pitch the tuner chose on the sampling page and tuned the string to,
            // not the string as it happens to stand: until 5 Oct 2026 this overwrote his choice with
            // the reading, so there was never a pitch to aim at (Peter, 15:55)
            val s = TuningSession(_referenceA4Hz.value, _settings.value)
            s.record(m)
            session = s
            advance(s)
            return _referenceA4Hz.value
        }
        val s = session ?: return null
        if (s.sampling) {
            // Accept: the string caught from the last strike, as it was measured, is kept
            val c = caught ?: return null
            if (c.state != Measured.MEASURED) return null
            val m = c.measurement ?: return null
            if (!s.record(m)) return null
            c.accepted = true
            if (c.state == Measured.SHORT) shortSamples += c.midi else shortSamples -= c.midi
            publish(s)
            return c.f1
        }
        if (!matchedNow()) return null
        val r = _reading.value ?: return null
        val hz = r.hz ?: return null
        measureNote(t, r.k, hz)?.let { s.record(it) }
        advance(s)
        return hz
    }

    /**
     * Undo of a sampled note (the sampling page's keyboard): its measurement
     * leaves the session; struck again, the key is caught afresh and Accept
     * keeps it. A4, the reference, is not taken back here. True when a
     * sample was removed.
     */
    fun forgetSample(midi: Int): Boolean {
        val s = session ?: return false
        if (!s.forget(midi)) return false
        if (caught?.midi == midi) caught = null
        publish(s)
        return true
    }

    /**
     * Cancel on the sampling page (Peter, 12 Oct 2026: "cancel automatically resets data caught by
     * a strike and requires new strike and new reading"): what the last strike caught is dropped --
     * its sample too, once Accept had taken it or a sampled key struck again had replaced its own
     * (A4, the reference, is never taken back here) -- and the false-beat tally of that note with
     * it, so the page waits for a new strike and a new reading. True when something was dropped.
     */
    fun cancelCaught(): Boolean {
        val s = session ?: return false
        if (!s.sampling) return false
        val c = caught ?: return false
        if (c.accepted && c.midi != TuningSession.MIDI_A4) s.forget(c.midi)
        caught = null
        falseBeats.clear(); keptClaims = emptyList()
        publish(s)
        return true
    }

    /** Done while sampling: the sampling ends, the curve stands on what is in, the tuning begins. */
    fun finishSampling() {
        val s = session ?: return
        if (!s.sampling || !s.samplingReady) return
        s.finishSampling()
        caught = null
        publish(s)
    }

    /** Done can be tapped: while tuning a live match, while sampling once enough strings are in. */
    fun doneReady(): Boolean {
        val s = session ?: return _liveHz.value != null
        if (s.sampling) return s.samplingReady
        return matchedNow()
    }

    /** Accept can be tapped: while sampling, a key caught and not yet kept. */
    fun acceptReady(): Boolean {
        val s = session ?: return false
        // once the string is measured, not before (Peter, 24 Oct 2026: a tester could accept a key a few
        // milliseconds after it was caught, before anything of it was measured)
        return s.sampling && caught?.let { it.measurement != null && !it.accepted && it.state == Measured.MEASURED } == true
    }

    /** Whether the partial read matches its target now (what Done asks). */
    fun matchedNow(): Boolean {
        val r = _reading.value ?: return false
        return r.live && TuningSession.matched(r.hz, r.targetHz, _settings.value.matchCents)
    }

    /**
     * The partials of the string that was just struck, placed where this strike
     * puts them, with the partial being tuned on its own target.
     *
     * The ratios between a string's partials are a property of the string and
     * are measured; they are not carried over from an earlier strike, an
     * earlier session, or the sampling pass. A stored table lets a strike made
     * hours ago furnish today's targets, and the screen then shows agreement or
     * disagreement that has nothing to do with the string now sounding.
     *
     * Measured on the 30 September corpus: the ratios repeat from strike to
     * strike on one string to within 0.04-0.11 cents over all twelve partials,
     * so deriving them afresh each time costs nothing in steadiness. Against
     * them a clean string's partials stand on one line, which is what the
     * display is for; against a table from another strike they scattered by
     * more than two cents.
     *
     * Returns null when the strike cannot be measured, and the caller then
     * leaves the model's prediction standing — an unmeasured string is read
     * against the model, as it always was.
     */
    private fun livePartials(t: TuningView): List<PredictedPartial>? {
        val k = t.listening.k
        val target = t.listening.targetHz
        if (target <= 0.0) return null
        val f1Guess = target / (k * Inharmonicity.ratio(k, t.b))
        val m = StringMeasure.measure(ringSnapshot(), audioSource.sampleRateHz, t.midi, f1Guess, t.b, timeMs = clock())
            ?: return null
        val list = m.partials.mapNotNull { p -> m.partialHz(p.k)?.let { PredictedPartial(p.k, it, p.levelDb) } }
        if (list.none { it.k == k }) return null
        return list
    }

    /**
     * The string of the note on screen, measured from the ring, with its
     * partial [k] as it was just read at [hz]: that partial was tuned, and
     * the reading is what the notes linked to it are tuned against. Null
     * when not even the first partial can be placed.
     */
    private fun measureNote(t: TuningView, k: Int, hz: Double, upToHop: Long = hops): NoteMeasurement? {
        val f1Guess = hz / (k * Inharmonicity.ratio(k, t.b))
        val all = ringSnapshot()
        val cut = ((hops - upToHop).coerceAtLeast(0) * HOP).coerceAtMost(all.size.toLong()).toInt()
        val m = StringMeasure.measure(all.copyOfRange(0, all.size - cut), audioSource.sampleRateHz, t.midi, f1Guess, t.b, timeMs = clock())
        val f1 = m?.f1Hz ?: f1Guess
        val own = MeasuredPartial(k, TuningSession.centsOff(hz, k * f1), 0.0, 0.0)
        if (m == null) {
            val partials = if (k == 1) listOf(own) else listOf(MeasuredPartial(1, 0.0, 0.0, 0.0), own)
            return NoteMeasurement(t.midi, f1, t.b, 0.0, partials, clock())
        }
        val level = m.partials.firstOrNull { it.k == k }?.levelDb ?: 0.0
        // the reference string tuned alone: what the tally has claimed of it goes with it (referenceAlone)
        // and a note recorded again with its unison sounding keeps what its string alone was found to do
        val facts = if (referenceAlone && keptClaimsMidi == t.midi) keptClaims else session?.measurements?.get(t.midi)?.falseBeats ?: m.falseBeats
        return m.copy(partials = (m.partials.filter { it.k != k } + own.copy(levelDb = level)).sortedBy { it.k }, falseBeats = facts)
    }

    // ---- Live input ----

    private val _heardMidi = MutableStateFlow<Int?>(null)
    /**
     * The key struck, as the note detector names it in the first moments of
     * a strike (the whole comb of partials, docs/ENGINE.md §5); null in
     * silence. The screen follows it ([TunerSettings.autoNote]) or names it.
     */
    val heardMidi: StateFlow<Int?> = _heardMidi.asStateFlow()

    /** What the reader is to be set on: [hz] of partial [k]; [generation] changes with every note or partial. */
    /** The partial being read: [hz] is what it is tuned to, [listenHz] where its reader listens for it. */
    private class Wanted(val hz: Double, val k: Int, val generation: Int, val listenHz: Double = hz)

    @Volatile private var wanted: Wanted? = null
    @Volatile private var generation = 0

    private class Shown(val hz: Double, val targetHz: Double, val k: Int, val hop: Long, val generation: Int)

    /**
     * Sampling: the string caught from the last strike — the key as the
     * identifier fitted it ([f1], [b]), measured from the ring
     * ([measurement], again once the decay is in), kept by Accept
     * ([accepted]). Struck again, the same key replaces its measurement and
     * stays accepted; another key starts afresh.
     */
    private class Caught(val midi: Int, val f1: Double, val b: Double, val strikeHop: Long, var measurement: NoteMeasurement?, var accepted: Boolean) {
        /** B of every measurement of this strike, a quarter second apart: whether more sound still changes it. */
        val history = ArrayList<Double>()
        var state = Measured.MEASURING
    }

    /** How far the caught string's measurement has got: still measuring, measured, or cut short by the next strike. */
    enum class Measured { MEASURING, MEASURED, SHORT }

    /** Sampled keys whose kept measurement was cut short by the next strike (shown so, until struck again in full). */
    private val shortSamples = HashSet<Int>()

    @Volatile private var caught: Caught? = null

    /**
     * A4 on the hub, measured from its strike as a sampled key is (Peter, 24 Oct 2026: Done stays green
     * once A4 is measured, the string sounding or not). Until then Done took the live reading at the
     * moment of the tap, and the tap on the glass is itself a strike to the microphone: the reading
     * restarted, stood empty for about 0.2 s, and a tap released inside it did nothing (a 5 ms transient
     * laid into the 225's A4, Boesendorfer_A4-C-s1, 22 Sept: no reading from 2.05 to 2.13 s).
     */
    private class HubA4(val strikeHop: Long, val f1: Double) {
        var measurement: NoteMeasurement? = null
        val history = ArrayList<Double>()
        var state = Measured.MEASURING
    }
    @Volatile private var hubA4: HubA4? = null
    /** The last A4 strike measured in full: what Done keeps. A strike after it replaces it only once measured itself. */
    @Volatile private var a4Kept: NoteMeasurement? = null

    /** A4 on the hub: how far the measurement of the last strike has got (null: none yet), and whether Done can keep one. */
    data class A4Status(val measured: Measured?, val ready: Boolean)
    private val _a4Status = MutableStateFlow(A4Status(null, false))
    val a4Status: StateFlow<A4Status> = _a4Status.asStateFlow()

    /** The hub's A4 strike measured again, a quarter second on; measured by the same rule as a sampled key ([stepCaught]). */
    private fun stepHubA4(a: HubA4, since: Long) {
        val sr = audioSource.sampleRateHz
        StringMeasure.measure(ringSnapshot(), sr, TuningSession.MIDI_A4, a.f1, timeMs = clock())?.let { m ->
            a.measurement = m
            a.history += m.b
        }
        val k = _settings.value.octaveTypeFor(TuningSession.MIDI_A4).low
        val back = (SAMPLE_STILL_HOPS / SAMPLE_STEP_HOPS).toInt()
        val still = a.history.size > back &&
            1200.0 / ln(2.0) * (k * k - 1) / 2.0 * abs(a.history.last() - a.history[a.history.size - 1 - back]) < SAMPLE_STILL_CENTS
        val windowFull = since * HOP >= (StringMeasure.SKIP_S + StringMeasure.LENGTH_S) * sr
        if (still || windowFull) {
            a.state = Measured.MEASURED
            a4Kept = a.measurement ?: NoteMeasurement(TuningSession.MIDI_A4, a.f1, 0.0, 0.0, listOf(MeasuredPartial(1, 0.0, 0.0, 0.0)), clock())
        }
        _a4Status.value = A4Status(a.state, a4Kept != null)
    }

    /**
     * What the hop decided and must publish, done at its end (18 Oct 2026). A publish mints a
     * generation, and the rest of the hop it falls in returns at the generation check (the note
     * may have changed): the clean partials and the bells then missed that hop of sound, and their
     * readers lost their phase across it. Sampling published twice per strike that way, at the catch
     * and at the remeasure, and the Samick A4 left string's partial-3 false beat, which stands at
     * the detector's line on its first strike, was claimed or not by whether a hop was lost at
     * 1.4 or at 2.5 s. Sampling's catch and its remeasure are published here.
     */
    private val afterHop = ArrayList<() -> Unit>()

    private fun runAfterHop() {
        if (afterHop.isEmpty()) return
        val todo = afterHop.toList()
        afterHop.clear()
        for (f in todo) f()
    }

    /** The key decided for a strike while sampling: caught, or the accepted key's measurement replaced. */
    private fun catchKey(fit: KeyIdentifier.Fit, strikeHop: Long) {
        val s = session ?: return
        if (!s.sampling || !s.selectable(fit.midi)) return
        // above G6 a string is not sampled: its B does not repeat there (TunerSettings.SAMPLE_HIGHEST_MIDI)
        if (fit.midi > TunerSettings.SAMPLE_HIGHEST_MIDI) return
        // a key already sampled, struck again: its new measurement replaces the sample and it stays accepted
        // (A4 is the hub's reference and is only replaced by hand)
        val c = Caught(fit.midi, fit.f1, fit.b, strikeHop, null, accepted = fit.midi != TuningSession.MIDI_A4 && fit.midi in s.measurements)
        c.measurement = measureCaught(c)
        c.measurement?.let { c.history += it.b }
        shortSamples -= c.midi
        caught = c
        if (c.accepted) c.measurement?.let { s.record(it) }
        if (s.current != fit.midi) s.select(fit.midi)
        publish(s)
    }

    /** The caught string measured from the ring, from its strike ([StringMeasure]); at least its fundamental as the fit read it. */
    private fun measureCaught(c: Caught, ring: FloatArray = ringSnapshot()): NoteMeasurement =
        (StringMeasure.measure(ring, audioSource.sampleRateHz, c.midi, c.f1, c.b, timeMs = clock())
            ?.takeIf { Notes.nearestMidi(it.f1Hz, _referenceA4Hz.value) == c.midi }
            ?: NoteMeasurement(c.midi, c.f1, c.b, 0.0, listOf(MeasuredPartial(1, 0.0, 0.0, 0.0)), clock()))
            .copy(falseBeats = factsOf(c.midi))

    /**
     * The caught string measured again, a quarter second on ([SAMPLE_STEP_HOPS]); an accepted one is
     * kept anew. Measured once half a second more sound has moved the partial its octaves meet on
     * by less than a strike repeats ([SAMPLE_STILL_CENTS]), or once the measurement's own decay
     * window is full ([StringMeasure.SKIP_S] + [StringMeasure.LENGTH_S]).
     */
    private fun stepCaught(since: Long) {
        val s = session ?: return
        val c = caught ?: return
        if (!s.sampling || c.state != Measured.MEASURING) return
        val m = measureCaught(c)
        c.measurement = m
        c.history += m.b
        val k = s.settings.octaveTypeFor(c.midi).low
        val back = (SAMPLE_STILL_HOPS / SAMPLE_STEP_HOPS).toInt()
        val still = c.history.size > back &&
            1200.0 / ln(2.0) * (k * k - 1) / 2.0 * abs(m.b - c.history[c.history.size - 1 - back]) < SAMPLE_STILL_CENTS
        val windowFull = since * HOP >= (StringMeasure.SKIP_S + StringMeasure.LENGTH_S) * audioSource.sampleRateHz
        if (still || windowFull) c.state = Measured.MEASURED
        if (c.accepted) {
            s.record(m)
            if (c.state == Measured.MEASURED) shortSamples -= c.midi
        }
        // the measurement reaches the targets once, when it is complete, as the one remeasure at
        // 2.5 s did; the steps before only tell the page. A publish mints a generation and places
        // the note's partials anew, which four times a second in the middle of a strike would do
        // for nothing (and, mid-hop, it cost the clean partials a hop of sound until the publishes
        // moved to the hop's end the same day)
        if (c.state == Measured.MEASURED) publish(s) else caughtChanged(s)
    }

    /** The caught string's measurement, and the samples it changes, reach the page without a publish (see [recorded]). */
    private fun caughtChanged(s: TuningSession) {
        _tuning.value = _tuning.value?.copy(
            measured = s.measurements.keys.toSet(),
            tuned = s.tuned,
            deviations = deviationsOf(s),
            samplesIn = s.samplesIn,
            samplesNeeded = s.samplesNeeded,
            samplingReady = s.samplingReady,
            caughtAccepted = caught?.accepted == true,
            caughtMeasured = caught?.state ?: Measured.MEASURING,
            sampled = s.measurements.keys.filter { it != TuningSession.MIDI_A4 }.toSet(),
            sampledShort = shortSamples.filter { it in s.measurements }.toSet(),
            curveReady = s.curve.ready,
        )
        if (caught?.accepted == true) onSessionChanged?.let { save -> snapshot()?.let(save) }
    }

    /**
     * The next strike came while the caught string was still being measured: it is measured once
     * more from everything heard up to that strike -- the ring less the hop the strike falls in --
     * and called short, so the page can say that a strike again gives the full measurement.
     */
    private fun cutCaught(strikeHopSamples: Int) {
        val s = session ?: return
        val c = caught ?: return
        if (!s.sampling || c.state != Measured.MEASURING) return
        val all = ringSnapshot()
        if (all.size <= strikeHopSamples) return
        c.measurement = measureCaught(c, all.copyOfRange(0, all.size - strikeHopSamples))
        c.state = Measured.SHORT
        if (c.accepted) { c.measurement?.let { s.record(it) }; shortSamples += c.midi }
        caughtChanged(s)
    }
    @Volatile private var lastShown: Shown? = null
    @Volatile private var hops = 0L

    private val ring = FloatArray((RING_S * audioSource.sampleRateHz).toInt())
    @Volatile private var ringPos = 0
    @Volatile private var ringFilled = 0

    /**
     * The last [RING_S] seconds, oldest first. Read while the capture thread
     * writes: at worst the oldest hop is already the next one, which lies
     * before the strike a measurement uses.
     */
    private fun ringSnapshot(): FloatArray {
        val n = ringFilled
        val pos = ringPos
        val out = FloatArray(n)
        if (n < ring.size) { ring.copyInto(out, 0, 0, n); return out }
        ring.copyInto(out, 0, pos, ring.size)
        ring.copyInto(out, ring.size - pos, 0, pos)
        return out
    }

    private fun toRing(chunk: FloatArray) {
        var pos = ringPos
        for (x in chunk) {
            ring[pos] = x
            if (++pos == ring.size) pos = 0
        }
        ringPos = pos
        ringFilled = minOf(ring.size, ringFilled + chunk.size)
    }

    /**
     * Every buffer the microphone delivers while the screen is live, as it
     * arrives: for a recorder that keeps the session. The array is the
     * source's and is reused; copy or write it before returning.
     */
    @Volatile
    var tap: ((FloatArray) -> Unit)? = null

    /** The audio source, for tests that queue strikes. */
    fun audioSourceForTest(): AudioSource = audioSource

    /**
     * Starts listening. Returns false if the input could not be opened
     * (e.g. microphone permission not yet granted); safe to call again.
     */
    fun startLive(): Boolean {
        if (_live.value) return true
        val sr = audioSource.sampleRateHz
        var reader: PhaseReader? = null
        var readerGeneration = -1
        val shownReaders = HashMap<Int, PhaseReader>()
        var shownGeneration = -1
        // the A4 page: the A4 string's partials 2 and up, each by its own reader at the place the strike
        // put it, which is the only lawful place before a B is measured (Rule 0; Peter, 22 Oct 2026)
        val hubReaders = HashMap<Int, PhaseReader>()
        var hubPlaces: Map<Int, Double> = emptyMap()
        var hubPlace1 = 0.0
        val hubHeld = HashMap<Int, PhaseReader.Reading>()
        val hubAt = HashMap<Int, Long>()
        var hubStrike = -1L
        // the clean partials read together (CleanReading): targets follow the note, readings the strike
        val clean = CleanReading(sr, memoryS = _settings.value.readingMemoryS, holdHops = holdHops(sr))
        var cleanGeneration = -1
        // the still strobe: since which hop the listened partial has read inside the match window,
        // which strike was recorded for it, and for which note (a change of either starts over)
        var stillSince = -1L; var stillRecorded = -1L; var stillNote = -1
        // the string and the anchor partial the clean window was last built for; a change of either resets it
        var cleanMidi = -1; var cleanK = -1
        var memoryInForce = _settings.value.readingMemoryS
        var cleanStrike = -1L
        val shownHeld = HashMap<Int, PhaseReader.Reading>()
        // when each shown partial was last actually read: a reading not refreshed within
        // HOLD_HOPS is stale and is dropped rather than stood there looking live (30 Sept,
        // at the Samick: numbers still on screen seconds after the sound had died)
        val shownAt = HashMap<Int, Long>()
        // how well each shown partial is read: the spread of its last half second, cleared
        // at a strike and dropped with the partial (the bell's width, docs/UI.md)
        val shownSpread = HashMap<Int, ReadingSpread>()
        var peakDb = -200.0
        val recent = DoubleArray(PhaseReader.ONSET_LOOKBACK) { -200.0 }
        var coarse: Double? = null
        // the keys the identifier chooses from: the instrument's compass, nothing below it
        var identifierLow = -1
        var identifier = KeyIdentifier(sr, DETECT_SAMPLES)
        var identifierLong = KeyIdentifier(sr, SAMPLE_LONG_SAMPLES)
        var strikeHop = -1_000_000L
        var livePartialsStrike = -1L
        var strikePeakDb = -200.0
        var candidate: Int? = null
        var run = 0
        var followed = false
        var early: KeyIdentifier.Fit? = null
        var decided = false
        val earlyHop = ((SAMPLE_EARLY_AT_S * sr + SAMPLE_EARLY_SAMPLES) / HOP).toLong() + 1
        val longHop = ((SAMPLE_LONG_AT_S * sr + SAMPLE_LONG_SAMPLES) / HOP).toLong() + 1
        _live.value = true
        return try {
            audioSource.start(HOP) { chunk -> try {
                if (!_live.value) return@start
                tap?.invoke(chunk)
                toRing(chunk)
                val hop = ++hops

                // the level bar: relative to the strike's peak
                var ss = 0.0
                for (x in chunk) ss += x.toDouble() * x
                val rms = kotlin.math.sqrt(ss / chunk.size)
                val db = 20 * log10(maxOf(rms, 1e-12))
                val loudest = recent.max()
                // a strike; the attack's own rising hops are the same strike, not new ones
                val strike = db - loudest >= PhaseReader.ONSET_DB && rms >= SILENCE_RMS && hop - strikeHop > STRIKE_HOPS
                // a strike before the caught string's measurement is complete: it is measured from all it had
                if (strike) cutCaught(chunk.size)
                if (strike) { strikeHop = hop; strikePeakDb = db; candidate = null; run = 0; followed = false; early = null; decided = false; _heardMidi.value = null; shownSpread.values.forEach { it.clear() }; stillSince = -1L }
                if (strike || db > peakDb) peakDb = db
                if (db > strikePeakDb) strikePeakDb = db
                recent[(hop % recent.size).toInt()] = db
                _liveLevel.value = if (rms < SILENCE_RMS) 0.0 else (1.0 + (db - peakDb) / LEVEL_RANGE_DB).coerceIn(0.0, 1.0)

                val finderDue = hop % FINDER_EVERY == 0L && ringFilled >= FINDER_SAMPLES
                val finder = if (finderDue) PartialFinder(lastSamples(FINDER_SAMPLES), sr) else null
                val w = wanted
                if (w == null) {
                    // the hub: the reader goes where the finder places the A4 string
                    val centre = (MIN_REFERENCE_HZ * MAX_REFERENCE_HZ).let { kotlin.math.sqrt(it) }
                    val span = 1200 * ln(MAX_REFERENCE_HZ / centre) / ln(2.0) + 5
                    finder?.near(centre, span)?.let { p ->
                        val r = reader
                        if (r == null || abs(TuningSession.centsOff(p.hz, r.targetHz)) > HUB_RETARGET_CENTS) {
                            if (r == null) reader = PhaseReader(sr, p.hz, memoryS = _settings.value.readingMemoryS, window = true) else r.retarget(p.hz)
                        }
                    }
                    readerGeneration = -1
                    // the A4 string as it sounds now, and nothing once it is gone (Law 3): a shown reading or none
                    reader?.push(chunk)?.let { rd -> _liveHz.value = if (rd.shown) rd.hz else null }
                    // A4 measured from its strike: caught once the strike gives a reading of the A4 string, then
                    // measured again every quarter second until more sound no longer changes it (HubA4)
                    val f1Now = _liveHz.value
                    if (strikeHop > 0 && f1Now != null && hubA4?.strikeHop != strikeHop) {
                        hubA4 = HubA4(strikeHop, f1Now)
                        _a4Status.value = A4Status(Measured.MEASURING, a4Kept != null)
                    }
                    hubA4?.let { a -> if (a.state == Measured.MEASURING && (hop - a.strikeHop) % SAMPLE_STEP_HOPS == 0L) stepHubA4(a, hop - a.strikeHop) }
                    if (_fullSpectrum.value && finder != null) hubSpectrum(finder, _liveHz.value ?: _referenceA4Hz.value)
                    // the A4 string's other partials as strobes, so a false beat shows before a string is
                    // chosen as the reference (Peter, 22 Oct 2026: "No bells on A4, just strobe. Strobe
                    // shows false beats"): once the ring holds enough of the strike, the strike says where
                    // they stand, and each is read there; a partial gone quiet is dropped (Rule 3)
                    if (strikeHop > 0 && hop - strikeHop == livePartialsHops(sr) && hubStrike != strikeHop) {
                        hubStrike = strikeHop
                        reader?.targetHz?.let { f1 -> StringMeasure.measure(ringSnapshot(), sr, TuningSession.MIDI_A4, f1, timeMs = clock()) }?.let { m ->
                            hubPlaces = m.partials.filter { it.k >= 2 }.mapNotNull { p -> m.partialHz(p.k)?.let { p.k to it } }.toMap()
                            hubPlace1 = m.partialHz(1) ?: m.f1Hz
                            _liveAudible.value = hubPlaces.keys + 1
                            hubReaders.keys.retainAll(hubPlaces.keys)
                            for ((k, hz) in hubPlaces) {
                                val r = hubReaders[k]
                                if (r == null) hubReaders[k] = PhaseReader(sr, hz, memoryS = _settings.value.readingMemoryS)
                                else if (abs(TuningSession.centsOff(hz, r.targetHz)) > PhaseReader.DEFAULT_BAND_CENTS / 2) r.retarget(hz)
                            }
                        }
                    }
                    for ((k, r) in hubReaders) r.push(chunk)?.let { if (it.shown) { hubHeld[k] = it; hubAt[k] = hop } }
                    hubAt.entries.removeAll { (k, at) -> if (hop - at > holdHops(sr)) { hubHeld.remove(k); true } else false }
                    // each partial against the pitch chosen, by the ratio this strike measured between it and
                    // partial 1: the string's partials move together (Law 1), so an A4 standing flat shows
                    // flat on every strobe, and a false partial beats against the others (Peter, 22 Oct 2026:
                    // partial 1 moved, the others stood still against their own places, which made no sense)
                    val top = hubHeld.values.maxOfOrNull { it.levelDbfs }
                    val hidden = _hiddenPartials.value
                    _livePartials.value = hubHeld.entries.filter { it.key !in hidden }.sortedBy { it.key }.mapNotNull { (k, r) ->
                        val place = hubPlaces[k] ?: return@mapNotNull null
                        if (hubPlace1 <= 0.0) return@mapNotNull null
                        LivePartial(k, r.hz, TuningSession.centsOff(r.hz, _referenceA4Hz.value * place / hubPlace1),
                            (1.0 + (r.levelDbfs - (top ?: r.levelDbfs)) / LEVEL_RANGE_DB).coerceIn(0.0, 1.0), null, r.levelDbfs - r.noiseDbfs)
                    }
                    return@start
                }

                // a changed reading memory remakes every reader (on the next hop, not mid-strike)
                if (_settings.value.readingMemoryS != memoryInForce) {
                    memoryInForce = _settings.value.readingMemoryS
                    reader = null; readerGeneration = -1
                    shownReaders.clear(); shownHeld.clear(); shownSpread.clear(); shownGeneration = -1
                    clean.memoryS = memoryInForce; cleanGeneration = -1; cleanMidi = -1
                }
                // the tuning screen: the reader is set on the partial listened to
                if (readerGeneration != w.generation) {
                    val r = reader
                    // a new generation at the same place leaves the reader alone: a retarget starts
                    // its memory afresh, and the note recorded from a still strobe is published
                    // without the place moving (7 Oct 2026) -- the reading must not blink for that
                    // the strobe's reader reads through its window (PhaseReader.window, 17 Oct 2026): up
                    // as soon as the window is clear of the strike, and a move to a newly named key
                    // reads again what the window has heard instead of starting afresh
                    if (r == null) reader = PhaseReader(sr, w.listenHz, memoryS = _settings.value.readingMemoryS, window = true)
                    else if (r.targetHz != w.listenHz) r.retarget(w.listenHz)
                    readerGeneration = w.generation
                    coarse = null
                }
                val rd = reader!!.push(chunk)
                if (rd != null && rd.shown) {
                    lastShown = Shown(rd.hz, w.hz, w.k, hop, w.generation)
                    coarse = null
                } else if (finder != null && _liveLevel.value > 0.0) {
                    coarse = finder.near(w.hz, COARSE_CENTS)?.let { TuningSession.centsOff(it.hz, w.hz) }
                        ?.takeIf { abs(it) > PhaseReader.DEFAULT_BAND_CENTS }
                    coarse?.let { followCoarse(w, it) }
                } else if (_liveLevel.value == 0.0) coarse = null
                // which key was struck: named within the first moments of a strike,
                // and followed when the note on screen is not what sounds
                val since = hop - strikeHop
                val low = session?.lowMidi ?: 21
                if (low != identifierLow) {
                    identifier = KeyIdentifier(sr, DETECT_SAMPLES, low, TuningSession.MIDI_C8)
                    identifierLong = KeyIdentifier(sr, SAMPLE_LONG_SAMPLES, low, TuningSession.MIDI_C8)
                    identifierLow = low
                }
                if (since in DETECT_FROM_HOPS..DETECT_UNTIL_HOPS && hop % DETECT_EVERY == 0L && ringFilled >= DETECT_SAMPLES && strikePeakDb - db < DETECT_DECAY_DB) {
                    val all = ringSnapshot()
                    // what sounded before the strike — the room, a note still ringing — is not the strike
                    val key = identifier.detectIn(all, _referenceA4Hz.value, backgroundEnd = (all.size - since * HOP).toInt())
                    if (key != null && key == candidate) run++ else { candidate = key; run = if (key != null) 1 else 0 }
                    val h = _heardMidi.value
                    if (run >= DETECT_RUN && h != candidate && (h == null || since <= DETECT_SETTLED_HOPS || candidate!! < h)) _heardMidi.value = candidate
                }
                if (_liveLevel.value == 0.0) _heardMidi.value = null
                val heard = _heardMidi.value
                val s = session
                // sampling: the key of this strike, decided once — at the onset for the treble, past the thump for the rest
                if (s != null && s.sampling && !decided && strikeHop > 0) {
                    val bgEnd = { all: FloatArray -> (all.size - since * HOP).toInt() }
                    if (since == earlyHop && ringFilled >= SAMPLE_EARLY_SAMPLES) {
                        val all = ringSnapshot()
                        early = identifier.identify(all, _referenceA4Hz.value, all.size - SAMPLE_EARLY_SAMPLES, bgEnd(all))?.takeIf { it.f1 >= SAMPLE_EARLY_MIN_HZ }
                    }
                    if (since == longHop && ringFilled >= SAMPLE_LONG_SAMPLES) {
                        val all = ringSnapshot()
                        val long = identifierLong.identify(all, _referenceA4Hz.value, all.size - SAMPLE_LONG_SAMPLES, bgEnd(all))
                        val e = early
                        val pick = if (long == null) e else if (e != null && e.score > long.score) e else long
                        decided = true
                        // at the hop's end: a publish in the middle of it would cost the clean partials this
                        // hop of sound (the generation check below), and with it their phase
                        pick?.let { p -> val at = strikeHop; afterHop += { catchKey(p, at) } }
                    }
                }
                if (s != null && s.sampling && since > longHop && (since - longHop) % SAMPLE_STEP_HOPS == 0L && caught?.strikeHop == strikeHop) { val n = since; afterHop += { stepCaught(n) } }
                // the partials of this strike, once the ring holds enough of it: the
                // targets of every shown partial come from the string now sounding
                val tv = _tuning.value
                if (tv != null && s != null && !s.sampling && strikeHop > 0 &&
                    since == livePartialsHops(sr) && livePartialsStrike != strikeHop
                ) {
                    livePartialsStrike = strikeHop
                    livePartials(tv)?.let { heardSeries ->
                        // where this strike put the partials: the readers listen there from now on, so a
                        // string standing far from its target is still read and drawn — but only when it is
                        // this note's string that sounds, never a neighbour's (see followCoarse)
                        if (_heardMidi.value == s.current) heardHz = heardSeries.associate { it.k to it.hz }
                        val series = anchored(heardSeries, tv.listening.k, tv.listening.targetHz)
                        _targets.value = series
                        // the strike said which partials the string carries: read them all, up to
                        // the highest the tuner's setting offers, less what he hid
                        val cap = highestPartial(tv.targetHz)
                        _shownPartials.value = (series.map { it.k }.filter { it <= cap } + tv.listening.k).toSet() - _hiddenPartials.value
                        wantShown()
                        // and the clean partials are read against that series from here on, not the
                        // model's: until 3 Oct 2026 they kept the model's placing for the whole strike,
                        // and a partial standing in for the one being tuned carried the model's error
                        clean.target(series.filter { it.k <= CleanReading.PARTIALS }.associate { it.k to it.hz }, reset = false)
                    }
                }
                // The screen follows the key the identifier has positively named, and nothing else
                // holds it back (Peter, 7 Oct 2026, 16:29: after sampling, auto note detection must
                // fully work without restrictions -- the A3-A4 sequence is a suggestion, and a pro
                // tuner's order is not our business). Two restrictions left here that day: the
                // temperament-first gate, and the wait for the note on screen to stop reading, which
                // held the screen on the last key while it still rang. Never while sampling: the
                // string just kept is still ringing when the screen moves on.
                if (!followed && heard != null && s != null && heard != s.current &&
                    _settings.value.autoNote && !s.sampling && s.selectable(heard)) {
                    followed = true
                    selectNote(heard)
                }
                if (w.generation != generation) return@start      // the note changed meanwhile
                val held = lastShown?.takeIf { it.generation == w.generation && hop - it.hop <= holdHops(sr) }?.hz
                val t = _tuning.value
                // the clean partials together: only on the partial being tuned (a tapped partial is read alone),
                // and only when the setting says so. While sampling as well as while tuning: this is also
                // the detector that feeds the false-beat tally, and the sampling pass is where the string's
                // false beats are measured (Peter, 6 Oct 2026, 10:26: "in measurement we only measure
                // inharmonicity and partial truth/false beats"). It ran "only while tuning" from 28 Sept
                // (dfe615a, written about the combined hz, before the sampling page had a picture to put a
                // false beat on), and so the page could never mark one -- the Samick's left A4 string beats
                // at E6 at 0.67 Hz on all three strikes of 30 Sept and the screen had nowhere to say so.
                // Nothing kept depends on the reading here: while sampling, Accept keeps the strike's own
                // StringMeasure and the key is caught by the identifier, not read from _liveHz.
                var combinedHz: Double? = null
                var used: List<Int> = emptyList()
                var falseOnes: List<Int> = emptyList()
                if (_settings.value.cleanPartials && t != null && w.k == t.listening.k && s != null) {
                    if (cleanGeneration != w.generation) {
                        // A new generation resets the window only when it is a different string or a
                        // different anchor partial; the same string published again keeps what it has
                        // read, said against the new targets (CleanReading.target's own shift). While
                        // sampling, a strike is published twice -- caught, then remeasured with the
                        // decay in -- and each publish minted a generation; with reset on every one the
                        // window was wiped mid-strike, partial 3 of the Samick's left A4 left the
                        // presence gate before a window could stand again, and no false beat was ever
                        // tallied in sampling (the probe of 6 Oct 2026: k3 spread 0.19 c at 2.1 s,
                        // gone at 3.0 s on every strike; partial 1 tallied on 2 strikes of 3 where it
                        // reaches 0.56-0.65 c on all three read without the resets).
                        val sameString = cleanMidi == t.midi && cleanK == w.k
                        clean.target(_targets.value.filter { it.k <= CleanReading.PARTIALS }.associate { it.k to it.hz }, reset = !sameString)
                        cleanGeneration = w.generation
                        cleanMidi = t.midi; cleanK = w.k
                        if (!sameString) cleanStrike = strikeHop
                    } else if (cleanStrike != strikeHop) { clean.strike(); cleanStrike = strikeHop }
                    // The string's facts: what sampling measured of its false beats, kept with the
                    // note (NoteMeasurement.falseBeats). While sampling they are being made; while
                    // tuning they are what the screen marks.
                    val facts = s.measurements[t.midi]?.falseBeats ?: emptyList()
                    val alone = s.sampling || referenceAlone
                    val cr = clean.push(chunk, w.k, combine = s.isMeasured(), alone = alone, known = facts.map { it.k }.toSet())
                    used = cr.used
                    if (alone) {
                        // The string sounds alone (the mutes are in, by the orders of the sampling
                        // page), so a beat heard is the string's own. A false beat is claimed only
                        // when it repeats across strikes at one rate, never from one (FalseBeatTally,
                        // and the corpus rule of three strikes): what this strike hears is evidence
                        // towards the claim; the claim is what the screen is told and, the moment it
                        // is made, what the note's measurement keeps.
                        for (k in cr.falseBeating) {
                            val b = cr.partials.firstOrNull { it.k == k }?.beat
                            falseBeats.heard(k, strikeHop, b?.rateHz ?: 0.0, b?.fit ?: 0.0, b?.depthDb ?: 0.0)
                        }
                        val claims = falseBeats.claims()
                        falseOnes = claims.map { it.k }
                        if (claims != keptClaims) { keptClaims = claims; keepFacts(s, t.midi, claims) }
                    } else {
                        // With the unison sounding, a beat at one partial is the unison's or the
                        // string's and the microphone cannot say which (FALSE-BEATS §15): nothing is
                        // tallied here, and the violet is the string's facts from sampling. Until
                        // 8 Oct 2026 the tuning strikes fed the tally too, and the Samick's A4 was
                        // called false at a partial beating against its partner.
                        falseOnes = facts.map { it.k }
                    }
                    cr.cents?.let { combinedHz = w.hz * 2.0.pow(it / 1200.0) }
                }
                _reading.value = Reading(
                    w.k, combinedHz ?: held, w.hz,
                    live = if (combinedHz != null) true else rd?.shown == true,
                    settling = combinedHz == null && rd?.settling == true,
                    coarseCents = coarse, usedPartials = used, falsePartials = falseOnes,
                )
                _liveHz.value = combinedHz ?: held
                // The still strobe is the tuner's verdict, and the engine notices it. The listened
                // partial read inside the match window for HOLD_S without a break, and the note is
                // measured from this strike and recorded -- once per strike, the next strike still
                // again replacing it -- with no Done to tap: "it's done when the strobe is still, and
                // that's for the tuner to decide" (Peter at the piano, 7 Oct 2026, 16:20). The screen
                // does not move: with the key followed, the next strike is the next note. A string
                // left flat on purpose is never still inside the window and is never recorded by this.
                if (t != null && s != null && !s.sampling) {
                    if (stillNote != t.midi) { stillNote = t.midi; stillSince = -1L; stillRecorded = -1L }
                    if (matchedNow()) {
                        if (stillSince < 0) stillSince = hop
                        else if (hop - stillSince >= holdHops(sr) && stillRecorded != strikeHop) {
                            _reading.value?.hz?.let { hz -> measureNote(t, w.k, hz) }?.let { m ->
                                if (s.record(m)) { stillRecorded = strikeHop; recorded(s) }
                            }
                        }
                    } else stillSince = -1L
                }
                // the other shown partials, each by its own reader
                val ws = wantedSet
                if (ws != null && ws.generation != shownGeneration) {
                    shownGeneration = ws.generation
                    shownReaders.keys.retainAll(ws.targets.keys)
                    shownHeld.keys.retainAll(ws.targets.keys)
                    shownAt.keys.retainAll(ws.targets.keys)
                    shownSpread.keys.retainAll(ws.targets.keys + w.k)
                    for ((k, target) in ws.targets) {
                        val hz = ws.listen[k] ?: target
                        val r = shownReaders[k]
                        // a reader is remade only when its target leaves the band it reads in: a
                        // retarget starts its memory afresh, and the strike's own series arriving a
                        // few cents from the model must not make every bell swing mid-strike
                        if (r == null) shownReaders[k] = PhaseReader(sr, hz, memoryS = _settings.value.readingMemoryS)
                        else if (abs(TuningSession.centsOff(hz, r.targetHz)) > PhaseReader.DEFAULT_BAND_CENTS / 2) { r.retarget(hz); shownSpread[k]?.clear() }
                    }
                }
                for ((k, r) in shownReaders) r.push(chunk)?.let { if (it.shown) { shownHeld[k] = it; shownAt[k] = hop; shownSpread.getOrPut(k) { ReadingSpread() }.push(it.cents) } }
                shownAt.entries.removeAll { (k, at) -> if (hop - at > holdHops(sr)) { shownHeld.remove(k); shownSpread.remove(k); true } else false }
                // the listened partial's own reader feeds its width the same way
                if (rd != null && rd.shown) shownSpread.getOrPut(w.k) { ReadingSpread() }.push(rd.cents)
                else if (held == null) shownSpread.remove(w.k)
                if (t != null) spectrum(finder, t, w.k, held, rd?.takeIf { it.shown }, shownHeld, shownSpread)
            } finally { runAfterHop() } }
            true
        } catch (e: Exception) {
            _live.value = false
            false
        }
    }

    private fun lastSamples(n: Int): FloatArray {
        val all = ringSnapshot()
        return all.copyOfRange(all.size - n, all.size)
    }

    /** The hub's Full Spectrum: where the finder hears the A4 string's partials. */
    private fun hubSpectrum(finder: PartialFinder, f1Hz: Double) {
        val found = (1..highestPartial(f1Hz)).mapNotNull { k -> finder.near(k * f1Hz, 40.0)?.let { k to it } }
        val top = found.maxOfOrNull { it.second.levelDb } ?: run { _livePartials.value = emptyList(); _liveAudible.value = emptySet(); return }
        _livePartials.value = found.map { (k, p) -> LivePartial(k, p.hz, TuningSession.centsOff(p.hz, k * f1Hz), (1.0 + (p.levelDb - top) / LEVEL_RANGE_DB).coerceIn(0.0, 1.0)) }
        _liveAudible.value = found.map { it.first }.toSet()
    }

    /**
     * Full Spectrum on the tuning screen: the shown partials as their readers
     * read them ([activeHz] for the active one), and, from the [finder] when
     * it ran, which further partials are there to be tapped.
     */
    /** Each shown partial's level, 0..1 of the loudest, as the finder last measured it; emptied with the note. */
    private var levels: Map<Int, Double> = emptyMap()

    private fun spectrum(
        finder: PartialFinder?, t: TuningView, activeK: Int, activeHz: Double?, activeReading: PhaseReader.Reading?,
        held: Map<Int, PhaseReader.Reading>, spread: Map<Int, ReadingSpread>,
    ) {
        val read = LinkedHashMap<Int, Double>()
        activeHz?.let { read[activeK] = it }
        for ((k, r) in held) read[k] = r.hz
        // against the partial's own target, not against k * f1: the harmonic is not
        // where a piano string puts its partials (F3 on the Samick stands 11.7 c above
        // it at partial 8), so measuring from it made every clean string read sharp
        // the level of each partial from the finder when it ran this hop, else the last known
        if (finder != null) {
            val peaks = read.keys.associateWith { k -> finder.near(read[k]!!, 40.0)?.levelDb }
            val top = peaks.values.filterNotNull().maxOrNull()
            if (top != null) levels = peaks.mapNotNull { (k, db) -> db?.let { k to (1.0 + (it - top) / LEVEL_RANGE_DB).coerceIn(0.0, 1.0) } }.toMap()
        }
        _livePartials.value = read.entries.sortedBy { it.key }.map { (k, hz) ->
            LivePartial(k, hz, TuningSession.centsOff(hz, targetOf(t, k)), levels[k] ?: 1.0, spread[k]?.widthCents,
                (held[k] ?: if (k == activeK) activeReading else null)?.let { it.levelDbfs - it.noiseDbfs })
        }
        if (finder != null) {
            val cap = highestPartial(t.targetHz)
            val heard = (1..cap).filter { k -> finder.near(k * t.targetHz * Inharmonicity.ratio(k, t.b), 40.0) != null }.toSet()
            _liveAudible.value = heard + read.keys
        } else if (read.keys.any { it !in _liveAudible.value }) _liveAudible.value = _liveAudible.value + read.keys
    }

    fun stopLive() {
        if (!_live.value) return
        _live.value = false
        _liveLevel.value = 0.0
        audioSource.stop()
    }
}
