package at.clavierhaus.unisonmaster.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import at.clavierhaus.unisonmaster.ui.ProbeButton
import at.clavierhaus.unisonmaster.ui.TuningPictureView
import at.clavierhaus.unisonmaster.picture.TuningPicture
import at.clavierhaus.unisonmaster.ui.DejaVuSerif
import at.clavierhaus.unisonmaster.ui.DoneButton
import at.clavierhaus.unisonmaster.ui.FitText
import at.clavierhaus.unisonmaster.ui.SamplingKeyboard
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/*
 * The sampling page (Peter, 4 Oct 2026): the start of a tuning has one
 * function — set this piano's A4, which is defined here and nowhere else
 * (the string's live reading to the tenth, with no reference to colour it;
 * once set, the reference itself, movable by a tenth, with the string as one
 * bell against it), define the temperament octave (A3–A4 by
 * default), and sample notes across the whole keyboard so the engine has the
 * piano's inharmonicity to compute the stretch from. No spectrum, no
 * partials to tap. Left, "Piano Sampling" and the short orders; right, A4,
 * the temperament, the key caught from the last strike with its Undo and its
 * Accept, the count and Done; along the bottom the piano's keyboard, every
 * sampled key green.
 *
 * Two screens, one file (Peter, 7 Oct 2026, 09:23: "this initial screen
 * serves only one purpose"). Before the session the page is the A4 screen:
 * the pitch chooser, the temperament, the picture of the string against the
 * pitch chosen, Done -- and nothing else, no title, no orders, no keyboard.
 * From Done on it is the sampling page described above.
 *
 * The right side is the only interactive area (Peter, 5 Oct 2026). The
 * keyboard indicates the key and shows the progress and takes no touch: a key
 * is 17 dp wide on a 891 dp screen, so a tap on it could not be aimed, and
 * what it used to offer — a sample out of the sampling, or struck anew — is
 * the Undo button beside the caught key and striking that key again.
 */
@Composable
internal fun SamplingPage(
    controller: TuningController,
    view: TuningController.TuningView?,
    /**
     * The one picture, built by the hub for every state of the screen: before the session A4's
     * first partial against the pitch chosen; from the session on the engine's reading of the note
     * on screen, its partials placed from the measured B and its false beats claimed by the tally.
     * Until 6 Oct 2026 this page built a frame of its own here -- a synthetic partial 1 with the
     * false set hardcoded empty -- and could never show a false beat (Peter's word for "b").
     */
    picture: TuningPicture,
    /** Moves the temperament octave's low note (C3…A3); null when the app does not offer it. */
    onTemperamentLow: ((Int) -> Unit)?,
    /** The recording being fed to the engine in place of the microphone, or null; see docs/PROBE.md. */
    feeding: String? = null,
    /** Opens the probe's file picker; null when the app does not offer the probe. */
    onPickRecording: (() -> Unit)? = null,
    /** Back to the microphone. */
    onUseMicrophone: () -> Unit = {},
) {
    val hz by controller.liveHz.collectAsState()
    val a4 by controller.referenceA4Hz.collectAsState()
    val cfg by controller.settings.collectAsState()
    val white = Color(Brand.WHITE); val muted = Color(Brand.WHITE_MUTED); val orange = Color(Brand.ORANGE)
    // the one thing this page asks before doing: A4 set again, which starts the sampling over
    var resetA4 by remember { mutableStateOf(false) }
    val sampled = view?.sampled.orEmpty() + (view?.measured.orEmpty() - TuningSession.MIDI_A4)
    val low = cfg.temperamentLowMidi
    // A4 is what sounds: before the session there is nothing else for the hub's reader to be reading,
    // and after it the strike must have been caught as A4 -- the strike of any other key is that key's
    // and is never taken for this pitch (4 Oct 2026, 16:52, where 193.5 Hz stood here). Done and
    // Accept go by this; the picture itself is the hub's and already follows the note on screen.
    val a4Live = hz != null && (view == null || view.caught == TuningSession.MIDI_A4)

    Box(Modifier.fillMaxSize()) {
        // The page is one band above the keyboard, and it is given its height rather than left to wrap
        // (Peter's screen recording of 4 Oct 2026, 16:52: the column grew past the pane and Caught with
        // its Accept and Sampled with its Done stood below the screen's edge, where they could not be
        // tapped). The work of the page — what was caught, how many are in — sits at the foot of that
        // band, pushed there by the spacer, so it is on the screen whatever stands above it.
        // Two screens share this file and are told apart by the session (Peter, 7 Oct 2026, 09:23):
        // before it, the page serves one purpose -- A4 set to the pitch the tuner wants -- and carries
        // nothing else: no title, no orders, no keyboard; the picture has the width. From Done on it
        // is the sampling page, with the orders on the left and the compass along the bottom.
        val settingA4 = view == null
        Row(Modifier.fillMaxSize().padding(bottom = if (settingA4) 0.dp else KEYBOARD_H + 8.dp)) {
            if (!settingA4) {
                // ---- left: what this page is for, and the orders ----
                Column(Modifier.width(380.dp).fillMaxHeight()) {
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
            }
            // ---- right: A4 and the temperament above, the work at the foot ----
            Column(Modifier.weight(1f).fillMaxHeight()) {
                CornerBand()
                // The pitch A4 is to be tuned to is chosen here, and the string is brought to it.
                // It starts at the engine's 440.0 Hz and the two steps move it by a tenth of a hertz;
                // until 5 Oct 2026 this page read the string and took it as the reference, which gave
                // the tuner nothing to aim at (Peter, 15:55: "we define the initial pitch for A4").
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(44.dp)) {
                    Text("A4", color = muted, fontSize = 14.sp, modifier = Modifier.width(60.dp))
                    StepBox("−") { controller.setReference(tenths(a4 - 0.1)) }
                    Text(
                        String.format(java.util.Locale.ROOT, "%.1f Hz", a4),
                        color = white, fontFamily = DejaVuSerif, fontSize = 30.sp,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    StepBox("+") { controller.setReference(tenths(a4 + 0.1)) }
                    if (view == null) {
                        // The string's own reading taken as the pitch to tune to, by one tap, for the
                        // piano that stays where it is. Until 5 Oct this happened by itself and was
                        // wrong, because the display was being tuned and not the string; now the pitch
                        // is defined first and the string tuned to it, and taking the reading as that
                        // pitch is the tuner's choice (Peter, 7 Oct 2026, 10:18).
                        Spacer(Modifier.width(14.dp))
                        Chip("as it stands", selected = false, enabled = a4Live) { hz?.let { controller.setReference(tenths(it)) } }
                    }
                    Spacer(Modifier.weight(1f))
                    if (view == null) DoneButton(onClick = { controller.acceptLive() }, enabled = a4Live)
                    else Text("set again", color = muted, fontSize = 13.sp, modifier = Modifier.clickable { resetA4 = true }.padding(6.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(36.dp)) {
                    Text("Temperament", color = muted, fontSize = 14.sp, modifier = Modifier.width(120.dp))
                    val canDown = onTemperamentLow != null && low > TunerSettings.MIN_TEMPERAMENT_LOW
                    val canUp = onTemperamentLow != null && low < TunerSettings.MAX_TEMPERAMENT_LOW
                    Text("−", color = if (canDown) white else Color(0xFF444444), fontSize = 22.sp, modifier = Modifier.clickable(enabled = canDown) { onTemperamentLow?.invoke(low - 1) }.padding(horizontal = 10.dp))
                    Text("${Notes.name(low)}–A4", color = white, fontFamily = DejaVuSerif, fontSize = 20.sp)
                    Text("+", color = if (canUp) white else Color(0xFF444444), fontSize = 22.sp, modifier = Modifier.clickable(enabled = canUp) { onTemperamentLow?.invoke(low + 1) }.padding(horizontal = 10.dp))
                    // The probe on this page too (Peter, 6 Oct 2026): A4 is set here, and a recording
                    // is the only way to see what the display makes of a string when the piano is out
                    // of reach. It stood in the corner band first and was drawn over the wordmark
                    // (his screenshot, 20:55), because that band is the brand's and not a place for a
                    // control; it sits at the end of this row instead, which is the right side — the
                    // only interactive area of this page — and has the width for the file's name.
                    if (onPickRecording != null) {
                        Spacer(Modifier.weight(1f))
                        ProbeButton(feeding = feeding, onPick = onPickRecording, onStop = onUseMicrophone)
                    }
                }
                // The tuning session's own display, so that hitting the pitch here looks exactly as it
                // looks on every other note and green means the same thing (Peter, 15:56). It is given
                // the band between the temperament and the work, and so pushes the work to the foot as
                // the spacer used to.
                TuningPictureView(
                    picture, cfg.matchCents,
                    modifier = Modifier.fillMaxWidth().weight(1f), follows = false,
                    // measuring, not tuning: the stripes keep one colour here (Peter, 7 Oct 2026)
                    marks = false,
                )
                if (view != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(50.dp)) {
                        Text("Caught", color = muted, fontSize = 14.sp, modifier = Modifier.width(92.dp))
                        val c = view.caught
                        Text(
                            if (c != null) Notes.name(c) else "strike a key",
                            color = if (c != null) orange else muted, fontFamily = DejaVuSerif, fontSize = if (c != null) 28.sp else 18.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        // Undo stands beside the key it takes back and names it, so nothing is asked
                        // twice: the sample goes, and striking that key again puts a new one in its
                        // place, which is what the engine does with any key struck a second time. The
                        // keyboard takes no touch — the right side of this page is the only interactive
                        // area (Peter, 5 Oct 2026) — and a 17 dp key could not be aimed at anyway.
                        c?.takeIf { it != TuningSession.MIDI_A4 && it in sampled }?.let { back ->
                            Chip("Undo ${Notes.name(back)}", selected = false, enabled = true) { controller.forgetSample(back) }
                            Spacer(Modifier.width(10.dp))
                        }
                        AcceptButton(accepted = view.caughtAccepted, enabled = controller.acceptReady(), onClick = { controller.acceptLive() })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(50.dp)) {
                        Text("Sampled", color = muted, fontSize = 14.sp, modifier = Modifier.width(92.dp))
                        Text("${view.samplesIn} · at least ${view.samplesNeeded}", color = white, fontFamily = DejaVuSerif, fontSize = 18.sp)
                        Spacer(Modifier.weight(1f))
                        DoneButton(onClick = { controller.finishSampling() }, enabled = view.samplingReady)
                    }
                }
            }
        }
        // ---- along the bottom: the whole keyboard, once there is a sampling to show on it ----
        if (!settingA4) SamplingKeyboard(
            lowestMidi = cfg.lowestKeyMidi,
            sampled = sampled,
            caught = view?.caught,
            referenceSet = true,
            temperament = low..TuningSession.MIDI_A4,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(KEYBOARD_H),
        )
    }

    // Setting A4 again throws every sample away, so it is asked before it is done. Nothing else on
    // this page asks: Undo names the key it takes back, and the key can simply be struck again.
    if (resetA4) {
        Dialog(onDismissRequest = { resetA4 = false }) {
            Column(Modifier.background(Color(Brand.NEAR_BLACK), RoundedCornerShape(10.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Notes.name(TuningSession.MIDI_A4), color = white, fontFamily = DejaVuSerif, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Setting A4 again starts the sampling over: every sample goes.", color = muted, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Set A4 again", selected = true, enabled = true) { resetA4 = false; controller.resetSession() }
                    Chip("Keep", selected = false, enabled = true) { resetA4 = false }
                }
            }
        }
    }
}

/** The keyboard's height along the foot of the page; the band above it is what is left. */
private val KEYBOARD_H = 116.dp

/** The orders, military-short (Peter, 4 Oct 2026). */
private val ORDERS = listOf(
    "A4: one string, the others muted. Set it.",
    "Sample the lowest note.",
    "First and last bichord: one string muted.",
    "First plain-wire trichord: two strings muted.",
    "Then: Two notes per octave, up to C7.",
    "Each key struck: check it, Accept.",
)

/** A pitch to the tenth of a hertz, within the reference's range. */
private fun tenths(hz: Double): Double =
    (kotlin.math.round(hz * 10) / 10).coerceIn(TuningController.MIN_REFERENCE_HZ, TuningController.MAX_REFERENCE_HZ)

/** A step of the reference: a box with its sign, to the left of the number it moves. */
@Composable
private fun StepBox(label: String, onClick: () -> Unit) {
    // A tap steps once. A finger held steps again after STEP_HOLD_MS and then every STEP_REPEAT_MS
    // until it lifts: 439.2 from 440.0 was eight taps. Not too fast, but visible (Peter, 7 Oct 2026,
    // 10:18): about six tenths a second, so a hertz takes a little under two seconds and the eye
    // can stop it on the tenth it wants.
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onClick)
    Box(
        Modifier.size(width = 40.dp, height = 34.dp)
            .background(Color(0xFF2A2A2A), RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    step()
                    val running = scope.launch {
                        delay(STEP_HOLD_MS)
                        while (isActive) { step(); delay(STEP_REPEAT_MS) }
                    }
                    tryAwaitRelease()
                    running.cancel()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color(Brand.WHITE), fontSize = 20.sp)
    }
}

/** A held step box waits this long before it runs, ms. */
private const val STEP_HOLD_MS = 450L
/** ... and then steps this often, ms: a tenth of a hertz every 160 ms. */
private const val STEP_REPEAT_MS = 160L
