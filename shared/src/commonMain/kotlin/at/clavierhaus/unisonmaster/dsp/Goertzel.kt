package at.clavierhaus.unisonmaster.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Goertzel algorithm: spectral power at ONE known frequency.
 *
 * This is the workhorse of the partial analyzer. Since we always know which
 * frequency we are looking for (partial k of a known note in a known
 * temperament), evaluating a small bank of Goertzel filters is both cheaper
 * and more precise than binning a full FFT: the Goertzel evaluates the DTFT
 * at the *exact* target frequency instead of the nearest FFT bin.
 *
 * Frequency resolution is governed by the analysis window length:
 * resolvable detail ~ sampleRate / blockSize. At 48 kHz a 16384-sample
 * window (~341 ms) resolves ~2.9 Hz — adjust the window upward for the
 * low end (C2 fundamental ~65 Hz) if neighbouring partials must be split.
 */
object Goertzel {

    /** Raw spectral power at [frequencyHz] over [samples]. */
    fun power(samples: FloatArray, sampleRateHz: Double, frequencyHz: Double): Double {
        val omega = 2.0 * PI * frequencyHz / sampleRateHz
        val coeff = 2.0 * cos(omega)
        var s1 = 0.0
        var s2 = 0.0
        for (x in samples) {
            val s0 = x + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        return s1 * s1 + s2 * s2 - coeff * s1 * s2
    }

    /**
     * Complex DFT value at [frequencyHz]: returns doubleArrayOf(re, im).
     * Phase (atan2(im, re)) is the basis of the precise f0 refinement:
     * comparing the phase of two time-shifted windows measures frequency
     * far beyond bin/lag resolution.
     */
    fun complex(samples: FloatArray, sampleRateHz: Double, frequencyHz: Double): DoubleArray {
        val omega = 2.0 * PI * frequencyHz / sampleRateHz
        val cosw = cos(omega)
        val coeff = 2.0 * cosw
        var s1 = 0.0
        var s2 = 0.0
        for (x in samples) {
            val s0 = x + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        val re = s1 - s2 * cosw
        val im = s2 * kotlin.math.sin(omega)
        return doubleArrayOf(re, im)
    }

    /** Magnitude normalised by window length (comparable across window sizes). */
    fun magnitude(samples: FloatArray, sampleRateHz: Double, frequencyHz: Double): Double =
        sqrt(power(samples, sampleRateHz, frequencyHz)) / (samples.size / 2.0)

    /**
     * Level in dBFS relative to a full-scale sine at the target frequency.
     * Returns [floorDb] for silence/underflow so UIs get a bounded range.
     */
    fun levelDb(
        samples: FloatArray,
        sampleRateHz: Double,
        frequencyHz: Double,
        floorDb: Double = -120.0,
    ): Double {
        val m = magnitude(samples, sampleRateHz, frequencyHz)
        if (m <= 0.0) return floorDb
        val db = 20.0 * log10(m)
        return if (db < floorDb) floorDb else db
    }
}
