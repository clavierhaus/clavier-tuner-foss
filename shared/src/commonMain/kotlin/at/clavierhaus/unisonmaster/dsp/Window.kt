package at.clavierhaus.unisonmaster.dsp

import kotlin.math.PI
import kotlin.math.cos

/** Analysis window functions. Applied in place or via a cached coefficient array. */
object Window {

    /** Hann window coefficients of length [size]. Cache per window size. */
    fun hann(size: Int): FloatArray {
        val w = FloatArray(size)
        val n1 = (size - 1).toDouble()
        for (i in 0 until size) {
            w[i] = (0.5 * (1.0 - cos(2.0 * PI * i / n1))).toFloat()
        }
        return w
    }

    /**
     * 4-term Blackman-Harris window: first sidelobe ~ -92 dB (vs. -31 dB for
     * Hann). The choice for partial isolation: a loud fundamental leaks far
     * less onto quiet, decaying upper partials, so their true level stays
     * measurable deep into the decay.
     */
    fun blackmanHarris(size: Int): FloatArray {
        val a0 = 0.35875; val a1 = 0.48829; val a2 = 0.14128; val a3 = 0.01168
        val w = FloatArray(size)
        val n1 = (size - 1).toDouble()
        for (i in 0 until size) {
            val x = 2.0 * PI * i / n1
            w[i] = (a0 - a1 * cos(x) + a2 * cos(2 * x) - a3 * cos(3 * x)).toFloat()
        }
        return w
    }

    /**
     * Coherent gain of a window = mean of its coefficients. Dividing measured
     * magnitudes by this keeps dBFS calibration identical across windows
     * (a full-scale sine reads 0 dBFS with Hann and Blackman-Harris alike).
     */
    fun coherentGain(coefficients: FloatArray): Double {
        var sum = 0.0
        for (c in coefficients) sum += c
        return sum / coefficients.size
    }

    /** Multiplies [samples] by [coefficients] in place. Arrays must match in length. */
    fun applyInPlace(samples: FloatArray, coefficients: FloatArray) {
        require(samples.size == coefficients.size) {
            "window length ${coefficients.size} != buffer length ${samples.size}"
        }
        for (i in samples.indices) samples[i] *= coefficients[i]
    }
}
