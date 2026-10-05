package at.clavierhaus.unisonmaster.measure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FalseBeatTallyTest {
    @Test
    fun oneStrikeIsAnAnecdoteAndIsNotClaimed() {
        val t = FalseBeatTally()
        t.heard(2, strike = 100L)
        assertTrue(t.claimed().isEmpty(), "one strike claims nothing: ${t.claimed()}")
    }

    @Test
    fun aStrikeHeardOverManyHopsIsStillOneStrike() {
        val t = FalseBeatTally()
        repeat(40) { t.heard(2, strike = 100L) }
        assertEquals(1, t.strikesFor(2))
        assertTrue(t.claimed().isEmpty(), "forty hops of one strike are one strike")
    }

    @Test
    fun threeStrikesAreARateAndAreClaimed() {
        val t = FalseBeatTally()
        for (s in listOf(100L, 240L, 390L)) { t.heard(2, s); t.heard(5, s) }
        t.heard(3, 100L); t.heard(3, 240L)
        assertEquals(listOf(2, 5), t.claimed(), "partial 3 was heard on two strikes only")
        assertEquals(2, t.strikesFor(3), "and the evidence for it is kept")
    }

    @Test
    fun theTallyIsEmptiedWithTheNote() {
        val t = FalseBeatTally()
        for (s in listOf(1L, 2L, 3L)) t.heard(2, s)
        assertEquals(listOf(2), t.claimed())
        t.clear()
        assertTrue(t.claimed().isEmpty(), "a new note starts with no evidence")
        assertEquals(0, t.strikesFor(2))
    }
}
