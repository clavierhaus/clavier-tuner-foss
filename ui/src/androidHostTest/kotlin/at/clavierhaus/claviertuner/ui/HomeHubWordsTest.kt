package at.clavierhaus.claviertuner.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** Every door of the hub reads first word white, the rest orange (26 Nov 2026), however its words were handed in. */
class HomeHubWordsTest {
    private fun door(verb: String, rest: String = "", lead: String = "") = HomeHubItem(verb, rest, lead = lead) { }

    @Test fun twoWordsSplitWhiteThenOrange() {
        assertEquals("Continue" to "Tuning", whiteThenOrange(door("Continue", " Tuning")))
        assertEquals("My" to "Customers", whiteThenOrange(door("Customers", lead = "My")))
        assertEquals("Pitch" to "raise", whiteThenOrange(door("Pitch raise")))
    }

    @Test fun oneWordIsOrange() = assertEquals("" to "Recordings", whiteThenOrange(door("Recordings")))
}
