package at.clavierhaus.unisonmaster

import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.measure.LivePartial
import at.clavierhaus.unisonmaster.measure.PartialFinder
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
import at.clavierhaus.unisonmaster.tuning.PartialSelection
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
        /** The caught string is measured when decided and again this long after the strike, with the decay in. */
        const val SAMPLE_REMEASURE_S = 2.5
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
    ) {
        val cents: Double? get() = hz?.let { TuningSession.centsOff(it, targetHz) }
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
    /** Partials the tuner has switched off on the hub's Full Spectrum. */
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
            _hiddenPartials.value = PartialSelection.tap(k, _fullSpectrum.value, _liveAudible.value, _hiddenPartials.value)
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
                if (k !in available || shown.size >= MAX_SHOWN) return
                _shownPartials.value = shown + k
                setActive(k)
                wantShown()
            }
            // ... and one tap removes it again
            else -> {
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
    private class WantedSet(val targets: Map<Int, Double>, val generation: Int)
    @Volatile private var wantedSet: WantedSet? = null
    private var shownGen = 0

    private fun wantShown() {
        val t = _tuning.value ?: run { wantedSet = null; return }
        val active = _activePartial.value
        val map = if (_fullSpectrum.value) _shownPartials.value.filter { it != active }.associateWith { targetOf(t, it) } else emptyMap()
        wantedSet = WantedSet(map, ++shownGen)
    }

    private fun setActive(k: Int) {
        _activePartial.value = k
        val t = _tuning.value ?: return
        wanted = Wanted(targetOf(t, k), k, ++generation)
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
        /** Notes already measured. */
        val measured: Set<Int>,
        /**
         * Every measured note's first partial, as cents from equal
         * temperament on this session's A4: the tuning as executed.
         */
        val deviations: Map<Int, Double>,
        /** True once the temperament octave is measured throughout. */
        val temperamentComplete: Boolean,
        /** True when moving to another note registers this one as done. */
        val recordsOnLeaving: Boolean,
        /** Lowest and highest note the arrows may reach. */
        val stepLowMidi: Int,
        val stepHighMidi: Int,
        /** True once every note of the session is measured. */
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

    /** The strings proposed for sampling, lowest first. */
    fun sampleNotes(): List<Int> = session?.sampleNotes ?: _settings.value.sampleNotes

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
        )
    }

    /** "New Tuning": no session; the next Done on the hub defines A4 afresh. */
    fun resetSession() {
        caught = null
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
        setReference(snap.a4Hz)
        val s = TuningSession(_referenceA4Hz.value, _settings.value)
        for (m in snap.measurements) if (m.midi in s.notes) s.record(m)
        if (!snap.sampling) s.finishSampling()
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
     * While sampling, the reader goes where the string is: a string as it
     * stands may lie outside the band around the curve's place for it.
     */
    private fun followCoarse(w: Wanted, coarseCents: Double) {
        val t = _tuning.value ?: return
        if (!t.sampling) return
        val hz = w.hz * 2.0.pow(coarseCents / 1200.0)
        wanted = Wanted(hz, w.k, ++generation)
        _reading.value = Reading(w.k, null, hz, live = false, settling = false, coarseCents = null)
    }

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
            deviations = s.measurements.mapValues { (m, v) -> TuningSession.centsOff(v.f1Hz, TuningSession.targetF1(m, s.a4Hz)) },
            temperamentComplete = s.temperamentComplete,
            recordsOnLeaving = s.recordsOnLeaving,
            stepLowMidi = s.stepLowMidi,
            stepHighMidi = s.stepHighMidi,
            complete = s.nextUnmeasured() == null,
            sampling = s.sampling,
            samplesIn = s.samplesIn,
            samplesNeeded = s.samplesNeeded,
            samplingReady = s.samplingReady,
            caught = caught?.midi,
            caughtAccepted = caught?.accepted == true,
            curveReady = s.curve.ready,
            sampled = s.measurements.keys.filter { it != TuningSession.MIDI_A4 }.toSet(),
            breakHere = midi in s.settings.curveBreaks,
        )
        _targetHz.value = f1
        _targets.value = s.predictedPartials(midi, highestPartial(f1).coerceAtLeast(listening.k))
        _tuning.value = view
        _suggested.value = listening.k
        if (noteChanged) {
            _liveHz.value = null
            _fullSpectrum.value = false
            _hiddenPartials.value = emptySet()
            _shownPartials.value = setOf(listening.k)
            _activePartial.value = listening.k
        } else if (_activePartial.value !in _targets.value.map { it.k } && _activePartial.value != listening.k) {
            _activePartial.value = listening.k
        }
        val k = _activePartial.value
        wanted = Wanted(targetOf(view, k), k, ++generation)
        _reading.value = Reading(k, null, wanted!!.hz, live = false, settling = false, coarseCents = null)
        if (noteChanged) { _livePartials.value = emptyList(); _liveAudible.value = emptySet() }
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
            val hz = _liveHz.value ?: return null
            setReference(TuningSession.roundToTenth(hz))
            val s = TuningSession(_referenceA4Hz.value, _settings.value)
            val m = StringMeasure.measure(ringSnapshot(), audioSource.sampleRateHz, TuningSession.MIDI_A4, hz, timeMs = clock())
                ?: NoteMeasurement(TuningSession.MIDI_A4, hz, 0.0, 0.0, listOf(MeasuredPartial(1, 0.0, 0.0, 0.0)), clock())
            s.record(m)
            session = s
            advance(s)
            return _referenceA4Hz.value
        }
        val s = session ?: return null
        if (s.sampling) {
            // Accept: the string caught from the last strike, as it was measured, is kept
            val c = caught ?: return null
            val m = c.measurement ?: return null
            if (!s.record(m)) return null
            c.accepted = true
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
        return s.sampling && caught?.let { it.measurement != null && !it.accepted } == true
    }

    /** Whether the partial read matches its target now (what Done asks). */
    fun matchedNow(): Boolean {
        val r = _reading.value ?: return false
        return r.live && TuningSession.matched(r.hz, r.targetHz, _settings.value.matchHz)
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
        return m.copy(partials = (m.partials.filter { it.k != k } + own.copy(levelDb = level)).sortedBy { it.k })
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
    private class Wanted(val hz: Double, val k: Int, val generation: Int)

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
    private class Caught(val midi: Int, val f1: Double, val b: Double, val strikeHop: Long, var measurement: NoteMeasurement?, var accepted: Boolean)

    @Volatile private var caught: Caught? = null

    /** The key decided for a strike while sampling: caught, or the accepted key's measurement replaced. */
    private fun catchKey(fit: KeyIdentifier.Fit, strikeHop: Long) {
        val s = session ?: return
        if (!s.sampling || !s.selectable(fit.midi)) return
        // a key already sampled, struck again: its new measurement replaces the sample and it stays accepted
        // (A4 is the hub's reference and is only replaced by hand)
        val c = Caught(fit.midi, fit.f1, fit.b, strikeHop, null, accepted = fit.midi != TuningSession.MIDI_A4 && fit.midi in s.measurements)
        c.measurement = measureCaught(c)
        caught = c
        if (c.accepted) c.measurement?.let { s.record(it) }
        if (s.current != fit.midi) s.select(fit.midi)
        publish(s)
    }

    /** The caught string measured from the ring, from its strike ([StringMeasure]); at least its fundamental as the fit read it. */
    private fun measureCaught(c: Caught): NoteMeasurement =
        StringMeasure.measure(ringSnapshot(), audioSource.sampleRateHz, c.midi, c.f1, c.b, timeMs = clock())
            ?.takeIf { Notes.nearestMidi(it.f1Hz, _referenceA4Hz.value) == c.midi }
            ?: NoteMeasurement(c.midi, c.f1, c.b, 0.0, listOf(MeasuredPartial(1, 0.0, 0.0, 0.0)), clock())

    /** With the decay in, the caught string measured again; an accepted one is kept anew. */
    private fun remeasureCaught() {
        val s = session ?: return
        val c = caught ?: return
        if (!s.sampling) return
        c.measurement = measureCaught(c)
        if (c.accepted) c.measurement?.let { s.record(it) }
        publish(s)
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
        val shownHeld = HashMap<Int, PhaseReader.Reading>()
        var peakDb = -200.0
        val recent = DoubleArray(PhaseReader.ONSET_LOOKBACK) { -200.0 }
        var coarse: Double? = null
        // the keys the identifier chooses from: the instrument's compass, nothing below it
        var identifierLow = -1
        var identifier = KeyIdentifier(sr, DETECT_SAMPLES)
        var identifierLong = KeyIdentifier(sr, SAMPLE_LONG_SAMPLES)
        var strikeHop = -1_000_000L
        var strikePeakDb = -200.0
        var candidate: Int? = null
        var run = 0
        var followed = false
        var early: KeyIdentifier.Fit? = null
        var decided = false
        val earlyHop = ((SAMPLE_EARLY_AT_S * sr + SAMPLE_EARLY_SAMPLES) / HOP).toLong() + 1
        val longHop = ((SAMPLE_LONG_AT_S * sr + SAMPLE_LONG_SAMPLES) / HOP).toLong() + 1
        val remeasureHop = (SAMPLE_REMEASURE_S * sr / HOP).toLong()
        _live.value = true
        return try {
            audioSource.start(HOP) { chunk ->
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
                if (strike) { strikeHop = hop; strikePeakDb = db; candidate = null; run = 0; followed = false; early = null; decided = false; _heardMidi.value = null }
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
                            if (r == null) reader = PhaseReader(sr, p.hz) else r.retarget(p.hz)
                        }
                    }
                    readerGeneration = -1
                    reader?.push(chunk)?.let { rd -> if (rd.shown) _liveHz.value = rd.hz }
                    if (_fullSpectrum.value && finder != null) hubSpectrum(finder, _liveHz.value ?: _referenceA4Hz.value)
                    return@start
                }

                // the tuning screen: the reader is set on the partial listened to
                if (readerGeneration != w.generation) {
                    val r = reader
                    if (r == null) reader = PhaseReader(sr, w.hz) else r.retarget(w.hz)
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
                        pick?.let { catchKey(it, strikeHop) }
                    }
                }
                if (s != null && s.sampling && since == remeasureHop && caught?.strikeHop == strikeHop) remeasureCaught()
                // (never while sampling: the string just kept is still ringing when the screen moves on)
                if (!followed && heard != null && s != null && heard != s.current && rd?.shown != true &&
                    _settings.value.autoNote && !s.gated && !s.sampling && s.selectable(heard)) {
                    followed = true
                    selectNote(heard)
                }
                if (w.generation != generation) return@start      // the note changed meanwhile
                val held = lastShown?.takeIf { it.generation == w.generation }?.hz
                _reading.value = Reading(w.k, held, w.hz, live = rd?.shown == true, settling = rd?.settling == true, coarseCents = coarse)
                _liveHz.value = held
                val t = _tuning.value
                // the other shown partials, each by its own reader
                val ws = wantedSet
                if (ws != null && ws.generation != shownGeneration) {
                    shownGeneration = ws.generation
                    shownReaders.keys.retainAll(ws.targets.keys)
                    shownHeld.keys.retainAll(ws.targets.keys)
                    for ((k, hz) in ws.targets) {
                        val r = shownReaders[k]
                        if (r == null) shownReaders[k] = PhaseReader(sr, hz)
                        else if (abs(TuningSession.centsOff(hz, r.targetHz)) > 0.01) r.retarget(hz)
                    }
                }
                for ((k, r) in shownReaders) r.push(chunk)?.let { if (it.shown) shownHeld[k] = it }
                if (_fullSpectrum.value && t != null) spectrum(finder, t, w.k, held, shownHeld)
            }
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
    private fun spectrum(finder: PartialFinder?, t: TuningView, activeK: Int, activeHz: Double?, held: Map<Int, PhaseReader.Reading>) {
        val read = LinkedHashMap<Int, Double>()
        activeHz?.let { read[activeK] = it }
        for ((k, r) in held) read[k] = r.hz
        _livePartials.value = read.entries.sortedBy { it.key }.map { (k, hz) ->
            LivePartial(k, hz, TuningSession.centsOff(hz, k * t.targetHz), 1.0)
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
