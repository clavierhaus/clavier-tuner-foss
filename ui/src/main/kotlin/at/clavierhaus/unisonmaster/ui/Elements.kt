package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.ui.unit.em
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The elements a screen is made of, and the two rules of their type (Peter, 24 Oct 2026, 15:10: "Wir
 * müssen in Elementen denken, die auf einem Bildschirm platziert werden"):
 *
 * - **A button's word never breaks.** When it does not fit, the button first gives up the room between
 *   its word and its edges, then the word steps down in size; it never wraps and is never cut.
 *   "Übernehmen" stood as "Ü / h" on a small phone, and "gemessen" as "ge / me" on the Motorola edge
 *   50 neo (Gregor, Clavio, 24 Oct 2026).
 * - **One element, one size of type.** A block of text that belongs together -- the orders of the
 *   sampling page, a status beside a key -- is set in one size. It wraps between words, and if it does
 *   not fit its box, the whole block steps down together. The orders stood in four sizes on one card,
 *   each line shrunk alone to fit its width, and the last line was not on the screen at all.
 *
 * LayoutCheckTest draws every screen on the phones it must work on and holds both rules, with the old
 * ones: no text is cut, no word is broken across lines (docs/UI.md, laws 1 and 3).
 */

/** The line height of the elements' text, as a share of its size: the theme's 24 sp line stood taller than the type at every size. */
const val LINE = 1.25f

/** The tag of an element whose text is one size; LayoutCheckTest reads it. */
const val ELEMENT_TAG = "element"

/**
 * A button of one word or a few: its label on one line, always. Given less width than its word and
 * its padding need, it narrows the padding from [padding] to [minPadding], then steps the type down
 * from [size] to [minSize] in whole points.
 */
@Composable
fun WordButton(
    label: String,
    fill: Color,
    textColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: TextUnit = 18.sp,
    minSize: TextUnit = 12.sp,
    padding: Dp = 18.dp,
    minPadding: Dp = 6.dp,
    height: Dp = 44.dp,
    fontFamily: FontFamily = DejaVuSerif,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val room = constraints.maxWidth
        val base = LocalTextStyle.current
        fun style(sp: Float) = base.merge(TextStyle(fontSize = sp.sp, fontFamily = fontFamily, lineHeight = LINE.em))
        fun width(sp: Float) = measurer.measure(label, style(sp), softWrap = false, constraints = Constraints()).size.width
        val padPx = with(density) { padding.roundToPx() }
        val minPadPx = with(density) { minPadding.roundToPx() }
        var sp = size.value
        var pad = padPx
        if (room != Constraints.Infinity) {
            val w = width(sp)
            if (w + 2 * pad > room) {
                pad = ((room - w) / 2).coerceIn(minPadPx, padPx)
                while (width(sp) + 2 * pad > room && sp - 1f >= minSize.value) sp -= 1f
            }
        }
        Box(
            Modifier
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(fill)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = with(density) { pad.toDp() }),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = textColor, style = style(sp), softWrap = false)
        }
    }
}

/**
 * A block of text set in one size: [lines] one under the other, each wrapping between its words. The
 * size is the largest from [size] down to [minSize] at which every word fits the width and the whole
 * block fits the height it is given; the block is tagged as one element.
 */
@Composable
fun TextBlock(
    lines: List<String>,
    modifier: Modifier = Modifier,
    size: TextUnit = 15.sp,
    minSize: TextUnit = 10.sp,
    color: Color = Color(0xFFFFFFFF),
    fontFamily: FontFamily = DejaVuSerif,
    fontWeight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
    gap: Dp = 4.dp,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.testTag(ELEMENT_TAG)) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val gapPx = with(density) { gap.roundToPx() }
        val base = LocalTextStyle.current
        fun style(sp: Float) = base.merge(TextStyle(fontSize = sp.sp, fontFamily = fontFamily, fontWeight = fontWeight, lineHeight = LINE.em))
        fun fits(sp: Float): Boolean {
            if (w == Constraints.Infinity) return true
            val words = lines.flatMap { it.split(' ') }.filter { it.isNotEmpty() }
            if (words.any { measurer.measure(it, style(sp), softWrap = false, constraints = Constraints()).size.width > w - 2 }) return false   // two pixels of room: the line breaker rounds where the measure does not
            if (h == Constraints.Infinity) return true
            val total = lines.sumOf { measurer.measure(it, style(sp), constraints = Constraints(maxWidth = w)).size.height } + gapPx * (lines.size - 1)
            return total <= h
        }
        var sp = size.value
        while (!fits(sp) && sp - 1f >= minSize.value) sp -= 1f
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (line in lines) Text(line, color = color, style = style(sp), textAlign = align)
        }
    }
}
