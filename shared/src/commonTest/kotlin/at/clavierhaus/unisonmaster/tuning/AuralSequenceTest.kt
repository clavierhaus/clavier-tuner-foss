package at.clavierhaus.unisonmaster.tuning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuralSequenceTest {
    @Test
    fun aFileIsOneNotePerLineAndTheRestOfTheLineIsTheHint() {
        val s = AuralSequence.parse("mine", "# a comment\n\nA4 the reference\nA3\n  F#3   5th down from C#4  \n")
        assertEquals(3, s.size)
        assertEquals(69, s.steps[0].midi); assertEquals("the reference", s.steps[0].hint)
        assertEquals(57, s.steps[1].midi); assertEquals("", s.steps[1].hint)
        assertEquals(54, s.steps[2].midi); assertEquals("5th down from C#4", s.steps[2].hint)
        assertEquals("F#3", s.steps[2].note)
    }

    @Test
    fun aLineThatIsNotANoteStopsTheFileAndNamesTheLine() {
        val e = assertFailsWith<IllegalArgumentException> { AuralSequence.parse("bad", "A4\nA3\nH3 the German B\n") }
        assertTrue(e.message!!.startsWith("line 3:"), e.message)
        assertFailsWith<IllegalArgumentException> { AuralSequence.parse("empty", "# nothing\n") }
    }

    @Test
    fun aTuneLabFileOfBareNotesParses() {
        val s = AuralSequence.parse("4ths and 5ths.txt", "A4\nA3\nD4\nG3\nC4\nF3\n")
        assertEquals(listOf(69, 57, 62, 55, 60, 53), s.steps.map { it.midi })
        assertTrue(s.steps.all { it.hint.isEmpty() })
    }

    @Test
    fun theShippedExampleIsALegalCycleOfFourthsAndFifthsInsideF3ToF4() {
        val s = AuralSequence.EXAMPLE
        assertEquals(AuralSequence.EXAMPLE_NAME, s.name)
        assertEquals(69, s.steps.first().midi, "A4 first")
        val rest = s.steps.drop(1).map { it.midi }
        assertTrue(rest.all { it in 53..65 }, "every other note inside F3-F4: $rest")
        // every step from the second on is a fourth or a fifth from the one before, or the octave check
        rest.zipWithNext().forEach { (a, b) ->
            val d = kotlin.math.abs(b - a)
            assertTrue(d == 5 || d == 7, "${Notes.name(a)} -> ${Notes.name(b)} is $d semitones, not a 4th or 5th")
        }
        assertEquals(12, rest.toSet().size, "every note of the temperament octave is visited once, A3 twice")
    }
}
