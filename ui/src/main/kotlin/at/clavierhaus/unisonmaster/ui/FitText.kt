package at.clavierhaus.unisonmaster.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand

/**
 * Text that is never cut (docs/UI.md, law 3). It is laid out at [maxSize]
 * and, if it overflows its box, steps its size down by [step] until it fits
 * or reaches [minSize]; it never wraps, never clips by design, never shows an
 * ellipsis. On a supported pane the step-down must not fire: a label that
 * needs it is a budget error in the screen, not a feature of this text.
 */
@Composable
fun FitText(
    text: String,
    maxSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color(Brand.WHITE),
    fontFamily: FontFamily = DejaVuSerif,
    fontWeight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
    minSize: TextUnit = 9.sp,
    step: Float = 1f,
) {
    var size by remember(text, maxSize) { mutableFloatStateOf(maxSize.value) }
    Text(
        text,
        modifier = modifier,
        color = color,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        fontSize = size.sp,
        textAlign = align,
        softWrap = false,
        onTextLayout = { result ->
            if ((result.didOverflowWidth || result.didOverflowHeight) && size - step >= minSize.value) size -= step
        },
    )
}
