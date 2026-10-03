package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.measure.Partials

/**
 * The partials of the note, one chip each, in one row that always fits: every
 * chip takes an equal share of the width and its label steps down rather
 * than being cut. Orange: shown, read, in the bells. Red-violet: shown and
 * measured false on this strike — a tap takes it out of the bells (and out
 * of the reading). Grey: not shown; a tap brings it in when the string
 * carries it.
 */
@Composable
fun PartialChips(
    a4Hz: Double,
    baseHz: Double,
    shown: Set<Int>,
    falseMarked: Set<Int>,
    tappable: Set<Int>,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    count: Int = Partials.MAX,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = modifier) {
        for (k in 1..count.coerceIn(1, Partials.MAX)) {
            val on = k in shown
            val isFalse = on && k in falseMarked
            val canTap = k in tappable
            val fill = when {
                isFalse -> Color(Brand.FALSE_VIOLET)
                on -> Color(Brand.ORANGE)
                canTap -> Color(0xFF3A3A3A)
                else -> Color(0xFF222222)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .background(fill, RoundedCornerShape(6.dp))
                        .then(if (isFalse) Modifier.border(2.dp, Color(Brand.WHITE), RoundedCornerShape(6.dp)) else Modifier)
                        .clickable(enabled = canTap || on, onClick = { onTap(k) }),
                    contentAlignment = Alignment.Center,
                ) {
                    FitText(
                        partialNoteName(k, baseHz, a4Hz),
                        12.sp,
                        color = if (on) Color(Brand.BLACK) else Color(Brand.WHITE_MUTED),
                        align = TextAlign.Center,
                    )
                }
                FitText("$k", 10.sp, color = if (isFalse) Color(Brand.FALSE_VIOLET) else Color(Brand.WHITE_MUTED), align = TextAlign.Center)
            }
        }
    }
}
