package at.clavierhaus.unisonmaster.tuning

import kotlin.math.ln
import kotlin.math.roundToInt

/** MIDI note helpers and the note range the app operates on. */
object Notes {
    const val MIDI_A4 = 69
    const val MIDI_C2 = 36
    const val MIDI_C6 = 84

    /** The keyboard range of the proof of concept: C2..C6 inclusive. */
    val appRange: IntRange = MIDI_C2..MIDI_C6

    private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** Scientific pitch notation, e.g. 69 -> "A4". */
    fun name(midi: Int): String {
        val octave = midi / 12 - 1
        return NAMES[midi % 12] + octave
    }

    /** Nearest MIDI note to [frequencyHz] under 12-TET with [referenceA4Hz]. */
    fun nearestMidi(frequencyHz: Double, referenceA4Hz: Double): Int =
        (MIDI_A4 + 12.0 * ln(frequencyHz / referenceA4Hz) / ln(2.0)).roundToInt()

    /** Deviation of [frequencyHz] from [targetHz] in cents. */
    fun centsOff(frequencyHz: Double, targetHz: Double): Double =
        1200.0 * ln(frequencyHz / targetHz) / ln(2.0)
}
