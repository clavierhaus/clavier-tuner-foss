package at.clavierhaus.claviertuner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.claviertuner.Brand

/**
 * The settings after gnubg-android's (Plus, 21 Nov 2026, Peter: "gnubg has 8 Buttons/Tabs and no sub tabs. Various options
 * are separated in one scrollable full text and switch menu"; the free app's and Pro's since 23 Nov 2026, Peter: "foss and
 * pro get the same look of settings as plus. Strike the law about scrolling for the settings tabs"): a strip of tab buttons
 * across the top, equal in width, the open one orange; under it the open tab as one list that scrolls, each of its pages a
 * card with its name, its rows one under another (what stood in a right column follows what stood in the left). No
 * sub-tabs, no swipe, no second column.
 *
 * It draws the settings pages unchanged (SettingsPage, SettingsTab): only the frame differs from SettingsScreen. Help, whose
 * index and text scroll on their own, is drawn as one page filling the pane ([fullPage]).
 */
@Composable
fun SettingsGear(
    tabs: List<SettingsTab>,
    onClose: () -> Unit,
    onHome: () -> Unit,
    /** The tab to open on (its page is scrolled to by nothing: the list starts at its top). */
    open: Pair<Int, Int>? = null,
    /**
     * Tabs drawn as one page filling the pane, its two columns side by side, not as a list that scrolls: Help, whose index
     * and text scroll on their own (a list that scrolls cannot hold one that scrolls).
     */
    fullPage: Set<Int> = emptySet(),
) {
    var selected by rememberSaveable(open) { mutableIntStateOf(open?.first ?: 0) }
    val tab = tabs[selected.coerceIn(tabs.indices)]
    Box(Modifier.fillMaxSize().background(Color(Brand.BLACK)).safeDrawingPadding()) {
        Corners(onSettings = onClose, onHome = onHome, settingsOpen = true)
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp)) {
            CornerBand()
            // the tab buttons: one strip, equal widths, the open one filled orange (gnubg's SettingsTabs)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tabs.forEachIndexed { i, t ->
                    val on = i == selected
                    Box(
                        Modifier.weight(1f).height(44.dp)
                            .background(Color(if (on) Brand.ORANGE else Brand.CONTROL), RoundedCornerShape(9.dp))
                            .clickable { selected = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        FitText(t.title, 16.sp, Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                            color = Color(if (on) Brand.BLACK else Brand.WHITE),
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, align = TextAlign.Center, minSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (selected in fullPage) {
                val page = tab.pages.first()
                Column(Modifier.fillMaxSize().background(Color(Brand.PANEL), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FitText(page.title.uppercase(), 13.sp, Modifier.weight(1f), color = Color(Brand.WHITE_MUTED), fontWeight = FontWeight.Bold, minSize = 10.sp)
                        page.tools?.invoke()
                    }
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(if (page.right == null) 1f else 0.4f).fillMaxSize()) { page.left(this) }
                        page.right?.let { right -> Column(Modifier.weight(0.6f).fillMaxSize()) { right(this) } }
                    }
                }
            } else {
            // the open tab: its pages as cards in one list that scrolls; a new tab starts at its top
            val scroll = rememberSaveable(selected, saver = androidx.compose.foundation.ScrollState.Saver) { androidx.compose.foundation.ScrollState(0) }
            Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (page in tab.pages) {
                    Column(
                        Modifier.fillMaxWidth().background(Color(Brand.PANEL), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            FitText(page.title.uppercase(), 13.sp, Modifier.weight(1f), color = Color(Brand.WHITE_MUTED),
                                fontWeight = FontWeight.Bold, minSize = 10.sp)
                            page.tools?.invoke()
                        }
                        page.left(this)
                        page.right?.invoke(this)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            }
        }
    }
}
