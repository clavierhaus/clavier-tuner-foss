package at.clavierhaus.unisonmaster.i18n

import at.clavierhaus.unisonmaster.tuning.Notes
import kotlin.test.Test
import kotlin.test.assertEquals

/** German note names on the screen (Peter, 14 Oct 2026): H for B, the sharps as they are, the files untouched. */
class NoteNamesTest {
    @Test
    fun germanWritesHForBAndKeepsTheSharps() {
        assertEquals("H1", noteName(35, "de"))      // B1
        assertEquals("A#1", noteName(34, "de"))     // A#1 stays
        assertEquals("C4", noteName(60, "de"))
        assertEquals("B1", noteName(35, "en"))
        assertEquals("B1", Notes.name(35), "the corpus' names never change with the language")
    }
}
