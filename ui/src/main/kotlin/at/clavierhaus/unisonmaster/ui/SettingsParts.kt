package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ColumnScope
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
 * the screen (docs/UI.md, 4 Oct 2026): a header with back arrow, title and
 * subtitle; a strip of tabs; under a tab with more than one page a strip of
 * sub-tabs with ◀ ▶ at its ends, a swipe stepping through them; the page as
 * two columns of rows — a row is a name and a control, nothing under the
 * name — with one line at the foot saying what the page means. Nothing
 * scrolls: a page holds five rows and a header per column on the reference
 * pane, and a context that needs more is two pages, named. Black, orange
 * for what is open. Each app assembles its own tabs from these parts.
 */

private val TAB = Color(0xFF1E1E1E)
private val TAB_ACTIVE = Color(0xFF333333)
private val CONTROL = Color(0xFF2A2A2A)

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
    subtitle: String,
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
        Column(Modifier.height(CORNER_BAND).padding(start = CORNER_LEFT_WIDTH - 20.dp)) {
            Text("Settings", color = Color(Brand.WHITE), fontFamily = DejaVuSerif, fontSize = 24.sp)
            Text(subtitle, color = Color(Brand.WHITE_MUTED), fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
        // the tabs
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { i, t ->
                Strip(t.title, active = i == selected, big = true, modifier = Modifier.weight(1f)) { selected = i }
            }
        }
        // the sub-tabs: only where a tab has more than one page; ◀ ▶ step through them
        if (tab.pages.size > 1) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Arrow("◀", page > 0) { turnTo(page - 1) }
                tab.pages.forEachIndexed { i, p ->
                    Strip(p.title, active = i == page, big = false, modifier = Modifier.weight(1f)) { turnTo(i) }
                }
                Arrow("▶", page < tab.pages.size - 1) { turnTo(page + 1) }
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

/** A drag this long, in pixels, turns the page. */
private const val SWIPE_PX = 120f

@Composable
private fun Strip(title: String, active: Boolean, big: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(if (big) 38.dp else 30.dp)
            .background(if (active) TAB_ACTIVE else TAB, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // orange is the open one and nothing else
        FitText(
            title, if (big) 15.sp else 13.sp,
            color = if (active) Color(Brand.ORANGE) else Color(Brand.WHITE),
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            align = TextAlign.Center,
        )
    }
}

@Composable
private fun Arrow(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 36.dp, height = 30.dp)
            .background(if (enabled) TAB else Color(0xFF141414), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Color(Brand.WHITE) else Color(0xFF444444), fontSize = 14.sp)
    }
}

/** A column's header, small caps. */
@Composable
fun SettingsSection(title: String) {
    Spacer(Modifier.height(6.dp))
    Text(title.uppercase(), color = Color(Brand.WHITE_MUTED), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
    Spacer(Modifier.height(2.dp))
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
    HorizontalDivider(color = Color(0xFF2A2A2A))
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
    HorizontalDivider(color = Color(0xFF2A2A2A))
}
