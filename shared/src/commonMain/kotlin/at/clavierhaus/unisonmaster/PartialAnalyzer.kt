package at.clavierhaus.unisonmaster

import at.clavierhaus.unisonmaster.dsp.Goertzel
import at.clavierhaus.unisonmaster.dsp.Window
import at.clavierhaus.unisonmaster.model.PartialLevel
import at.clavierhaus.unisonmaster.tuning.Temperament
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Isolates the levels of individual partials of one note from a PCM buffer.
 *
 * Analysis window: 4-term Blackman-Harris (sidelobes ~ -92 dB) so that quiet,
 * decaying partials remain measurable next to a loud fundamental. Magnitudes
 * are normalised by the window's coherent gain, keeping dBFS calibration
 * window-independent (full-scale sine = 0 dBFS).
 *
 * Current model: partial k sits at exactly k * f0 (harmonic approximation).
 * Inharmonicity (f_k = k * f0 * sqrt(1 + B*k^2)) and stretching are future
 * work and will change only [partialFrequency], nothing else.
 */
class PartialAnalyzer(
    private val sampleRateHz: Int,
    var windowSize: Int = 16384,
    val floorDb: Double = -140.0,
) {
    private var window: FloatArray = Window.blackmanHarris(windowSize)
    private var coherentGain: Double = Window.coherentGain(window)
    private var scratch: FloatArray = FloatArray(windowSize)

    /** Target frequency of partial [k] (1-based) of [midi]. */
    fun partialFrequency(
        midi: Int,
        k: Int,
        temperament: Temperament,
        referenceA4Hz: Double,
    ): Double = k * temperament.frequencyOf(midi, referenceA4Hz)

    /**
     * Measures partials 1..[partialCount] of [midi] in [buffer].
     * [buffer] must hold at least [windowSize] samples; the first
     * [windowSize] are analysed. Partials above Nyquist are skipped.
     */
    /**
     * Measures levels at explicitly given target frequencies (k to Hz).
     * Used once a string model is available: the Goertzel main lobe is only
     * a few Hz wide, so a partial displaced by inharmonicity reads as
     * sidelobe noise if the filter is left at its harmonic target. Level and
     * frequency must both be taken at the partial's actual position.
     */
    fun analyzeAt(buffer: FloatArray, targets: List<Pair<Int, Double>>): List<PartialLevel> {
        require(buffer.size >= windowSize) {
            "buffer (${buffer.size}) smaller than window ($windowSize)"
        }
        prepare(buffer)
        val nyquist = sampleRateHz / 2.0
        val sr = sampleRateHz.toDouble()
        val norm = windowSize / 2.0 * coherentGain
        val out = ArrayList<PartialLevel>(targets.size)
        for ((k, f) in targets) {
            if (f >= nyquist) break
            val magnitude = sqrt(Goertzel.power(scratch, sr, f)) / norm
            val db = if (magnitude <= 0.0) floorDb else {
                (20.0 * log10(magnitude)).coerceAtLeast(floorDb)
            }
            out += PartialLevel(index = k, frequencyHz = f, levelDb = db)
        }
        return out
    }

    private fun prepare(buffer: FloatArray) {
        if (window.size != windowSize) {
            window = Window.blackmanHarris(windowSize)
            coherentGain = Window.coherentGain(window)
            scratch = FloatArray(windowSize)
        }
        buffer.copyInto(scratch, 0, 0, windowSize)
        Window.applyInPlace(scratch, window)
    }

    fun analyze(
        buffer: FloatArray,
        midi: Int,
        partialCount: Int,
        temperament: Temperament,
        referenceA4Hz: Double,
    ): List<PartialLevel> {
        require(buffer.size >= windowSize) {
            "buffer (${buffer.size}) smaller than window ($windowSize)"
        }
        prepare(buffer)

        val nyquist = sampleRateHz / 2.0
        val sr = sampleRateHz.toDouble()
        val norm = windowSize / 2.0 * coherentGain
        val out = ArrayList<PartialLevel>(partialCount)
        for (k in 1..partialCount) {
            val f = partialFrequency(midi, k, temperament, referenceA4Hz)
            if (f >= nyquist) break
            val magnitude = sqrt(Goertzel.power(scratch, sr, f)) / norm
            val db = if (magnitude <= 0.0) floorDb else {
                (20.0 * log10(magnitude)).coerceAtLeast(floorDb)
            }
            out += PartialLevel(index = k, frequencyHz = f, levelDb = db)
        }
        return out
    }
}
