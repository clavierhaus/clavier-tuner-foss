package at.clavierhaus.unisonmaster.measure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The other strobes' places (BandPlaces, 31 Oct 2026): the ratio as read since the strike, averaged over the equalizer's window. */
class BandPlacesTest {
    @Test fun nothingIsPlacedUntilBothPartialsWereReadTogether() {
        val p = BandPlaces()
        assertNull(p.ratio(3, 10))
        p.push(10, 3, 0.0, 220.0)
        assertNull(p.ratio(3, 10), "a reading of nothing places nothing")
    }

    @Test fun overTheWholeStrikeTheRatioIsTheMeanOfEveryReading() {
        val p = BandPlaces()
        p.push(1, 3, 3.0, 1.0); p.push(2, 3, 3.3, 1.0); p.push(50, 3, 3.6, 1.0)
        assertEquals(3.3, p.ratio(3, 50)!!, 1e-12)
    }

    @Test fun aShortWindowForgetsTheOlderReadingsButNeverTheLast() {
        val p = BandPlaces(windowHops = 10)
        p.push(1, 3, 3.0, 1.0); p.push(20, 3, 3.6, 1.0)
        assertEquals(3.6, p.ratio(3, 20)!!, 1e-12)
        assertEquals(3.6, p.ratio(3, 500)!!, 1e-12, "the last reading stands until the next strike clears it")
        p.clear(); assertNull(p.ratio(3, 500))
    }
}
