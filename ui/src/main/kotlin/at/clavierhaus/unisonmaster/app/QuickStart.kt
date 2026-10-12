package at.clavierhaus.unisonmaster.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.i18n.K
import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.ui.SettingsPage
import at.clavierhaus.unisonmaster.ui.SettingsSection

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
    left = { Steps(A4_STEPS to "1 · A4", SAMPLING_STEPS to "2 · Sampling") },
    right = { Steps(OCTAVE_STEPS to "3 · Octave types", TUNING_STEPS to "4 · Tuning") },
)

/** Sections of numbered lines, wrapped, the column scrolling if a small screen needs it. */
@Composable
private fun Steps(vararg sections: Pair<List<String>, String>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        for ((lines, title) in sections) {
            SettingsSection(title)
            for (line in lines) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("–", color = Color(Brand.ORANGE), fontSize = 14.sp, modifier = Modifier.width(14.dp))
                    Text(line, color = Color(Brand.WHITE), fontSize = 14.sp, lineHeight = 18.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

private val A4_STEPS = listOf(
    "On the home screen: New Tuning.",
    "Mute A4 to one string. Choose its pitch with − and +, a tenth of a hertz at a time (hold to run on), or tap \"as it stands\" to keep the string where it is.",
    "Tune the string until the strobe stands still, then Done.",
    "The temperament octave is A3–A4; its low note can be moved on the same screen.",
)

private val SAMPLING_STEPS = listOf(
    "Strike single strings, the others muted. The app catches the key by itself: check it, Accept. Undo takes a sample back; a key struck again replaces it.",
    "Sample where the piano changes: the lowest note, the first and last bichord, the first plain-wire trichord, both sides of the struts — and about two notes per octave up to C7.",
    "Six samples are the minimum; ten to fifteen give the better result.",
    "Done ends the sampling. The piano's inharmonicity is measured from these strings, and it stays as measured for the whole tuning.",
)

private val OCTAVE_STEPS = listOf(
    "Settings › Stretch, for the bass, the middle and the treble.",
    "An octave type names the partials made beatless: 4:2 puts the 4th partial of the lower note on the 2nd of the upper. The higher the numbers, the wider the octave. 4:1 is the double octave.",
    "Set out: bass 6:3, middle 4:2, treble 4:2.",
    "The width, in cents, widens the octave beyond beatless: the lower note flatter, the upper sharper.",
    "Set them before you tune: every note's target is computed from them.",
)

private val TUNING_STEPS = listOf(
    "Strike a key and the screen is on it. Tap the note's name to choose a key by hand; tap it again and the screen follows the strike once more.",
    "Tune until the strobe on the right stands still. When it stands within your match window, the note is kept.",
    "The bells on the left are the string's partials. A clean string's bells stand together; one standing apart beats on its own. They are no target to tune to.",
    "Progress shows the tuning so far. Continue Tuning on the home screen takes you back to where you stopped.",
    "Unisons are tuned by ear.",
)

private const val QUICK_START_NOTE = "Pitch raises are out of scope: a piano far from pitch needs one first."
