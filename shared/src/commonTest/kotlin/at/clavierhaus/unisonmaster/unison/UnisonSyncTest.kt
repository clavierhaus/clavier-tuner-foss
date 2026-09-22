package at.clavierhaus.unisonmaster.unison

import at.clavierhaus.unisonmaster.measure.SyntheticString
import at.clavierhaus.unisonmaster.tuning.TuningSession
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SR = 48_000
private const val A4 = 443.0

/** Three strings of one note struck together, [cents] apart at their first partial. */
private fun unison(midi: Int, vararg cents: Double, seconds: Double = 5.0, b: Double = 4e-4): FloatArray {
    val f1 = TuningSession.targetF1(midi, A4)
    val out = FloatArray((seconds * SR).toInt())
    for ((i, c) in cents.withIndex()) {
        val s = SyntheticString.strike(f1 * Math2.pow2(c / 1200), b, seconds, amplitude = 0.08, decayS = 4.0, seed = 7 + i)
        for (j in out.indices) out[j] += s[j]
    }
    return out
}

private object Math2 { fun pow2(x: Double) = exp(x * kotlin.math.ln(2.0)) }

private fun UnisonSync.play(signal: FloatArray) {
    val buf = FloatArray(1024)
    var p = 0
    while (p + 1024 <= signal.size) { signal.copyInto(buf, 0, p, p + 1024); push(buf); p += 1024 }
}

/** Silence long enough for the strike before it to be judged. */
private fun quiet(seconds: Double = 6.5) = FloatArray((seconds * SR).toInt())

class UnisonSyncTest {
    private fun sync() = UnisonSync(SR, { A4 })

    @Test
    fun aCleanUnisonIsInSyncOnEveryPartialHeard() {
        val u = sync()
        u.play(unison(57, 0.0, 0.05, -0.05) + quiet())
        val s = u.state.value
        assertEquals(57, s.midi)
        assertTrue(s.counted >= 6, "partials heard: ${s.partials}")
        assertEquals(s.counted, s.best, "the best: ${s.best} of ${s.bestOf}; ${s.partials}")
    }

    @Test
    fun aStringTwoCentsOutBeatsOnThePartialsAndIsNotInSync() {
        val u = sync()
        u.play(unison(57, 0.0, 2.0, 0.0) + quiet())
        val s = u.state.value
        assertTrue(s.best <= 1, "a string 2 cents out (0.26 Hz at partial 1): best ${s.best} of ${s.bestOf}; ${s.partials}")
    }

    @Test
    fun aStringsOwnBeatIsMarkedByItsCheckAndLeftOutOfTheCount() {
        val u = sync()
        // one string alone, whose third partial is split in two 0.6 Hz apart: a false beat
        val f1 = TuningSession.targetF1(57, A4)
        val p3 = SyntheticString.partialHz(f1, 4e-4, 3)
        fun falseBeat(n: Int) = FloatArray(n) { i ->
            val t = i.toDouble() / SR - 0.3
            if (t < 0) 0f else (0.02 * exp(-t / 4.0) * sin(2 * PI * (p3 + 0.6) * t)).toFloat()
        }
        val single = SyntheticString.strike(f1, 4e-4, 5.0, amplitude = 0.08, decayS = 4.0)
        val fb = falseBeat(single.size)
        u.checkString()
        u.play(FloatArray(single.size) { single[it] + fb[it] } + quiet())
        val checked = u.state.value
        assertEquals(1, checked.stringsChecked)
        assertTrue(checked.partials.first { it.k == 3 }.ownBeat, "partial 3 marked: ${checked.partials}")
        assertEquals(listOf(3), checked.partials.filter { it.ownBeat }.map { it.k })
        // the unison, clean but for that string's own beat: in sync on everything else
        val clean = unison(57, 0.0, 0.05, -0.05)
        val fb2 = falseBeat(clean.size)
        u.play(FloatArray(clean.size) { clean[it] + fb2[it] } + quiet())
        val s = u.state.value
        assertEquals(s.counted, s.best, "all counted partials in sync: ${s.partials}")
        assertTrue(s.partials.none { it.k == 3 && !it.ownBeat })
    }
}
