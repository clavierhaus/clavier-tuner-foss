package at.clavierhaus.unisonmaster

import at.clavierhaus.unisonmaster.audio.AudioSource
import at.clavierhaus.unisonmaster.dsp.PreciseF0
import at.clavierhaus.unisonmaster.dsp.Yin
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class ChunkedSource(
    override val sampleRateHz: Int,
    private val signal: FloatArray,
) : AudioSource {
    private var stopped = false
    override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
        var pos = 0
        val buffer = FloatArray(bufferSize)
        while (!stopped && pos + bufferSize <= signal.size) {
            signal.copyInto(buffer, 0, pos, pos + bufferSize)
            onBuffer(buffer)
            pos += bufferSize
        }
    }
    override fun stop() { stopped = true }
}

class PrecisionTest {

    private val sr = 48_000

    @Test
    fun phaseRefinementReachesMillihertz() {
        val target = 443.170
        val buf = FloatArray(16384) { (0.3 * sin(2.0 * PI * target * it / sr)).toFloat() }
        val coarse = Yin.estimateF0(buf, sr.toDouble(), 380.0, 500.0)
        assertNotNull(coarse)
        // YIN alone may be off by a substantial fraction of a Hz...
        val refined = PreciseF0.refine(buf, sr.toDouble(), coarse, 4096)
        // ...phase refinement must land within 5 mHz.
        assertTrue(
            abs(refined - target) < 0.005,
            "refined $refined, expected $target (coarse was $coarse)",
        )
    }

    @Test
    fun measurementProtocolConvergesOnPianoLikeTone() {
        val target = 442.730
        val rng = Random(42)
        val silence = sr / 2               // 0.5 s of near-silence (noise only)
        val toneLen = sr * 3               // 3 s decaying tone
        val glideLen = sr / 5              // attack: starts ~4 cents sharp, glides down over 200 ms

        val signal = FloatArray(silence + toneLen)
        for (i in signal.indices) {
            signal[i] = ((rng.nextDouble() - 0.5) * 2e-4).toFloat() // mic noise floor
        }
        var phase = 0.0
        for (i in 0 until toneLen) {
            val glide = if (i < glideLen) 1.0 + 0.0023 * (1.0 - i.toDouble() / glideLen) else 1.0
            val f = target * glide
            phase += 2.0 * PI * f / sr
            val amp = 0.25 * exp(-i / (sr * 2.0)) // slow decay
            signal[silence + i] = (signal[silence + i] + amp * sin(phase)).toFloat()
        }

        val source = ChunkedSource(sr, signal)
        val tuning = TuningController(source)
        tuning.measureReferenceFromInstrument() // ChunkedSource runs synchronously

        val measured = tuning.lastMeasuredHz.value
        val dispersion = tuning.dispersionHz.value
        assertNotNull(measured, "no measurement produced")
        assertNotNull(dispersion, "measurement did not converge")
        assertTrue(!tuning.measuring.value, "measurement should have completed")
        assertTrue(
            abs(measured - target) < 0.03,
            "measured $measured, expected $target +-0.03 (dispersion $dispersion)",
        )
        assertTrue(dispersion < 0.05, "dispersion too high: $dispersion")
        assertTrue(
            abs(tuning.referenceA4Hz.value - target) < 0.03,
            "reference not adopted: ${tuning.referenceA4Hz.value}",
        )
    }

    @Test
    fun stoppingEarlyAdoptsWhatWasMeasured() {
        // Field bug: a measurement that had not yet met the 0.05 Hz
        // convergence criterion was discarded when the operator stopped it,
        // leaving the reference at its old value although the app had a
        // perfectly good running estimate.
        val target = 442.600
        val rng = Random(11)
        val silence = sr / 4
        // Short tone: enough estimates to be meaningful, too few to hit the
        // hard cap, with slight drift so the tight convergence test fails.
        val toneLen = (sr * 1.4).toInt()
        val signal = FloatArray(silence + toneLen)
        for (i in signal.indices) signal[i] = ((rng.nextDouble() - 0.5) * 2e-4).toFloat()
        var phase = 0.0
        for (i in 0 until toneLen) {
            // Symmetric ~+-0.2 Hz glide: the run cannot meet the 0.05 Hz
            // convergence criterion, and ends well short of the hard cap.
            val drift = 1.0 + 4.5e-4 * (2.0 * i / toneLen - 1.0)
            phase += 2.0 * PI * target * drift / sr
            signal[silence + i] = (signal[silence + i] + 0.25 * sin(phase)).toFloat()
        }

        val tuning = TuningController(ChunkedSource(sr, signal))
        tuning.measureReferenceFromInstrument() // returns when the signal ends
        assertTrue(tuning.measuring.value, "expected measurement still running")
        assertTrue(tuning.estimateCount.value >= 4, "too few estimates gathered")

        tuning.stopMeasuring() // operator stops it

        assertTrue(!tuning.measuring.value)
        assertNotNull(tuning.dispersionHz.value, "nothing adopted on stop")
        assertTrue(
            abs(tuning.referenceA4Hz.value - target) < 0.15,
            "reference ${tuning.referenceA4Hz.value} not adopted (expected ~$target)",
        )
    }
}
