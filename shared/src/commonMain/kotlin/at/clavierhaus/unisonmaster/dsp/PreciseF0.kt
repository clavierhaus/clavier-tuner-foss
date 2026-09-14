package at.clavierhaus.unisonmaster.dsp

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.round

/**
 * Precise frequency measurement via phase advance.
 *
 * Two sub-windows of the same buffer, offset by [hop] samples, are evaluated
 * at the coarse frequency. A tone of exactly that frequency would advance in
 * phase by 2*pi*f*hop/sr between them; the measured deviation from that
 * prediction converts directly into a frequency correction:
 *
 *     f_true = f_coarse + wrap(dPhi_measured - dPhi_predicted) * sr / (2*pi*hop)
 *
 * Capture range is +-sr/(2*hop) around the coarse estimate (+-5.9 Hz at
 * 48 kHz / hop 4096) — far wider than YIN's coarse error. Precision is
 * limited by SNR, not by window length, and reaches millihertz on clean
 * decaying tones. This is the standard phase-vocoder frequency estimator.
 */
object PreciseF0 {

    // Scratch cached per sub-window size (two sizes alternate in the
    // two-stage scheme, so a single-slot cache would thrash). Not
    // thread-safe by design: exactly one capture pipeline runs at a time.
    private class Scratch(size: Int) {
        val window = Window.blackmanHarris(size)
        val a = FloatArray(size)
        val b = FloatArray(size)
    }

    private val scratches = HashMap<Int, Scratch>()

    /**
     * Two-stage refinement. Stage 1 uses a short baseline [coarseHop] for a
     * wide capture range (+-fs/(2*coarseHop), 23.4 Hz at 48k/1024) to
     * resolve the phase-wrap ambiguity; stage 2 re-refines around that
     * result with the long baseline [fineHop] for full precision. Without
     * stage 1, a partial displaced beyond +-fs/(2*fineHop) wraps and is
     * reported on the WRONG SIDE of its target.
     */
    fun refineTwoStage(
        buffer: FloatArray,
        sampleRateHz: Double,
        coarseHz: Double,
        fineHop: Int = 4096,
        coarseHop: Int = 1024,
    ): Double {
        val wide = refine(buffer, sampleRateHz, coarseHz, coarseHop)
        return refine(buffer, sampleRateHz, wide, fineHop)
    }

    fun refine(
        buffer: FloatArray,
        sampleRateHz: Double,
        coarseHz: Double,
        hop: Int = 4096,
    ): Double {
        require(buffer.size > hop * 2) { "buffer too small for hop $hop" }
        val sub = buffer.size - hop
        val sc = scratches.getOrPut(sub) { Scratch(sub) }
        val a = sc.a
        val b = sc.b
        buffer.copyInto(a, 0, 0, sub)
        buffer.copyInto(b, 0, hop, hop + sub)
        Window.applyInPlace(a, sc.window)
        Window.applyInPlace(b, sc.window)

        val ca = Goertzel.complex(a, sampleRateHz, coarseHz)
        val cb = Goertzel.complex(b, sampleRateHz, coarseHz)
        val phiA = atan2(ca[1], ca[0])
        val phiB = atan2(cb[1], cb[0])

        val predicted = 2.0 * PI * coarseHz * hop / sampleRateHz
        var delta = (phiB - phiA) - predicted
        delta -= 2.0 * PI * round(delta / (2.0 * PI)) // wrap to [-pi, pi]

        return coarseHz + delta * sampleRateHz / (2.0 * PI * hop)
    }
}