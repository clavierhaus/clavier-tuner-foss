package at.clavierhaus.unisonmaster.dsp

/**
 * YIN fundamental-frequency estimator.
 *
 * Implemented directly from the published method (de Cheveigné & Kawahara,
 * "YIN, a fundamental frequency estimator for speech and music", JASA 2002):
 * difference function -> cumulative mean normalised difference -> absolute
 * threshold -> parabolic interpolation. Clean-room implementation; do NOT
 * paste code from GPL libraries (e.g. TarsosDSP) into this file.
 *
 * Used by the hub's "measure reference from the piano" function.
 */
object Yin {

    /**
     * Estimates f0 of [samples] in Hz, or null if no pitch clears the
     * confidence threshold. [minHz]/[maxHz] bound the search lag range;
     * defaults comfortably cover A4 measurement and the C2..C6 keyboard range.
     */
    fun estimateF0(
        samples: FloatArray,
        sampleRateHz: Double,
        minHz: Double = 50.0,
        maxHz: Double = 1200.0,
        threshold: Double = 0.15,
    ): Double? {
        val maxLag = (sampleRateHz / minHz).toInt()
        val minLag = (sampleRateHz / maxHz).toInt().coerceAtLeast(2)

        // The integration window W is independent of the lag, several periods
        // long, and taken from the NEWEST samples: a caller hands in a ring
        // with the newest at the end. Until 19 September it was maxLag
        // samples from index 0 — about 1.2 periods, 330 ms stale — and that
        // is why the bass was lost (see step 3 as well). 4·maxLag keeps the
        // cost bounded: about 2 M multiply-adds per hop at E2.
        val w = minOf(samples.size - maxLag, 4 * maxLag)
        if (w < maxLag) return null
        val start = samples.size - (w + maxLag)

        // Step 1+2: difference function for EVERY lag from 1. The lags below
        // minLag are never candidates, but the normalisation in step 3 needs
        // them: computing d only from minLag left the running sum holding
        // zeros, so at the true period it held a sixth of what it should and
        // the normalised dip was inflated six times — enough to miss the
        // threshold on any string that is not cleanly periodic, which is
        // every bass string.
        val d = DoubleArray(maxLag + 1)
        for (lag in 1..maxLag) {
            var sum = 0.0
            for (i in 0 until w) {
                val delta = (samples[start + i] - samples[start + i + lag]).toDouble()
                sum += delta * delta
            }
            d[lag] = sum
        }

        // Step 3: cumulative mean normalised difference
        val cmnd = DoubleArray(maxLag + 1)
        cmnd[0] = 1.0
        var runningSum = 0.0
        for (lag in 1..maxLag) {
            runningSum += d[lag]
            cmnd[lag] = if (runningSum == 0.0) 1.0 else d[lag] * lag / runningSum
        }

        // Step 4: absolute threshold — first dip below threshold, then local minimum
        var tau = -1
        var lag = minLag
        while (lag <= maxLag) {
            if (cmnd[lag] < threshold) {
                while (lag + 1 <= maxLag && cmnd[lag + 1] < cmnd[lag]) lag++
                tau = lag
                break
            }
            lag++
        }
        if (tau == -1) return null

        // Step 5: parabolic interpolation around tau for sub-sample precision
        val betterTau: Double = if (tau in 1 until maxLag) {
            val s0 = cmnd[tau - 1]
            val s1 = cmnd[tau]
            val s2 = cmnd[tau + 1]
            val denom = 2.0 * (2.0 * s1 - s2 - s0)
            if (denom == 0.0) tau.toDouble() else tau + (s2 - s0) / denom
        } else {
            tau.toDouble()
        }

        return sampleRateHz / betterTau
    }
}
