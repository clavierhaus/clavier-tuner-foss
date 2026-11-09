package at.clavierhaus.unisonmaster.picture

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BandPhaseTest {
    private val clockAlreadyOldMs = 3_600_000L   // an hour of frames before the tuner looked

    /** A steady rate advances by rate x dt per frame, whatever the clock already stands at. */
    @Test
    fun steadyRateAdvancesByRateTimesDt() {
        val p = BandPhase()
        var t = clockAlreadyOldMs
        p.advance(t, mapOf(1 to 0.8))
        var last = 0.0
        repeat(10) {
            t += 100
            val now = p.advance(t, mapOf(1 to 0.8))[1]!!
            val step = (now - last + 1.0) % 1.0
            assertTrue(abs(step - 0.08) < 1e-9, "a tenth of a second at 0.8 Hz is 0.08 of a period, got $step")
            last = now
        }
    }

    /**
     * The fault the bench read found. A whisper in the reading must move the
     * pattern by a whisper, not by the age of the clock: 0.001 Hz over 16 ms is
     * 16 millionths of a period, while rate x absolute-time would move it 3.6
     * whole periods and land the band anywhere.
     */
    @Test
    fun aWhisperInTheRateDoesNotTeleportThePattern() {
        val p = BandPhase()
        var t = clockAlreadyOldMs
        p.advance(t, mapOf(1 to 0.800))
        t += 16
        val before = p.advance(t, mapOf(1 to 0.800))[1]!!
        t += 16
        val after = p.advance(t, mapOf(1 to 0.801))[1]!!
        val step = (after - before + 1.0) % 1.0
        assertTrue(step < 0.02, "one frame at 0.801 Hz moves 0.0128 of a period; moved $step")
    }

    /**
     * A string 60 Hz off at 60 frames a second turned the band one whole period per frame, and on the screen it stood
     * still: the 225's E7 on 9 Nov 2026 was tuned to where the band stopped, 60.6 Hz flat of its target. However far off
     * the string, the band goes on moving, and the way the string lies.
     */
    @Test
    fun aStringFarOffKeepsTheBandMovingTheWayTheStringLies() {
        for (rate in listOf(60.0, -60.0, 120.0, -120.0, 45.0, -45.0, 30.0, -30.0, 24.0)) {
            val p = BandPhase()
            val start = clockAlreadyOldMs
            var last = p.advance(start, mapOf(1 to rate))[1]!!
            for (frame in 1..60) {
                val now = p.advance(start + frame * 1000L / 60, mapOf(1 to rate))[1]!!
                var step = now - last
                if (step > 0.5) step -= 1.0
                if (step < -0.5) step += 1.0
                assertTrue(step * rate > 0 && abs(step) > 0.05, "at $rate Hz frame $frame moved $step of a period")
                last = now
            }
        }
    }

    /** Below the limit the band's speed is the beat, untouched. */
    @Test
    fun aBeatTheEyeCanFollowIsDrawnAsItIs() {
        val p = BandPhase()
        val a = p.advance(clockAlreadyOldMs, mapOf(1 to 9.0))[1]!!
        val b = p.advance(clockAlreadyOldMs + 20, mapOf(1 to 9.0))[1]!!
        assertEquals(0.18, b - a, 1e-9)
    }

    /** Where the window Mistral measured should have stood still: no target difference, no motion. */
    @Test
    fun zeroRateHoldsStill() {
        val p = BandPhase()
        var t = clockAlreadyOldMs
        p.advance(t, mapOf(1 to 0.8))
        repeat(5) { t += 333; p.advance(t, mapOf(1 to 0.8)) }
        val parked = p.advance(t, mapOf(1 to 0.0))[1]!!
        repeat(8) {
            t += 333
            assertEquals(parked, p.advance(t, mapOf(1 to 0.0))[1]!!, 1e-12, "a band at rest moved")
        }
    }

    /** A band that leaves the set does not return holding where it stood. */
    @Test
    fun aBandThatLeavesIsForgotten() {
        val p = BandPhase()
        var t = clockAlreadyOldMs
        p.advance(t, mapOf(1 to 0.5, 3 to 0.5))
        t += 1000
        val moved = p.advance(t, mapOf(1 to 0.5, 3 to 0.5))[3]!!
        assertTrue(moved > 0.4, "band 3 should have advanced half a period, stands at $moved")
        t += 16; p.advance(t, mapOf(1 to 0.5))
        t += 16
        // readmitted at nothing-carried-over plus this frame's own advance, not at where it stood
        assertEquals(0.008, p.advance(t, mapOf(1 to 0.5, 3 to 0.5))[3]!!, 1e-9)
    }
}
