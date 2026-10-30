package at.clavierhaus.unisonmaster.picture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BandFadeTest {
    private fun fade() = BandFade(fadeMs = 300, waitMs = 800)

    @Test fun eachBandComesUpOverThreeHundredMillisecondsFromItsOwnFirstReading() {
        val f = fade()
        assertEquals(mapOf(4 to 0f), f.alphas(1000, setOf(4), setOf(1, 2, 4), together = false))
        assertEquals(0.5f, f.alphas(1150, setOf(4), setOf(1, 2, 4), false).getValue(4), 1e-6f)
        val later = f.alphas(1300, setOf(4, 2), setOf(1, 2, 4), false)
        assertEquals(1f, later.getValue(4)); assertEquals(0f, later.getValue(2))
        assertEquals(1f, f.alphas(1700, setOf(4, 2), setOf(1, 2, 4), false).getValue(2))
    }

    @Test fun togetherNothingComesUpUntilEveryBandIsReadThenAllAtOnce() {
        val f = fade()
        assertEquals(0f, f.alphas(1000, setOf(4), setOf(1, 2, 4), true).getValue(4))
        assertTrue(f.alphas(1200, setOf(4, 2), setOf(1, 2, 4), true).values.all { it == 0f }, "two of three read: nothing drawn")
        f.alphas(1400, setOf(4, 2, 1), setOf(1, 2, 4), true)
        val up = f.alphas(1550, setOf(4, 2, 1), setOf(1, 2, 4), true)
        assertEquals(setOf(0.5f), up.values.toSet(), "the row comes up as one")
    }

    @Test fun togetherTheRowWaitsNoLongerThanTheWaitAfterTheFirstBand() {
        val f = fade()
        f.alphas(1000, setOf(4), setOf(1, 2, 4), true)
        assertEquals(0f, f.alphas(1799, setOf(4, 2), setOf(1, 2, 4), true).getValue(2))
        f.alphas(1800, setOf(4, 2), setOf(1, 2, 4), true)
        assertEquals(1f, f.alphas(2100, setOf(4, 2), setOf(1, 2, 4), true).getValue(4))
        // partial 1, read only now, comes up on its own
        assertEquals(0f, f.alphas(2200, setOf(4, 2, 1), setOf(1, 2, 4), true).getValue(1))
        assertEquals(1f, f.alphas(2500, setOf(4, 2, 1), setOf(1, 2, 4), true).getValue(1))
    }

    @Test fun aBandNoLongerReadLeavesAndASilentRowStartsAfresh() {
        val f = fade()
        f.alphas(1000, setOf(4, 2), setOf(2, 4), false)
        assertEquals(setOf(4), f.alphas(1400, setOf(4), setOf(2, 4), false).keys)
        assertEquals(0f, f.alphas(1500, setOf(4, 2), setOf(2, 4), false).getValue(2), "partial 2 read again comes up again")
        assertEquals(emptyMap(), f.alphas(2000, emptySet(), setOf(2, 4), true))
        assertEquals(0f, f.alphas(3000, setOf(4), setOf(2, 4), true).getValue(4), "the next strike waits for its row again")
    }
}
