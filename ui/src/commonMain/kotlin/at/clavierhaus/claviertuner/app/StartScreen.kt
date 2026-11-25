package at.clavierhaus.claviertuner.app

import at.clavierhaus.claviertuner.tuning.NamedTemperament
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.claviertuner.Brand
import at.clavierhaus.claviertuner.TuningController
import at.clavierhaus.claviertuner.i18n.K
import at.clavierhaus.claviertuner.i18n.Strings
import at.clavierhaus.claviertuner.i18n.noteName
import at.clavierhaus.claviertuner.i18n.t
import at.clavierhaus.claviertuner.picture.TuningPicture
import at.clavierhaus.claviertuner.settings.TunerSettings
import at.clavierhaus.claviertuner.tuning.TuningSession
import at.clavierhaus.claviertuner.ui.AcceptButton
import at.clavierhaus.claviertuner.ui.CORNER_BAND
import at.clavierhaus.claviertuner.ui.CORNER_LEFT_WIDTH
import at.clavierhaus.claviertuner.ui.CORNER_RIGHT_WIDTH
import at.clavierhaus.claviertuner.ui.Corners
import at.clavierhaus.claviertuner.ui.DejaVuSerif
import at.clavierhaus.claviertuner.ui.DoneButton
import at.clavierhaus.claviertuner.ui.FitText
import at.clavierhaus.claviertuner.ui.ProbeButton
import at.clavierhaus.claviertuner.ui.SamplingKeyboard
import at.clavierhaus.claviertuner.ui.TextBlock
import at.clavierhaus.claviertuner.ui.TuningPictureView
import at.clavierhaus.claviertuner.ui.formatHz
import at.clavierhaus.claviertuner.ui.nowMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/*
 * The start of a tuning: A4 and the sampling on the tuning screen's frame (Plus's start screen of 21-23 Nov 2026, taken into
 * the free app on Peter's word, 23 Nov 2026, 16:34: "the interface restructuring is a winner. Please apply it to foss and
 * Pro"). The same three zones as the tuning screen: the edge, the work, the full keyboard at the foot. A strip of the three
 * steps under the edge says where the tuner is, an earlier step one tap back; the keyboard leads, the next key dotted on it
 * and named large; the temperament is an edge item with its strip (start, end and the named temperaments), the octave types
 * the stretch window, and an edition's piano an edge item of its own.
 *
 * Every number is the engine's: the A4 picture is built as BasicHub builds it (A4's first partial against the pitch chosen,
 * by the hub's reader), the sampling's facts are the TuningView's. SamplingPage, drawn by BasicHub, stays for an edition
 * that mounts BasicHub.
 */

private val White = Color(Brand.WHITE)
private val Muted = Color(Brand.WHITE_MUTED)
private val Orange = Color(Brand.ORANGE)

@Composable
fun StartScreen(
    controller: TuningController,
    onSettings: () -> Unit,
    onHome: () -> Unit,
    onTemperamentLow: (Int) -> Unit,
    onTemperamentHigh: (Int) -> Unit,
    onChangeSettings: ((TunerSettings) -> TunerSettings) -> Unit,
    /** An edition's piano, named on the edge (null: none chosen); the item shows only with [onPianoIndex]. */
    piano: String? = null,
    /** An edition's fields for a new piano, drawn in the piano's strip; null when the tuning has its piano. */
    pianoFields: (@Composable () -> Unit)? = null,
    /** An edition's pianos; null in the free app, which keeps no pianos, and the edge has no Piano. */
    onPianoIndex: (() -> Unit)? = null,
    feeding: String? = null,
    onPickRecording: (() -> Unit)? = null,
    onUseMicrophone: () -> Unit = {},
    /**
     * The temperament list under the edge's Temperament, for an edition that offers it (Pro). The free app is equal
     * temperament only (Peter, 23 Nov 2026, 22:00): its strip holds the temperament octave's start and end, and the edge
     * says Equal.
     */
    temperaments: Boolean = false,
) {
    val view by controller.tuning.collectAsState()
    val a4 by controller.referenceA4Hz.collectAsState()
    val hubHz by controller.liveHz.collectAsState()
    val cfg by controller.settings.collectAsState()
    val a4State by controller.a4Status.collectAsState()
    @Suppress("UNUSED_VARIABLE") val language by Strings.language.collectAsState()
    val pictureBuilder = remember { TuningPicture.Builder() }
    val v = view
    val settingA4 = v == null
    val low = cfg.temperamentLowMidi
    val high = cfg.temperamentHighMidi

    /** Which edge item is open: "temperament" and "piano" strips, "stretch" the window; null none. */
    var open by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color(Brand.BLACK)).safeDrawingPadding()) {
        Corners(onSettings = onSettings, onHome = onHome)
        // ---- the edge ----
        Row(
            Modifier.fillMaxWidth().height(CORNER_BAND).padding(start = CORNER_LEFT_WIDTH, end = CORNER_RIGHT_WIDTH + 8.dp).padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            EdgeItem("A4", formatHz(a4), false, Modifier.weight(1f)) { open = null }
            EdgeItem(t(K.edge_temperament),
                // "Equal" too, so the edge says there is a choice (Peter, 23 Nov 2026)
                temperamentName(cfg.temperament),
                open == "temperament", Modifier.weight(1.1f)) {
                open = if (open == "temperament") null else "temperament"
            }
            EdgeItem(t(K.edge_octaves), octaveLine(cfg), open == "stretch", Modifier.weight(1.6f)) { open = if (open == "stretch") null else "stretch" }
            if (v != null) EdgeItem(t(K.sampling_sampledLabel), t(K.sampling_atLeast, v.samplesIn, v.samplesNeeded), false, Modifier.weight(1.2f)) { open = null }
            if (onPianoIndex != null) EdgeItem(t(K.edge_piano), piano ?: t(K.edge_noPiano), open == "piano", Modifier.weight(1.6f)) {
                open = if (pianoFields == null) { onPianoIndex(); null } else if (open == "piano") null else "piano"
            }
            onPickRecording?.let { pick -> ProbeButton(feeding = feeding, onPick = pick, onStop = onUseMicrophone) }
        }

        // ---- the steps, under the edge, as on the tuning screen (PlusSteps, 22 Nov 2026): A4 is one tap back from the sampling ----
        FlowStepStrip(
            current = if (settingA4) STEP_A4 else STEP_SAMPLING,
            back = { n -> n == STEP_A4 },
            onStep = { n -> if (n == STEP_A4) { open = null; controller.backToA4() } },
            modifier = Modifier.fillMaxWidth().padding(top = CORNER_BAND + 2.dp, start = 12.dp, end = 12.dp).height(FLOW_STEP_STRIP),
        )
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = CORNER_BAND + FLOW_STEP_STRIP + 6.dp, start = 12.dp, end = 12.dp, bottom = 6.dp)) {
            val keyboardH = maxHeight * FLOW_KEYBOARD_SHARE
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (v == null) A4Work(controller, a4, a4State, hubHz, cfg.matchCents, pictureBuilder)
                    else SamplingWork(controller, v)
                }
                Spacer(Modifier.height(6.dp))
                // the keyboard leads: the key offered next dotted (A4 itself before the session), sampled keys green
                SamplingKeyboard(
                    lowestMidi = cfg.lowestKeyMidi,
                    sampled = v?.let { it.sampled + (it.measured - TuningSession.MIDI_A4) }.orEmpty(),
                    short = v?.sampledShort.orEmpty(),
                    caught = v?.caught,
                    referenceSet = v != null,
                    temperament = low..high,
                    wanted = listOfNotNull(v?.spreadLow, v?.spreadHigh),
                    suggested = if (v == null) TuningSession.MIDI_A4 else v.suggested,
                    modifier = Modifier.fillMaxWidth().height(keyboardH),
                )
            }

            // ---- the strips: one at a time, a tap outside closing it ----
            if (open == "temperament" || (open == "piano" && pianoFields != null)) {
                Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = null })
            }
            if (open == "temperament") Strip(Modifier.align(Alignment.BottomCenter).padding(bottom = keyboardH)) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t(K.sampling_temperament), color = White, fontFamily = DejaVuSerif, fontSize = 15.sp, softWrap = false)
                        NoteStep(t(K.sampling_temperamentStart), low, low > TunerSettings.MIN_TEMPERAMENT_LOW, low < TunerSettings.MAX_TEMPERAMENT_LOW, onTemperamentLow)
                        NoteStep(t(K.sampling_temperamentEnd), high, high > TunerSettings.TEMPERAMENT_HIGH, high < TunerSettings.MAX_TEMPERAMENT_HIGH, onTemperamentHigh)
                    }
                    // the named temperaments (22 Nov 2026), all of them at once, one tap choosing (23 Nov 2026, Peter: "one tap on
                    // the temperament button should bring up a list of those temperaments available"); the chosen one orange
                    if (temperaments) NamedTemperament.entries.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { named ->
                                val chosen = named == cfg.temperament
                                Box(
                                    Modifier.weight(1f).heightIn(min = 48.dp)
                                        .border(1.dp, if (chosen) Orange else EdgeRule, RoundedCornerShape(6.dp))
                                        .clickable {
                                            onChangeSettings { it.copy(temperament = named) }
                                        }
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    FitText(temperamentName(named), 16.sp,
                                        color = if (chosen) Orange else White, align = TextAlign.Center)
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            // the piano's strip stands at the top, under the edge: the phone's keyboard comes up from the bottom for its fields
            if (open == "piano" && pianoFields != null) Strip(Modifier.align(Alignment.TopCenter)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pianoFields()
                }
            }
        }
        if (open == "stretch") TuningInfoDialog(cfg, onChangeSettings, onClose = { open = null }, onStep = null, suggestion = null, sampled = false)
    }
}

/** A4 before the session: the pitch with − and + and its slide, how far A4 is measured, one line, Done; the strobe right. */
@Composable
private fun A4Work(
    controller: TuningController, a4: Double, a4State: TuningController.A4Status, hubHz: Double?, matchCents: Double,
    pictureBuilder: TuningPicture.Builder,
) {
    val a4Now by rememberUpdatedState(a4)
    // one band: A4's first partial against the pitch chosen, by the hub's reader (BasicHub.hubFrame, 15 Nov 2026)
    val picture = pictureBuilder.build(TuningPicture.Frame(
        note = "A4", listened = 1, live = hubHz != null, listenedHz = hubHz, listenedTargetHz = a4,
        partials = hubHz?.let { mapOf(1 to TuningPicture.Frame.Partial(it, 1.0, null, null)) } ?: emptyMap(),
        targets = mapOf(1 to a4), falsePartials = emptySet(), dropped = emptySet(),
        matchCents = matchCents, nowMs = nowMs(), seats = listOf(1),
    ))
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(0.42f).fillMaxHeight().padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp).fillMaxWidth()) {
                HoldStepBox("−") { controller.setReference(a4Tenths(a4Now - 0.1)) }
                FitText(formatHz(a4), 30.sp, Modifier.weight(1f).padding(horizontal = 8.dp), color = White, align = TextAlign.Center)
                HoldStepBox("+") { controller.setReference(a4Tenths(a4Now + 0.1)) }
            }
            // the slide under the pitch, a tenth of a hertz every A4_SLIDE_DP (foss SamplingPage, 23 Oct 2026)
            Box(
                Modifier.fillMaxWidth().heightIn(min = 30.dp).background(Color(Brand.CONTROL), RoundedCornerShape(6.dp))
                    .pointerInput(Unit) {
                        val stepPx = A4_SLIDE_DP.dp.toPx()
                        var acc = 0f
                        detectHorizontalDragGestures(onDragStart = { acc = 0f }) { change, dx ->
                            change.consume(); acc += dx
                            val steps = (acc / stepPx).toInt()
                            if (steps != 0) { acc -= steps * stepPx; controller.setReference(a4Tenths(a4Now + steps * 0.1)) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Text("‹   ›", color = Muted, fontSize = 13.sp) }
            when (a4State.measured) {
                TuningController.Measured.MEASURING -> Text(t(K.sampling_measuring), color = Muted, fontSize = 14.sp)
                TuningController.Measured.MEASURED -> Text(t(K.sampling_measured), color = Color(Brand.GO_GREEN), fontSize = 14.sp)
                else -> Spacer(Modifier.height(18.dp))
            }
            // one line where four orders stood: what to do now
            // the line takes the height left above Done, whole: shared with a spacer it had half, and in German and French on
            // the narrow phones its words ran past it (layout check, 25 Nov 2026)
            TextBlock(listOf(t(K.sampling_a4_tune)), Modifier.fillMaxWidth().weight(1f), size = 15.sp, color = White)
            DoneButton(onClick = { controller.acceptLive() }, enabled = a4State.ready)
        }
        TuningPictureView(picture, matchCents, modifier = Modifier.weight(0.58f).fillMaxHeight(), follows = false, marks = false,
            twoPanels = false, hint = t(K.sampling_a4Strobe))
    }
}

/** The sampling: the key offered next, large, with one line; the key caught with Cancel and Accept; Done. No strobe (24 Oct 2026). */
@Composable
private fun SamplingWork(controller: TuningController, v: TuningController.TuningView) {
    Row(Modifier.fillMaxSize()) {
        // left: where to go next, the keyboard's dot named
        Column(Modifier.weight(0.5f).fillMaxHeight().padding(end = 16.dp)) {
            val next = v.suggested
            val ends = v.spreadLow != null || v.spreadHigh != null
            if (next != null) {
                Text(t(K.start_next), color = Muted, fontFamily = DejaVuSerif, fontSize = 13.sp, softWrap = false)
                FitText(noteName(next), 48.sp, Modifier.fillMaxWidth(), color = if (ends) Orange else White, minSize = 20.sp)
            } else Spacer(Modifier.height(8.dp))
            // one line where four orders stood (foss's last order, the one that is done at every key)
            TextBlock(listOf(t(K.sampling_order_accept)), Modifier.fillMaxWidth(), size = 15.sp, color = Muted)
        }
        // right: the key caught and the answers to it, the count's Done at the foot
        Column(Modifier.weight(0.5f).fillMaxHeight()) {
            val c = v.caught
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 52.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(t(K.sampling_caughtLabel), color = Muted, fontFamily = DejaVuSerif, fontSize = 13.sp, softWrap = false)
                    Text(
                        if (c != null) noteName(c) else t(K.sampling_strikeKey),
                        color = if (c == null) Muted else if (v.caughtAccepted) Color(Brand.GO_GREEN) else Orange,
                        fontFamily = DejaVuSerif, fontSize = if (c != null) 26.sp else 16.sp, softWrap = false,
                    )
                }
                if (c != null) { Chip(t(K.app_cancel), selected = false, enabled = true) { controller.cancelCaught() }; Spacer(Modifier.width(8.dp)) }
                AcceptButton(accepted = v.caughtAccepted, enabled = controller.acceptReady(), onClick = { controller.acceptLive() })
            }
            if (c != null) {
                val (word, colour) = when (v.caughtMeasured) {
                    TuningController.Measured.MEASURING -> t(K.sampling_measuring) to Muted
                    TuningController.Measured.MEASURED -> t(K.sampling_measured) to Color(Brand.GO_GREEN)
                    TuningController.Measured.SHORT -> t(K.sampling_short) to Color(Brand.MEASURED_SHORT)
                }
                Text(word, color = colour, fontSize = 14.sp)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                DoneButton(onClick = { controller.finishSampling() }, enabled = v.samplingReady)
            }
        }
    }
}

/** A strip over the work: one subject, its controls, a tap outside closing it (the blueprint's one overlay, section 4). */
@Composable
private fun Strip(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxWidth()
            .background(Color(0xF20B0B0B), RoundedCornerShape(8.dp)).border(1.dp, EdgeRule, RoundedCornerShape(8.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .padding(12.dp),
    ) { content() }
}

/** A note with − and +, as the temperament's start and end are set (foss SamplingPage's stepRow), each sign 48 dp to touch. */
@Composable
private fun NoteStep(label: String, note: Int, canDown: Boolean, canUp: Boolean, set: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontFamily = DejaVuSerif, fontSize = 14.sp, softWrap = false, modifier = Modifier.padding(end = 4.dp))
        Box(Modifier.size(48.dp).clickable(enabled = canDown) { set(note - 1) }, contentAlignment = Alignment.Center) {
            Text("−", color = if (canDown) White else Color(0xFF444444), fontSize = 22.sp)
        }
        Text(noteName(note), color = White, fontFamily = DejaVuSerif, fontSize = 20.sp)
        Box(Modifier.size(48.dp).clickable(enabled = canUp) { set(note + 1) }, contentAlignment = Alignment.Center) {
            Text("+", color = if (canUp) White else Color(0xFF444444), fontSize = 22.sp)
        }
    }
}

/** A pitch to the tenth of a hertz, within the reference's range (foss SamplingPage). */
internal fun a4Tenths(hz: Double): Double =
    (kotlin.math.round(hz * 10) / 10).coerceIn(TuningController.MIN_REFERENCE_HZ, TuningController.MAX_REFERENCE_HZ)

/** A step of the reference, held to repeat (foss SamplingPage.StepBox, 7 Oct 2026), 48 dp to touch. */
@Composable
internal fun HoldStepBox(label: String, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onClick)
    Box(
        Modifier.size(48.dp).background(Color(Brand.CONTROL), RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    step()
                    val running = scope.launch { delay(STEP_HOLD_MS); while (isActive) { step(); delay(STEP_REPEAT_MS) } }
                    tryAwaitRelease()
                    running.cancel()
                })
            },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = White, fontSize = 20.sp) }
}

internal const val A4_SLIDE_DP = 8
private const val STEP_HOLD_MS = 450L
private const val STEP_REPEAT_MS = 160L

/** A temperament's name in the tuner's language (the strings' temperament.<NAME>). */
internal fun temperamentName(temperament: NamedTemperament): String = t(temperamentKey(temperament))

/** The strings table's key of [temperament]'s name, "temperament.vallotti"; its Help line is "help.temp.vallotti". */
internal fun temperamentKey(temperament: NamedTemperament): String = when (temperament) {
    NamedTemperament.EQUAL -> K.temperament_equal
    NamedTemperament.WERCKMEISTER_III -> K.temperament_werckmeister3
    NamedTemperament.KIRNBERGER_III -> K.temperament_kirnberger3
    NamedTemperament.VALLOTTI -> K.temperament_vallotti
    NamedTemperament.YOUNG_II -> K.temperament_young2
    NamedTemperament.KELLNER -> K.temperament_kellner
    NamedTemperament.MEANTONE_QUARTER -> K.temperament_meantoneQuarter
}
