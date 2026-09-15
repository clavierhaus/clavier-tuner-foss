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
}
