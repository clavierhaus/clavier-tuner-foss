package at.clavierhaus.unisonmaster.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.i18n.K
import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.settings.StrobeMode
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.ui.Stepper
import at.clavierhaus.unisonmaster.ui.TextBlock

/*
 * What the strobe shows, chosen beside it (Peter, 31 Oct 2026: "Three menu items in three lines and only one can be
 * active ... right into the tuning window left to the strobes"): one band whose motion is the clean partials' average
 * (where a tuner starts since 9 Nov 2026), the lowest few, or every partial, each line saying what it shows. It holds no measurement; it is the tuner's choice, kept in the settings, and the
 * engine takes it for the note on screen at once (TuningController.publish).
 */

/**
 * The three lines: One strobe; Limit to N with − and +; All partials. [onChange] changes the settings in force.
 * [beside] is what an edition puts at the end of the third line, where the panel has width to spare
 * and, on the smallest phone, no height below.
 */
@Composable
fun StrobeChoice(
    s: TunerSettings,
    onChange: ((TunerSettings) -> TunerSettings) -> Unit,
    modifier: Modifier = Modifier,
    beside: (@Composable () -> Unit)? = null,
    /** The strobe moves at the pin's pace on this key (PinPace): said above the choices, where the mode is looked for. */
    pinPace: Boolean = false,
) {
    // each line says what it shows, beside its button (Peter, 9 Nov 2026: a clean description of the options, and a tap
    // changes the strobe at once): three lines as ever, the panel has the width and on the smallest phone no height
    @Composable
    fun Says(key: String, m: Modifier) = TextBlock(listOf(t(key)), m.height(40.dp), size = 13.sp, color = Color(Brand.WHITE_MUTED))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (pinPace) TextBlock(listOf(t(K.tuning_pinPace)), Modifier.fillMaxWidth(), size = 14.sp, minSize = 10.sp, color = Color(Brand.ORANGE))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(t(K.tuning_strobeOne), selected = s.strobeMode == StrobeMode.ONE, enabled = true) {
                onChange { it.copy(strobeMode = StrobeMode.ONE) }
            }
            Says(K.tuning_strobeOne_note, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(t(K.tuning_strobeLimit), selected = s.strobeMode == StrobeMode.LIMIT, enabled = true) {
                onChange { it.copy(strobeMode = StrobeMode.LIMIT) }
            }
            // the number is changed whatever the mode, and choosing it chooses the limit
            Stepper(
                s.strobeCount.toString(),
                canDecrease = s.strobeCount > 1,
                canIncrease = s.strobeCount < TunerSettings.MAX_STROBE_COUNT,
                onDecrease = { onChange { it.copy(strobeMode = StrobeMode.LIMIT, strobeCount = (it.strobeCount - 1).coerceAtLeast(1)) } },
                onIncrease = { onChange { it.copy(strobeMode = StrobeMode.LIMIT, strobeCount = (it.strobeCount + 1).coerceAtMost(TunerSettings.MAX_STROBE_COUNT)) } },
            )
            Says(K.tuning_strobeLimit_note, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(t(K.tuning_strobeAll), selected = s.strobeMode == StrobeMode.ALL, enabled = true) {
                onChange { it.copy(strobeMode = StrobeMode.ALL) }
            }
            Says(K.tuning_strobeAll_note, Modifier.weight(1f))
            if (beside != null) Box(Modifier.weight(1f)) { beside() }
        }
    }
}
