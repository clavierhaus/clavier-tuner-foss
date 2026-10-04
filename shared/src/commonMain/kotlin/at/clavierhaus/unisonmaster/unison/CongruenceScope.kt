package at.clavierhaus.unisonmaster.unison

import at.clavierhaus.unisonmaster.dsp.Fft

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Strike-anchored analysis.
 *
 * The analysis window starts at the detected strike, skips the attack, and
 * GROWS with the note - so the bell is honestly broad at first and sharpens
 * as evidence accumulates, and the onset edge is never inside the window to
 * scatter energy into a phantom second peak. Once the window reaches
 * FRAME_S it is locked: the evaluation frame stops sliding into the decay
 * tail and is cached until the next strike.
 *
 *  - Onset: energy-slope trigger with a hold-off, so decay fluctuations and
 *    room reflections cannot re-trigger mid-note.
 *  - Window: a Hann taper scaled to the CURRENT frame length, so the growing
 *    buffer's right edge never leaks.
 *  - Lock: at FRAME_S the spectrum per partial is computed once and held.
 *
 * combSplit(k) and profile(k) read from that spectrum.
 */
class CongruenceScope(private val sampleRate: Int) {

    companion object {
        const val PARTIALS = 12
        const val ZOOM_RATE = 200.0           // decimated rate, Hz
        const val ZOOM_N = 2048               // FFT size after zero-padding: 0.098 Hz bins
        const val ATTACK_SKIP_S = 0.150       // hammer noise and amplitude-dependent pitch
        const val FRAME_S = 2.000             // window length at lock
        const val MIN_FRAME_S = 0.100         // below this, nothing is drawn
        const val ONSET_HOLDOFF_S = 0.500     // no re-trigger inside this
        const val ONSET_RATIO = 4.0           // short energy must exceed slow by this
    }

    private val capLen = sampleRate * 3               // 3 s: holds a locked frame plus margin
    private val cap = FloatArray(capLen)
    private var cpos = 0
    private var total = 0L                            // absolute samples pushed

    // onset detector state
    private val blk = sampleRate / 100                // 10 ms blocks
    private var blkAcc = 0.0
    private var blkN = 0
    private var slowEnv = 0.0
    private var onsetAt = -1L                         // absolute sample of the last strike

    // lock cache
    private var lockedOnset = -2L
    private val locked = HashMap<Int, DoubleArray>()

    private var tunedF0 = 0.0
    val selected = BooleanArray(PARTIALS + 1)
    var autoSelected = false
        private set

    private val octaves = listOf(2, 4, 8)
    private val fifths = listOf(3, 6, 12)
    private val thirds = listOf(5, 10)
    private val musical = (octaves + fifths + thirds).sorted()

    fun sampleRateForDraw(): Double = sampleRate.toDouble()
    fun isOn(k: Int) = k in 1..PARTIALS && selected[k]
    fun toggle(k: Int) { if (k in 1..PARTIALS) selected[k] = !selected[k] }
    fun f0(): Double = tunedF0
    fun tune(f0: Double) { if (f0 > 0) tunedF0 = f0 }

    /** Seconds of clean tone in the current analysis frame (0 = none yet). */
    fun frameSeconds(): Double {
        if (onsetAt < 0) return 0.0
        val start = onsetAt + (ATTACK_SKIP_S * sampleRate).toLong()
        val end = minOf(total, start + (FRAME_S * sampleRate).toLong())
        return maxOf(0.0, (end - start) / sampleRate.toDouble())
    }
    fun isLocked(): Boolean = frameSeconds() >= FRAME_S - 1e-9

    /** Strength of partial k in the current frame (peak of its band). */
    fun strength(k: Int): Double {
        val mag = bandSpectrum(k) ?: return 0.0
        var mx = 0.0; for (v in mag) if (v > mx) mx = v
        return mx
    }

    fun autoSelectFour() {
        if (tunedF0 <= 0.0 || frameSeconds() < 1.0) return
        for (k in 0..PARTIALS) selected[k] = false
        val third = thirds.maxByOrNull { strength(it) } ?: 5
        selected[third] = true
        musical.filter { it != third }
            .sortedByDescending { strength(it) }
            .take(3)
            .forEach { selected[it] = true }
        autoSelected = true
    }

    fun push(buffer: FloatArray) {
        for (x in buffer) {
            cap[cpos] = x
            cpos = (cpos + 1) % capLen
            total++
            // ---- onset detector: 10 ms block energy against a slow envelope ----
            blkAcc += x.toDouble() * x; blkN++
            if (blkN >= blk) {
                val rms = sqrt(blkAcc / blkN)
                val ref = maxOf(slowEnv, 1e-4)
                val sinceOnset = total - onsetAt
                if (rms > ONSET_RATIO * ref && rms > 1e-3 &&
                    (onsetAt < 0 || sinceOnset > ONSET_HOLDOFF_S * sampleRate)) {
                    onsetAt = total - blkN                 // start of this block
                    locked.clear(); lockedOnset = -2L
                }
                slowEnv += 0.05 * (rms - slowEnv)          // ~200 ms
                blkAcc = 0.0; blkN = 0
            }
        }
    }

    private val zre = DoubleArray(ZOOM_N)
    private val zim = DoubleArray(ZOOM_N)

    /**
     * Magnitude spectrum of the band around k*f0 over the strike-anchored
     * frame: bin 0 = k*f0, negative offsets wrap to the top. Null until at
     * least MIN_FRAME_S of clean tone exists. Cached once the frame is locked.
     */
    private fun bandSpectrum(k: Int): DoubleArray? {
        if (tunedF0 <= 0.0 || onsetAt < 0) return null
        val start = onsetAt + (ATTACK_SKIP_S * sampleRate).toLong()
        val end = minOf(total, start + (FRAME_S * sampleRate).toLong())
        val len = (end - start).toInt()
        if (len < MIN_FRAME_S * sampleRate) return null
        if (total - start > capLen) return null            // frame has scrolled out of the ring
        val lockedNow = len >= (FRAME_S * sampleRate).toInt()
        if (lockedNow && lockedOnset == onsetAt) locked[k]?.let { return it }

        val centreHz = k * tunedF0
        val decim = (sampleRate / ZOOM_RATE).toInt()
        java.util.Arrays.fill(zre, 0.0); java.util.Arrays.fill(zim, 0.0)
        val w = 2.0 * PI * centreHz / sampleRate
        val blocks = len / decim
        var phase = 0.0
        val ringStart = ((start % capLen) + capLen) % capLen
        for (b in 0 until blocks) {
            var sr = 0.0; var si = 0.0
            val base = b * decim
            for (j in 0 until decim) {
                val i = base + j
                val x = cap[((ringStart + i) % capLen).toInt()].toDouble()
                // Hann scaled to the CURRENT frame length: no edge leakage
                val hann = 0.5 - 0.5 * cos(2.0 * PI * i / (len - 1))
                val v = x * hann
                sr += v * cos(phase); si -= v * sin(phase)
                phase += w; if (phase > 2 * PI) phase -= 2 * PI
            }
            if (b < ZOOM_N) { zre[b] = sr / decim; zim[b] = si / decim }
        }
        Fft.transform(zre, zim)
        val mag = DoubleArray(ZOOM_N)
        var maxv = 0.0
        for (i in 0 until ZOOM_N) {
            val m = sqrt(zre[i] * zre[i] + zim[i] * zim[i])
            mag[i] = m; if (m > maxv) maxv = m
        }
        if (maxv < 1e-4) return null
        if (lockedNow) { lockedOnset = onsetAt; locked[k] = mag }
        return mag
    }

    private fun binOf(offsetHz: Double): Int {
        val b = kotlin.math.round(offsetHz / ZOOM_RATE * ZOOM_N).toInt()
        return ((b % ZOOM_N) + ZOOM_N) % ZOOM_N
    }
    private fun hzOf(bin: Int): Double {
        val b = if (bin > ZOOM_N / 2) bin - ZOOM_N else bin
        return b * ZOOM_RATE / ZOOM_N
    }

    /** One or two component offsets (Hz from k*f0), parabolic-interpolated. */
    fun combSplit(k: Int, searchHz: Double = 12.0): DoubleArray {
        val mag = bandSpectrum(k) ?: return DoubleArray(0)
        val lo = binOf(-searchHz); val hi = binOf(searchHz)
        val bins = ArrayList<Int>()
        var i = lo; while (true) { bins.add(i); if (i == hi) break; i = (i + 1) % ZOOM_N }
        var maxv = 0.0
        for (b in bins) if (mag[b] > maxv) maxv = mag[b]
        if (maxv <= 0.0) return DoubleArray(0)
        val sepBins = (1.0 / (ZOOM_RATE / ZOOM_N)).toInt().coerceAtLeast(1)
        val peaks = ArrayList<Int>()
        for (t in 1 until bins.size - 1) {
            val b = bins[t]; val pb = bins[t - 1]; val nb = bins[t + 1]
            if (mag[b] > mag[pb] && mag[b] >= mag[nb] && mag[b] > 0.20 * maxv) {
                if (peaks.none { kotlin.math.abs(it - t) < sepBins }) peaks.add(t)
            }
        }
        fun vertex(t: Int): Pair<Double, Double> {
            val b = bins[t]; val pb = bins[t - 1]; val nb = bins[t + 1]
            val a0 = mag[pb]; val a1v = mag[b]; val a2v = mag[nb]
            val denom = a0 - 2 * a1v + a2v
            val d = if (denom != 0.0) 0.5 * (a0 - a2v) / denom else 0.0
            val height = a1v - 0.25 * (a0 - a2v) * d
            return (hzOf(b) + d * ZOOM_RATE / ZOOM_N) to height
        }
        val ranked = peaks.map { vertex(it) }.sortedByDescending { it.second }
        return ranked.take(2).map { it.first }.sorted().toDoubleArray()
    }

    /** The measured bell across +-halfHz around partial k, normalised 0..1. */
    fun profile(k: Int, halfHz: Double = 1.5, step: Double = 0.05): DoubleArray {
        val offs = combSplit(k)
        val centreOff = if (offs.isNotEmpty()) offs.average() else 0.0
        val mag = bandSpectrum(k) ?: return DoubleArray(0)
        val n = (2 * halfHz / step).toInt() + 1
        val out = DoubleArray(n)
        var maxv = 0.0
        val binHz = ZOOM_RATE / ZOOM_N
        for (i in 0 until n) {
            val off = centreOff - halfHz + i * step
            val fb = off / binHz
            val b0 = kotlin.math.floor(fb).toInt()
            val frac = fb - b0
            val i0 = ((b0 % ZOOM_N) + ZOOM_N) % ZOOM_N
            val i1 = (i0 + 1) % ZOOM_N
            val m = mag[i0] * (1 - frac) + mag[i1] * frac
            out[i] = m; if (m > maxv) maxv = m
        }
        if (maxv <= 0.0) return DoubleArray(0)
        for (i in 0 until n) out[i] /= maxv
        return out
    }
}
