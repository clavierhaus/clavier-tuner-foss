package at.clavierhaus.unisonmaster.app

import at.clavierhaus.unisonmaster.i18n.noteName
import at.clavierhaus.unisonmaster.ui.formatHz
import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.i18n.K
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
import androidx.compose.ui.text.style.TextAlign
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
import at.clavierhaus.unisonmaster.ui.PartialChips
import at.clavierhaus.unisonmaster.ui.TuningPictureView
import at.clavierhaus.unisonmaster.picture.TuningPicture
import at.clavierhaus.unisonmaster.ui.DejaVuSerif
import at.clavierhaus.unisonmaster.ui.DoneButton
import at.clavierhaus.unisonmaster.ui.TextBlock
import at.clavierhaus.unisonmaster.ui.LINE
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import at.clavierhaus.unisonmaster.ui.SamplingKeyboard
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
    val hiddenA4 by controller.hiddenPartials.collectAsState()
    val audibleA4 by controller.liveAudible.collectAsState()
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

    // a line as tall as its type, not the theme's 24 sp at every size (24 Oct 2026: at the phone's largest
    // text the two lines of a label and its value stood taller than their row)
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.merge(TextStyle(lineHeight = LINE.em))) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // the keyboard's height, a share of the page's (docs/UI.md, law 1): at a fixed 116 dp a small
        // phone's work rows stood below its top edge (24 Oct 2026)
        val keyboardH = maxHeight * KEYBOARD_SHARE
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
        Row(Modifier.fillMaxSize().padding(bottom = if (settingA4) 0.dp else keyboardH + 8.dp)) {
            if (!settingA4) {
                // ---- left: what this page is for, and the orders ----
                // a share of the width, never a number of dp (docs/UI.md, law 1): at 380 dp the right side
                // was left too little on a small phone, and its buttons broke their words (24 Oct 2026)
                Column(Modifier.weight(LEFT_SHARE).fillMaxHeight()) {
                    CornerBand()
                    Text(t(K.sampling_title), color = white, fontFamily = DejaVuSerif, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    // the subtitle ("Retrieve the piano's inharmonicity") left on 24 Oct 2026: at the phone's largest
                    // text it took two lines, and the orders, which say what to do, no longer fitted under it
                    Spacer(Modifier.height(6.dp))
                    // the orders: one element, one size of type, wrapping between words (Peter, 24 Oct 2026,
                    // 15:10); until then each line shrank alone to its width and the last fell off the card
                    Box(Modifier.fillMaxWidth().weight(1f, fill = false).border(1.dp, Color(Brand.RULE), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        TextBlock(orders(view?.samplesNeeded ?: 0), Modifier.fillMaxWidth(), size = 15.sp, color = white, gap = 4.dp)
                    }
                }
                Spacer(Modifier.width(24.dp))
            }
            // ---- right: A4 and the temperament above, the work at the foot ----
            Column(Modifier.weight(if (settingA4) 1f else 1f - LEFT_SHARE).fillMaxHeight()) {
                CornerBand()
                // The pitch A4 is to be tuned to is chosen here, and the string is brought to it.
                // It starts at the engine's 440.0 Hz and the two steps move it by a tenth of a hertz;
                // until 5 Oct 2026 this page read the string and took it as the reference, which gave
                // the tuner nothing to aim at (Peter, 15:55: "we define the initial pitch for A4").
                val a4Now by rememberUpdatedState(a4)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(44.dp)) {
                    Text("A4", color = muted, fontFamily = DejaVuSerif, fontSize = 14.sp, softWrap = false, modifier = Modifier.width(60.dp))
                    StepBox("−") { controller.setReference(tenths(a4Now - 0.1)) }
                    Text(
                        formatHz(a4),
                        color = white, fontFamily = DejaVuSerif, fontSize = 30.sp, softWrap = false,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    StepBox("+") { controller.setReference(tenths(a4Now + 0.1)) }
                    // "as it stands", the string's reading taken as the pitch by one tap (7 Oct 2026), left on
                    // 23 Oct 2026 (Peter): the tuner who keeps the pitch shown taps Done
                    Spacer(Modifier.weight(1f))
                    if (view == null) DoneButton(onClick = { controller.acceptLive() }, enabled = a4Live)
                    else Text(t(K.sampling_setAgain), color = muted, fontFamily = DejaVuSerif, fontSize = 13.sp, softWrap = false, modifier = Modifier.clickable { resetA4 = true }.padding(6.dp))
                }
                // Under − 440.0 +, the line that says how the pitch is set is the slider itself: a finger
                // drawn right along it raises A4 a tenth of a hertz every SLIDE_STEP_DP, drawn left lowers
                // it (Peter, 23 Oct 2026: on the number alone the slide was too small to find, and the
                // boxes took the touch). A strip of its own, so it reads as something to touch.
                if (view == null) Box(
                    Modifier.padding(start = 60.dp, top = 4.dp).fillMaxWidth(SLIDER_SHARE).heightIn(min = 30.dp)
                        .background(Color(Brand.CONTROL), RoundedCornerShape(6.dp))
                        .pointerInput(Unit) {
                            val stepPx = SLIDE_STEP_DP.dp.toPx()
                            var acc = 0f
                            detectHorizontalDragGestures(onDragStart = { acc = 0f }) { change, dx ->
                                change.consume()
                                acc += dx
                                val steps = (acc / stepPx).toInt()
                                if (steps != 0) { acc -= steps * stepPx; controller.setReference(tenths(a4Now + steps * 0.1)) }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // the hint wraps between its words inside the strip rather than being cut at its end (24 Oct 2026)
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("‹", color = muted, fontSize = 13.sp)
                        TextBlock(listOf(t(K.sampling_a4Hint)), Modifier.weight(1f).padding(horizontal = 8.dp), size = 13.sp, color = muted, align = TextAlign.Center)
                        Text("›", color = muted, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(36.dp)) {
                    Text(t(K.sampling_temperament), color = muted, fontFamily = DejaVuSerif, fontSize = 14.sp, softWrap = false)
                    Spacer(Modifier.width(12.dp))
                    val canDown = onTemperamentLow != null && low > TunerSettings.MIN_TEMPERAMENT_LOW
                    val canUp = onTemperamentLow != null && low < TunerSettings.MAX_TEMPERAMENT_LOW
                    Text("−", color = if (canDown) white else Color(0xFF444444), fontSize = 22.sp, modifier = Modifier.clickable(enabled = canDown) { onTemperamentLow?.invoke(low - 1) }.padding(horizontal = 10.dp))
                    Text("${noteName(low)}–A4", color = white, fontFamily = DejaVuSerif, fontSize = 20.sp)
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
                // On a small phone the sampling page leaves the picture a few millimetres, where its labels
                // could only shrink to nothing (24 Oct 2026, a 780 x 360 dp pane): under MIN_PICTURE it is not
                // drawn. The A4 page has no keyboard and always has the room.
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    if (settingA4 || maxHeight >= MIN_PICTURE) TuningPictureView(
                        picture, cfg.matchCents,
                        modifier = Modifier.fillMaxSize(), follows = false,
                        // measuring, not tuning: the stripes keep one colour here (Peter, 7 Oct 2026)
                        marks = false,
                        // A4: the strobe alone, no bells (Peter, 22 Oct 2026)
                        bells = view != null,
                    )
                }
                if (view == null) {
                    // A4's partials under the strobe: a tap takes one out of it, another brings it back,
                    // so the strings can be compared on the partials that matter (Peter, 22 Oct 2026)
                    PartialChips(
                        a4Hz = a4, baseHz = a4,
                        count = controller.highestPartial(a4),
                        shown = audibleA4 - hiddenA4,
                        falseMarked = emptySet(),
                        tappable = audibleA4 - 1,
                        onTap = { k -> controller.tapPartial(k) },
                        // the chip and its number under it: 36 dp cut the numbers (24 Oct 2026)
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    )
                }
                if (view != null) {
                    // what the last strike caught, under its label, and the two answers to it; below, the count,
                    // how far the caught string's measurement has got, and Done. The status is an element of its
                    // own in the row with the room for it, and wraps between words; until 24 Oct 2026 it was
                    // given what Cancel and Accept left, and "gemessen" stood as "ge / me" on the Motorola edge
                    // 50 neo (Gregor, Clavio).
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = ROW_H)) {
                        val c = view.caught
                        Column {
                            Text(t(K.sampling_caughtLabel), color = muted, fontFamily = DejaVuSerif, fontSize = 13.sp, softWrap = false)
                            Text(
                                if (c != null) noteName(c) else t(K.sampling_strikeKey),
                                // the key kept stands green, as it does on the keyboard (Peter, 22 Oct 2026)
                                color = if (c == null) muted else if (view.caughtAccepted) Color(Brand.GO_GREEN) else orange,
                                fontFamily = DejaVuSerif, fontSize = if (c != null) 26.sp else 16.sp, softWrap = false,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        // Cancel and Accept, the two answers to a strike (Peter, 12 Oct 2026: the
                        // button names its act, the other says Cancel). Cancel drops what the strike
                        // caught -- its sample too, once taken -- and the page waits for a new strike
                        // and a new reading. The keyboard takes no touch — the right side of this page
                        // is the only interactive area (Peter, 5 Oct 2026).
                        if (c != null) {
                            Spacer(Modifier.width(8.dp))
                            Chip(t(K.app_cancel), selected = false, enabled = true) { controller.cancelCaught() }
                        }
                        Spacer(Modifier.width(8.dp))
                        AcceptButton(accepted = view.caughtAccepted, enabled = controller.acceptReady(), onClick = { controller.acceptLive() })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = ROW_H)) {
                        Column {
                            Text(t(K.sampling_sampledLabel), color = muted, fontFamily = DejaVuSerif, fontSize = 13.sp, softWrap = false)
                            Text(t(K.sampling_atLeast, view.samplesIn, view.samplesNeeded), color = white, fontFamily = DejaVuSerif, fontSize = 18.sp, softWrap = false)
                        }
                        Spacer(Modifier.width(12.dp))
                        // how far the caught string's measurement has got (18 Oct 2026: a tuner could not
                        // know that the string must ring on until it is measured; SAMICK findings §29-30):
                        // measuring while more sound still changes it, measured once it does not, and
                        // short when the next strike came first
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                            if (view.caught != null) {
                                val (word, colour) = when (view.caughtMeasured) {
                                    TuningController.Measured.MEASURING -> t(K.sampling_measuring) to muted
                                    TuningController.Measured.MEASURED -> t(K.sampling_measured) to Color(Brand.GO_GREEN)
                                    TuningController.Measured.SHORT -> t(K.sampling_short) to Color(Brand.MEASURED_SHORT)
                                }
                                TextBlock(listOf(word), Modifier.fillMaxWidth(), size = 14.sp, color = colour)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        DoneButton(onClick = { controller.finishSampling() }, enabled = view.samplingReady)
                    }
                }
            }
        }
        // ---- along the bottom: the whole keyboard, once there is a sampling to show on it ----
        if (!settingA4) SamplingKeyboard(
            lowestMidi = cfg.lowestKeyMidi,
            sampled = sampled,
            short = view?.sampledShort.orEmpty(),
            caught = view?.caught,
            referenceSet = true,
            temperament = low..TuningSession.MIDI_A4,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(keyboardH),
        )
    }

    }
    // Setting A4 again throws every sample away, so it is asked before it is done. Nothing else on
    // this page asks: Undo names the key it takes back, and the key can simply be struck again.
    if (resetA4) {
        Dialog(onDismissRequest = { resetA4 = false }) {
            Column(Modifier.background(Color(Brand.NEAR_BLACK), RoundedCornerShape(10.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(noteName(TuningSession.MIDI_A4), color = white, fontFamily = DejaVuSerif, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(t(K.sampling_resetWarning), color = muted, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(t(K.sampling_setA4Again), selected = true, enabled = true) { resetA4 = false; controller.resetSession() }
                    Chip(t(K.app_cancel), selected = false, enabled = true) { resetA4 = false }
                }
            }
        }
    }
}

/** The keyboard's share of the page's height along its foot; the band above it is what is left. */
private const val KEYBOARD_SHARE = 0.28f

/**
 * The orders, short (Peter, 24 Oct 2026): no key is named -- the tuner samples where he likes -- and
 * the count says the minimum and what gives the best result. Until then they named the lowest note,
 * the bichords, the first plain trichord and C7, and a tuner who leaves out A0-B0 by habit read them as
 * an instruction to sample A0 (Gregor, Clavio, 24 Oct 2026).
 */
private fun orders(needed: Int): List<String> = listOf(
    t(K.sampling_order_strings), t(K.sampling_order_count, needed), t(K.sampling_order_accept),
)

/** The left side's share of the width; the right side, with the controls, has the rest. */
private const val LEFT_SHARE = 0.42f

/** The A4 hint's strip, as a share of the right side under − 440.0 +. */
private const val SLIDER_SHARE = 0.75f

/** The height of the rows of the work: the key caught, and the count. */
private val ROW_H = 50.dp

/** Under this the sampling page's picture is not drawn: its labels would shrink past reading. */
private val MIN_PICTURE = 90.dp

/** A pitch to the tenth of a hertz, within the reference's range. */
private fun tenths(hz: Double): Double =
    (kotlin.math.round(hz * 10) / 10).coerceIn(TuningController.MIN_REFERENCE_HZ, TuningController.MAX_REFERENCE_HZ)

/** A step of the reference: a box with its sign, to the left of the number it moves. */
@Composable
private fun StepBox(label: String, blocked: () -> Boolean = { false }, onClick: () -> Unit) {
    // A tap steps once. A finger held steps again after STEP_HOLD_MS and then every STEP_REPEAT_MS
    // until it lifts: 439.2 from 440.0 was eight taps. Not too fast, but visible (Peter, 7 Oct 2026,
    // 10:18): about six tenths a second, so a hertz takes a little under two seconds and the eye
    // can stop it on the tenth it wants.
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onClick)
    Box(
        Modifier.size(width = 40.dp, height = 34.dp)
            .background(Color(Brand.CONTROL), RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    step()
                    val running = scope.launch {
                        delay(STEP_HOLD_MS)
                        while (isActive && !blocked()) { step(); delay(STEP_REPEAT_MS) }
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

/** A finger slid this far along the strip under − 440.0 + moves A4 a tenth of a hertz, dp. */
private const val SLIDE_STEP_DP = 8
/** A held step box waits this long before it runs, ms. */
private const val STEP_HOLD_MS = 450L
/** ... and then steps this often, ms: a tenth of a hertz every 160 ms. */
private const val STEP_REPEAT_MS = 160L
