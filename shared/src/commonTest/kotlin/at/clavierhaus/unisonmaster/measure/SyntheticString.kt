package at.clavierhaus.unisonmaster.measure

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A struck stiff string for the tests: partials k·f0·√(1+B·k²), each
 * falling 3 dB per partial and decaying over [decayS], struck at [strikeS]
 * (silence before, bar [noise]). Nothing about any recorded piano.
 */
object SyntheticString {
    fun f0For(f1: Double, b: Double) = f1 / sqrt(1 + b)

    fun partialHz(f1: Double, b: Double, k: Int) = k * f0For(f1, b) * sqrt(1 + b * k * k)

    fun strike(
        f1: Double,
        b: Double,
        seconds: Double,
        sampleRate: Int = 48_000,
        strikeS: Double = 0.3,
        partials: Int = 12,
        amplitude: Double = 0.2,
        decayS: Double = 3.0,
        noise: Double = 1e-4,
        seed: Int = 1,
        /** Hz of drift per second of partial 1 (a pin moving); the other partials follow in proportion. */
        driftHzPerS: Double = 0.0,
    ): FloatArray {
        val rng = Random(seed)
        val n = (seconds * sampleRate).toInt()
        val out = FloatArray(n)
        val phase = DoubleArray(partials + 1)
        val start = (strikeS * sampleRate).toInt()
        for (i in 0 until n) {
            var x = noise * (rng.nextDouble() * 2 - 1)
            if (i >= start) {
                val t = (i - start).toDouble() / sampleRate
                val env = exp(-t / decayS) * (1 - exp(-t / 0.002))
                val f1Now = f1 + driftHzPerS * t
                for (k in 1..partials) {
                    val hz = partialHz(f1Now, b, k)
                    if (hz > sampleRate * 0.45) break
                    phase[k] += 2 * PI * hz / sampleRate
                    x += amplitude * env * 10.0.pow(-3.0 * (k - 1) / 20) * sin(phase[k])
                }
            }
            out[i] = x.toFloat()
        }
        return out
    }
}
