package at.clavierhaus.unisonmaster.tuning

import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.audio.AudioSource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SR = 48_000
private const val HOP = 4096

/** A struck string: fundamental plus two partials, exponential decay. */
private fun strike(f0: Double, seconds: Double, amp: Double = 0.4, tau: Double = 1.5): FloatArray {
    val n = (seconds * SR).toInt()
    return FloatArray(n) { i ->
        val t = i.toDouble() / SR
        val env = amp * exp(-t / tau)
        (env * (sin(2 * PI * f0 * t) + 0.4 * sin(2 * PI * 2 * f0 * t + 0.3) +
            0.2 * sin(2 * PI * 3 * f0 * t + 1.1))).toFloat()
    }
}

private fun feed(live: LiveReference, signal: FloatArray) {
    var pos = 0
    val buf = FloatArray(HOP)
    while (pos + HOP <= signal.size) {
        signal.copyInto(buf, 0, pos, pos + HOP)
        live.push(buf)
        pos += HOP
    }
}

/** Plays a prepared signal synchronously, one hop per callback. */
private class LiveTestSource(private val signal: FloatArray) : AudioSource {
    override val sampleRateHz: Int = SR
    override fun start(bufferSize: Int, onBuffer: (FloatArray) -> Unit) {
        var pos = 0
        val buf = FloatArray(bufferSize)
        while (pos + bufferSize <= signal.size) {
            signal.copyInto(buf, 0, pos, pos + bufferSize)
            onBuffer(buf)
            pos += bufferSize
        }
    }
    override fun stop() = Unit
}

class LiveReferenceTest {

    @Test
    fun readsASteadyString() {
        val live = LiveReference(SR)
        feed(live, FloatArray(HOP * 2) + strike(440.37, 3.0))
        val hz = live.hz
        assertTrue(hz != null && abs(hz - 440.37) < 0.02, "read $hz, expected 440.37")
        assertTrue(live.level > 0.5, "bell should stand while the note sounds, level ${live.level}")
    }

    @Test
    fun aQuietStrikeIsRegistered() {
        // about -54 dBFS: below the old threshold, above the new one
        val live = LiveReference(SR)
        feed(live, FloatArray(HOP * 2) + strike(440.2, 3.0, amp = 0.0025))
        val hz = live.hz
        assertTrue(hz != null && abs(hz - 440.2) < 0.03, "read $hz, expected 440.2")
        assertTrue(live.level > 0.5, "a quiet strike still gets a full bell, level ${live.level}")
    }

    @Test
    fun theBellStartsFullAtTheStrike() {
        val live = LiveReference(SR)
        feed(live, FloatArray(HOP * 2) + strike(440.0, 0.3))
        assertTrue(live.level > 0.9, "level right after the strike ${live.level}")
    }

    @Test
    fun readingsAreRoundedToATenth() {
        assertEquals(440.4, LiveReference.roundToTenth(440.37))
        assertEquals(441.0, LiveReference.roundToTenth(440.96))
    }

    @Test
    fun aNewStrikeIsNotAveragedWithTheLast() {
        val live = LiveReference(SR)
        feed(live, FloatArray(HOP * 2) + strike(440.0, 2.5) + strike(441.2, 2.5))
        val hz = live.hz
        assertTrue(hz != null && abs(hz - 441.2) < 0.03, "read $hz, expected the new strike 441.2")
    }

    @Test
    fun silenceGivesNoReading() {
        val live = LiveReference(SR)
        feed(live, FloatArray(SR * 2))
        assertNull(live.hz)
        assertEquals(0.0, live.level)
    }

    @Test
    fun theReadingIsHeldAfterTheToneDies() {
        val live = LiveReference(SR)
        feed(live, FloatArray(HOP * 2) + strike(439.6, 2.0) + FloatArray(SR))
        val hz = live.hz
        assertTrue(hz != null && abs(hz - 439.6) < 0.03, "held $hz, expected 439.6")
        assertEquals(0.0, live.level)
    }

    @Test
    fun controllerFollowsAndDoneSetsTheReference() {
        val tuning = TuningController(LiveTestSource(FloatArray(HOP * 2) + strike(441.84, 3.0)))
        tuning.startLive()
        val hz = tuning.liveHz.value
        assertTrue(hz != null && abs(hz - 441.84) < 0.02, "live $hz, expected 441.84")
        val set = tuning.acceptLive()
        assertEquals(441.8, set)
        assertEquals(441.8, tuning.referenceA4Hz.value)
        tuning.stopLive()
    }

    // ---- the fundamental with a neighbour in the range ----

    /** A stiff string struck at [startS]: ten partials, slow decay, a little noise. */
    private fun stiff(f1: Double, seconds: Double, amp: Double, startS: Double, b: Double = 3.0e-4): FloatArray {
        val f0 = f1 / kotlin.math.sqrt(1 + b)
        val rnd = kotlin.random.Random((f1 * 100).toInt())
        return FloatArray((seconds * SR).toInt()) { i ->
            val t = i.toDouble() / SR - startS
            if (t < 0) 0f else {
                var v = 0.0
                for (k in 1..10) v += amp / k * sin(2 * PI * k * f0 * kotlin.math.sqrt(1 + b * k * k) * t + k)
                (v * exp(-t / 4.0) + 1e-4 * (rnd.nextDouble() * 2 - 1)).toFloat()
            }
        }
    }

    /** Every reading of the app's live pipeline (hop 1024, range ±3 semitones on [targetHz]). */
    private fun readings(signal: FloatArray, targetHz: Double, fromS: Double): List<Double> {
        val semis = Math.pow(2.0, 3.0 / 12.0)
        val live = LiveReference(SR, hopSize = 1024, minHz = targetHz / semis, maxHz = targetHz * semis)
        val out = ArrayList<Double>()
        val buf = FloatArray(1024)
        var pos = 0
        while (pos + 1024 <= signal.size) {
            signal.copyInto(buf, 0, pos, pos + 1024)
            live.push(buf)
            pos += 1024
            if (pos >= fromS * SR) live.hz?.let(out::add)
        }
        return out
    }

    @Test
    fun aNeighbourStillRingingDoesNotPullTheFundamentalOffTheString() {
        val dSharp3 = 155.56; val e3 = 164.81
        val sig = stiff(e3, 4.0, 0.2, 0.0)                 // E3 rings from the start ...
        val struck = stiff(dSharp3, 4.0, 0.2, 0.5)          // ... D#3 is struck half a second later
        val mix = FloatArray(sig.size) { sig[it] + struck[it] }
        val rs = readings(mix, dSharp3, fromS = 1.2)
        assertTrue(rs.size > 50)
        val worst = rs.maxOf { abs(it - dSharp3) }
        assertTrue(worst < 0.5, "readings strayed up to %.2f Hz from D#3 with E3 ringing".format(worst))
    }

    @Test
    fun twoNotesSoundingReadAsOneOrTheOtherNeverAsAPitchBetween() {
        val dSharp3 = 155.56; val d3 = 146.83
        val a = stiff(dSharp3, 4.0, 0.2, 0.5)
        val c = stiff(d3, 4.0, 0.2, 0.5)
        val mix = FloatArray(a.size) { a[it] + c[it] }
        val rs = readings(mix, dSharp3, fromS = 1.2)
        assertTrue(rs.size > 50)
        val between = rs.filter { abs(it - dSharp3) > 0.5 && abs(it - d3) > 0.5 }
        assertTrue(between.isEmpty(), "read a pitch that is neither note: ${between.take(5)}")
    }

    @Test
    fun theOctaveAboveSoundingAloneIsNotReadAsThisNote() {
        val dSharp3 = 155.56; val dSharp4 = 311.13
        val rs = readings(stiff(dSharp4, 3.0, 0.2, 0.0), dSharp3, fromS = 0.0)
        assertTrue(rs.isEmpty(), "read ${rs.take(3)} with nothing but D#4 sounding: a phantom at half its pitch")
    }

    @Test
    fun detectionNamesTheFundamentalEvenWhereTheSecondPartialIsLouder() {
        // an E2 string as the microphone hears it: partial 2 ten dB above partial 1
        val f1 = 82.41; val b = 1.8e-4
        val f0 = f1 / kotlin.math.sqrt(1 + b)
        val sig = FloatArray(3 * SR) { i ->
            val t = i.toDouble() / SR
            var v = 0.0
            for (k in 1..8) v += (if (k == 2) 0.3 else 0.1 / k) * sin(2 * PI * k * f0 * kotlin.math.sqrt(1 + b * k * k) * t + k)
            (v * exp(-t / 4.0)).toFloat()
        }
        val live = LiveReference(SR, hopSize = 1024, minHz = 400.0, maxHz = 500.0)   // the screen is on A4
        val buf = FloatArray(1024)
        var pos = 0
        val seen = ArrayList<Int>()
        while (pos + 1024 <= sig.size) { sig.copyInto(buf, 0, pos, pos + 1024); live.push(buf); pos += 1024; live.detectedMidi?.let(seen::add) }
        assertTrue(seen.isNotEmpty(), "nothing detected")
        val bad = seen.filter { it != 40 }
        assertTrue(bad.isEmpty(), "detected ${bad.take(3)} for an E2 string")
    }
}
