package at.clavierhaus.unisonmaster.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.i18n.K
import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.ui.WordButton

/**
 * Where the session stands before tuning, and the way back (Peter, 9 Nov 2026: a tester could not get back to change
 * anything, and a piano app is judged on it): 1 A4, 2 Sampling, 3 Tuning, the step the engine is in orange ([current]),
 * an earlier one a button that goes back to it ([onStep], its number), a later one dim. Nothing measured is lost by it.
 */
@Composable
fun StepLine(current: Int, onStep: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(K.step_a4, K.step_sampling, K.step_tuning).forEachIndexed { i, key ->
            val n = i + 1
            val label = "$n ${t(key)}"
            WordButton(
                label,
                fill = when { n == current -> Color(Brand.ORANGE); n < current -> Color(0xFF333333); else -> Color(0xFF1F1F1F) },
                textColor = when { n == current -> Color(Brand.BLACK); n < current -> Color(Brand.WHITE); else -> Color(Brand.WHITE_MUTED) },
                enabled = n < current, onClick = { onStep(n) },
                // each its share by its word and its padding, so that a narrow screen shrinks all three and cuts none
                modifier = Modifier.weight(label.length + 3f),
                size = 14.sp, minSize = 10.sp, padding = 10.dp, minPadding = 4.dp, height = 40.dp,
            )
        }
    }
}
