package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand

/** One sequence on offer: its name, a line about it, and what to do when it is chosen. */
data class SequenceChoice(val name: String, val note: String, val onPick: () -> Unit)

/**
 * The aural sequences on offer, one per line, and the way to one's own file.
 * A screen and nothing else: the sequence chosen is handed to the tuning
 * screen, whose reading is the engine's own.
 */
@Composable
fun SequencePicker(
    choices: List<SequenceChoice>,
    onPickFile: () -> Unit,
    fileNote: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Color(Brand.BLACK))
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "<-",
                color = Color(Brand.WHITE),
                fontSize = 22.sp,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(end = 20.dp, top = 8.dp, bottom = 8.dp),
            )
            Column {
                Text("Aural sequence", color = Color(Brand.WHITE), fontFamily = DejaVuSerif, fontSize = 26.sp)
                Text(
                    "Tune each interval by ear; the arrows follow the sequence, the reading says whether the ear landed",
                    color = Color(Brand.WHITE_MUTED), fontSize = 14.sp,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            for (c in choices) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = c.onPick)
                        .padding(vertical = 12.dp),
                ) {
                    Text(c.name, color = Color(Brand.WHITE), fontFamily = DejaVuSerif, fontSize = 20.sp)
                    if (c.note.isNotEmpty()) Text(c.note, color = Color(Brand.WHITE_MUTED), fontSize = 13.sp)
                }
                HorizontalDivider(color = Color(0xFF2A2A2A))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPickFile)
                    .padding(vertical = 12.dp),
            ) {
                Text("A file of your own…", color = Color(Brand.ORANGE), fontFamily = DejaVuSerif, fontSize = 20.sp)
                Text(
                    "A text file, one note per line — A4, F#3 — and anything after the note is shown with it; " +
                        "lines starting with # are notes to yourself. A TuneLab sequence file works as it is.",
                    color = Color(Brand.WHITE_MUTED), fontSize = 13.sp,
                )
            }
            HorizontalDivider(color = Color(0xFF2A2A2A))
            if (fileNote != null) {
                Spacer(Modifier.height(10.dp))
                Text(fileNote, color = Color(Brand.ORANGE), fontSize = 13.sp)
            }
        }
    }
}
