package at.clavierhaus.unisonmaster.picture

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The strobe at the pin's pace in the treble (PinPace, 14 Nov 2026): unchanged up to C6, slower above, by the piano's own B. */
class PinPaceTest {
    @Test
    fun nothingChangesUpToC6() {
        val b = { _: Int -> 1e-3 }
        for (m in 21..PinPace.FROM) assertEquals(1.0, PinPace.of(m, b), "key $m")
    }

    @Test
    fun aboveC6TheStrobeSlowsAsTheStringShortens() {
        // B rising by half again per octave's twelfth part of its doubling: any rising curve will do
        val b = { m: Int -> 1e-3 * 2.0.pow((m - PinPace.FROM) / 7.0) }
        var last = 1.0
        for (m in PinPace.FROM + 1..108) {
            val p = PinPace.of(m, b)
            assertTrue(p < last, "key $m slower than the key below: $p against $last")
            last = p
        }
        // the formula itself, at E7: (B ratio)^(-3/4) times (f ratio)^(-1/2)
        val e7 = 100
        val want = (2.0.pow((e7 - PinPace.FROM) / 7.0)).pow(-0.75) * (2.0.pow((e7 - PinPace.FROM) / 12.0)).pow(-0.5)
        assertEquals(want, PinPace.of(e7, b), 1e-12)
    }

    @Test
    fun withoutAnInharmonicityThePaceStaysOne() {
        assertEquals(1.0, PinPace.of(100) { 0.0 })
    }
}
