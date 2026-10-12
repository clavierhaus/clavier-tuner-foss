package at.clavierhaus.unisonmaster.ui

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

/*
 * The Help tab's parts. Help is the one place where text scrolls and wraps (Peter, 12 Oct 2026:
 * "For all stuff under Help, that rule does not apply. We will over time create documents that
 * will have a length where scrolling is the only way of decently showing the contents."). Every
 * other screen keeps the law that nothing scrolls and no text is cut (docs/UI.md); LawWallsTest
 * exempts this file by name and no other, so a scroll anywhere else still fails the build.
 */

/**
 * A help page's column: sections, each a header and its lines, each line wrapped and led by an
 * orange dash, the column scrolling when the text is longer than the page. Every help page draws
 * its text with this and nothing else -- Quick start in both editions, the book's help in Pro --
 * so that the Help tab reads as one hand wrote it (Peter, 12 Oct 2026: one UX throughout).
 */
@Composable
fun HelpSteps(vararg sections: Pair<String, List<String>>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        for ((title, lines) in sections) {
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
