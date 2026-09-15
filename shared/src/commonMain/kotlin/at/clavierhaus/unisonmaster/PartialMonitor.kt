package at.clavierhaus.unisonmaster

import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.dsp.BeatRate
import at.clavierhaus.unisonmaster.dsp.Goertzel
import at.clavierhaus.unisonmaster.dsp.InharmonicityFit
import at.clavierhaus.unisonmaster.dsp.PreciseF0
import at.clavierhaus.unisonmaster.dsp.StringModel
import at.clavierhaus.unisonmaster.dsp.Yin
import at.clavierhaus.unisonmaster.model.CapturedString
import at.clavierhaus.unisonmaster.model.PartialLevel
import at.clavierhaus.unisonmaster.model.StringSlot
import at.clavierhaus.unisonmaster.tuning.Notes
import at.clavierhaus.unisonmaster.unison.LiveIdentification
import at.clavierhaus.unisonmaster.unison.UnisonState
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live partial measurement for one selected note (C2..C6).
 *
 * Overlapping-window scheme: audio arrives in hops of [hopSize] samples and
 * is shifted into a ring of [windowSize] samples; each hop re-analyzes the
 * full window. At 48 kHz with 16384/4096 this yields ~12 updates/s with the
 * frequency resolution of the full 341 ms window — smooth meters without
 * sacrificing low-end resolution.
 *
 * Reference pitch and temperament are read live from [tuning], so moving the
 * A4 slider retunes the Goertzel bank on the next hop.
 */
class PartialMonitor(
    private val audioSource: AudioSource,
    private val tuning: TuningController,
    // 16, not 8. Tuners judge a unison at the highest partial they can
    // reliably isolate, because the beat there is k times the beat at the
    // fundamental. For A3 on a 225 that partial is 10 (~2240 Hz, ~C#7),
    // which an 8-partial ceiling cannot even measure. Partials above
    // Nyquist are skipped automatically.
    val partialCount: Int = 16,
    private val windowSize: Int = 16384,
    private val hopSize: Int = 4096,
) {
    private val analyzer = PartialAnalyzer(audioSource.sampleRateHz, windowSize)

    private val _selectedMidi = MutableStateFlow(60) // C4
    val selectedMidi: StateFlow<Int> = _selectedMidi.asStateFlow()

    val scope = at.clavierhaus.unisonmaster.dsp.Scope(audioSource.sampleRateHz)

    /** Sample rate of the input, for analysers attached through [addTap]. */
    val sampleRateHz: Int get() = audioSource.sampleRateHz

    // Raw-buffer taps: further analysers receive every input buffer before the
    // monitor's own processing. The list is replaced, never mutated, so the
    // audio thread always iterates a stable snapshot.
    private var taps: List<(FloatArray) -> Unit> = emptyList()

    /** Attach an analyser that receives every raw input buffer. */
    fun addTap(tap: (FloatArray) -> Unit) {
        taps = taps + tap
    }

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    /**
     * Automatic note identification. When enabled, each hop with signal
     * present runs YIN over the ring (60–1100 Hz, covering C2–C6
     * fundamentals); the nearest 12-TET note under the current reference is
     * adopted after two consecutive agreeing detections within ±45 cents.
     * Known limitation: weak-fundamental bass notes risk octave errors
     * (detection locking onto partial 2); manual stepping remains available
     * as override and disables auto via the UI.
     */
    private val _autoDetect = MutableStateFlow(true)
    val autoDetect: StateFlow<Boolean> = _autoDetect.asStateFlow()

    fun setAutoDetect(enabled: Boolean) {
        _autoDetect.value = enabled
    }

    /**
     * Note lock: once the tuner has decided which note to work on, locking
     * freezes the selection — auto identification keeps running silently
     * but cannot retarget, so test blows on neighboring keys or a partner's
     * playing can't yank the analysis away mid-unison. Manual stepping
     * implies a decision and engages the lock.
     */
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    fun setLocked(locked: Boolean) {
        _locked.value = locked
    }

    fun toggleLock() {
        _locked.value = !_locked.value
    }

    private var pendingMidi = -1
    private var pendingCount = 0

    private val detectVotes = IntArray(DETECT_WINDOW) { -1 }
    private var detectVoteIdx = 0
    private var liveDriftFrames = 0
    private var detectVoteCount = 0

    /**
     * Corrects a coarse estimate that has landed an octave (or twelfth) out.
     *
     * YIN minimises a lag-domain difference, so when a piano's fundamental is
     * weak relative to its second partial it happily reports twice the true
     * frequency. Deciding the octave is a spectral question, so it is settled
     * spectrally: a lower candidate is accepted only if it carries real
     * energy of its own AND its odd partial is present — the pattern a true
     * fundamental leaves and an octave error does not.
     */
    private fun correctOctave(buffer: FloatArray, sampleRateHz: Double, coarseHz: Double): Double {
        fun mag(f: Double): Double =
            if (f < 20.0) 0.0 else sqrt(Goertzel.power(buffer, sampleRateHz, f))

        var f = coarseHz
        // Try two levels down; a twelfth error is rarer but does occur.
        repeat(2) {
            val half = f / 2.0
            if (half >= 55.0) {
                val mHalf = mag(half)
                val mFull = mag(f)
                val mOdd = mag(half * 3.0) // second odd partial of the lower candidate
                if (mFull > 0.0 && mHalf > OCTAVE_OWN_ENERGY * mFull &&
                    mOdd > OCTAVE_ODD_ENERGY * mFull
                ) {
                    f = half
                }
            }
        }
        return f
    }

    /**
     * Which partials contribute to the displayed curve. Each partial is the
     * output of its own narrow filter (Goertzel at the target, Blackman-
     * Harris windowed, ~-92 dB rejection of neighbors); disabling a partial
     * excludes that filter's contribution. All partials remain measured
     * regardless, so re-enabling is instant and data stays complete.
     */
    private val _enabledPartials = MutableStateFlow((1..partialCount).toSet())
    val enabledPartials: StateFlow<Set<Int>> = _enabledPartials.asStateFlow()

    /**
     * Selects a single partial, exclusively. Once the three strings are
     * sampled the operator works on ONE partial — the display shows that
     * partial's three bells and nothing else — so selection there is a
     * choice among partials, not a set of toggles.
     */
    fun selectOnlyPartial(k: Int) {
        if (k in 1..partialCount) _enabledPartials.value = setOf(k)
    }

    /** True once every slot holds a captured string. */
    val samplingComplete: Boolean get() = _captured.value.size == StringSlot.entries.size

    fun togglePartial(k: Int) {
        if (k !in 1..partialCount) return
        val current = _enabledPartials.value
        _enabledPartials.value = if (k in current) current - k else current + k
    }

    private val _levels = MutableStateFlow<List<PartialLevel>>(emptyList())
    val levels: StateFlow<List<PartialLevel>> = _levels.asStateFlow()

    /**
     * Fitted stiff-string model of the sounding string (f1 and the
     * inharmonicity coefficient B). Partial targets are taken from this
     * model, so displayed deviations are residuals against the string's own
     * physics rather than against an ideal harmonic series.
     */
    private val _stringModel = MutableStateFlow<StringModel?>(null)
    val stringModel: StateFlow<StringModel?> = _stringModel.asStateFlow()

    /**
     * False once the model has not been re-fitted for a while (the tone has
     * decayed or was never strong enough). The model is deliberately held in
     * that state, but a held value must never masquerade as a live reading.
     */
    /**
     * RMS residual of the fitted model against the measured partials, in
     * cents — the honest quality indicator for a reading. A large residual
     * means the stiff-string model is not describing this string well, so
     * f0 and B should not be trusted to their printed precision. Introduced
     * after a Bosendorfer 225 recording in which f0 excursions of ~0.9 Hz
     * coincided with B swings, with nothing on screen signalling that the
     * fit had degraded.
     */
    private val _fitResidualCents = MutableStateFlow<Double?>(null)
    val fitResidualCents: StateFlow<Double?> = _fitResidualCents.asStateFlow()

    /**
     * Departure of the sounding first partial from the captured reference
     * string, in cents, and the corresponding beat rate at the fundamental
     * in Hz. Null when no reference is captured or nothing is measured.
     *
     * Note this is a *computed* frequency difference between two
     * measurements, not an observation of envelope modulation, so it is not
     * subject to the hop-rate bound that limits observing beats as
     * modulation (§10/I12). At partial k the beat is approximately k times
     * the value reported here.
     */
    private val _refDeltaCents = MutableStateFlow<Double?>(null)
    val refDeltaCents: StateFlow<Double?> = _refDeltaCents.asStateFlow()

    private val _refBeatHz = MutableStateFlow<Double?>(null)
    val refBeatHz: StateFlow<Double?> = _refBeatHz.asStateFlow()

    /**
     * Departure from the reference AT THE ARMED PARTIAL, in cents and as a
     * beat rate. The fundamental figures above are not interchangeable with
     * these: the same error reads 16 ¢ / 2.1 Hz at the fundamental and
     * 16 ¢ / 20.7 Hz at partial 10, and reporting the fundamental beneath a
     * display of partial 10 understates what the ear hears by the partial
     * number.
     */
    private val _armedDeltaCents = MutableStateFlow<Double?>(null)
    val armedDeltaCents: StateFlow<Double?> = _armedDeltaCents.asStateFlow()

    private val _armedBeatHz = MutableStateFlow<Double?>(null)
    val armedBeatHz: StateFlow<Double?> = _armedBeatHz.asStateFlow()

    /**
     * The listening partial: the highest partial currently measured with a
     * trustworthy level. This is the partial a tuner judges the unison on,
     * because beating there is k times the beating at the fundamental — the
     * fundamental is the least sensitive detector available, not the most.
     */
    private val _listeningPartial = MutableStateFlow<Int?>(null)
    val listeningPartial: StateFlow<Int?> = _listeningPartial.asStateFlow()

    /** Beat rate in Hz at [listeningPartial] against the captured reference. */
    private val _listeningBeatHz = MutableStateFlow<Double?>(null)
    val listeningBeatHz: StateFlow<Double?> = _listeningBeatHz.asStateFlow()

    /**
     * Per-partial estimate of remaining usable life, in seconds: how long
     * the partial is predicted to stay above the trust threshold at its
     * current level and measured decay rate. Index 0 = partial 1.
     *
     * This, not level, is what makes a partial tunable. Upper partials are
     * frequently loud at the attack and gone within half a second, which is
     * useless to work on; a quieter partial that sustains is worth more.
     */
    private val _sustainSeconds = MutableStateFlow<List<Double>>(emptyList())
    val sustainSeconds: StateFlow<List<Double>> = _sustainSeconds.asStateFlow()

    /**
     * Partials currently fit to tune on: measurable, and predicted to
     * sustain at least [MIN_TUNABLE_SECONDS]. The highest of these is the
     * sensitive one — the beat at partial k is k times the beat at the
     * fundamental, so the fundamental is the *worst* available detector of
     * a unison error and the highest reliable partial the best.
     */
    private val _tunablePartials = MutableStateFlow<Set<Int>>(emptySet())
    val tunablePartials: StateFlow<Set<Int>> = _tunablePartials.asStateFlow()

    /** Highest tunable partial, or null. The recommended working partial. */
    private val _recommendedPartial = MutableStateFlow<Int?>(null)
    val recommendedPartial: StateFlow<Int?> = _recommendedPartial.asStateFlow()

    /**
     * Partials actually worth offering to the operator.
     *
     * Before all three strings are captured this is the live tunable set.
     * Once the unison is complete it becomes the **intersection** across the
     * three strings: a partial that dies on one string cannot be used to
     * compare it with the others, however well it sustains elsewhere.
     * Partials outside this set are of no value and are not shown.
     */
    /**
     * Slots whose captured inharmonicity is implausible beside the others.
     *
     * The strings of a unison share speaking length and wire, so their B
     * values agree closely — measured within ±8 % on two instruments. A slot
     * differing by more than [SUSPECT_B_FRACTION] from the median of the
     * others was therefore not measuring that string cleanly, whatever the
     * residual said at the time. Flagged rather than rejected: the operator
     * decides, and a genuine oddity should be visible rather than silently
     * discarded.
     */
    private val _suspectSlots = MutableStateFlow<Set<StringSlot>>(emptySet())
    val suspectSlots: StateFlow<Set<StringSlot>> = _suspectSlots.asStateFlow()

    /**
     * Partials that survived on at least ONE string — the union. Offered for
     * display alongside [usablePartials] (the intersection) so a partial that
     * a string dropped is visible as dropped, rather than vanishing without
     * explanation. Loosening thresholds blind was costing an iteration each
     * time a partial the operator could plainly hear failed to appear.
     */
    private val _candidatePartials = MutableStateFlow<Set<Int>>(emptySet())
    val candidatePartials: StateFlow<Set<Int>> = _candidatePartials.asStateFlow()

    /** For each candidate not in the intersection, the strings that lack it. */
    private val _missingOn = MutableStateFlow<Map<Int, Set<StringSlot>>>(emptyMap())
    val missingOn: StateFlow<Map<Int, Set<StringSlot>>> = _missingOn.asStateFlow()

    private val _usablePartials = MutableStateFlow<Set<Int>>(emptySet())
    val usablePartials: StateFlow<Set<Int>> = _usablePartials.asStateFlow()

    /**
     * Per partial, the largest beat rate in Hz between any two captured
     * strings — the audible defect at that partial. Note this grows with k:
     * the beat at partial k is k times the beat at the fundamental, which is
     * why the highest usable partial is the sensitive place to work.
     */
    private val _capturedBeatsHz = MutableStateFlow<Map<Int, Double>>(emptyMap())

    /**
     * Beat rate in Hz at each usable partial between the **currently
     * sounding** string and the captured reference — the number to tune by.
     *
     * Captured models are snapshots: the instant a pin is turned they
     * describe a state that no longer exists. Annotating the partial bar
     * from capture-to-capture comparison therefore showed the unison as it
     * was before the work, unchanged, while the operator tuned it — the
     * defect this replaces. Falls back to the capture comparison only when
     * nothing is sounding.
     */
    private val _unisonBeatsHz = MutableStateFlow<Map<Int, Double>>(emptyMap())
    val unisonBeatsHz: StateFlow<Map<Int, Double>> = _unisonBeatsHz.asStateFlow()

    /**
     * Publishes the derived unison sets. The arithmetic lives in
     * [UnisonState]; this function only moves its result into the flows the
     * UI observes. Thresholds stay here, because this is where
     * ENGINEERING.md documents them.
     */
    private fun recomputeUnison() {
        val sets = UnisonState.sets(
            captured = _captured.value,
            liveTunable = _tunablePartials.value,
            slotCount = StringSlot.entries.size,
            suspectBFraction = SUSPECT_B_FRACTION,
        )
        _suspectSlots.value = sets.suspectSlots
        _usablePartials.value = sets.usable
        _candidatePartials.value = sets.candidates
        _missingOn.value = sets.missingOn
        _capturedBeatsHz.value = sets.capturedBeatsHz
    }

    /**
     * Input level in dBFS and the raw pitch estimate, published before any
     * gating or voting. Diagnostic: it distinguishes "no audio is arriving"
     * from "audio arrives but the note is not being identified", which a
     * screen recording cannot.
     */
    private val _inputLevelDb = MutableStateFlow(-140.0)
    val inputLevelDb: StateFlow<Double> = _inputLevelDb.asStateFlow()

    private val _rawDetectHz = MutableStateFlow<Double?>(null)
    val rawDetectHz: StateFlow<Double?> = _rawDetectHz.asStateFlow()

    private val _modelFresh = MutableStateFlow(false)
    val modelFresh: StateFlow<Boolean> = _modelFresh.asStateFlow()

    private var hopsSinceFit = Int.MAX_VALUE

    /**
     * Captured reference string. Unison work needs a FIXED yardstick: while
     * two strings sound, each partial's measured frequency is the
     * amplitude-weighted mean of both, so a live fit absorbs the unison
     * error and the display would measure deviation against a moving
     * target. Capturing the model from a single sounding string freezes the
     * reference; subsequent strings are then measured against that string's
     * own physics.
     */
    /**
     * Which captured string currently plays the reference role.
     *
     * The role is assigned at comparison time and may be held by ANY of the
     * three: a tuner matches strings to whichever one is chosen, and nothing
     * about a slot's position makes it a better yardstick. Sampling and
     * storage are therefore identical for all three slots — the previous
     * design privileged CENTER in storage, which is what made the third
     * string behave differently from the first two.
     */
    private val _referenceSlot = MutableStateFlow<StringSlot?>(null)
    val referenceSlot: StateFlow<StringSlot?> = _referenceSlot.asStateFlow()

    private val _referenceModel = MutableStateFlow<StringModel?>(null)
    val referenceModel: StateFlow<StringModel?> = _referenceModel.asStateFlow()

    /**
     * Assigns the reference role to whichever captured string lies closest
     * to equal temperament for this note, under the A4 measured at the hub.
     *
     * That is the string the other two should be brought to: it is already
     * where the note belongs, so matching the unison to it moves the pitch
     * of the note least. Any of the three may hold the role — nothing about
     * a slot's position qualifies it — and the operator can override.
     */
    fun chooseReferenceByTemperament() {
        val caps = _captured.value
        if (caps.isEmpty()) return
        // The CENTRE string is the reference, not a candidate chosen by
        // measurement.
        //
        // A temperament is laid with a felt strip muting the outer strings,
        // so the centre string of each unison is tuned by ear against the
        // temperament and is by definition where the note belongs. It is
        // never touched during unison work: the outer strings are brought to
        // it. Selecting the reference by proximity to equal temperament could
        // therefore hand the role to an outer string — which also made the
        // centre string eligible to be identified as "sounding" and to have
        // its mark moved. If an orange mark moves, something is wrong.
        val target = tuning.temperament.value
            .frequencyOf(_selectedMidi.value, tuning.referenceA4Hz.value)
        val slot = UnisonState.chooseReference(caps, target)
        _referenceSlot.value = slot
        // Preserved exactly from I48: the accumulated current position is
        // cleared only when the CENTRE string takes the role, not when an
        // outer string does.
        //
        // [open] That asymmetry means a reference chosen by temperament can
        // keep a current position, and a mark that should be fixed can move
        // — which is the condition I38 recorded as "if an orange mark moves,
        // something is wrong". Left alone here on purpose: this commit
        // changes structure and nothing else. It wants its own test and its
        // own deploy.
        if (slot == StringSlot.CENTER) _currentModels.value = _currentModels.value - slot
        syncReferenceModel()
    }

    /** Assigns the reference role, or clears it. Slot must be captured. */
    fun setReferenceSlot(slot: StringSlot?) {
        _referenceSlot.value = slot?.takeIf { _captured.value.containsKey(it) }
        // A slot may have accumulated a current position before the role was
        // assigned; the reference has none by definition.
        _referenceSlot.value?.let { _currentModels.value = _currentModels.value - it }
        syncReferenceModel()
    }

    private fun syncReferenceModel() {
        val slot = _referenceSlot.value
        _referenceModel.value = slot?.let { _captured.value[it]?.model }
    }

    /**
     * Whether two settled readings describe the same string: pitch within
     * [AGREE_CENTS] and inharmonicity within [AGREE_B_FRACTION]. B is the
     * more discriminating of the two, since it is a property of the wire and
     * its terminations and barely moves with tuning, so a large disagreement
     * means the two readings were not of the same thing.
     */
    /** True when every reading in [models] agrees with every other. */
    private fun allAgree(models: List<StringModel>): Boolean =
        UnisonState.allAgree(models, AGREE_CENTS, AGREE_B_FRACTION)

    private fun modelsAgree(a: StringModel, b: StringModel): Boolean =
        UnisonState.modelsAgree(a, b, AGREE_CENTS, AGREE_B_FRACTION)

    /** Freezes the current fit as the reference. No-op if nothing fitted. */
    fun captureReference() {
        _stringModel.value?.let { _referenceModel.value = it }
    }

    // ---- Three-string unison workflow -------------------------------------

    /**
     * The strings of the unison, each measured and frozen separately and
     * **identically**. No slot is privileged: the reference role is held by
     * whichever slot the operator assigns it to (see [referenceSlot]).
     */
    /**
     * The slot currently listening, if any. Arming is the middle of three
     * states: idle, listening, captured. While armed the monitor watches for
     * a measurement good enough to keep and captures it by itself — the
     * operator should not have to judge the moment a reading became
     * trustworthy when the software already evaluates exactly that.
     */
    private val _armedSlot = MutableStateFlow<StringSlot?>(null)
    val armedSlot: StateFlow<StringSlot?> = _armedSlot.asStateFlow()

    private var armStableFrames = 0
    private var armBlowCount = 0

    /**
     * How many armed frames each partial was tunable in. The stored tunable
     * set must not be the instantaneous set at the moment of capture: a
     * capture landing mid-decay, or during a momentary mute contact, would
     * record two or three partials and then poison the intersection for the
     * whole unison. Accumulating across the arming period records what the
     * string actually offered.
     */
    private val armTunableCounts = HashMap<Int, Int>()
    private var armFrames = 0

    /**
     * The model from the first settled strike while armed. A capture is only
     * taken when a second settled strike **agrees** with it. Counting two
     * strikes was not enough: an involuntary mute contact that sounds the
     * string is a strike, and a capture built on one is silently wrong — a
     * string was captured with B = 7.5e-4 while the other two of the same
     * unison sat at 2.5e-4. Two readings that agree cannot both be accidents.
     */
    private val armSettled = ArrayList<StringModel>()
    private var armSettledThisBlow = false

    /**
     * Strikes counted since the slot was armed. A capture requires at least
     * [MIN_CAPTURE_BLOWS]: one blow can be atypical — a glancing strike, a
     * momentary mute contact — and a string worth freezing as a yardstick
     * should have shown the same thing twice.
     */
    private val _armBlows = MutableStateFlow(0)
    val armBlows: StateFlow<Int> = _armBlows.asStateFlow()

    /** Blows required before an armed slot may capture. */
    val requiredBlows: Int get() = MIN_CAPTURE_BLOWS

    /** Arms [slot] for capture, or disarms when passed null or the same slot. */
    fun armString(slot: StringSlot?) {
        _armedSlot.value = if (_armedSlot.value == slot) null else slot
        armStableFrames = 0
        armBlowCount = 0
        armTunableCounts.clear()
        armFrames = 0
        armSettled.clear()
        armSettledThisBlow = false
        _armBlows.value = 0
        // Each string is measured from nothing.
        if (_armedSlot.value != null) clearMeasurementState()
        // Arming implies listening: a slot that cannot hear anything would
        // pulse indefinitely with no way for the operator to know why.
        if (_armedSlot.value != null && !_listening.value) start()
    }

    private val _captured = MutableStateFlow<Map<StringSlot, CapturedString>>(emptyMap())
    val captured: StateFlow<Map<StringSlot, CapturedString>> = _captured.asStateFlow()

    /** Freezes the current fit and levels into [slot]. No-op if nothing fitted. */
    fun captureString(slot: StringSlot) = captureString(slot, null)

    /** [explicitModel] captures a specific reading (the median of an
        agreeing set) instead of whatever the live fit holds right now. */
    fun captureString(slot: StringSlot, explicitModel: StringModel?) {
        val model = explicitModel ?: _stringModel.value ?: return
        val levels = _levels.value.associate { it.index to it.levelDb }
        // Partials offered in at least a quarter of the armed frames, rather
        // than whatever happened to be tunable in the final instant.
        val accumulated = if (armFrames >= 8) {
            // Offered in at least ~15 % of the sounding frames. The set is
            // then intersected across three strings, so a strict per-string
            // share compounds: three independent 22 % tests leave very little
            // standing, which is why a display full of usable partials
            // collapsed to four. The intersection is the real filter.
            armTunableCounts.filterValues { it * 20 >= armFrames * 3 }.keys.toSet()
        } else {
            _tunablePartials.value
        }
        val tunableForSlot = if (accumulated.isEmpty()) _tunablePartials.value else accumulated
        _captured.value = _captured.value +
            (slot to CapturedString(slot, model, levels, tunableForSlot))
        // The first capture takes the role provisionally.
        if (_referenceSlot.value == null) _referenceSlot.value = slot
        syncReferenceModel()
        recomputeUnison()
        // Sampling complete: the operator now works on ONE chosen partial,
        // so everything but the fundamental is deselected rather than left
        // on. Tapping a partial adds it; the fundamental is the anchor.
        if (_captured.value.size == StringSlot.entries.size) {
            _enabledPartials.value = setOf(1)
            // The reference is the string already closest to where equal
            // temperament puts this note, given the A4 measured at the start
            // — the string that needs least moving. It may be any of the
            // three; the operator can still reassign it.
            val target = tuning.temperament.value
                .frequencyOf(_selectedMidi.value, tuning.referenceA4Hz.value)
            _captured.value.entries
                .minByOrNull { (_, cap) -> abs(Notes.centsOff(cap.model.partialHz(1), target)) }
                ?.let { (best, _) -> _referenceSlot.value = best }
            syncReferenceModel()
        }
    }

    fun clearString(slot: StringSlot) {
        _captured.value = _captured.value - slot
        _currentModels.value = _currentModels.value - slot
        if (_referenceSlot.value == slot) {
            _referenceSlot.value = _captured.value.keys.firstOrNull { it != slot }
        }
        syncReferenceModel()
        recomputeUnison()
    }

    /** Installs a current position for a slot. Tests only. */
    fun setCurrentModelForTest(slot: StringSlot, model: StringModel) {
        _currentModels.value = _currentModels.value + (slot to model)
    }

    /** Installs a live model directly. Tests only. */
    fun setModelForTest(model: StringModel?) {
        _stringModel.value = model
    }

    /** Installs a captured string directly. Tests and session restore. */
    fun captureStringForTest(slot: StringSlot, model: StringModel, tunable: Set<Int>) {
        _captured.value = _captured.value +
            (slot to CapturedString(slot, model, emptyMap(), tunable))
        syncReferenceModel()
        recomputeUnison()
        if (_captured.value.size == StringSlot.entries.size) chooseReferenceByTemperament()
    }

    /** Full reset: forget every sampled string and start again. */
    fun resetSampling() {
        _armedSlot.value = null
        clearMeasurementState()
        _unisonBeatsHz.value = emptyMap()
        _capturedBeatsHz.value = emptyMap()
        _usablePartials.value = emptySet()
        _suspectSlots.value = emptySet()
        _recommendedPartial.value = null
        _workingPartial.value = null
        armStableFrames = 0
        armBlowCount = 0
        armTunableCounts.clear()
        armFrames = 0
        armSettled.clear()
        armSettledThisBlow = false
        _armBlows.value = 0
        // Each string is measured from nothing.
        if (_armedSlot.value != null) clearMeasurementState()
        _enabledPartials.value = (1..partialCount).toSet()
        clearAllStrings()
    }

    fun clearAllStrings() {
        _captured.value = emptyMap()
        _currentModels.value = emptyMap()
        _referenceSlot.value = null
        _referenceModel.value = null
        recomputeUnison()
    }

    /**
     * Partial indices where two or more captured strings agree within
     * [COINCIDENCE_CENTS] — i.e. where the unison is closed at that partial.
     */
    fun coincidentPartials(): Set<Int> {
        val models = _captured.value.values.map { it.model }
        if (models.size < 2) return emptySet()
        val out = HashSet<Int>()
        for (k in 1..partialCount) {
            val fs = models.map { it.partialHz(k) }
            for (i in fs.indices) for (j in i + 1 until fs.size) {
                if (abs(Notes.centsOff(fs[i], fs[j])) <= COINCIDENCE_CENTS) {
                    out += k
                }
            }
        }
        return out
    }

    fun clearReference() {
        _referenceSlot.value = null
        _referenceModel.value = null
    }

    /** Installs a reference captured elsewhere (used by tests and, later,
        by session restore). */
    fun setReferenceForTest(model: StringModel?) {
        _referenceModel.value = model
    }

    /**
     * Display gain in dB, added to measured levels before meter mapping.
     * Pure display-domain: UNPROCESSED capture has no AGC by design, so a
     * piano at mic distance peaks far below full scale; this shifts the
     * meter range onto the real signal without touching the audio path.
     */
    private val _gainDb = MutableStateFlow(30.0)
    val gainDb: StateFlow<Double> = _gainDb.asStateFlow()

    /** Meter floor in dB (after gain); deeper floor = longer visible decay. */
    private val _floorDb = MutableStateFlow(-90.0)
    val floorDb: StateFlow<Double> = _floorDb.asStateFlow()

    fun setGain(db: Double) {
        _gainDb.value = db.coerceIn(0.0, 60.0)
    }

    fun cycleFloor() {
        val steps = listOf(-60.0, -90.0, -120.0)
        val i = steps.indexOf(_floorDb.value)
        _floorDb.value = steps[(i + 1).mod(steps.size)]
    }

    /** Meter bar fraction (0..1) for [levelDb] under the current gain/floor. */
    fun barFraction(levelDb: Double): Double {
        val floor = _floorDb.value
        return ((levelDb + _gainDb.value - floor) / -floor).coerceIn(0.0, 1.0)
    }

    private val ring = FloatArray(windowSize)
    private var filled = 0

    // ---- Display smoothing stage (measurement -> publication) ----
    // A settled single string is quasi-static; per-hop jitter is measurement
    // noise (phase variance ~ 1/SNR). Smoothing constants (per ~85 ms hop):
    private class SmoothState {
        var levelDb = Double.NaN
        var freqHz = Double.NaN
    }

    private val smoothStates = Array(partialCount) { SmoothState() }
    private var smoothedMidi = -1
    private var prevRms = 0.0

    /**
     * Slowly-tracked background level, used to detect a strike as a RISE
     * rather than as the crossing of a fixed threshold.
     *
     * An absolute threshold assumes the sound falls below it between
     * strikes. In a room with an open piano still ringing it may not, and
     * then no onset is ever detected: no strike is counted, the capture
     * counter never advances, and — since a strike is what re-seeds the
     * model — a corrupted model is never cleared either. Both were observed
     * together in the field, which is what identified this.
     */
    private var noiseFloorRms = 0.0

    /**
     * Hops remaining before frequency re-seeding after an onset. The first
     * windows after a strike still contain pre-strike content (and the
     * attack transient itself, cf. the A4 protocol's attack exclusion);
     * seeding the frequency EMA from them biases the display. 3 hops
     * ~ 256 ms.
     */
    private var freqSeedDelay = 0

    /**
     * Wipes every trace of the note measured so far: smoothing, detector
     * histories, envelopes, the fitted model and everything derived from it.
     *
     * Called on a full reset and **whenever a slot is armed**, because each
     * string must be measured from nothing. Without this the envelopes and
     * histories still hold the previous string, so the first seconds of a
     * new sample are contaminated by the last one — remnants of earlier
     * keystrokes appearing in a fresh measurement.
     */
    private fun clearMeasurementState() {
        resetSmoothing()
        for (h in histories) h.reset()
        for (e in envelopes) e.reset()
        fineEnvelope.reset()
        fineEnvelopePartial = -1
        _stringModel.value = null
        _modelFresh.value = false
        hopsSinceFit = Int.MAX_VALUE
        smoothedMidi = -1
        filled = 0
        _levels.value = emptyList()
        _fitResidualCents.value = null
        _sustainSeconds.value = emptyList()
        _tunablePartials.value = emptySet()
        _measuredBeatHz.value = emptyMap()
        _liveSlot.value = null
        _refDeltaCents.value = null
        _refBeatHz.value = null
        prevRms = 0.0
        noiseFloorRms = 0.0
        freqSeedDelay = 0
    }

    private fun resetSmoothing() {
        for (s in smoothStates) {
            s.levelDb = Double.NaN
            s.freqHz = Double.NaN
        }
    }

    /** Rolling raw-measurement history per partial for unison detection. */
    private class PartialHistory {
        val level = DoubleArray(24) { Double.NaN }
        val freq = DoubleArray(24) { Double.NaN }
        var idx = 0
        var count = 0
        var flagCounter = 0
        var flag = false

        /** Decay slope in dB per frame from the last linear fit, or NaN. */
        var slopeDbPerFrame = Double.NaN

        fun reset() {
            for (i in level.indices) {
                level[i] = Double.NaN
                freq[i] = Double.NaN
            }
            idx = 0; count = 0; flagCounter = 0; flag = false
            slopeDbPerFrame = Double.NaN
        }

        fun push(levelDb: Double, freqHz: Double) {
            level[idx] = levelDb
            freq[idx] = freqHz
            idx = (idx + 1) % level.size
            if (count < level.size) count++
        }
    }

    private val histories = Array(partialCount) { PartialHistory() }

    /**
     * Long envelope history per partial, for direct beat measurement. Longer
     * than the detector history because a near-null beat is slow: two
     * periods of a 0.25 Hz beat need eight seconds.
     */
    private class EnvelopeHistory {
        val db = DoubleArray(ENVELOPE_FRAMES) { Double.NaN }
        var idx = 0
        var count = 0
        fun reset() {
            for (i in db.indices) db[i] = Double.NaN
            idx = 0; count = 0
        }
        fun push(levelDb: Double) {
            db[idx] = levelDb
            idx = (idx + 1) % db.size
            if (count < db.size) count++
        }
        /** Chronological copy, oldest first, or null if not yet full enough. */
        fun ordered(minCount: Int): DoubleArray? {
            if (count < minCount) return null
            val out = DoubleArray(count)
            for (j in 0 until count) out[j] = db[(idx - count + j).mod(db.size)]
            return out
        }
    }

    private val envelopes = Array(partialCount) { EnvelopeHistory() }

    /**
     * Fine envelope of the ARMED partial, sampled several times per analysis
     * hop.
     *
     * The beat rate is a modulation of the partial's amplitude, so Nyquist
     * applies to how often that amplitude is read: one reading per hop is
     * 11.7 per second and cannot show a beat above 5.9 per second. That
     * ceiling falls inside the working range — a string 10 ¢ out beats at
     * 6–13 per second at partial 5 — so the display would stall exactly
     * during the coarse approach. Reading the armed partial every
     * [ENVELOPE_SUB_BLOCK] samples raises it to about 23 per second.
     *
     * The shorter block widens the filter to roughly 90 Hz, which is
     * harmless here: partials of one note are spaced by f0 (221 Hz at A3) so
     * no neighbour leaks in, while the two unison components lie a fraction
     * of a hertz apart and both remain inside — their interference is the
     * beat, and excluding either would destroy the measurement.
     */
    private val fineEnvelope = FineEnvelope()
    private var fineEnvelopePartial = -1

    private class FineEnvelope {
        val db = DoubleArray(FINE_ENVELOPE_FRAMES) { Double.NaN }
        var idx = 0
        var count = 0
        fun reset() {
            for (i in db.indices) db[i] = Double.NaN
            idx = 0; count = 0
        }
        fun push(v: Double) {
            db[idx] = v
            idx = (idx + 1) % db.size
            if (count < db.size) count++
        }
        fun ordered(minCount: Int): DoubleArray? {
            if (count < minCount) return null
            val out = DoubleArray(count)
            for (j in 0 until count) out[j] = db[(idx - count + j).mod(db.size)]
            return out
        }
    }

    /**
     * Directly measured beat rate per partial, in beats per second, from the
     * amplitude modulation of that partial with both strings sounding. This
     * is what the tuner hears and nulls; it is not derived from the captured
     * reference and remains valid when the two components are far too close
     * to separate spectrally.
     */
    private val _measuredBeatHz = MutableStateFlow<Map<Int, Double>>(emptyMap())
    val measuredBeatHz: StateFlow<Map<Int, Double>> = _measuredBeatHz.asStateFlow()

    /**
     * The partial the operator is working on: the highest partial that is
     * both usable and enabled. Highest, because the beat at partial k is k
     * times the beat at the fundamental.
     */
    /**
     * Which sampled string is sounding now, identified by proximity to the
     * stored positions.
     *
     * The sounding string is one of the three already on screen, not a
     * fourth thing: drawing it separately duplicated a mark and made the
     * picture unreadable. Identity is fixed at the strike and held while the
     * tone rings, so a string being pulled toward the reference cannot
     * re-identify as its neighbour halfway through the adjustment. The
     * reference itself is excluded as a candidate: it is the yardstick
     * rather than a string being moved, and excluding it also removes the
     * ambiguity that would otherwise arise exactly as a string converges.
     */
    /**
     * The latest good measurement of each sampled string, which is what the
     * marks show.
     *
     * A capture is a snapshot; the moment a string is adjusted it describes a
     * state that no longer exists. Drawing the snapshots meant a string just
     * brought into tune sprang back to its old position as soon as it stopped
     * sounding, so the picture showed the unison as it was rather than as it
     * is. The captured models are retained unchanged — they set the scale, so
     * that stays fixed while the marks move within it.
     */
    private val _currentModels = MutableStateFlow<Map<StringSlot, StringModel>>(emptyMap())
    val currentModels: StateFlow<Map<StringSlot, StringModel>> = _currentModels.asStateFlow()

    private val _liveSlot = MutableStateFlow<StringSlot?>(null)
    val liveSlot: StateFlow<StringSlot?> = _liveSlot.asStateFlow()

    private val _workingPartial = MutableStateFlow<Int?>(null)
    val workingPartial: StateFlow<Int?> = _workingPartial.asStateFlow()

    /**
     * Multi-component test on one partial's recent raw history.
     * A single decaying string is exponential in amplitude = linear in dB:
     * fit a line to the valid dB samples and test the residual scatter.
     * Beating additionally wobbles the phase-derived frequency; test its
     * scatter against single-string phase noise. Either fingerprint counts.
     */
    private fun multiCandidate(h: PartialHistory): Boolean {
        if (h.count < MIN_DETECT_FRAMES) return false
        var n = 0
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        for (j in 0 until h.count) {
            val pos = (h.idx - h.count + j).mod(h.level.size)
            val y = h.level[pos]
            if (y.isNaN() || y < FREQ_RELIABLE_DB) continue
            val x = j.toDouble()
            n++; sx += x; sy += y; sxx += x * x; sxy += x * y
        }
        if (n < MIN_DETECT_FRAMES) return false
        val denom = n * sxx - sx * sx
        if (denom == 0.0) return false
        val slope = (n * sxy - sx * sy) / denom
        val intercept = (sy - slope * sx) / n
        var ssr = 0.0
        var fn = 0
        var fSum = 0.0; var fSumSq = 0.0
        for (j in 0 until h.count) {
            val pos = (h.idx - h.count + j).mod(h.level.size)
            val y = h.level[pos]
            if (y.isNaN() || y < FREQ_RELIABLE_DB) continue
            val r = y - (intercept + slope * j)
            ssr += r * r
            val f = h.freq[pos]
            if (!f.isNaN()) { fn++; fSum += f; fSumSq += f * f }
        }
        h.slopeDbPerFrame = slope
        val residStd = kotlin.math.sqrt(ssr / (n - 2).coerceAtLeast(1))
        if (residStd > LEVEL_RESIDUAL_DB) return true
        if (fn >= 8) {
            val mean = fSum / fn
            val varF = (fSumSq / fn - mean * mean).coerceAtLeast(0.0)
            if (kotlin.math.sqrt(varF) > FREQ_SCATTER_HZ) return true
        }
        return false
    }

    /**
     * Scatter of a partial's recent frequency estimates, in cents, or NaN.
     * This tests directly what matters — whether the partial can be measured
     * reliably — instead of proxying it through an absolute level. A quiet
     * partial whose frequency sits still is more useful to a tuner than a
     * loud one that wanders, and the ear reaches far below any level gate
     * we would care to set.
     */
    private fun freqScatterCents(h: PartialHistory): Double {
        var n = 0
        var sum = 0.0
        var sumSq = 0.0
        for (j in 0 until h.count) {
            val pos = (h.idx - h.count + j).mod(h.freq.size)
            val f = h.freq[pos]
            if (f.isNaN() || f <= 0.0) continue
            n++; sum += f; sumSq += f * f
        }
        if (n < 6) return Double.NaN
        val mean = sum / n
        val varF = (sumSq / n - mean * mean).coerceAtLeast(0.0)
        val sd = kotlin.math.sqrt(varF)
        return 1200.0 * kotlin.math.ln((mean + sd) / mean) / kotlin.math.ln(2.0)
    }

    /** Schmitt-trigger hysteresis so the flag (and its color) can't flicker. */
    private fun updateFlag(h: PartialHistory, candidate: Boolean): Boolean {
        h.flagCounter = (h.flagCounter + if (candidate) 1 else -1).coerceIn(0, 6)
        if (h.flagCounter >= 4) h.flag = true
        else if (h.flagCounter <= 0) h.flag = false
        return h.flag
    }

    fun select(midi: Int) {
        _selectedMidi.value = midi.coerceIn(Notes.appRange.first, Notes.appRange.last)
    }

    fun step(delta: Int) = select(_selectedMidi.value + delta)

    fun start() {
        if (_listening.value) return
        _listening.value = true
        filled = 0
        audioSource.start(hopSize) { chunk ->
            scope.push(chunk)
            val attached = taps
            for (tap in attached) tap(chunk)
            onHop(chunk)
        }
    }

    fun stop() {
        if (!_listening.value) return
        audioSource.stop()
        _listening.value = false
        // Do not leave stale bells on screen: what was displayed at the
        // moment of stopping is usually deep-decay data.
        _levels.value = emptyList()
        resetSmoothing()
        for (h in histories) h.reset()
    }

    private fun onHop(chunk: FloatArray) {
        // Shift the ring left by one hop, append the new chunk.
        // (copyInto has arraycopy semantics: overlapping ranges are safe.)
        ring.copyInto(ring, 0, hopSize, windowSize)
        chunk.copyInto(ring, windowSize - hopSize)
        if (filled < windowSize) {
            filled += hopSize
            if (filled < windowSize) return
        }
        val sr = audioSource.sampleRateHz.toDouble()

        var sq = 0.0
        for (x in chunk) sq += x.toDouble() * x
        val rms = kotlin.math.sqrt(sq / chunk.size)
        // Track the floor: fall fast toward a quieter level, rise slowly, so
        // a sustained ring raises it and a strike still stands out against it.
        noiseFloorRms = when {
            noiseFloorRms <= 0.0 -> rms
            rms < noiseFloorRms -> noiseFloorRms + 0.30 * (rms - noiseFloorRms)
            else -> noiseFloorRms + 0.02 * (rms - noiseFloorRms)
        }
        val onset = rms >= ONSET_RMS &&
            rms > prevRms * ONSET_RISE_FACTOR &&
            rms > noiseFloorRms * ONSET_RISE_FACTOR
        prevRms = rms
        _inputLevelDb.value = if (rms <= 0.0) -140.0 else 20.0 * log10(rms)

        // Auto note identification.
        //
        // Rewritten after field reports of spurious detections and of the app
        // sitting on a stale note. Three changes, each addressing one way the
        // previous version failed:
        //
        //  - It ran whenever the level cleared a fixed threshold, so decay
        //    tails and room noise were candidates. It now requires the signal
        //    to stand clear of the measured noise floor.
        //  - It trusted YIN's octave. A lag-domain estimator reports the
        //    second partial as the fundamental when the fundamental is weak,
        //    which is common on piano; the candidate is now checked against
        //    the spectrum before being believed.
        //  - It demanded two CONSECUTIVE agreeing frames, so a single stray
        //    estimate reset the count and, with alternating estimates, the
        //    count never completed at all. It now takes a majority of recent
        //    detections, which tolerates the occasional stray without
        //    accepting it.
        // Gated on absolute presence only. A gate relative to the noise floor
        // was tried and is wrong: the floor rises to meet a sustained tone,
        // so detection would stop working on exactly the long ring it most
        // needs to work on. Rejecting noise is the vote's job, not a level
        // test's — noise does not produce the same note repeatedly.
        // The estimate is computed and published whether or not it will be
        // acted on. Publishing it only when detection runs meant that while
        // the note was locked the operator could not see what detection would
        // have said — which is precisely when that question is asked.
        val detected: Double? = if (rms > ONSET_RMS) {
            Yin.estimateF0(ring, sr, minHz = 55.0, maxHz = 1200.0)
                ?.let { correctOctave(ring, sr, it) }
        } else null
        _rawDetectHz.value = detected

        if (_autoDetect.value && !_locked.value) {
            val refHz = tuning.referenceA4Hz.value
            val f0 = detected
            if (f0 != null) {
                val midi = Notes.nearestMidi(f0, refHz)
                val target = tuning.temperament.value.frequencyOf(midi, refHz)
                if (midi in Notes.appRange && abs(Notes.centsOff(f0, target)) < 45.0) {
                    detectVotes[detectVoteIdx] = midi
                    detectVoteIdx = (detectVoteIdx + 1) % detectVotes.size
                    if (detectVoteCount < detectVotes.size) detectVoteCount++
                    if (detectVoteCount >= DETECT_MIN_VOTES) {
                        var best = -1
                        var bestN = 0
                        for (i in 0 until detectVoteCount) {
                            val cand = detectVotes[i]
                            var n = 0
                            for (j in 0 until detectVoteCount) if (detectVotes[j] == cand) n++
                            if (n > bestN) { bestN = n; best = cand }
                        }
                        if (bestN >= DETECT_MAJORITY && best != _selectedMidi.value) {
                            _selectedMidi.value = best
                            detectVoteCount = 0
                            detectVoteIdx = 0
                        }
                    }
                }
            }
        }

        val temperament = tuning.temperament.value
        val ref = tuning.referenceA4Hz.value
        val midiSel = _selectedMidi.value
        val harmonic = analyzer.analyze(
            buffer = ring,
            midi = midiSel,
            partialCount = partialCount,
            temperament = temperament,
            referenceA4Hz = ref,
        )
        // Per-partial frequency refinement: for partials with usable level,
        // phase-refine the actual frequency around the harmonic target.
        // Capture range is +-sr/(2*hop) around the target (5.86 Hz at
        // 48k/4096) — note this shrinks in CENTS as k*f0 grows; results
        // outside a 90 % safety margin are discarded as unreliable.
        // Acceptance window = the WIDE (coarse-stage) capture range, since
        // two-stage refinement resolves wraps up to that bound.
        val maxDev = sr / (2.0 * COARSE_HOP) * 0.9
        // --- Staged measurement -------------------------------------------
        // Targeting model: the previous full fit when available (it uses all
        // partials and is far better conditioned than a 3-point seed), else
        // a seed fit from the low partials at their harmonic targets.
        val prevModel = _stringModel.value
        // NOTE: the captured reference is deliberately NOT used here.
        //
        // A reference is a yardstick for comparison; it must never become the
        // target the filters are pointed at. Using it meant that once the
        // centre string was captured, every subsequent string was measured at
        // the centre string's partial frequencies — so a string out of tune
        // by 15 ¢ had its upper partials sitting 10–20 Hz from where the
        // filters were looking, outside the main lobe, returning sidelobe
        // noise and aliased values. The first two strings sampled correctly
        // (no reference existed yet) and the third never could: an asymmetry
        // in the software, not in the instrument. It also made the I32 escape
        // path unreachable, since targeting consulted the reference before
        // the live model it had just discarded.
        val targetModel = prevModel ?: run {
            val harmonic = analyzer.analyze(
                buffer = ring,
                midi = midiSel,
                partialCount = SEED_MAX_K,
                temperament = temperament,
                referenceA4Hz = ref,
            )
            val seed = ArrayList<Pair<Int, Double>>(SEED_MAX_K)
            for (p in harmonic) {
                if (p.levelDb <= REFINE_THRESHOLD_DB) continue
                val f = PreciseF0.refineTwoStage(ring, sr, p.frequencyHz, hopSize, COARSE_HOP)
                if (abs(f - p.frequencyHz) < maxDev) seed += p.index to f
            }
            InharmonicityFit.fit(seed)
        }

        // Measure level AND frequency at the model-predicted positions.
        val raw = if (targetModel == null) {
            analyzer.analyze(
                buffer = ring,
                midi = midiSel,
                partialCount = partialCount,
                temperament = temperament,
                referenceA4Hz = ref,
            )
        } else {
            analyzer.analyzeAt(ring, (1..partialCount).map { k -> k to targetModel.partialHz(k) })
        }
        val measured = raw.map { p ->
            if (p.levelDb <= REFINE_THRESHOLD_DB) return@map p
            val f = PreciseF0.refineTwoStage(ring, sr, p.frequencyHz, hopSize, COARSE_HOP)
            if (abs(f - p.frequencyHz) < maxDev) p.copy(measuredHz = f) else p
        }

        // Refit from reliable partials only. With too few, hold B and track
        // f1 alone: fitting a decaying tone's noise made B wander in the field.
        val fitPoints = measured.mapNotNull { p ->
            val m = p.measuredHz
            if (m != null && p.levelDb > FIT_MIN_DB) p.index to m else null
        }
        // Hold the model while a unison sounds: with two strings the
        // measured partials are amplitude-weighted means, which would drag
        // the fit (and hence the display reference) toward the error we are
        // trying to see. Flags are from the previous frame, which is exactly
        // the causality we want here.
        val lowMulti = (0 until minOf(SEED_MAX_K, partialCount)).any { histories[it].flag }
        val holdModel = lowMulti && prevModel != null
        val fitWeights = fitPoints.map { (k, _) ->
            val db = measured.firstOrNull { it.index == k }?.levelDb ?: -120.0
            // amplitude weighting, floored so a weak partial still counts a little
            maxOf(0.02, 10.0.pow(db / 20.0))
        }
        // B is a property of the unison, not of one string. Once any string
        // has been captured, hold B at its value and fit only f0: this makes
        // a weak string with few measurable partials measurable, where a
        // free fit would be underdetermined and produce a runaway B.
        val priorB = _captured.value.values.map { it.model.b }.sorted()
            .let { if (it.isEmpty()) null else it[it.size / 2] }

        val fitted: StringModel? = if (holdModel) null else if (priorB != null) {
            if (fitPoints.isNotEmpty()) {
                InharmonicityFit.fitWithFixedB(fitPoints, priorB, fitWeights)
            } else null
        } else when {
            fitPoints.size >= MIN_FIT_PARTIALS -> InharmonicityFit.fit(fitPoints, fitWeights)?.let { f ->
                val prevB = prevModel?.b
                StringModel(f.f0Hz, if (prevB == null) f.b else prevB + B_ALPHA * (f.b - prevB))
            }
            fitPoints.isNotEmpty() && prevModel != null -> {
                val (k, f) = fitPoints.first()
                val b = prevModel.b
                StringModel(f / (k * kotlin.math.sqrt(1.0 + b * k * k)), b)
            }
            else -> null
        }
        // Residual of the CANDIDATE, before adopting it: a fit is only worth
        // taking if it actually describes the partials it was fitted to.
        fun residualOf(m: StringModel): Double? {
            if (fitPoints.size < 2) return null
            var acc = 0.0
            for ((k, f) in fitPoints) {
                val c = Notes.centsOff(f, m.partialHz(k))
                acc += c * c
            }
            return sqrt(acc / fitPoints.size)
        }
        val candidateResidual = fitted?.let { residualOf(it) }
        // A fit can be internally consistent and still be of the wrong thing:
        // seeded onto the wrong component it produces a low residual at a
        // fundamental a fifth or an octave away (observed: 387.6 Hz on A3
        // with res 0.3 ¢). The residual cannot detect this, because the model
        // agrees with the partials it chose. The note being measured is
        // known, so require the fit to land near it.
        val noteTarget = temperament.frequencyOf(midiSel, ref)
        val plausibleNote = fitted == null ||
            abs(Notes.centsOff(fitted.partialHz(1), noteTarget)) <= MAX_NOTE_DEVIATION_CENTS
        val acceptable = fitted != null && plausibleNote &&
            (candidateResidual == null || candidateResidual <= REJECT_RESIDUAL_CENTS)
        if (acceptable) _stringModel.value = fitted
        _fitResidualCents.value = _stringModel.value?.let { residualOf(it) }

        if (fitPoints.size >= MIN_FIT_PARTIALS && acceptable) hopsSinceFit = 0
        else if (hopsSinceFit < Int.MAX_VALUE) hopsSinceFit++

        // Escape from a corrupted model.
        //
        // Targets are taken from the current model, so a wrong model points
        // the filters at wrong frequencies, too few partials come back to
        // support a refit, and the wrong model is therefore held — a trap
        // that cannot correct itself. Observed in the field: a string in
        // tune to within a few cents reported -33 cents with B six times its
        // true value, held indefinitely, while its two neighbours measured
        // correctly. Discarding the model after a sustained failure to refit
        // forces the next hop to re-seed from the nominal grid, which is
        // independent of anything the bad model touched.
        // Discard ONCE, when there is something to discard. Without the
        // null check this fires on every subsequent hop, because
        // hopsSinceFit is pinned at its maximum — so any model fitted later
        // in the session was destroyed before the rest of the hop could use
        // it. Symptom: after the first pause between strikes, no further
        // reading was ever accepted and capture stalled at one blow.
        if (_stringModel.value != null && hopsSinceFit >= RESEED_AFTER_HOPS) {
            _stringModel.value = null
            _fitResidualCents.value = null
            hopsSinceFit = Int.MAX_VALUE
            for (st in smoothStates) st.freqHz = Double.NaN
        }
        _modelFresh.value = hopsSinceFit <= STALE_AFTER_HOPS

        // Display reference: the captured reference string when present,
        // otherwise the best live fit. This is a DISPLAY choice only — the
        // measurement above is deliberately independent of it (see the note
        // at targetModel), so that comparing a string against the reference
        // cannot alter how that string is measured.
        val refModelForDisplay = _referenceModel.value
        val displayModel = refModelForDisplay ?: _stringModel.value ?: targetModel
        val enriched = if (displayModel == null) measured else measured.map { p ->
            p.copy(frequencyHz = displayModel.partialHz(p.index))
        }

        // Smoothing: EMA levels (asymmetric), SNR-gated frequency hold,
        // re-seed frequencies on note change and on each new strike.
        val midiNow = _selectedMidi.value
        if (midiNow != smoothedMidi) {
            resetSmoothing()
            for (h in histories) h.reset()
            for (e in envelopes) e.reset()
        fineEnvelope.reset()
        fineEnvelopePartial = -1
            _stringModel.value = null
            _modelFresh.value = false
            hopsSinceFit = Int.MAX_VALUE
            smoothedMidi = midiNow
        }
        if (onset) {
            for (st in smoothStates) st.freqHz = Double.NaN
            for (h in histories) h.reset() // history must not span the attack
            // A strike is an independent measurement event, so the model is
            // re-seeded from it rather than carried across the silence. A
            // model retained through a pause was found to fit the next strike
            // poorly (residual > 10 ¢), so every new fit was rejected and no
            // reading was ever accepted again — capture stalled after the
            // first blow. Re-seeding costs a few hops and makes each strike
            // genuinely independent, which is what the agreement protocol
            // assumes in the first place.
            _stringModel.value = null
            _fitResidualCents.value = null
            hopsSinceFit = Int.MAX_VALUE
            freqSeedDelay = 3
        }
        val freqUpdatesAllowed = freqSeedDelay == 0
        if (freqSeedDelay > 0) freqSeedDelay--

        _levels.value = enriched.map { p ->
            val hist = histories[p.index - 1]
            if (freqUpdatesAllowed) {
                hist.push(p.levelDb, p.measuredHz ?: Double.NaN)
            }
            val multi = updateFlag(hist, multiCandidate(hist))
            val st = smoothStates[p.index - 1]
            st.levelDb = if (st.levelDb.isNaN()) p.levelDb else {
                val a = if (p.levelDb > st.levelDb) LEVEL_ATTACK_ALPHA else LEVEL_RELEASE_ALPHA
                st.levelDb + a * (p.levelDb - st.levelDb)
            }
            val m = p.measuredHz
            if (freqUpdatesAllowed && m != null && p.levelDb > FREQ_RELIABLE_DB) {
                st.freqHz = if (st.freqHz.isNaN()) m else st.freqHz + FREQ_ALPHA * (m - st.freqHz)
            }
            p.copy(
                levelDb = st.levelDb,
                measuredHz = if (st.freqHz.isNaN()) null else st.freqHz,
                multiString = multi,
            )
        }

        // Tunability: seconds of usable life per partial, from the measured
        // decay slope. hopRate frames per second.
        val hopRate = audioSource.sampleRateHz.toDouble() / hopSize
        val published = _levels.value
        val sustainList = ArrayList<Double>(partialCount)
        val tunable = LinkedHashSet<Int>()
        for (k in 1..partialCount) {
            val p = published.firstOrNull { it.index == k }
            val h = histories[k - 1]
            val slope = h.slopeDbPerFrame
            val level = p?.levelDb ?: Double.NaN
            val secs = when {
                p == null || level.isNaN() || p.measuredHz == null -> 0.0
                level <= TUNABLE_MIN_DB -> 0.0
                slope.isNaN() || slope >= -1e-4 -> Double.POSITIVE_INFINITY // not decaying
                else -> (level - TUNABLE_MIN_DB) / (-slope * hopRate)
            }
            sustainList += secs
            val scatter = freqScatterCents(h)
            val steady = !scatter.isNaN() && scatter <= TUNABLE_MAX_SCATTER_CENTS
            if (secs >= MIN_TUNABLE_SECONDS && level > TUNABLE_MIN_DB && steady) tunable += k
        }
        _sustainSeconds.value = sustainList

        // Fine envelope of the armed partial: the amplitude is read several
        // times per hop so fast beats stay countable (see [fineEnvelope]).
        val armedK = _enabledPartials.value.singleOrNull()
        // Where to point the fine filter. The reference string first: it is
        // fixed and known, and during tuning the live fit is frequently
        // unavailable — two beating strings make a poor fit, so a target
        // taken from it would keep vanishing and reset the envelope, which
        // is exactly when the beat most needs measuring. The nominal grid is
        // the last resort so the filter always has somewhere to sit.
        val armedTargetHz = armedK?.let { k ->
            _captured.value[_referenceSlot.value]?.model?.partialHz(k)
                ?: _stringModel.value?.partialHz(k)
                ?: (k * temperament.frequencyOf(midiSel, ref))
        }
        if (armedK == null || armedTargetHz == null) {
            fineEnvelope.reset()
            fineEnvelopePartial = -1
        } else {
            if (armedK != fineEnvelopePartial) {
                fineEnvelope.reset()
                fineEnvelopePartial = armedK
            }
            // Sub-blocks spanning only the newest hop, so each amplitude
            // reading is a distinct slice of time rather than a sliding
            // average that would smear the modulation being measured.
            val sub = ENVELOPE_SUB_BLOCK
            val first = windowSize - hopSize
            var off = first
            while (off + sub <= windowSize) {
                val slice = FloatArray(sub)
                ring.copyInto(slice, 0, off, off + sub)
                val mag = sqrt(Goertzel.power(slice, sr, armedTargetHz)) / (sub / 2.0)
                fineEnvelope.push(if (mag <= 0.0) -140.0 else 20.0 * log10(mag))
                off += sub
            }
        }

        // Envelope tracking and direct beat measurement.
        val frameRate = audioSource.sampleRateHz.toDouble() / hopSize
        val beats = HashMap<Int, Double>()
        for (k in 1..partialCount) {
            val env = envelopes[k - 1]
            val lvl = published.firstOrNull { it.index == k }?.levelDb
            if (lvl == null || lvl.isNaN() || lvl <= TUNABLE_MIN_DB) {
                env.reset()
                continue
            }
            env.push(lvl)
            val series = env.ordered(BEAT_MIN_FRAMES) ?: continue
            BeatRate.estimate(series, frameRate)?.let { beats[k] = it }
        }
        // The armed partial is measured from the fine envelope instead, which
        // reaches far faster beats.
        if (armedK != null) {
            val fineRate = audioSource.sampleRateHz.toDouble() / ENVELOPE_SUB_BLOCK
            val series = fineEnvelope.ordered(FINE_BEAT_MIN_FRAMES)
            if (series != null) {
                BeatRate.estimate(series, fineRate, maxBeatHz = FINE_MAX_BEAT_HZ)
                    ?.let { beats[armedK] = it }
            }
        }
        _measuredBeatHz.value = beats
        _tunablePartials.value = tunable
        _recommendedPartial.value = tunable.maxOrNull()
        _workingPartial.value = (_usablePartials.value intersect _enabledPartials.value).maxOrNull()
            ?: tunable.maxOrNull()
        if (_captured.value.size < StringSlot.entries.size) _usablePartials.value = tunable

        // Armed slot: capture as soon as the reading is worth keeping and
        // has held that way for a moment. The criteria are the ones already
        // used to decide whether a reading is trustworthy at all.
        val armed = _armedSlot.value
        if (armed != null) {
            if (onset) {
                armBlowCount++
                armSettledThisBlow = false
                armStableFrames = 0 // each strike must settle on its own
            }
            // Accumulate what this string offered while it was SOUNDING.
            // Counting every armed frame diluted the tally with the silence
            // between strikes — most of the arming period, since the operator
            // is handling mutes — so a partial had to be tunable in a quarter
            // of the pauses as well, which nothing can be. The fundamental,
            // which sustains longest, was lost to this along with the rest.
            if (tunable.isNotEmpty()) {
                armFrames++
                for (k in tunable) armTunableCounts[k] = (armTunableCounts[k] ?: 0) + 1
            }

            val res = _fitResidualCents.value
            val good = _modelFresh.value &&
                _stringModel.value != null &&
                res != null && res <= CAPTURE_MAX_RESIDUAL_CENTS &&
                tunable.isNotEmpty()
            armStableFrames = if (good) armStableFrames + 1 else 0
            val settled = _stringModel.value
            if (armStableFrames >= CAPTURE_STABLE_FRAMES && settled != null &&
                !armSettledThisBlow
            ) {
                // One accepted reading per strike, never several from a
                // single ring: a long tone must not stand in for repetition.
                armSettled += settled
                armSettledThisBlow = true
                _armBlows.value = armSettled.size
                armStableFrames = 0

                if (armSettled.size >= MIN_CAPTURE_BLOWS) {
                    val recent = armSettled.takeLast(MIN_CAPTURE_BLOWS)
                    if (allAgree(recent)) {
                        // Capture the median reading of the agreeing set
                        // rather than the last, so no single strike decides.
                        val median = recent.sortedBy { it.partialHz(1) }[recent.size / 2]
                        captureString(armed, median)
                        _armedSlot.value = null
                        armStableFrames = 0
                        armBlowCount = 0
                        armSettled.clear()
                        armSettledThisBlow = false
                        _armBlows.value = 0
                    } else {
                        // Not all of one string: drop the oldest and ask for
                        // another strike rather than accepting a mixed set.
                        armSettled.removeAt(0)
                        _armBlows.value = armSettled.size
                    }
                }
            }
        }

        // Identify the sounding string at the strike, then hold it.
        if (onset) _liveSlot.value = null
        if (_liveSlot.value == null && _captured.value.isNotEmpty()) {
            val focusK = _enabledPartials.value.singleOrNull() ?: 1
            val liveHz = published.firstOrNull { it.index == focusK }?.measuredHz
            if (liveHz != null) {
                var best: StringSlot? = null
                var bestCents = IDENTIFY_MAX_CENTS
                for ((slot, cap) in _captured.value) {
                    val d = abs(Notes.centsOff(liveHz, cap.model.partialHz(focusK)))
                    if (d < bestCents) { bestCents = d; best = slot }
                }
                _liveSlot.value = best
            }
        }

        // Which of the sampled strings is sounding: nearest sampled mark at
        // the working partial. Guarded, so an unrecognisable sound (a wrong
        // note, a knock) identifies nothing rather than something wrong.
        // Departure at the armed partial — the quantity being worked on.
        run {
            val k = _enabledPartials.value.singleOrNull()
            val refM = _referenceModel.value
            val liveHz = k?.let { published.firstOrNull { p -> p.index == it }?.measuredHz }
            if (k != null && refM != null && liveHz != null) {
                val refK = refM.partialHz(k)
                _armedDeltaCents.value = Notes.centsOff(liveHz, refK)
                _armedBeatHz.value = abs(liveHz - refK)
            } else {
                _armedDeltaCents.value = null
                _armedBeatHz.value = null
            }
        }

        // Which string is sounding: decided at the strike, held while it rings.
        val focusPartial = _enabledPartials.value.singleOrNull()
        if (focusPartial == null || _captured.value.isEmpty()) {
            _liveSlot.value = null
        } else {
            // Identify on the armed partial when it is measurable, otherwise
            // on the lowest partial that is.
            //
            // High partials are exactly where the work is done and exactly
            // where measurement is most intermittent: at partial 10 the
            // reading comes and goes, and identification tied to it left a
            // string's mark frozen because the app could not tell which
            // string was sounding. The fundamental is nearly always present
            // and identifies the string just as well — which string is
            // sounding is a different question from where its armed partial
            // lies.
            val identifyOn = published.firstOrNull {
                it.index == focusPartial && it.measuredHz != null
            } ?: published.firstOrNull { it.measuredHz != null }
            val identifyK = identifyOn?.index
            val liveHz = identifyOn?.measuredHz
            if (liveHz == null) {
                if (rms < ONSET_RMS * 0.5) _liveSlot.value = null
            } else if (onset || _liveSlot.value == null || liveDriftFrames >= LIVE_REID_FRAMES) {
                liveDriftFrames = 0
                // Compare against where each string is NOW, not where it was
                // captured. A string already tuned in toward the reference
                // sounds far from its own captured mark, so matching on
                // captures attributed its sound to the other string — which
                // then moved on its neighbour's strikes while its own went
                // unrecognised.
                val kId = identifyK ?: focusPartial
                // Separation is judged against the reading's own error bar,
                // so that three strings inside a cent of each other identify
                // as nothing rather than as whichever won a coin toss. See
                // LiveIdentification.
                _liveSlot.value = LiveIdentification.identify(
                    liveHz = liveHz,
                    partial = kId,
                    captured = _captured.value,
                    currentModels = _currentModels.value,
                    referenceSlot = _referenceSlot.value,
                    maxCents = LIVE_MATCH_MAX_CENTS,
                    uncertaintyCents = _fitResidualCents.value ?: 0.0,
                )
            }
        }

        // Update the sounding string's current position when the reading is
        // trustworthy, so its mark reflects the work just done.
        _liveSlot.value?.takeIf { it != _referenceSlot.value }?.let { slot ->
            val live = _stringModel.value
            val res = _fitResidualCents.value
            if (live != null && _modelFresh.value && res != null &&
                res <= CAPTURE_MAX_RESIDUAL_CENTS
            ) {
                _currentModels.value = _currentModels.value + (slot to live)
            }
        }

        // Identity is normally fixed at the strike, but onsets are not
        // guaranteed during continuous playing: without this the identity
        // sticks to the first string identified and a second string being
        // worked on never moves its mark.
        run {
            val slot = _liveSlot.value
            val k = _enabledPartials.value.singleOrNull()
            val liveHz = k?.let { published.firstOrNull { p -> p.index == it }?.measuredHz }
            if (slot != null && k != null && liveHz != null) {
                val nearest = LiveIdentification.nearestByFrequency(
                    liveHz = liveHz,
                    partial = k,
                    captured = _captured.value,
                    currentModels = _currentModels.value,
                    referenceSlot = _referenceSlot.value,
                )
                liveDriftFrames = if (nearest != null && nearest != slot) liveDriftFrames + 1 else 0
            } else {
                liveDriftFrames = 0
            }
        }

        // Departure from the captured reference at the fundamental.
        val reference = _referenceModel.value
        val soundingF1 = published.firstOrNull()?.measuredHz

        // Highest partial with a trustworthy level and a valid measurement.
        val listening = published.lastOrNull {
            it.measuredHz != null && it.levelDb > FREQ_RELIABLE_DB && it.index in _enabledPartials.value
        }
        _listeningPartial.value = listening?.index

        if (reference != null && soundingF1 != null) {
            val refF1 = reference.partialHz(1)
            _refDeltaCents.value = Notes.centsOff(soundingF1, refF1)
            _refBeatHz.value = abs(soundingF1 - refF1)
            _listeningBeatHz.value = listening?.let { p ->
                p.measuredHz?.let { abs(it - reference.partialHz(p.index)) }
            }
        } else {
            _refDeltaCents.value = null
            _refBeatHz.value = null
            _listeningBeatHz.value = null
        }
    }

    private companion object {
        /** Below this raw level a partial is too weak to phase-refine. */
        const val REFINE_THRESHOLD_DB = -100.0

        /** Level EMA: fast attack so strikes register immediately... */
        const val LEVEL_ATTACK_ALPHA = 0.55
        /** ...slow release so decay reads calmly (tau ~ 0.7 s at 11.7 Hz). */
        const val LEVEL_RELEASE_ALPHA = 0.12
        /** Frequency EMA coefficient while the partial is reliable. */
        const val FREQ_ALPHA = 0.25
        /** Below this raw level the phase is too noisy: hold position. */
        const val FREQ_RELIABLE_DB = -92.0
        /**
         * Agreeing readings required before an armed slot captures — one per
         * strike. Two, matching established practice: two agreeing readings
         * reject an accidental one, and a third only costs the operator time
         * at the instrument.
         */
        const val MIN_CAPTURE_BLOWS = 2

        /** Frames the sounding string must look nearer a different mark
            before its identity is reassigned without an onset. */
        const val LIVE_REID_FRAMES = 8
        /** Recent detections retained for the majority vote. */
        const val DETECT_WINDOW = 7
        /** Detections needed before a vote is taken at all. */
        const val DETECT_MIN_VOTES = 3
        /** Agreeing detections within the window needed to switch note. */
        const val DETECT_MAJORITY = 3
        /** A lower octave candidate needs this share of the upper one's
            energy at its own frequency... */
        const val OCTAVE_OWN_ENERGY = 0.20
        /** ...and this share at its second odd partial. */
        const val OCTAVE_ODD_ENERGY = 0.10
        /**
         * A sounding string further than this from every sampled mark at the
         * armed partial identifies as nothing, so a wrong note or a knock
         * moves no mark.
         *
         * Expressed in CENTS, not hertz. A fixed hertz tolerance is far too
         * tight at the partials this tool exists to work on: 12 Hz is 94 ¢ at
         * the fundamental of A3 but only 19 ¢ at partial 5, so a string 20 ¢
         * out matched nothing and its mark never moved — the symptom that
         * exposed this.
         */
        const val LIVE_MATCH_MAX_CENTS = 80.0
        /** Widest departure at which a sounding string is still recognised
            as one of the sampled ones. Generous: a string may be pulled a
            long way while being tuned. */
        const val IDENTIFY_MAX_CENTS = 60.0
        /** B deviation from the other strings marking a capture suspect. */
        const val SUSPECT_B_FRACTION = 0.40
        /** Pitch agreement required between two settled strikes. */
        const val AGREE_CENTS = 3.0
        /** Inharmonicity agreement required between two settled strikes. */
        const val AGREE_B_FRACTION = 0.35
        /** Model residual at or below which a reading may be captured. */
        const val CAPTURE_MAX_RESIDUAL_CENTS = 2.5
        /** Consecutive good frames required before an armed slot captures
            (~0.5 s), so a momentary good frame during the attack does not
            freeze a reading that has not settled. */
        const val CAPTURE_STABLE_FRAMES = 4

        /**
         * Floor for offering a partial. Deliberately low: a tuner works on
         * the highest partial the ear can isolate, which reaches well below
         * any comfortable level gate, and stability (below) is the real
         * test of whether a partial can be measured.
         */
        const val TUNABLE_MIN_DB = -105.0
        /** Frequency scatter above which a partial is not steady enough. */
        const val TUNABLE_MAX_SCATTER_CENTS = 6.6
        /** Predicted seconds above the threshold before a partial is worth
            offering: upper partials are often loud at the attack and gone
            within half a second, which cannot be tuned on. */
        const val MIN_TUNABLE_SECONDS = 0.9

        /** Absolute floor below which nothing counts as a strike at all. */
        const val ONSET_RMS = 0.004
        /**
         * A strike must be this much louder than both the previous hop and
         * the tracked background. Detecting the rise rather than an absolute
         * crossing means strikes are still found while the instrument is
         * ringing, which a fixed threshold cannot do.
         */
        const val ONSET_RISE_FACTOR = 2.2

        /** Short baseline for the wide-capture first refinement stage. */
        const val COARSE_HOP = 1024
        /** Partials used to seed the model fit: their inharmonic offsets are
            small enough to sit safely inside the harmonic-target capture
            range even before B is known. */
        const val SEED_MAX_K = 3
        /** EMA coefficient for the published inharmonicity coefficient. */
        const val B_ALPHA = 0.15
        /** A partial must exceed this level to enter the model fit. */
        const val FIT_MIN_DB = -85.0
        /**
         * A candidate fit disagreeing with its own partials by more than
         * this is not adopted. Real strings sit well under 1 ¢ (measured
         * 0.1–0.7 ¢ in the field); this bound rejects nonsense without
         * touching legitimate readings.
         */
        const val REJECT_RESIDUAL_CENTS = 10.0
        /**
         * How far a fitted fundamental may lie from the selected note before
         * the fit is rejected as being of something else. Generous enough for
         * any string a technician would call out of tune, far tighter than
         * the interval errors this rejects.
         */
        const val MAX_NOTE_DEVIATION_CENTS = 200.0
        /**
         * Hops of failed refitting after which the model is discarded so
         * targeting re-seeds from the nominal grid (~2 s).
         */
        const val RESEED_AFTER_HOPS = 24

        /** Hops without a full fit after which the model reads as held. */
        const val STALE_AFTER_HOPS = 12
        /**
         * Agreement within this many cents counts as coincident. 0.5 ¢ at
         * A3 is a 0.064 Hz beat at the fundamental — one beat per 16 s,
         * below what is useful to chase further by ear at that pitch.
         */
        const val COINCIDENCE_CENTS = 0.5
        /** Partials required for a full (f1, B) fit; below this the model is
            held and only f1 tracked, so a decaying tone cannot make B wander. */
        const val MIN_FIT_PARTIALS = 3

        // ---- Multi-string (unison) detection ----
        /** Envelope frames retained per partial (~8 s at the hop rate),
            enough for two periods of a 0.25 Hz beat. */
        const val ENVELOPE_FRAMES = 96
        /** Samples per fine envelope reading: 1024 at 48 kHz = 46.9 per second. */
        const val ENVELOPE_SUB_BLOCK = 1024
        /**
         * Fine envelope length, ~11 s at 46.9 readings/s. Long because the
         * slow end matters most: two cycles of a 0.2 /s beat need ten
         * seconds, and that is the final approach to beatlessness.
         */
        const val FINE_ENVELOPE_FRAMES = 512
        /** Readings required before a fine beat estimate is attempted. */
        const val FINE_BEAT_MIN_FRAMES = 96
        /** Ceiling for the fine estimate, below the 23.4 /s Nyquist limit. */
        const val FINE_MAX_BEAT_HZ = 20.0
        /** Envelope frames required before a beat estimate is attempted. */
        const val BEAT_MIN_FRAMES = 28

        /** Frames of per-partial history (~2 s at the 11.7 Hz hop rate). */
        const val HIST_FRAMES = 24
        /** Minimum valid frames before the detector may fire. */
        const val MIN_DETECT_FRAMES = 16
        /** dB residual std around a linear (= exponential-decay) level fit
            above which the envelope is beating, not decaying. */
        const val LEVEL_RESIDUAL_DB = 2.5
        /** Raw frequency-estimate std above single-string phase noise. */
        const val FREQ_SCATTER_HZ = 0.12
    }
}
