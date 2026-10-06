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
