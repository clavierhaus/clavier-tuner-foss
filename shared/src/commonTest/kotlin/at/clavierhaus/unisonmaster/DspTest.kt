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

    /**
     * A bass string as the microphone hears it walking down chromatically:
     * E2 with eight partials on a stiff-string comb, the third and sixth
     * louder than the fundamental (the Boesendorfer E2 of 2026-09-16) — and
     * the semitone above still ringing at 0.4 of its level, because bass
     * strings sustain for ten seconds and the walk does not wait for them.
     * Searched three semitones either side, as LiveReference does.
     *
     * Until 19 September the normalised dip at the period came out at 0.55
     * here against a threshold of 0.15, so YIN returned null and the tuner
     * saw no reading at all. The published normalisation puts it at 0.06.
     */
    @Test
    fun yinFindsTheFundamentalOfABassStringWhileItsNeighbourStillRings() {
        val f1 = 82.6
        val b = 1.8e-4
        val ring = FloatArray(16384)
        fun comb(f: Double, level: Double, gains: DoubleArray) {
            for (k in 1 until gains.size) {
                val fk = k * f * kotlin.math.sqrt((1 + b * k * k) / (1 + b))
                val part = sine(fk, level * gains[k], ring.size)
                for (i in ring.indices) ring[i] += part[i]
            }
        }
        comb(f1, 0.05, doubleArrayOf(0.0, 1.0, 1.0, 1.5, 0.1, 0.4, 1.7, 1.2, 0.3))
        comb(69.2, 0.05 * 0.4, doubleArrayOf(0.0, 1.0, 0.8, 1.0, 0.5))   // F2, the semitone above, ringing on
        val semis = Math.pow(2.0, 3.0 / 12.0)
        val f0 = Yin.estimateF0(ring, sr, minHz = f1 / semis, maxHz = f1 * semis)
        assertNotNull(f0, "no coarse estimate on a bass string with its neighbour ringing")
        assertTrue(abs(f0 - f1) < 1.0, "coarse at $f0, expected near $f1")
    }
}
