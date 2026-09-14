package at.clavierhaus.unisonmaster

import at.clavierhaus.unisonmaster.dsp.Goertzel
import at.clavierhaus.unisonmaster.dsp.Yin
import at.clavierhaus.unisonmaster.tuning.EqualTemperament
import at.clavierhaus.unisonmaster.tuning.Notes
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DspTest {

    private val sr = 48_000.0

    private fun sine(freq: Double, amplitude: Double, size: Int): FloatArray =
        FloatArray(size) { (amplitude * sin(2.0 * PI * freq * it / sr)).toFloat() }

    private fun mix(vararg parts: FloatArray): FloatArray {
        val out = FloatArray(parts[0].size)
        for (p in parts) for (i in out.indices) out[i] += p[i]
        return out
    }

    @Test
    fun equalTemperamentAnchors() {
        assertTrue(abs(EqualTemperament.frequencyOf(69, 440.0) - 440.0) < 1e-9)
        assertTrue(abs(EqualTemperament.frequencyOf(Notes.MIDI_C2, 440.0) - 65.406) < 0.01)
        assertTrue(abs(EqualTemperament.frequencyOf(Notes.MIDI_C6, 443.0) - 1053.63) < 0.05)
    }

    @Test
    fun goertzelSeparatesPartials() {
        // A2-ish tone: f0 220 Hz strong, partial 2 weaker, partial 3 weak
        val n = 16384
        val signal = mix(
            sine(220.0, 0.5, n),
            sine(440.0, 0.25, n),
            sine(660.0, 0.05, n),
        )
        val l1 = Goertzel.levelDb(signal, sr, 220.0)
        val l2 = Goertzel.levelDb(signal, sr, 440.0)
        val l3 = Goertzel.levelDb(signal, sr, 660.0)
        val lNoise = Goertzel.levelDb(signal, sr, 555.0) // between partials

        assertTrue(l1 > l2 && l2 > l3, "partial ordering wrong: $l1 / $l2 / $l3")
        assertTrue(l3 - lNoise > 20.0, "off-partial floor too high: $l3 vs $lNoise")
        // 0.5 amplitude sine ~ -6 dBFS
        assertTrue(abs(l1 - (-6.02)) < 1.0, "absolute level off: $l1")
    }

    @Test
    fun yinFindsA4At443() {
        val f0 = Yin.estimateF0(sine(443.0, 0.4, 8192), sr, minHz = 380.0, maxHz = 500.0)
        assertNotNull(f0)
        assertTrue(abs(f0 - 443.0) < 0.5, "YIN estimate off: $f0")
    }

    @Test
    fun analyzerMeasuresKnownSpectrum() {
        val n = 16384
        val ref = 440.0
        val midi = 57 // A3, 220 Hz
        val f0 = EqualTemperament.frequencyOf(midi, ref)
        val signal = mix(sine(f0, 0.4, n), sine(2 * f0, 0.2, n), sine(3 * f0, 0.1, n))

        val result = PartialAnalyzer(48_000, windowSize = n)
            .analyze(signal, midi, partialCount = 4, EqualTemperament, ref)

        assertTrue(result.size == 4)
        assertTrue(result[0].levelDb > result[1].levelDb)
        assertTrue(result[1].levelDb > result[2].levelDb)
        assertTrue(result[2].levelDb > result[3].levelDb) // partial 4 absent -> floor-ish
    }
}
