package at.clavierhaus.unisonmaster.tuning

import kotlin.test.Test
import kotlin.test.assertEquals

class PartialSelectionTest {
    @Test
    fun selectionFollowsTheHubRules() {
        val audible = setOf(1, 2, 3, 5)
        assertEquals(setOf(1), PartialSelection.shown(false, audible, emptySet()))
        assertEquals(audible, PartialSelection.shown(true, audible, emptySet()))
        val hidden = PartialSelection.tap(3, true, audible, emptySet())
        assertEquals(setOf(3), hidden)
        assertEquals(setOf(1, 2, 5), PartialSelection.shown(true, audible, hidden))
        assertEquals(emptySet(), PartialSelection.tap(3, true, audible, hidden))
        assertEquals(emptySet(), PartialSelection.tap(4, true, audible, emptySet()), "inaudible partial ignored")
        assertEquals(emptySet(), PartialSelection.tap(2, false, audible, emptySet()), "no taps in fundamental mode")
    }
}
