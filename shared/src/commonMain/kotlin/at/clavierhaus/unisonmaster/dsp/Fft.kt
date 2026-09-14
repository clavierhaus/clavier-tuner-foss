package at.clavierhaus.unisonmaster.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Iterative in-place radix-2 FFT.
 *
 * Used only for the visual overview spectrum; the actual partial measurement
 * runs through [Goertzel]. Self-written on purpose: keeps the DSP layer 100 %
 * proprietary-safe and 100 % multiplatform (no JVM-only dependency).
 */
object Fft {

    /**
     * Transforms [re]/[im] in place. Length must be a power of two.
     * Forward transform; no normalisation (divide by N if you need it).
     */
    fun transform(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(n == im.size) { "re/im length mismatch" }
        require(n > 0 && n and (n - 1) == 0) { "length $n is not a power of two" }

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
            var m = n shr 1
            while (m in 1..j) {
                j -= m
                m = m shr 1
            }
            j += m
        }

        // Danielson–Lanczos butterflies
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wRe = cos(ang)
            val wIm = sin(ang)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until len / 2) {
                    val aRe = re[i + k]
                    val aIm = im[i + k]
                    val bRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val bIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = aRe + bRe
                    im[i + k] = aIm + bIm
                    re[i + k + len / 2] = aRe - bRe
                    im[i + k + len / 2] = aIm - bIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Convenience: magnitude spectrum (first N/2 bins) of a real signal. */
    fun magnitudeSpectrum(samples: FloatArray): DoubleArray {
        val n = samples.size
        val re = DoubleArray(n) { samples[it].toDouble() }
        val im = DoubleArray(n)
        transform(re, im)
        val half = n / 2
        val out = DoubleArray(half)
        for (i in 0 until half) out[i] = sqrt(re[i] * re[i] + im[i] * im[i]) / (n / 2.0)
        return out
    }
}
