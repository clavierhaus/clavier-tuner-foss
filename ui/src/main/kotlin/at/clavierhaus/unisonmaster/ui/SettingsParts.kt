package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import kotlin.math.abs

/*
 * The settings screen in the clavierhaus convention, drawn to the laws of
 * the screen (docs/UI.md, 4 Oct 2026): the corners every screen has; the
 * tabs as a line of serif words, the open one orange, as the hub writes its
 * doors — no pills, which the hub never had; a hairline; under a tab with
 * more than one page its pages as a line of small words, the open one
 * orange and underlined, a swipe or a tap turning the page (Peter's choice
 * of 4 Oct 14:42, option A of wireframes/2026-10-04); the page as two
 * columns of rows — a row is a name and a control, nothing under the name —
 * with one line at the foot saying what the page means. Nothing scrolls: a
 * page holds five rows and a header per column on the reference pane, and a
 * context that needs more is two pages, named. Black, orange for what is
 * open. Each app assembles its own tabs from these parts.
 */

private val CONTROL = Color(Brand.CONTROL)
/** The hairline between the tabs and their pages. */
private val RULE = Color(Brand.RULE)

/**
 * One page of a tab: its name on the sub-tab strip, a [note] of one line at
 * the foot, and its rows in a [left] and a [right] column.
 */
class SettingsPage(
    val title: String,
    val note: String = "",
    val left: @Composable ColumnScope.() -> Unit,
    val right: (@Composable ColumnScope.() -> Unit)? = null,
)

/** One tab of the settings screen: its title and its pages. The open tab and the open page are the orange ones. */
class SettingsTab(val title: String, val pages: List<SettingsPage>) {
    constructor(title: String, page: SettingsPage) : this(title, listOf(page))
}

/**
 * The screen. [open] names a tab and a page to start on (the tuning screen
 * reaches the stretch in one tap); null starts where the screen last was.
 */
@Composable
fun SettingsScreen(
    tabs: List<SettingsTab>,
    /** The gear, orange here, closes the settings: back to the screen they were opened from. */
    onClose: () -> Unit,
    /** Home: the hub, from the corner every screen shares. */
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    open: Pair<Int, Int>? = null,
) {
    var selected by rememberSaveable(open) { mutableIntStateOf(open?.first ?: 0) }
    // the open page of each tab, remembered while the screen lives
    var pages by rememberSaveable(open) { mutableStateOf(IntArray(tabs.size).also { a -> open?.let { (t, p) -> if (t in a.indices) a[t] = p } }) }
    val tab = tabs[selected.coerceIn(tabs.indices)]
    val page = pages.getOrElse(selected) { 0 }.coerceIn(tab.pages.indices)
    fun turnTo(p: Int) { pages = pages.copyOf().also { it[selected] = p.coerceIn(tab.pages.indices) } }

    Box(modifier.fillMaxSize().background(Color(Brand.BLACK)).safeDrawingPadding()) {
    // the corners every screen has; the gear stands orange here and closes the settings
    Corners(onSettings = onClose, onHome = onHome, settingsOpen = true)
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp)) {
        CornerBand()
        // the tabs: a line of words, the open one orange, as the hub writes its doors
        Row(horizontalArrangement = Arrangement.spacedBy(34.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { i, t -> TabWord(t.title, open = i == selected) { selected = i } }
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = RULE)
        // the pages of the open tab: only where there is more than one; small words, the open one underlined
        if (tab.pages.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                tab.pages.forEachIndexed { i, p -> PageWord(p.title, open = i == page) { turnTo(i) } }
            }
        }
        Spacer(Modifier.height(8.dp))
        // the page: two columns of rows, the note at the foot; a swipe turns the page
        val current = tab.pages[page]
        var drag = 0f
        Pane(
            Modifier
                .fillMaxSize()
                .pointerInput(selected, page) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = { if (abs(drag) > SWIPE_PX) turnTo(if (drag < 0) page + 1 else page - 1) },
                    ) { _, dx -> drag += dx }
                },
        ) {
            Place(0f, 0f, if (current.right == null) 1f else 0.48f, if (current.note.isEmpty()) 1f else 0.88f) {
                Column(Modifier.fillMaxSize()) { current.left(this) }
            }
            current.right?.let { right ->
                Place(0.52f, 0f, 0.48f, if (current.note.isEmpty()) 1f else 0.88f) {
                    Column(Modifier.fillMaxSize()) { right(this) }
                }
            }
            if (current.note.isNotEmpty()) {
                Place(0f, 0.9f, 1f, 0.1f) {
                    FitText(current.note, 13.sp, Modifier.fillMaxSize(), color = Color(Brand.WHITE_MUTED))
                }
            }
        }
    }
    }
}

/** A drag this long, in pixels, turns a page — every page that turns by a swipe, settings and book alike (UI-UNIFORMITY, 12 Oct 2026). */
const val SWIPE_PX = 120f

/** A tab's name: a serif word, orange and bold when it is the open one, white otherwise; the touch target taller than the word. */
@Composable
private fun TabWord(title: String, open: Boolean, onClick: () -> Unit) {
    Text(
        title,
        color = if (open) Color(Brand.ORANGE) else Color(Brand.WHITE),
        fontFamily = DejaVuSerif, fontSize = 22.sp,
        fontWeight = if (open) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
    )
}

/** A page's name under its tab: a small serif word, muted; the open one orange with a bar beneath it as wide as the word. */
@Composable
private fun PageWord(title: String, open: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(IntrinsicSize.Min).clickable(onClick = onClick).padding(top = 8.dp, bottom = 2.dp)) {
        Text(
            title,
            color = if (open) Color(Brand.ORANGE) else Color(Brand.WHITE_MUTED),
            fontFamily = DejaVuSerif, fontSize = 15.sp,
            fontWeight = if (open) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (open) Color(Brand.ORANGE) else Color.Transparent))
    }
}

/** A column's header, small caps. */
@Composable
fun SettingsSection(title: String) {
    Spacer(Modifier.height(6.dp))
    Text(title.uppercase(), color = Color(Brand.WHITE_MUTED), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
    Spacer(Modifier.height(2.dp))
}

/**
 * A help page's column: sections, each a header and its lines, each line one row led by an
 * orange dash. Nothing scrolls and nothing is cut (docs/UI.md, LawWallsTest): a line is fitted to
 * its row, so a help page says in short lines what fits on it, as the sampling page gives its
 * orders -- a page that needs more is two pages. Every help page draws its text
 * with this and nothing else -- Quick start in both editions, the book's help in Pro -- so that the
 * Help tab reads as one hand wrote it (Peter, 12 Oct 2026: one UX throughout).
 */
@Composable
fun HelpSteps(vararg sections: Pair<String, List<String>>) {
    Column(Modifier.fillMaxSize()) {
        for ((title, lines) in sections) {
            SettingsSection(title)
            for (line in lines) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("–", color = Color(Brand.ORANGE), fontSize = 14.sp, modifier = Modifier.width(14.dp))
                    FitText(line, 14.sp, Modifier.weight(1f), color = Color(Brand.WHITE))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** A line of explanation inside a column, where a page's one-line note is not enough; measured, never cut. */
@Composable
fun SettingsNote(text: String) {
    FitText(text, 12.sp, Modifier.fillMaxWidth().padding(vertical = 4.dp), color = Color(Brand.WHITE_MUTED))
}

/** A row: the name, and the control at the right edge. Nothing under the name — the page's note says the rest. */
@Composable
private fun SettingsRow(name: String, control: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT),
    ) {
        FitText(name, 15.sp, Modifier.weight(1f), color = Color(Brand.WHITE))
        Spacer(Modifier.width(12.dp))
        control()
    }
    HorizontalDivider(color = CONTROL)
}

/** A row's height: five of them and a header fit a column of the reference pane. */
private val ROW_HEIGHT = 40.dp

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .background(if (enabled) CONTROL else Color(0xFF181818), RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Color(Brand.WHITE) else Color(Brand.WHITE_MUTED), fontSize = 17.sp)
    }
}

/** − value + */
@Composable
fun StepperRow(
    name: String,
    value: String,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    SettingsRow(name) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", canDecrease, onDecrease)
            FitText(
                value, 15.sp, Modifier.width(88.dp),
                color = Color(Brand.WHITE), align = TextAlign.Center,
            )
            StepButton("+", canIncrease, onIncrease)
        }
    }
}

/** A small set of options, the selected one in orange. */
@Composable
fun ChoiceRow(name: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SettingsRow(name) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                // a chip is as wide as its word: nothing is cut to a fixed width
                Box(
                    Modifier
                        .height(32.dp)
                        .defaultMinSize(minWidth = 44.dp)
                        .background(if (on) Color(Brand.ORANGE) else CONTROL, RoundedCornerShape(6.dp))
                        .clickable { onSelect(i) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FitText(label, 13.sp, color = if (on) Color(Brand.BLACK) else Color(Brand.WHITE), align = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun SwitchRow(name: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingsRow(name) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Color(Brand.ORANGE), checkedThumbColor = Color(Brand.BLACK)),
        )
    }
}

@Composable
fun ReadOnlyRow(name: String, value: String) {
    SettingsRow(name) {
        FitText(value, 15.sp, color = Color(Brand.WHITE_MUTED), align = TextAlign.End)
    }
}

/** A row with a line of text to edit at the right: one line, no wrap. */
@Composable
fun TextRow(name: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    SettingsRow(name) {
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.replace("\n", "")) },
            singleLine = true,
            textStyle = TextStyle(color = Color(Brand.WHITE), fontSize = 14.sp),
            cursorBrush = SolidColor(Color(Brand.ORANGE)),
            modifier = modifier
                .width(200.dp)
                .height(32.dp)
                .background(CONTROL, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp),
            decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { inner() } },
        )
    }
}

/** A row that does one thing when tapped: the name in orange, as a door. */
@Composable
fun ActionRow(name: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .clickable(onClick = onClick),
    ) {
        FitText(name, 15.sp, Modifier.weight(1f), color = Color(Brand.ORANGE))
        Text("›", color = Color(Brand.ORANGE), fontSize = 20.sp)
    }
    HorizontalDivider(color = CONTROL)
}
