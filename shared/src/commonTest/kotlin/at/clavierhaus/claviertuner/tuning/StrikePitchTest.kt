package at.clavierhaus.claviertuner.tuning

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One pitch per strike, held (StrikePitch, 18 Nov 2026). Each test fails on the code it guards: a value cleared with the
 * sound, a plain mean in place of the power-weighted one, the fading tail counted, a strike of the key below kept.
 */
class StrikePitchTest {
    private val hop = 1024.0 / 48000.0

    /** A strike read loud at [hz] for [n] hops, falling 1 dB a hop. */
    private fun StrikePitch.ring(hz: Double, n: Int, startDb: Double = -30.0) {
        strike()
        for (i in 0 until n) push(hz, startDb - i, hop)
    }

    @Test
    fun theValueOutlivesTheSoundAndTheNextStrikeReplacesIt() {
        val p = StrikePitch()
        p.ring(1120.0, 15)
        p.push(null, -200.0, hop)
        val held = p.value!!
        assertFalse(held.sounding)
        assertEquals(1120.0, held.hz, 1e-9)
        p.strike()
        assertEquals(1120.0, p.value!!.hz, 1e-9, "a new strike leaves the old value standing until it reads")
        p.push(1121.0, -30.0, hop)
        assertEquals(1121.0, p.value!!.hz, 1e-9)
        assertEquals(1120.0, p.value!!.previousHz!!, 1e-9)
    }

    @Test
    fun theLoudPartWeighsMostAndTheTailIsNotCounted() {
        val p = StrikePitch()
        p.strike()
        p.push(1120.0, -30.0, hop)        // loud
        p.push(1121.0, -40.0, hop)        // 10 dB down: a tenth of the weight
        p.push(1130.0, -55.0, hop)        // 25 dB down: the fading tail, ends the strike
        p.push(1130.0, -56.0, hop)
        val v = p.value!!
        val expected = (1120.0 * 1.0 + 1121.0 * 0.1) / 1.1
        assertTrue(abs(v.hz - expected) < 1e-6, "weighted ${v.hz} vs $expected")
        assertFalse(v.sounding)
    }

    @Test
    fun aShortStrikeIsRough() {
        val p = StrikePitch()
        p.ring(1120.0, 7)                 // 0.15 s
        p.push(null, -200.0, hop)
        assertTrue(p.value!!.rough)
        p.ring(1120.0, 14)                // 0.30 s
        p.push(null, -200.0, hop)
        assertFalse(p.value!!.rough)
    }

    @Test
    fun aStrikeOfAnotherKeyGivesNothing() {
        val p = StrikePitch()
        p.ring(1120.0, 15)
        p.push(null, -200.0, hop)
        p.strike()
        p.push(1108.0, -30.0, hop)        // read before the identifier named the key below
        p.cancel()
        assertEquals(1120.0, p.value!!.hz, 1e-9)
        assertNull(p.value!!.previousHz)
        p.push(1108.0, -31.0, hop)
        assertEquals(1120.0, p.value!!.hz, 1e-9)
    }

    @Test
    fun aNewStrikeHasReadNothingUntilItsFirstReading() {
        val p = StrikePitch()
        p.ring(1120.0, 15)
        p.push(null, -200.0, hop)
        p.strike()
        assertFalse(p.readThisStrike, "the value standing is the strike before's, not a verdict on this one")
        p.push(1121.0, -30.0, hop)
        assertTrue(p.readThisStrike)
    }
}
