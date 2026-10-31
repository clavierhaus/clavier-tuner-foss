package at.clavierhaus.unisonmaster.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.clavierhaus.unisonmaster.i18n.K
import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.settings.StrobeMode
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.ui.Stepper

/*
 * What the strobe shows, chosen beside it (Peter, 31 Oct 2026: "Three menu items in three lines and only one can be
 * active ... right into the tuning window left to the strobes"): every partial, the lowest few, or one band whose motion
 * is the clean partials' average. It holds no measurement; it is the tuner's choice, kept in the settings, and the
 * engine takes it for the note on screen at once (TuningController.publish).
 */

/**
 * The three lines: All partials; Limit to N with − and +; One strobe, averaged. [onChange] changes the settings in force.
 * [beside] is what an edition puts at the end of the third line (Pro's unison switch), where the panel has width to spare
 * and, on the smallest phone, no height below.
 */
@Composable
fun StrobeChoice(
    s: TunerSettings,
    onChange: ((TunerSettings) -> TunerSettings) -> Unit,
    modifier: Modifier = Modifier,
    beside: (@Composable () -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(t(K.tuning_strobeAll), selected = s.strobeMode == StrobeMode.ALL, enabled = true) {
            onChange { it.copy(strobeMode = StrobeMode.ALL) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(t(K.tuning_strobeLimit), selected = s.strobeMode == StrobeMode.LIMIT, enabled = true) {
                onChange { it.copy(strobeMode = StrobeMode.LIMIT) }
            }
            Spacer(Modifier.width(8.dp))
            // the number is changed whatever the mode, and choosing it chooses the limit
            Stepper(
                s.strobeCount.toString(),
                canDecrease = s.strobeCount > 1,
                canIncrease = s.strobeCount < TunerSettings.MAX_STROBE_COUNT,
                onDecrease = { onChange { it.copy(strobeMode = StrobeMode.LIMIT, strobeCount = (it.strobeCount - 1).coerceAtLeast(1)) } },
                onIncrease = { onChange { it.copy(strobeMode = StrobeMode.LIMIT, strobeCount = (it.strobeCount + 1).coerceAtMost(TunerSettings.MAX_STROBE_COUNT)) } },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(t(K.tuning_strobeOne), selected = s.strobeMode == StrobeMode.ONE, enabled = true) {
                onChange { it.copy(strobeMode = StrobeMode.ONE) }
            }
            if (beside != null) Box(Modifier.weight(1f)) { beside() }
        }
    }
}
