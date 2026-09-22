package at.clavierhaus.unisonmaster.measure

import at.clavierhaus.unisonmaster.dsp.Fft
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The finder (docs/REBUILD.md §4.8): a zero-padded FFT of a stretch of
 * sound, used only to say *where* a partial is — near a frequency, within
 * a window of cents — never to read it. The reading is [PhaseReader]'s.
 * Three uses: where the A4 string is on the hub before anything is set;
 * how far off a string is when it is outside the reader's band; and where
 * each partial of a string lies when Done measures it.
 */
class PartialFinder(samples: FloatArray, private val sampleRate: Int, zeroPad: Int = 4) {
    private val size: Int
    private val magnitude: DoubleArray
    private val binHz: Double

    init {
        var n = 1
        while (n < samples.size * zeroPad) n = n shl 1
        size = n
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        val m = samples.size
        for (i in 0 until m) re[i] = samples[i] * (0.5 - 0.5 * cos(2 * PI * i / (m - 1)))
        Fft.transform(re, im)
        magnitude = DoubleArray(n / 2) { sqrt(re[it] * re[it] + im[it] * im[it]) }
        binHz = sampleRate.toDouble() / n
    }

    /** A peak: its frequency (interpolated), how far it stands above its surroundings, and its level. */
    data class Peak(val hz: Double, val prominenceDb: Double, val levelDb: Double)

    /**
     * The strongest peak within ±[cents] of [hz], if it stands at least
     * [minProminenceDb] above the median of that window — something is
     * there, not just noise.
     */
    fun near(hz: Double, cents: Double, minProminenceDb: Double = MIN_PROMINENCE_DB): Peak? {
        val lo = ((hz * 2.0.pow(-cents / 1200)) / binHz).toInt().coerceIn(1, magnitude.size - 2)
        val hi = ((hz * 2.0.pow(cents / 1200)) / binHz).toInt().coerceIn(1, magnitude.size - 2)
        if (hi - lo < 2) return null
        var j = lo
        for (b in lo..hi) if (magnitude[b] > magnitude[j]) j = b
        val window = (lo..hi).map { magnitude[it] }.sorted()
        val median = window[window.size / 2]
        val prominence = 20 * log10(magnitude[j] / maxOf(median, 1e-30))
        if (prominence < minProminenceDb) return null
        // parabolic interpolation on the log magnitude
        val y0 = ln(magnitude[j - 1] + 1e-30); val y1 = ln(magnitude[j] + 1e-30); val y2 = ln(magnitude[j + 1] + 1e-30)
        val den = y0 - 2 * y1 + y2
        val p = if (den != 0.0) (0.5 * (y0 - y2) / den).coerceIn(-0.5, 0.5) else 0.0
        return Peak((j + p) * binHz, prominence, 20 * log10(magnitude[j] + 1e-30))
    }

    companion object {
        const val MIN_PROMINENCE_DB = 12.0
    }
}
