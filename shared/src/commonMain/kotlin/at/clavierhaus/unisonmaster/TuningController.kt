package at.clavierhaus.unisonmaster

import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.dsp.PreciseF0
import at.clavierhaus.unisonmaster.dsp.Yin
import kotlin.math.abs
import at.clavierhaus.unisonmaster.tuning.EqualTemperament
import at.clavierhaus.unisonmaster.tuning.LiveReference
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
        val follower = LiveReference(audioSource.sampleRateHz, hopSize = hopSize)
        _live.value = true
        return try {
            audioSource.start(hopSize) { chunk ->
                if (!_live.value) return@start
                follower.push(chunk)
                _liveHz.value = follower.hz
                _liveLevel.value = follower.level
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

    /** "Done": the live reading, to 0.1 Hz, becomes the A4 reference. Returns it, or null. */
    fun acceptLive(): Double? {
        val hz = _liveHz.value ?: return null
        setReference(LiveReference.roundToTenth(hz))
        return _referenceA4Hz.value
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
