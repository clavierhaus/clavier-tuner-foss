package at.clavierhaus.unisonmaster.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** The tuning screen's band seats (BasicHub.strobeSeats, 30 Oct 2026). */
class StrobeSeatsTest {
    @Test fun allOfferedPartialsLessThoseTakenOut() {
        assertEquals((1..8).toList(), strobeSeats(8, 4, emptySet(), 0, false, emptySet()))
        assertEquals(listOf(1, 2, 4, 5, 6, 7, 8), strobeSeats(8, 4, setOf(3), 0, false, emptySet()))
    }

    @Test fun theLowestNWithTheTunedBandAlwaysAmongThem() {
        assertEquals(listOf(1, 2, 3, 4), strobeSeats(8, 4, emptySet(), 4, false, emptySet()))
        assertEquals(listOf(1, 2, 6), strobeSeats(8, 6, emptySet(), 3, false, emptySet()))
        assertEquals(listOf(6), strobeSeats(8, 6, emptySet(), 1, false, emptySet()))
        assertEquals((1..3).toList(), strobeSeats(3, 2, emptySet(), 5, false, emptySet()))
    }

    @Test fun oneBandIsWhatTheEngineReads() {
        assertEquals(listOf(2), strobeSeats(8, 2, emptySet(), 0, true, setOf(2)))
        assertEquals(listOf(2, 5), strobeSeats(8, 2, emptySet(), 3, true, setOf(2, 5)))
    }
}
