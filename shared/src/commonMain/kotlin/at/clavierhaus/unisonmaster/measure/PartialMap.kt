package at.clavierhaus.unisonmaster.measure

/**
 * Which partial of a note is listened to — the Sanderson Accu-Tuner's map,
 * "664 4211" in its manual (docs/MEASUREMENT.md): the sixth partial from
 * the lowest key to B2, the fourth from C3 to B4, the second from C5 to
 * B5, the first from C6 up. The map is by register, never by what the
 * spectrum happens to show; a note below A0 (the 225's F0) is on the sixth
 * like the rest of the bass.
 *
 * The tuner may override it for one note (tapping another partial); that
 * override is stored with the note, as the SAT stores it, and is not this
 * map's business.
 */
object PartialMap {
    const val B2 = 47
    const val B4 = 71
    const val B5 = 83

    fun listening(midi: Int): Int = when {
        midi <= B2 -> 6
        midi <= B4 -> 4
        midi <= B5 -> 2
        else -> 1
    }

    /** Verituner's map, the alternative: the fourth to B3, the second to B4, the first above. */
    fun listeningVerituner(midi: Int): Int = when {
        midi <= 59 -> 4
        midi <= B4 -> 2
        else -> 1
    }
}
