package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.tuning.Notes

/**
 * The session's compass — every key the session can tune, [lowMidi] to
 * [highMidi] as [at.clavierhaus.unisonmaster.tuning.TuningSession] gives
 * them — laid out as a keyboard the tuner reads at a glance: one row per
 * octave, bass at the bottom, the twelve notes left to right, the key now on
 * screen marked. A tap names the key and closes the picker; a tap outside
 * closes it with nothing changed. Nothing scrolls and nothing is cut: every
 * cell is an equal share of the dialog, and the label steps down to fit.
 * Opened from the note name on the tuning picture; choosing a key here is
 * the tuner's hand, and the screen stops following the key struck until the
 * name is tapped again (docs/UI.md).
 */
@Composable
fun NotePicker(lowMidi: Int, highMidi: Int, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val lowOctave = lowMidi / 12 - 1
    val highOctave = highMidi / 12 - 1
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize(0.92f)
                .background(Color(Brand.NEAR_BLACK), RoundedCornerShape(12.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (octave in highOctave downTo lowOctave) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (pc in 0 until 12) {
                        val midi = (octave + 1) * 12 + pc
                        val inCompass = midi in lowMidi..highMidi
                        val isCurrent = midi == current
                        val black = pc in setOf(1, 3, 6, 8, 10)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    when {
                                        !inCompass -> Color.Transparent
                                        isCurrent -> Color(Brand.ORANGE)
                                        black -> Color(Brand.KEY_BLACK)
                                        else -> Color(Brand.KEY_WHITE)
                                    },
                                    RoundedCornerShape(6.dp),
                                )
                                .then(if (isCurrent) Modifier.border(2.dp, Color(Brand.WHITE), RoundedCornerShape(6.dp)) else Modifier)
                                .clickable(enabled = inCompass, onClick = { onPick(midi) }),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (inCompass) FitText(
                                Notes.name(midi),
                                16.sp,
                                color = Color(if (black && !isCurrent) Brand.WHITE else Brand.BLACK),
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                align = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
