package at.clavierhaus.unisonmaster

import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.dsp.PreciseF0
import at.clavierhaus.unisonmaster.dsp.Yin
import kotlin.math.abs
import kotlin.math.pow
import at.clavierhaus.unisonmaster.tuning.EqualTemperament
import at.clavierhaus.unisonmaster.tuning.Inharmonicity
import at.clavierhaus.unisonmaster.tuning.LiveReference
import at.clavierhaus.unisonmaster.tuning.MeasuredPartial
import at.clavierhaus.unisonmaster.tuning.NoteMeasurement
import at.clavierhaus.unisonmaster.tuning.PredictedPartial
import at.clavierhaus.unisonmaster.tuning.TuningSession
import at.clavierhaus.unisonmaster.tuning.Temperament
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared hub state: reference pitch, temperament, measurement.
 * Platform UIs (Compose today, SwiftUI later) observe the StateFlows and
 * call the mutation functions — no platform types anywhere in here.
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

        /** Accept the running median regardless once this many estimates
            have accumulated (~4 s of qualifying tone). */
        const val HARD_CAP_ESTIMATES = 48
        /** Fewest estimates worth adopting when the operator stops early. */
        /**
         * Fewest estimates worth adopting when the operator stops early.
         *
         * Two, not four. Accept is a deliberate act: the operator has read
         * the running value and decided it is good enough, and refusing it
         * silently — as happened in the field at n=3 — is worse than taking a
         * slightly noisier figure they can see and re-measure. The outlier
         * filter below keeps its own, separate minimum, since rejecting
         * outliers from two samples is meaningless.
         */
        const val MIN_ADOPTABLE_ESTIMATES = 2
        /** Survivors needed before the outlier filter is trusted at all. */
        const val MIN_FILTERED_ESTIMATES = 4
    }

    private val _referenceA4Hz = MutableStateFlow(DEFAULT_REFERENCE_HZ)
    val referenceA4Hz: StateFlow<Double> = _referenceA4Hz.asStateFlow()

    private val _temperament = MutableStateFlow<Temperament>(EqualTemperament)
    val temperament: StateFlow<Temperament> = _temperament.asStateFlow()

    /**
     * Accepted estimates of the running measurement. Held as an immutable
     * snapshot so the UI thread can adopt them when the operator stops the
     * measurement while the capture thread is still appending.
     */
    private var estimates: List<Double> = emptyList()
    private var adopted = false

    /** Number of estimates gathered so far (progress feedback). */
    private val _estimateCount = MutableStateFlow(0)
    val estimateCount: StateFlow<Int> = _estimateCount.asStateFlow()

    private val _measuring = MutableStateFlow(false)
    val measuring: StateFlow<Boolean> = _measuring.asStateFlow()

    /** Last f0 measured from the instrument (live running median while measuring). */
    private val _lastMeasuredHz = MutableStateFlow<Double?>(null)
    val lastMeasuredHz: StateFlow<Double?> = _lastMeasuredHz.asStateFlow()

    /** Standard deviation of the accepted estimates, in Hz (null until done). */
    private val _dispersionHz = MutableStateFlow<Double?>(null)
    val dispersionHz: StateFlow<Double?> = _dispersionHz.asStateFlow()

    fun setReference(hz: Double) {
        _referenceA4Hz.value = hz.coerceIn(MIN_REFERENCE_HZ, MAX_REFERENCE_HZ)
    }

    // ---- Live A4: follows one string continuously (hub) ----

    private val _live = MutableStateFlow(false)
    val live: StateFlow<Boolean> = _live.asStateFlow()

    private val _liveHz = MutableStateFlow<Double?>(null)
    /** Live pitch of the sounding string; held after the tone dies. */
    val liveHz: StateFlow<Double?> = _liveHz.asStateFlow()

    private val _livePartials = MutableStateFlow<List<LiveReference.LivePartial>>(emptyList())
    /** Measured partials of the sounding string (hub, Full Spectrum). */
    val livePartials: StateFlow<List<LiveReference.LivePartial>> = _livePartials.asStateFlow()

    private val _liveAudible = MutableStateFlow<Set<Int>>(emptySet())
    /** Partials audible during the current strike. */
    val liveAudible: StateFlow<Set<Int>> = _liveAudible.asStateFlow()

    private val _fullSpectrum = MutableStateFlow(false)
    /** Hub mode: false = fundamental only, true = all audible partials. */
    val fullSpectrum: StateFlow<Boolean> = _fullSpectrum.asStateFlow()

    private val _hiddenPartials = MutableStateFlow<Set<Int>>(emptySet())
    /** Partials the tuner has switched off in Full Spectrum mode. */
    val hiddenPartials: StateFlow<Set<Int>> = _hiddenPartials.asStateFlow()

    fun toggleFullSpectrum() {
        _fullSpectrum.value = !_fullSpectrum.value
        _hiddenPartials.value = emptySet()
        val t = _tuning.value ?: return
        _shownPartials.value = if (_fullSpectrum.value) {
            _targets.value.map { it.k }.toSet() + _liveAudible.value + 1
        } else setOf(1)
        _activePartial.value = 1
    }

    fun tapPartial(k: Int) {
        val t = _tuning.value
        if (t == null) {
            _hiddenPartials.value = at.clavierhaus.unisonmaster.tuning.PartialSelection.tap(
                k, _fullSpectrum.value, _liveAudible.value, _hiddenPartials.value,
            )
            return
        }
        val shown = _shownPartials.value
        when {
            // the fundamental is always shown; a tap only makes it the active one
            k == 1 -> _activePartial.value = 1
            // a new partial is added and becomes the one being tuned
            k !in shown -> {
                val available = _targets.value.map { it.k }.toSet() + _liveAudible.value
                if (k !in available) return
                _shownPartials.value = shown + k
                _activePartial.value = k
            }
            // a shown partial that is not active becomes active
            k != _activePartial.value -> _activePartial.value = k
            // tapping the active partial removes it
            else -> {
                _shownPartials.value = shown - k
                _activePartial.value = (shown - k).maxOrNull() ?: 1
            }
        }
    }

    private val _activePartial = MutableStateFlow(1)
    /** The partial currently being tuned: the last one added or tapped. */
    val activePartial: StateFlow<Int> = _activePartial.asStateFlow()

    private val _readoutView = MutableStateFlow(true)
    /** Tuning screen: true = one target bell and a readout column; false = bell pairs. */
    val readoutView: StateFlow<Boolean> = _readoutView.asStateFlow()

    fun toggleTuningView() {
        _readoutView.value = !_readoutView.value
    }

    // ---- Tuning session: after A4, the octave down to A3, single strings ----

    /** What the tuning screen shows for the current note. */
    data class TuningView(
        val midi: Int,
        /** Equal-temperament target of the first partial, Hz. */
        val targetHz: Double,
        /** Target partials, predicted from the nearest measured note. */
        val predicted: List<PredictedPartial>,
        /** The basis note's partials (levels, sustain) for the suggestion. */
        val basisPartials: List<MeasuredPartial>,
        /** Notes already measured. */
        val measured: Set<Int>,
        /** True once every note of the session is measured. */
        val complete: Boolean,
    )

    private var session: TuningSession? = null

    private val _tuning = MutableStateFlow<TuningView?>(null)
    /** Null while A4 is being defined (hub); set once Done has been tapped on A4. */
    val tuning: StateFlow<TuningView?> = _tuning.asStateFlow()

    private val _shownPartials = MutableStateFlow(setOf(1))
    /** Partials displayed on the tuning screen. Partial 1 is always among them. */
    val shownPartials: StateFlow<Set<Int>> = _shownPartials.asStateFlow()

    private val _suggested = MutableStateFlow<Int?>(null)
    /**
     * The partial recommended for a finer match, fixed for the whole note:
     * chosen once, from the neighbour's measurement. The screen offers it
     * only after the fundamental has matched; it never changes by itself.
     */
    val suggested: StateFlow<Int?> = _suggested.asStateFlow()

    private val _liveSummary = MutableStateFlow<NoteMeasurement?>(null)

    private val _targets = MutableStateFlow<List<PredictedPartial>>(emptyList())
    /**
     * Target frequencies of the current note's partials. Before the string
     * has sounded they are predicted from the nearest measured note; from
     * the first reading on they come from the string itself
     * ([Inharmonicity.ownTargets]), so they agree with the fundamental's
     * target by construction. Partials not yet heard keep the prediction.
     */
    val targets: StateFlow<List<PredictedPartial>> = _targets.asStateFlow()

    private fun refreshTargets(t: TuningView, own: NoteMeasurement?) {
        val self = own?.let { Inharmonicity.ownTargets(t.targetHz, it) } ?: emptyList()
        val heard = self.map { it.k }.toSet()
        _targets.value = (self + t.predicted.filter { it.k !in heard }).sortedBy { it.k }
    }

    private val _range = MutableStateFlow(380.0 to 500.0)

    /** The session's measurements so far (A4 first). */
    fun measurements(): Map<Int, NoteMeasurement> = session?.measurements ?: emptyMap()

    /** Tuning screen: tune [midi] next (any note of the session except A4). */
    fun selectNote(midi: Int) {
        val s = session ?: return
        if (midi == TuningSession.MIDI_A4 || midi !in TuningSession.sequence) return
        s.select(midi)
        publish(s, complete = s.nextUnmeasured() == null)
    }

    private fun publish(s: TuningSession, complete: Boolean) {
        val midi = s.current
        val target = s.targetF1(midi)
        _tuning.value = TuningView(
            midi = midi,
            targetHz = target,
            predicted = s.predictedPartials(midi),
            basisPartials = s.basisFor(midi)?.partials ?: emptyList(),
            measured = s.measurements.keys.toSet(),
            complete = complete,
        )
        refreshTargets(_tuning.value!!, null)
        _shownPartials.value = setOf(1)
        _activePartial.value = 1
        _fullSpectrum.value = false
        _hiddenPartials.value = emptySet()
        _suggested.value = TuningSession.recommend(s.basisFor(midi)?.partials ?: emptyList())
        _liveSummary.value = null
        val semis = 2.0.pow(3.0 / 12.0)
        _range.value = (target / semis) to (target * semis)
    }

    /** Arrows: one semitone down (-1) or up (+1), within G#4..A3. */
    fun stepNote(delta: Int) {
        val s = session ?: return
        val to = s.stepped(delta)
        if (to == s.current) return
        s.select(to)
        publish(s, complete = s.nextUnmeasured() == null)
    }

    /** After Done: one semitone down; on A3 the session stays and reports complete. */
    private fun advance(s: TuningSession) {
        val next = s.below()
        if (next != null) s.select(next)
        publish(s, complete = next == null || s.nextUnmeasured() == null)
    }

    private val _liveLevel = MutableStateFlow(0.0)
    /** Live loudness 0 .. 1. */
    val liveLevel: StateFlow<Double> = _liveLevel.asStateFlow()

    /**
     * Starts following the string. Returns false if the input could not be
     * opened (e.g. microphone permission not yet granted); safe to call again.
     */
    fun startLive(hopSize: Int = 4096): Boolean {
        if (_live.value) return true
        if (_measuring.value) return false
        var applied = _range.value
        val follower = LiveReference(
            audioSource.sampleRateHz, hopSize = hopSize, minHz = applied.first, maxHz = applied.second,
        )
        _live.value = true
        return try {
            audioSource.start(hopSize) { chunk ->
                if (!_live.value) return@start
                val want = _range.value
                if (want != applied) {
                    follower.setRange(want.first, want.second)
                    applied = want
                }
                follower.push(chunk)
                val hz = follower.hz
                _liveHz.value = hz
                _liveLevel.value = follower.level
                _livePartials.value = follower.partials
                _liveAudible.value = follower.audible
                val t = _tuning.value
                val summary = follower.summary(t?.midi ?: TuningSession.MIDI_A4)
                _liveSummary.value = summary
                if (t != null) refreshTargets(t, summary)
            }
            true
        } catch (e: Exception) {
            _live.value = false
            false
        }
    }

    fun stopLive() {
        if (!_live.value) return
        _live.value = false
        _liveLevel.value = 0.0
        audioSource.stop()
    }

    /**
     * "Done".
     *
     * On the hub: the live reading, to 0.1 Hz, becomes the A4 reference; the
     * string's full measurement is kept as the session's foundation and the
     * session moves to G#4. Returns the reference.
     *
     * While tuning: the current note's measurement is kept (only while its
     * fundamental matches the target, i.e. shows green) and the session moves
     * one semitone down. Returns the note's reading, or null if refused.
     */
    fun acceptLive(): Double? {
        val hz = _liveHz.value ?: return null
        val t = _tuning.value
        if (t == null) {
            setReference(LiveReference.roundToTenth(hz))
            val s = TuningSession(_referenceA4Hz.value)
            val m = _liveSummary.value ?: NoteMeasurement(TuningSession.MIDI_A4, hz, 0.0, 0.0, emptyList())
            s.record(m.copy(midi = TuningSession.MIDI_A4, timeMs = clock()))
            session = s
            advance(s)
            return _referenceA4Hz.value
        }
        val s = session ?: return null
        if (!TuningSession.matched(hz, t.targetHz)) return null
        val m = _liveSummary.value ?: return null
        s.record(m.copy(midi = t.midi, timeMs = clock()))
        advance(s)
        return hz
    }

    /**
     * Measures A4 from the instrument with tuning-grade precision.
     *
     * Protocol (all per hop of [hopSize] samples over a [windowSize] ring):
     *  1. Onset gate: wait until the signal exceeds an RMS threshold.
     *  2. Attack skip: discard [settleHops] hops (~250 ms) — piano strings
     *     start sharp and glide down while the attack settles; measuring
     *     there is what makes naive tuners jitter.
     *  3. Per hop: YIN gives a coarse f0, PreciseF0 refines it via phase.
     *  4. Statistics: collect estimates, reject outliers (median +- 3*MAD),
     *     accept when >= [minEstimates] survivors agree within
     *     [maxSpreadHz] standard deviation. Result = median, with the
     *     spread published as the estimate dispersion.
     *
     * If the tone dies before convergence, the gate re-arms — strike again
     * and measurement continues. Call [stopMeasuring] to abort.
     */
    fun measureReferenceFromInstrument(
        windowSize: Int = 16384,
        hopSize: Int = 4096,
        settleHops: Int = 3,
        minEstimates: Int = 8,
        maxSpreadHz: Double = 0.05,
    ) {
        if (_measuring.value) return
        _measuring.value = true
        _lastMeasuredHz.value = null
        _dispersionHz.value = null
        estimates = emptyList()
        _estimateCount.value = 0
        adopted = false

        val sr = audioSource.sampleRateHz.toDouble()
        val ring = FloatArray(windowSize)
        var filled = 0
        var settleRemaining = -1 // -1 = armed, waiting for onset
        val onsetRms = 0.005   // ~ -46 dBFS
        val releaseRms = 0.001 // tone considered ended below this

        audioSource.start(hopSize) { chunk ->
            if (!_measuring.value) return@start

            // Ring update
            ring.copyInto(ring, 0, hopSize, windowSize)
            chunk.copyInto(ring, windowSize - hopSize)
            if (filled < windowSize) filled += hopSize

            // RMS of the newest hop
            var sq = 0.0
            for (x in chunk) sq += x.toDouble() * x
            val rms = kotlin.math.sqrt(sq / chunk.size)

            when {
                settleRemaining == -1 -> {
                    if (rms > onsetRms) settleRemaining = settleHops
                    return@start
                }
                settleRemaining > 0 -> {
                    settleRemaining--
                    return@start
                }
                rms < releaseRms -> {
                    settleRemaining = -1 // tone died: re-arm for next strike
                    return@start
                }
            }
            if (filled < windowSize) return@start

            val coarse = Yin.estimateF0(ring, sr, minHz = 380.0, maxHz = 500.0)
                ?: return@start
            val refined = PreciseF0.refine(ring, sr, coarse, hopSize)
            if (refined < 380.0 || refined > 500.0) return@start

            estimates = estimates + refined
            _estimateCount.value = estimates.size
            _lastMeasuredHz.value = median(estimates)

            if (estimates.size < minEstimates) return@start
            val (value, spread) = robustEstimate(estimates)
            if (spread <= maxSpreadHz || estimates.size >= HARD_CAP_ESTIMATES) {
                adopt(value, spread)
                stopMeasuring()
            }
        }
    }

    /**
     * Stops measuring. Any estimates already gathered are adopted rather
     * than discarded: if the operator has heard enough, the app has too.
     * Only a run with too few estimates to be meaningful is dropped.
     */
    fun stopMeasuring() {
        if (!_measuring.value) return
        _measuring.value = false
        audioSource.stop()
        val snapshot = estimates
        if (!adopted && snapshot.size >= MIN_ADOPTABLE_ESTIMATES) {
            val (value, spread) = robustEstimate(snapshot)
            adopt(value, spread)
        }
    }

    private fun adopt(valueHz: Double, spreadHz: Double) {
        _referenceA4Hz.value = valueHz.coerceIn(MIN_REFERENCE_HZ, MAX_REFERENCE_HZ)
        _lastMeasuredHz.value = valueHz
        _dispersionHz.value = spreadHz
        adopted = true
    }

    /**
     * Robust centre and spread: median, then reject outliers beyond
     * max(0.03 Hz, 3*MAD). If that filter would leave too little to stand
     * on, it is abandoned rather than allowed to block the measurement —
     * the previous version could reject its way into never terminating.
     */
    private fun robustEstimate(values: List<Double>): Pair<Double, Double> {
        val med = median(values)
        val mad = median(values.map { abs(it - med) })
        val filtered = values.filter { abs(it - med) <= maxOf(0.03, 3.0 * mad) }
        val keep = if (filtered.size >= MIN_FILTERED_ESTIMATES) filtered else values
        val centre = median(keep)
        return centre to stdDev(keep, centre)
    }

    private fun median(values: List<Double>): Double {
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    private fun stdDev(values: List<Double>, center: Double): Double {
        if (values.size < 2) return 0.0
        var sum = 0.0
        for (v in values) sum += (v - center) * (v - center)
        return kotlin.math.sqrt(sum / (values.size - 1))
    }
}
