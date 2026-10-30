package at.clavierhaus.unisonmaster.i18n

import at.clavierhaus.unisonmaster.tuning.Notes

/**
 * A note's name as the screen writes it, in the language in force: A1, A#1, C4 … and in German H
 * for B (Peter, 14 Oct 2026: "durchgehend Notation A1, A#1 – aber H statt B"). Only the natural B
 * changes; the sharps keep their #. Everything that is not a screen -- file names, the recordings, the
 * logs, the tests -- keeps [Notes.name], which never changes with the language.
 */
fun noteName(midi: Int, language: String = Strings.language.value): String {
    val n = Notes.name(midi)
    return if (language.startsWith("de") && n.startsWith("B")) "H" + n.substring(1) else n
}
