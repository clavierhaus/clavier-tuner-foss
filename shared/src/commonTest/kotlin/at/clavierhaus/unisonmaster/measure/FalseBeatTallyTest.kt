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

    /**
     * A rate that does not repeat is not a rate (the Core Directive; FALSE-BEATS §3): three strikes
     * agreeing on 0.63 / 0.63 / 0.68 Hz are the Samick's E6 and are claimed; three at 0.74, 0.59
     * and 1.01 are the Bösendorfer A3's slow fundamental caught in early windows and are not.
     */
    @Test
    fun threeStrikesMustAgreeOnTheRate() {
        val t = FalseBeatTally()
        t.heard(3, 100L, 0.63, 0.8); t.heard(3, 240L, 0.63, 0.8); t.heard(3, 390L, 0.68, 0.8)
        t.heard(1, 100L, 0.74); t.heard(1, 240L, 0.59); t.heard(1, 390L, 1.01)
        assertEquals(listOf(3), t.claimed(), "E6's three strikes agree; k1's do not: ${t.ratesFor(1)}")
        // a strike heard again keeps the rate of its best-fitting window
        t.heard(3, 390L, 2.0, fit = 0.3)
        assertEquals(listOf(0.63, 0.63, 0.68), t.ratesFor(3))
        t.heard(3, 390L, 0.66, fit = 0.9)
        assertEquals(listOf(0.63, 0.63, 0.66), t.ratesFor(3))
    }

    /** The claim as the string's fact: the median rate and depth over the strikes that made it, and nothing for a partial not claimed. */
    @Test
    fun aClaimCarriesTheMedianRateAndDepthOfItsStrikes() {
        val t = FalseBeatTally()
        t.heard(3, 100L, 0.63, 0.8, depthDb = 3.2); t.heard(3, 240L, 0.68, 0.8, depthDb = 4.1); t.heard(3, 390L, 0.63, 0.8, depthDb = 3.6)
        t.heard(1, 100L, 0.74, 0.8, 8.0); t.heard(1, 240L, 0.59, 0.8, 8.0)
        assertEquals(listOf(FalseBeat(3, 0.63, 3.6)), t.claims())
        t.heard(1, 390L, 0.70, 0.8, 9.0)
        assertEquals(listOf(FalseBeat(1, 0.70, 8.0), FalseBeat(3, 0.63, 3.6)), t.claims())
    }
}
