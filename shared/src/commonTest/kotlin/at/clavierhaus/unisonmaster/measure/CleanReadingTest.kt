package at.clavierhaus.unisonmaster.measure

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CleanReadingTest {
    private val sr = 48_000
    private val hop = 1024

    /**
     * A struck string whose partial [falseK] is two components [splitHz]
     * apart at equal strength — a false beat — the rest clean; the whole
     * string [detuneCents] from the targets it is read against.
     */
    private fun string(f1: Double, b: Double, detuneCents: Double, falseK: Int, splitHz: Double, seconds: Double = 3.0): FloatArray {
        val n = (seconds * sr).toInt()
        val out = FloatArray(n)
        val f1d = f1 * 2.0.pow(detuneCents / 1200)
        val phase = DoubleArray(10)
        val phase2 = DoubleArray(10)
        val start = (0.3 * sr).toInt()
        for (i in 0 until n) {
            var x = 0.0
            if (i >= start) {
                val t = (i - start).toDouble() / sr
                val env = exp(-t / 3.0) * (1 - exp(-t / 0.002))
                for (k in 1..8) {
                    val hz = SyntheticString.partialHz(f1d, b, k)
                    val a = 0.2 * env * 10.0.pow(-3.0 * (k - 1) / 20)
                    if (k == falseK) {
                        phase[k] += 2 * PI * (hz - splitHz / 2) / sr; phase2[k] += 2 * PI * (hz + splitHz / 2) / sr
                        x += a / 2 * sin(phase[k]) + a / 2 * sin(phase2[k])
                    } else {
                        phase[k] += 2 * PI * hz / sr
                        x += a * sin(phase[k])
                    }
                }
            }
            out[i] = x.toFloat()
        }
        return out
    }

    private fun targets(f1: Double, b: Double) = (1..8).associateWith { SyntheticString.partialHz(f1, b, it) }

    private fun readAll(x: FloatArray, r: CleanReading, anchorK: Int, combine: Boolean): CleanReading.Result {
        var res: CleanReading.Result? = null
        var i = 0
        while (i + hop <= x.size) { res = r.push(x.copyOfRange(i, i + hop), anchorK, combine); i += hop }
        return res!!
    }

    @Test
    fun aMeasuredStringIsReadOnItsCleanPartialsAndTheFalseOneIsNamed() {
        val f1 = 440.0; val b = 4e-4
        val r = CleanReading(sr)
        r.target(targets(f1, b), reset = true)
        val res = readAll(string(f1, b, detuneCents = -3.0, falseK = 3, splitHz = 1.5), r, anchorK = 2, combine = true)
        assertEquals(-3.0, res.cents!!, 0.1)
        assertTrue(3 in res.falseBeating, "partial 3 beats on its own: ${res.partials}")
        assertTrue(3 !in res.used, "the false partial is not in the reading")
        assertTrue(res.used.size >= 4, "four clean partials at least: ${res.used}")
    }

    /**
     * A clean string in a room: every partial decays onto the room's floor,
     * and no partial beats on its own at any moment of the strike. The
     * floor is built as the recordings show it — not white, but many weak
     * steady components spread over the ±85 c the reader's neighbour probes
     * cover, together [floorDb] under each partial's first level. On the
     * Samick's nine F3 takes (30 Sept, read with the engine on 3 Oct) the
     * largest two-second spread of any partial was 0.22–0.30 c while it
     * stood 25 dB or more over the noise beside it, 0.27–0.52 c at 20 dB
     * and 0.32–0.88 c at 15 dB, and the engine called 22 partials false over
     * the nine takes, all of them past 4.6 s into the strike; this string
     * does the same. The partials decay in [tau] seconds (the takes' in
     * 1.1–2.3 s); the floor under partial 1 is set deeper, as the takes' is
     * — their partial 1 never fell under 26 dB over the noise beside it.
     */
    private fun decayingCleanString(f1: Double, b: Double, seconds: Double, floorDb: (Int) -> Double, tau: Double = 2.0, seed: Int = 7): FloatArray {
        val n = (seconds * sr).toInt()
        val out = FloatArray(n)
        val phase = DoubleArray(10)
        val start = (0.3 * sr).toInt()
        val rnd = java.util.Random(seed.toLong())
        val perPartial = 24
        val roomHz = ArrayList<Double>(); val roomA = ArrayList<Double>(); val roomPhase = ArrayList<Double>()
        for (k in 1..8) for (j in 0 until perPartial) {
            val off = (rnd.nextDouble() * 2 - 1) * 85.0
            roomHz += SyntheticString.partialHz(f1, b, k) * 2.0.pow(off / 1200.0)
            roomA += 0.2 * 10.0.pow(-3.0 * (k - 1) / 20) * 10.0.pow(-floorDb(k) / 20) / sqrt(perPartial.toDouble())
            roomPhase += rnd.nextDouble() * 2 * PI
        }
        for (i in 0 until n) {
            var x = 1e-3 * rnd.nextGaussian()
            for (j in roomHz.indices) { roomPhase[j] += 2 * PI * roomHz[j] / sr; x += roomA[j] * sin(roomPhase[j]) }
            if (i >= start) {
                val t = (i - start).toDouble() / sr
                for (k in 1..8) {
                    val hz = SyntheticString.partialHz(f1, b, k)
                    val env = exp(-t / tau) * (1 - exp(-t / 0.002))
                    val a = 0.2 * env * 10.0.pow(-3.0 * (k - 1) / 20)
                    phase[k] += 2 * PI * hz / sr
                    x += a * sin(phase[k])
                }
            }
            out[i] = x.toFloat()
        }
        return out
    }

    @Test
    fun aPartialDyingIntoTheRoomIsNotCalledFalse() {
        val f1 = 174.0; val b = 2.1e-4
        val r = CleanReading(sr)
        r.target(targets(f1, b), reset = true)
        val x = decayingCleanString(f1, b, seconds = 8.0, floorDb = { k -> if (k == 1) 60.0 else 30.0 })
        val everCalledFalse = HashMap<Int, String>()
        var everPresent = 0
        var i = 0
        while (i + hop <= x.size) {
            if (i == (0.3 * sr).toInt() / hop * hop) r.strike()
            val res = r.push(x.copyOfRange(i, i + hop), 4, combine = true)
            for (k in res.falseBeating) everCalledFalse.putIfAbsent(k, "at %.2f s, spread %.2f c".format(i.toDouble() / sr, res.partials.first { it.k == k }.spreadCents))
            everPresent = maxOf(everPresent, res.partials.count { it.present })
            i += hop
        }
        assertTrue(everPresent >= 6, "the partials were read while they stood: $everPresent present at most")
        assertTrue(everCalledFalse.isEmpty(), "a clean string, decaying, was called false: $everCalledFalse")
    }

    @Test
    fun anUnmeasuredStringIsReadOnThePartialBeingTunedAlone() {
        val f1 = 440.0; val b = 4e-4
        val r = CleanReading(sr)
        r.target(targets(f1, b), reset = true)
        val res = readAll(string(f1, b, detuneCents = 2.0, falseK = 6, splitHz = 2.0), r, anchorK = 2, combine = false)
        assertEquals(listOf(2), res.used)
        assertEquals(2.0, res.cents!!, 0.1)
    }

    @Test
    fun whenThePartialBeingTunedBeatsOnItsOwnAnotherStandsIn() {
        val f1 = 440.0; val b = 4e-4
        val r = CleanReading(sr)
        r.target(targets(f1, b), reset = true)
        val res = readAll(string(f1, b, detuneCents = 1.0, falseK = 2, splitHz = 1.2), r, anchorK = 2, combine = false)
        assertTrue(2 in res.falseBeating, "${res.partials}")
        assertEquals(1, res.used.size)
        assertTrue(res.used.single() != 2)
        assertEquals(1.0, res.cents!!, 0.15)
    }
}
