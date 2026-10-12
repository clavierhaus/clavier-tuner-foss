package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.settings.TunerSettings

/*
 * The Help tab's parts. Help is the one place where text scrolls and wraps (Peter, 12 Oct 2026:
 * "For all stuff under Help, that rule does not apply. We will over time create documents that
 * will have a length where scrolling is the only way of decently showing the contents."). Every
 * other screen keeps the law that nothing scrolls and no text is cut (docs/UI.md); LawWallsTest
 * exempts this file by name and no other, so a scroll anywhere else still fails the build.
 */

/**
 * Help's text size and the hand that changes it, provided by the settings screen from the
 * tuner's settings (TunerSettings.helpTextSp, kept on the phone) to every help page, whichever
 * edition built it.
 */
class HelpText(val sp: Int, val set: (Int) -> Unit)

val LocalHelpText = compositionLocalOf { HelpText(14) {} }

/**
 * A help page: its sections in one column or two, and A− A+ at its top right, which step the text
 * from 12 to 22 sp for every help page at once (Peter, 12 Oct 2026). Every page of the Help tab is
 * made with this -- Quick start, and Pro's Customers and Pianos -- so each has the same buttons in
 * the same place.
 */
fun helpPage(
    title: String,
    note: String = "",
    left: List<Pair<String, List<String>>>,
    right: List<Pair<String, List<String>>>? = null,
): SettingsPage = SettingsPage(
    title, note,
    left = { HelpSteps(*left.toTypedArray()) },
    right = right?.let { r -> { HelpSteps(*r.toTypedArray()) } },
    tools = { HelpTextSizeButtons() },
)

/** A− and A+: Help's text one step smaller or larger; each stops at its end of the range. */
@Composable
fun HelpTextSizeButtons() {
    val h = LocalHelpText.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        StepButton("A−", enabled = h.sp > TunerSettings.MIN_HELP_SP) { h.set(TunerSettings.helpTextStep(h.sp, -1)) }
        StepButton("A+", enabled = h.sp < TunerSettings.MAX_HELP_SP) { h.set(TunerSettings.helpTextStep(h.sp, +1)) }
    }
}

/**
 * A help page's column: sections, each a header and its lines, each line wrapped and led by an
 * orange dash, the column scrolling when the text is longer than the page. Every help page draws
 * its text with this and nothing else -- Quick start in both editions, the book's help in Pro --
 * so that the Help tab reads as one hand wrote it (Peter, 12 Oct 2026: one UX throughout).
 */
@Composable
fun HelpSteps(vararg sections: Pair<String, List<String>>) {
    val size = LocalHelpText.current.sp
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        for ((title, lines) in sections) {
            SettingsSection(title)
            for (line in lines) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("–", color = Color(Brand.ORANGE), fontSize = size.sp, modifier = Modifier.width(size.dp))
                    Text(line, color = Color(Brand.WHITE), fontSize = size.sp, lineHeight = (size * 1.3).sp)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
