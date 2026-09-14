package at.clavierhaus.unisonmaster.tuning

import kotlin.math.pow

/**
 * Tuning system strategy. Declared as an interface from day one so that
 * stretched tunings, historical temperaments and inharmonicity-aware models
 * slot in later without touching call sites.
 */
interface Temperament {
    val displayName: String

    /** Fundamental frequency of [midi] given the reference pitch of A4 in Hz. */
    fun frequencyOf(midi: Int, referenceA4Hz: Double): Double
}

/** 12-TET. The only temperament in the current proof of concept. */
object EqualTemperament : Temperament {
    override val displayName: String = "Equal Temperament (12-TET)"

    override fun frequencyOf(midi: Int, referenceA4Hz: Double): Double =
        referenceA4Hz * 2.0.pow((midi - Notes.MIDI_A4) / 12.0)
}
