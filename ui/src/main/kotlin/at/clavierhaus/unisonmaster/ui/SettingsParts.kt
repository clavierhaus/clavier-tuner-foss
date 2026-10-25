package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
 * columns of rows — a row is a name and a control, nothing under the name.
 * What an entry means is behind it: a round (i) after the row's name or the
 * section's header opens the text, and a tap anywhere outside closes it
 * (Peter, 14 Oct 2026, in place of the line of small print that stood at the
 * foot of each page and did not say which entry it belonged to). Nothing scrolls: a
 * page holds five rows and a header per column on the reference pane, and a
 * context that needs more is two pages, named. Black, orange for what is
 * open. Each app assembles its own tabs from these parts.
 */

private val CONTROL = Color(Brand.CONTROL)
/** The hairline between the tabs and their pages. */
private val RULE = Color(Brand.RULE)

/**
 * One page of a tab: its name on the sub-tab strip and its rows in a [left]
 * and a [right] column. A page has no line of its own: what an entry means is
 * the entry's (i) (`info` on [SettingsSection] and the rows).
 */
class SettingsPage(
    val title: String,
    val left: @Composable ColumnScope.() -> Unit,
    val right: (@Composable ColumnScope.() -> Unit)? = null,
    /** Controls of the page itself, at its top right, on the line of the page words (Help: A− A+). */
    val tools: (@Composable () -> Unit)? = null,
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
        // the pages of the open tab: only where there is more than one; small words, the open one underlined;
        // at the right end of the line, the open page's own tools where it has them (Help: A− A+)
        val tools = tab.pages[page].tools
        if (tab.pages.size > 1 || tools != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                if (tab.pages.size > 1) tab.pages.forEachIndexed { i, p -> PageWord(p.title, open = i == page) { turnTo(i) } }
                if (tools != null) {
                    Spacer(Modifier.weight(1f))
                    tools()
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // the page: two columns of rows; a swipe turns the page
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
            Place(0f, 0f, if (current.right == null) 1f else 0.48f, 1f) {
                Column(Modifier.fillMaxSize()) { current.left(this) }
            }
            current.right?.let { right ->
                Place(0.52f, 0f, 0.48f, 1f) {
                    Column(Modifier.fillMaxSize()) { right(this) }
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
        fontFamily = DejaVuSerif, fontSize = 22.sp, softWrap = false,
        fontWeight = if (open) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
    )
}

/** A page's name under its tab: a small serif word, muted; the open one orange with a bar beneath it as wide as the word. */
@Composable
private fun PageWord(title: String, open: Boolean, onClick: () -> Unit) {
    // as wide as the whole name on one line: at its narrowest width a name of two words ("Scale breaks")
    // stood on two lines (25 Oct 2026)
    Column(Modifier.width(IntrinsicSize.Max).clickable(onClick = onClick).padding(top = 8.dp, bottom = 2.dp)) {
        Text(
            title, softWrap = false,
            color = if (open) Color(Brand.ORANGE) else Color(Brand.WHITE_MUTED),
            fontFamily = DejaVuSerif, fontSize = 15.sp,
            fontWeight = if (open) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (open) Color(Brand.ORANGE) else Color.Transparent))
    }
}

/** A column's header, small caps; [info], what the section means, behind it as an (i). */
@Composable
fun SettingsSection(title: String, info: String? = null) {
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), color = Color(Brand.WHITE_MUTED), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        if (info != null) InfoButton(title, info)
    }
    Spacer(Modifier.height(2.dp))
}

/**
 * The round (i) behind an entry (Peter, 14 Oct 2026): a tap opens a window with the entry's
 * [title] and what it means, and a tap anywhere outside the window closes it. The ring is orange
 * while its window is open, as everything open is. Every explanation in the settings is reached
 * this way; none stands on the page.
 */
@Composable
fun InfoButton(title: String, text: String) {
    var open by remember { mutableStateOf(false) }
    val tint = if (open) Color(Brand.ORANGE) else Color(Brand.WHITE_MUTED)
    // the touch target is wider than the ring; its height is the row's or the header's
    Box(Modifier.clickable { open = true }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(20.dp).border(1.5.dp, tint, CircleShape), contentAlignment = Alignment.Center) {
            // the letter set without the font's padding, so its box is the ring's and nothing of it is cut
            Text("i", color = tint, fontWeight = FontWeight.Bold,
                style = TextStyle(fontFamily = DejaVuSerif, fontSize = 12.sp, lineHeight = 12.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)))
        }
    }
    if (open) {
        // Dialog closes on a tap outside it (dismissOnClickOutside) and on Back
        Dialog(onDismissRequest = { open = false }) {
            Column(
                Modifier.widthIn(max = 560.dp).background(Color(Brand.NEAR_BLACK), RoundedCornerShape(10.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title, color = Color(Brand.WHITE), fontFamily = DejaVuSerif, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(text, color = Color(Brand.WHITE), fontSize = 16.sp, lineHeight = 22.sp)
            }
        }
    }
}

/** A line inside a column that is not an explanation but news of the moment (Pro's probe: a file that could not be played); measured, never cut. */
@Composable
fun SettingsNote(text: String) {
    FitText(text, 12.sp, Modifier.fillMaxWidth().padding(vertical = 4.dp), color = Color(Brand.WHITE_MUTED))
}

/** A row: the name, its (i) right behind it when it has [info], and the control at the right edge. Nothing under the name. */
@Composable
private fun SettingsRow(name: String, info: String?, control: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            // the name takes what it needs, so the (i) stands right behind it, not at the far edge
            // the name wraps between its words rather than shrinking beside its control: a row is one size
            // of type (Peter, 24 Oct 2026, 15:10)
            TextBlock(listOf(name), Modifier.weight(1f, fill = false), size = 15.sp, minSize = 10.sp, color = Color(Brand.WHITE))
            if (info != null) InfoButton(name, info)
        }
        Spacer(Modifier.width(12.dp))
        control()
    }
    HorizontalDivider(color = CONTROL)
}

/** A row's height: five of them and a header fit a column of the reference pane. */
private val ROW_HEIGHT = 40.dp

@Composable
internal fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
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

/** − value +, without a name: the control of a StepperRow, and of a table whose column says the name once. */
@Composable
fun Stepper(value: String, canDecrease: Boolean, canIncrease: Boolean, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", canDecrease, onDecrease)
        // as wide as its value needs, never less than 88 dp: at a fixed 88 dp "schwebungsfrei" was cut
        // in the info line's window and shrunk beside its name (24 Oct 2026)
        Text(
            value, Modifier.widthIn(min = 88.dp).padding(horizontal = 6.dp), color = Color(Brand.WHITE),
            fontFamily = DejaVuSerif, fontSize = 15.sp, textAlign = TextAlign.Center, softWrap = false,
        )
        StepButton("+", canIncrease, onIncrease)
    }
}

/** − value + */
@Composable
fun StepperRow(
    name: String,
    value: String,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
    info: String? = null,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    SettingsRow(name, info) { Stepper(value, canDecrease, canIncrease, onDecrease, onIncrease) }
}

/** The options of a ChoiceRow without its name, the selected one in orange; each chip as wide as its word. */
@Composable
fun Choices(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .height(32.dp)
                    .defaultMinSize(minWidth = 32.dp)
                    .background(if (on) Color(Brand.ORANGE) else CONTROL, RoundedCornerShape(6.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) Color(Brand.BLACK) else Color(Brand.WHITE), fontFamily = DejaVuSerif, fontSize = 13.sp, softWrap = false)
            }
        }
    }
}

/** A small set of options, the selected one in orange. */
@Composable
fun ChoiceRow(name: String, options: List<String>, selected: Int, info: String? = null, onSelect: (Int) -> Unit) {
    SettingsRow(name, info) { Choices(options, selected, onSelect) }
}

@Composable
fun SwitchRow(name: String, checked: Boolean, info: String? = null, onChange: (Boolean) -> Unit) {
    SettingsRow(name, info) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Color(Brand.ORANGE), checkedThumbColor = Color(Brand.BLACK)),
        )
    }
}

@Composable
fun ReadOnlyRow(name: String, value: String, info: String? = null) {
    // the name and the value share the row, each an element that wraps between its words (24 Oct 2026:
    // a long value took the row and left its name to break inside a word)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
        Row(Modifier.weight(0.45f), verticalAlignment = Alignment.CenterVertically) {
            TextBlock(listOf(name), Modifier.weight(1f, fill = false), size = 15.sp, minSize = 10.sp, color = Color(Brand.WHITE))
            if (info != null) InfoButton(name, info)
        }
        Spacer(Modifier.width(12.dp))
        TextBlock(listOf(value), Modifier.weight(0.55f), size = 15.sp, minSize = 11.sp, color = Color(Brand.WHITE_MUTED), align = TextAlign.End)
    }
    HorizontalDivider(color = CONTROL)
}

/** A row with a line of text to edit at the right: one line, no wrap. */
@Composable
fun TextRow(name: String, value: String, modifier: Modifier = Modifier, info: String? = null, onChange: (String) -> Unit) {
    SettingsRow(name, info) {
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
