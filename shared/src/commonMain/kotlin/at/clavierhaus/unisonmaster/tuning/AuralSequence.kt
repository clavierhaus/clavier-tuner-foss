package at.clavierhaus.unisonmaster.tuning

/**
 * An aural tuning sequence: the notes in the order a tuner visits them by
 * ear — the temperament by fourths and fifths, say — so that the tuning
 * screen can walk that order instead of the keyboard's. The student tunes
 * each interval by ear; the reading beside it is the engine's own, unchanged,
 * and says whether the ear landed. Nothing here measures anything: this file
 * orders what the screen shows.
 *
 * TuneLab had this as "aural temperament sequence control" (Pro 4.0 and 5.1
 * manuals, *Special Functions*): a plain text file listing the notes, space
 * bar for the next, backspace for the previous, no automatic note switching.
 * The format here is a superset of that file: one note per line, and anything
 * after the note is the hint shown with it. Lines beginning with `#` and blank
 * lines are skipped. A line that is not a note stops the whole file with its
 * line number: a sequence with one wrong step teaches the wrong thing.
 *
 *     # 4ths and 5ths — an order, not a method
 *     A4   the reference
 *     A3   octave below A4
 *     D4   4th up from A3
 */
class AuralSequence(val name: String, val steps: List<Step>) {
    /** One note of the sequence and what the tuner is told about it. */
    data class Step(val midi: Int, val hint: String) {
        val note: String get() = Notes.name(midi)
    }

    init {
        require(steps.isNotEmpty()) { "a sequence has at least one note" }
    }

    val size: Int get() = steps.size

    companion object {
        /** Reads [text] as a sequence named [name]; throws with the line number of the first line that is not a note. */
        fun parse(name: String, text: String): AuralSequence {
            val steps = ArrayList<Step>()
            text.lines().forEachIndexed { i, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                val head = line.takeWhile { !it.isWhitespace() }
                val midi = Notes.midi(head)
                    ?: throw IllegalArgumentException("line ${i + 1}: '$head' is not a note (A0 to C8, # for sharps)")
                steps += Step(midi, line.drop(head.length).trim())
            }
            if (steps.isEmpty()) throw IllegalArgumentException("$name: no notes in the file")
            return AuralSequence(name, steps)
        }

        /**
         * The one sequence shipped: the temperament octave F3–F4 by fourths
         * and fifths, A4 first. An order, not a method — every line can be
         * changed, and a tuner's own files replace it.
         */
        const val EXAMPLE_NAME = "4ths and 5ths"
        const val EXAMPLE_TEXT = """# 4ths and 5ths — the temperament F3–F4 by fourths and fifths, A4 first.
# An order, not a method: change any line. One note per line; the words after it are shown with it.
A4   the reference
A3   octave below A4
D4   4th up from A3
G3   5th down from D4
C4   4th up from G3
F3   5th down from C4
A#3  4th up from F3
D#4  4th up from A#3
G#3  5th down from D#4
C#4  4th up from G#3
F#3  5th down from C#4
B3   4th up from F#3
E4   4th up from B3
A3   check: 5th down from E4, octave to A4
"""
        val EXAMPLE: AuralSequence get() = parse(EXAMPLE_NAME, EXAMPLE_TEXT)
    }
}
