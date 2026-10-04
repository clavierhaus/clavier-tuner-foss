package at.clavierhaus.unisonmaster.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.tuning.Notes
import at.clavierhaus.unisonmaster.tuning.TuningSession
import at.clavierhaus.unisonmaster.ui.AcceptButton
import at.clavierhaus.unisonmaster.ui.CornerBand
import at.clavierhaus.unisonmaster.ui.DejaVuSerif
import at.clavierhaus.unisonmaster.ui.DoneButton
import at.clavierhaus.unisonmaster.ui.FitText
import at.clavierhaus.unisonmaster.ui.SamplingKeyboard
import at.clavierhaus.unisonmaster.ui.formatHz

/*
 * The sampling page (Peter, 4 Oct 2026): the start of a tuning has one
 * function — set the reference A4, define the temperament octave (A3–A4 by
 * default), and sample notes across the whole keyboard so the engine has the
 * piano's inharmonicity to compute the stretch from. No spectrum, no
 * partials to tap. Left, "Piano Sampling" and the short orders; right, A4,
 * the temperament, the key caught from the last strike with Accept, the
 * count and Done; along the bottom the piano's keyboard, every sampled key
 * orange — a tap on one offers its undo: out of the sampling, or struck anew.
 */
@Composable
internal fun SamplingPage(
    controller: TuningController,
    view: TuningController.TuningView?,
    /** Moves the temperament octave's low note (C3…A3); null when the app does not offer it. */
    onTemperamentLow: ((Int) -> Unit)?,
) {
    val hz by controller.liveHz.collectAsState()
    val a4 by controller.referenceA4Hz.collectAsState()
    val cfg by controller.settings.collectAsState()
    val white = Color(Brand.WHITE); val muted = Color(Brand.WHITE_MUTED); val orange = Color(Brand.ORANGE)
    var undo by remember { mutableStateOf<Int?>(null) }
    var strikeAgain by remember { mutableStateOf<Int?>(null) }
    val sampled = view?.sampled.orEmpty() + (view?.measured.orEmpty() - TuningSession.MIDI_A4)
    val low = cfg.temperamentLowMidi

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 132.dp)) {
            // ---- left: what this page is for, and the orders ----
            Column(Modifier.width(380.dp)) {
                CornerBand()
                Text("Piano Sampling", color = white, fontFamily = DejaVuSerif, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Retrieve the piano's inharmonicity", color = muted, fontFamily = DejaVuSerif, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.fillMaxWidth().border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(8.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    for (line in ORDERS) FitText(line, 14.sp, Modifier.fillMaxWidth(), color = white)
                }
            }
            Spacer(Modifier.width(28.dp))
            // ---- right: A4, the temperament, the key caught, the count ----
            Column(Modifier.weight(1f).padding(top = 52.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A4", color = muted, fontSize = 14.sp, modifier = Modifier.width(120.dp))
                    if (view == null) {
                        Text(hz?.let { formatHz(it) } ?: "strike A4, one string", color = if (hz != null) white else muted, fontFamily = DejaVuSerif, fontSize = 22.sp)
                        Spacer(Modifier.weight(1f))
                        DoneButton(onClick = { controller.acceptLive() }, enabled = hz != null)
                    } else {
                        Text(formatHz(a4), color = Color(Brand.GO_GREEN), fontFamily = DejaVuSerif, fontSize = 22.sp)
                        Spacer(Modifier.weight(1f))
                        Text("set again", color = muted, fontSize = 13.sp, modifier = Modifier.clickable { undo = TuningSession.MIDI_A4 }.padding(6.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Temperament", color = muted, fontSize = 14.sp, modifier = Modifier.width(120.dp))
                    val canDown = onTemperamentLow != null && low > TunerSettings.MIN_TEMPERAMENT_LOW
                    val canUp = onTemperamentLow != null && low < TunerSettings.MAX_TEMPERAMENT_LOW
                    Text("−", color = if (canDown) white else Color(0xFF444444), fontSize = 22.sp, modifier = Modifier.clickable(enabled = canDown) { onTemperamentLow?.invoke(low - 1) }.padding(horizontal = 10.dp))
                    Text("${Notes.name(low)}–A4", color = white, fontFamily = DejaVuSerif, fontSize = 20.sp)
                    Text("+", color = if (canUp) white else Color(0xFF444444), fontSize = 22.sp, modifier = Modifier.clickable(enabled = canUp) { onTemperamentLow?.invoke(low + 1) }.padding(horizontal = 10.dp))
                }
                if (view != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Caught", color = muted, fontSize = 14.sp, modifier = Modifier.width(120.dp))
                        val c = view.caught
                        Text(
                            when {
                                c != null -> Notes.name(c)
                                strikeAgain != null -> "strike ${Notes.name(strikeAgain!!)} again"
                                else -> "strike a key"
                            },
                            color = if (c != null) orange else muted, fontFamily = DejaVuSerif, fontSize = if (c != null) 28.sp else 18.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        AcceptButton(accepted = view.caughtAccepted, enabled = controller.acceptReady(), onClick = { controller.acceptLive(); strikeAgain = null })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sampled", color = muted, fontSize = 14.sp, modifier = Modifier.width(120.dp))
                        Text("${view.samplesIn} · at least ${view.samplesNeeded}", color = white, fontFamily = DejaVuSerif, fontSize = 18.sp)
                        Spacer(Modifier.weight(1f))
                        DoneButton(onClick = { controller.finishSampling() }, enabled = view.samplingReady)
                    }
                }
            }
        }
        // ---- along the bottom: the whole keyboard ----
        SamplingKeyboard(
            lowestMidi = cfg.lowestKeyMidi,
            sampled = sampled,
            caught = view?.caught,
            referenceSet = view != null,
            temperament = low..TuningSession.MIDI_A4,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(120.dp),
            onKey = { m -> if (m in sampled || (m == TuningSession.MIDI_A4 && view != null)) undo = m },
        )
    }

    // the undo of a sampled key: out of the sampling, or struck anew; A4's is the session begun again
    undo?.let { m ->
        Dialog(onDismissRequest = { undo = null }) {
            Column(Modifier.background(Color(Brand.NEAR_BLACK), RoundedCornerShape(10.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Notes.name(m), color = white, fontFamily = DejaVuSerif, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (m == TuningSession.MIDI_A4) {
                    Text("Setting A4 again starts the sampling over: every sample goes.", color = muted, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Set A4 again", selected = true, enabled = true) { undo = null; controller.resetSession() }
                        Chip("Keep", selected = false, enabled = true) { undo = null }
                    }
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Strike again", selected = true, enabled = true) { controller.forgetSample(m); strikeAgain = m; undo = null }
                    Chip("Remove", selected = false, enabled = true) { controller.forgetSample(m); undo = null }
                    Chip("Keep", selected = false, enabled = true) { undo = null }
                }
            }
        }
    }
}

/** The orders, military-short (Peter, 4 Oct 2026). */
private val ORDERS = listOf(
    "A4: one string, the others muted. Set it.",
    "Sample the lowest note.",
    "First and last bichord: one string muted.",
    "First plain-wire trichord: two strings muted.",
    "Then across the piano: two notes per octave, up to C7.",
    "Each key struck: check it, Accept.",
)
