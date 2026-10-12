package at.clavierhaus.unisonmaster.app

import at.clavierhaus.unisonmaster.i18n.K
import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.ui.SettingsPage
import at.clavierhaus.unisonmaster.ui.HelpSteps

/*
 * The Help tab's first page (Peter, 12 Oct 2026): the way through a tuning, from A4 to the
 * tuning screen, in four short sections -- A4, the sampling, the octave types, the tuning. It
 * holds words and no logic; each line says what the screens it names do as they are written
 * (SamplingPage, TunerSettingsScreen's Stretch tab, BasicHub), and is changed when they are.
 * An edition adds its own help pages after this one (TunerSettingsScreen's extraHelpPages).
 */

/** The Quick start: left A4 and the sampling, right the octave types and the tuning. */
internal fun quickStartPage(): SettingsPage = SettingsPage(
    t(K.settings_page_quickstart),
    QUICK_START_NOTE,
    left = { HelpSteps("1 · A4" to A4_STEPS, "2 · Sampling" to SAMPLING_STEPS) },
    right = { HelpSteps("3 · Octave types" to OCTAVE_STEPS, "4 · Tuning" to TUNING_STEPS) },
)

// One row per line, each short enough to stand at its size (HelpSteps fits a line to its row).
private val A4_STEPS = listOf(
    "Home: New Tuning.",
    "A4 on one string, the others muted.",
    "Set its pitch with − and +, or tap \"as it stands\".",
    "Tune until the strobe stands still, then Done.",
)

private val SAMPLING_STEPS = listOf(
    "Single strings, the others muted. Accept each.",
    "The lowest note, first and last bichord, first trichord;",
    "both sides of the struts; two notes per octave to C7.",
    "Six at least, ten to fifteen better. Done ends it:",
    "the inharmonicity then stays for the whole tuning.",
)

private val OCTAVE_STEPS = listOf(
    "Settings › Stretch: bass, middle, treble.",
    "4:2 puts partial 4 of the lower note on 2 of the upper.",
    "Higher numbers, wider octave. 4:1 is the double octave.",
    "Width: cents beyond beatless. Set all before tuning.",
)

private val TUNING_STEPS = listOf(
    "Strike a key: the screen follows. Tap its name to choose.",
    "Tune until the strobe stands still: the note is kept.",
    "The bells are the string's partials, not a target.",
    "Progress shows the tuning so far.",
    "Unisons are tuned by ear.",
)

private const val QUICK_START_NOTE = "Pitch raises are out of scope: a piano far from pitch needs one first."
