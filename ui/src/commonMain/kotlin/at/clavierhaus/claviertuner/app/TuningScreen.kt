package at.clavierhaus.claviertuner.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.claviertuner.Brand
import at.clavierhaus.claviertuner.TuningController
import at.clavierhaus.claviertuner.i18n.K
import at.clavierhaus.claviertuner.i18n.Strings
import at.clavierhaus.claviertuner.i18n.noteName
import at.clavierhaus.claviertuner.i18n.t
import at.clavierhaus.claviertuner.picture.TuningPicture
import at.clavierhaus.claviertuner.settings.StrobeMode
import at.clavierhaus.claviertuner.settings.TunerSettings
import at.clavierhaus.claviertuner.tuning.OctaveSuggestion
import at.clavierhaus.claviertuner.ui.CORNER_BAND
import at.clavierhaus.claviertuner.ui.CORNER_LEFT_WIDTH
import at.clavierhaus.claviertuner.ui.CORNER_RIGHT_WIDTH
import at.clavierhaus.claviertuner.ui.Corners
import at.clavierhaus.claviertuner.ui.FitText
import at.clavierhaus.claviertuner.tuning.TuningSession
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import at.clavierhaus.claviertuner.ui.OctaveBarsView
import at.clavierhaus.claviertuner.ui.PartialChips
import at.clavierhaus.claviertuner.ui.ProbeButton
import at.clavierhaus.claviertuner.ui.ProgressKeyboard
import at.clavierhaus.claviertuner.ui.StateWord
import at.clavierhaus.claviertuner.ui.TuningPictureView
import at.clavierhaus.claviertuner.ui.formatHz
import at.clavierhaus.claviertuner.ui.nowMs
import at.clavierhaus.claviertuner.ui.num

/*
 * The tuning screen of the restructured interface (Plus's of 21-23 Nov 2026, taken into the free app on Peter's word, 23 Nov
 * 2026, 16:34: "the interface restructuring is a winner. Please apply it to foss and Pro"). It puts nothing new on the
 * screen: every number is the engine's, drawn by the library's own parts (TuningPictureView, PartialChips, ProgressKeyboard,
 * StrobeChoice, OctaveBarsView), built into the frame exactly as BasicHub builds it (one place puts a number on the picture,
 * Amendment 3). What changes against BasicHub is where things stand:
 *
 *  - the edge: the info line becomes items, each a label over its value -- A4, the octave types, the strobe, and an
 *    edition's piano. A4 opens A4's window in the left panel, Octaves the octave types in the work's place, Strobe one strip
 *    over the work's foot with the strobe's three choices;
 *  - under the edge, the three steps (A4, Sampling, Tuning), an earlier one a tap back while the tuning allows it;
 *  - the keyboard stands at the foot in every state, the keys tuned green: the Progress button and its view go.
 */

/** The keyboard's share of the height, as on the sampling page (foss SamplingPage.KEYBOARD_SHARE, 0.28). */
internal const val FLOW_KEYBOARD_SHARE = 0.28f
/** The strip's height over the work's foot: three lines of the strobe's choices with their words. */
private val STRIP_HEIGHT = 150.dp

private val White = Color(Brand.WHITE)
private val Muted = Color(Brand.WHITE_MUTED)
private val Orange = Color(Brand.ORANGE)
internal val EdgeFill = Color(0xFF141414)
internal val EdgeRule = Color(0xFF3A3A3A)

/** Which partial bands the strobe shows: foss's rule (BasicHub.strobeSeats, internal there), the same lines. */
private fun flowSeats(offered: Int, tuned: Int, hidden: Set<Int>, count: Int, oneBand: Boolean): List<Int> {
    if (oneBand) return listOf(tuned)
    val all = ((1..offered).toSet() - hidden + tuned).sorted()
    if (count <= 0 || all.size <= count) return all
    val lowest = all.take(count)
    return if (tuned in lowest) lowest else (lowest.dropLast(1) + tuned).sorted()
}

@Composable
fun TuningScreen(
    controller: TuningController,
    onSettings: () -> Unit,
    onHome: () -> Unit,
    /** An edition's piano, named on the edge (null: none chosen); the item shows only with [onPiano]. */
    piano: String? = null,
    /** An edition's pianos; null in the free app, which keeps no pianos, and the edge has no Piano. */
    onPiano: (() -> Unit)? = null,
    /** The strobe's pace as drawn, from the engine's: an edition's own slowing (Plus, 21 Nov 2026); the engine's as it is. */
    paceOf: (Double) -> Double = { it },
    onChangeSettings: ((TunerSettings) -> TunerSettings) -> Unit,
    onAutoNote: (Boolean) -> Unit,
    recordButton: (@Composable () -> Unit)? = null,
    feeding: String? = null,
    onPickRecording: (() -> Unit)? = null,
    onUseMicrophone: () -> Unit = {},
    leftPanel: (@Composable () -> Unit)? = null,
    afterNote: (@Composable () -> Unit)? = null,
    /** The octave types, drawn in the work's place while the edge's Octaves is open; [onOctavesStep] false closes them (Done). */
    octavesStep: Boolean = false,
    onOctavesStep: (Boolean) -> Unit = {},
    /** Keys an edition marks: a red dot on each, on the keyboard at the foot and on the note's keyboard. None in the free app. */
    marked: Set<Int> = emptySet(),
    /** What an edition's marks mean, over the note's keyboard while there are any. */
    markedLine: String? = null,
    /** Told when A4's window opens (true) and closes (false), for an edition that judges the key on screen: A4's window is not the key. */
    onA4Window: (Boolean) -> Unit = {},
) {
    val partials by controller.livePartials.collectAsState()
    val audible by controller.liveAudible.collectAsState()
    val a4 by controller.referenceA4Hz.collectAsState()
    val tuning by controller.tuning.collectAsState()
    val shownTuning by controller.shownPartials.collectAsState()
    val hiddenTuning by controller.hiddenPartials.collectAsState()
    val targets by controller.targets.collectAsState()
    val cfg by controller.settings.collectAsState()
    val liveTarget by controller.targetHz.collectAsState()
    val reading by controller.reading.collectAsState()
    val octave by controller.octave.collectAsState()
    @Suppress("UNUSED_VARIABLE") val language by Strings.language.collectAsState()  // read so the screen recomposes on a change
    val pictureBuilder = remember { TuningPicture.Builder() }
    val t = tuning ?: return

    /** Which edge item is open: "strobe" a strip; null none. The octave types are a step of their own (22 Nov 2026). */
    var open by remember { mutableStateOf<String?>(null) }
    var pickingNote by remember { mutableStateOf(false) }
    /**
     * A4's window (Peter, 22 Nov 2026: "Tapping on that should simply open the initial A4 window to either tune A4 correctly
     * or change the pitch to what A4 actually is. And this window should have an X to close it"): the screen goes to A4, its
     * strobe as always, and the left panel holds the pitch with − and + and its slide; X goes back to the key and the auto
     * note switching as they were.
     */
    var a4Open by remember { mutableStateOf(false) }
    var a4Back by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    fun openA4() {
        if (a4Open) return
        a4Back = t.midi to cfg.autoNote
        onOctavesStep(false); open = null; a4Open = true; onA4Window(true)
        onAutoNote(false); controller.selectNote(TuningSession.MIDI_A4)
    }
    fun closeA4() {
        a4Open = false; onA4Window(false)
        a4Back?.let { (midi, auto) -> controller.selectNote(midi); onAutoNote(auto) }
        a4Back = null
    }
    // the screen left with A4's window open (the unison mode, home, settings): the key and the auto note switching as they
    // were, as X gives them (22 Nov 2026, 19:21: "All of a sudden auto detection is completely broken!" -- the switch was
    // saved off whenever the window was left another way)
    val a4BackNow = androidx.compose.runtime.rememberUpdatedState(a4Back)
    val onA4WindowNow = androidx.compose.runtime.rememberUpdatedState(onA4Window)
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            a4BackNow.value?.let { (midi, auto) -> controller.selectNote(midi); onAutoNote(auto) }
            onA4WindowNow.value(false)
        }
    }

    val read = reading; val l = t.listening

    val picture = pictureBuilder.build(TuningPicture.Frame(
        note = noteName(t.midi), listened = read?.k ?: l.k, live = read?.live == true,
        listenedHz = read?.hz, listenedTargetHz = read?.targetHz ?: l.targetHz,
        partials = partials.associate { it.k to TuningPicture.Frame.Partial(it.hz, it.level, it.widthCents, it.overNoiseDb) },
        targets = targets.associate { it.k to it.hz },
        falsePartials = read?.falsePartials?.toSet() ?: emptySet(), dropped = emptySet(),
        matchCents = cfg.matchCents, nowMs = nowMs(),
        seats = flowSeats(controller.highestPartial(liveTarget), read?.k ?: l.k, hiddenTuning,
            if (cfg.strobeMode == StrobeMode.LIMIT) cfg.strobeCount else 0, cfg.strobeOne),
        pace = paceOf(t.pace),
    ))
    val follows = cfg.autoNote && (!cfg.temperamentFirst || t.temperamentComplete)
    val live = read?.live == true

    Box(Modifier.fillMaxSize().background(Color(Brand.BLACK)).safeDrawingPadding()) {
        Corners(onSettings = onSettings, onHome = onHome)
        // ---- the edge, between the corners ----
        Row(
            Modifier.fillMaxWidth().height(CORNER_BAND).padding(start = CORNER_LEFT_WIDTH, end = 8.dp).padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            EdgeItem("A4", formatHz(a4), a4Open, Modifier.weight(1f)) { if (a4Open) closeA4() else openA4() }
            // the temperament left the tuning screen's edge (Peter, 21 Nov 2026: it is set before the tuning); its room is the octaves'
            EdgeItem(t(K.edge_octaves), octaveLine(cfg), octavesStep, Modifier.weight(2.7f)) { open = null; onOctavesStep(!octavesStep) }
            EdgeItem(t(K.edge_strobe), strobeWord(cfg), open == "strobe", Modifier.weight(1.2f)) { open = if (open == "strobe") null else "strobe" }
            if (onPiano != null) EdgeItem(t(K.edge_piano), piano ?: t(K.edge_noPiano), false, Modifier.weight(1.6f)) { open = null; onPiano() }
            if (t.complete && !live) StateWord(t(K.state_complete), Color(Brand.GO_GREEN))
            recordButton?.let { Spacer(Modifier.width(6.dp)); it() }
            onPickRecording?.let { pick -> ProbeButton(feeding = feeding, onPick = pick, onStop = onUseMicrophone) }
            // the home corner keeps its room at the right
            Spacer(Modifier.width(52.dp))
        }

        // ---- the work and the keyboard under it ----
        // ---- the steps, under the edge: the same four on every step of the tuning (22 Nov 2026) ----
        FlowStepStrip(
            current = STEP_TUNING,
            back = { t.canStepBack },
            onStep = { n ->
                open = null
                when (n) {
                    STEP_A4 -> { onOctavesStep(false); controller.backToA4() }
                    STEP_SAMPLING -> { onOctavesStep(false); controller.backToSampling() }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = CORNER_BAND + 2.dp, start = 12.dp, end = 12.dp).height(FLOW_STEP_STRIP),
        )
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = CORNER_BAND + FLOW_STEP_STRIP + 6.dp, start = 12.dp, end = 12.dp, bottom = 6.dp)) {
            val keyboardH = maxHeight * FLOW_KEYBOARD_SHARE
            Column(Modifier.fillMaxSize()) {
                if (octavesStep) {
                    // the sampling's suggestion from the sampled strings only (Rule 9), as the stretch window computed it
                    val sampledDone = !t.sampling
                    val suggestion = remember(sampledDone, cfg.temperamentLowMidi) {
                        if (!sampledDone) null else controller.snapshot()?.let { snap ->
                            val keys = snap.basis?.keys
                            OctaveSuggestion.bass(snap.measurements.filter { keys == null || it.midi in keys }, cfg.temperamentLowMidi)
                        }
                    }
                    OctavesWork(cfg, onChangeSettings, suggestion, onStart = { onOctavesStep(false) }, modifier = Modifier.weight(1f).fillMaxWidth())
                } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    TuningPictureView(
                        picture, matchCents = cfg.matchCents, modifier = Modifier.fillMaxSize(), follows = follows,
                        left = {
                            if (a4Open) A4Panel(controller, a4, onClose = { closeA4() })
                            else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // what the key is tuned to, with its width, and the partner as measured against the curve:
                                // the check, never the source (Law 5); gone with BasicHub's info line on 23 Nov, back on
                                // Peter's word (25 Nov 2026: "Fix partner check and display offset")
                                at.clavierhaus.claviertuner.ui.TextBlock(targetLines(l, a4, cfg), Modifier.fillMaxWidth(),
                                    size = 14.sp, minSize = 10.sp, color = Muted)
                                leftPanel?.let { panel -> Box(Modifier.weight(1f).fillMaxWidth()) { panel() } }
                            }
                        },
                        together = cfg.strobeTogether, afterNote = afterNote,
                        strobe = octave?.takeIf { it.midi == t.midi }?.let { o -> @Composable { OctaveBarsView(o) } },
                        marks = false,
                        // a tap opens the keyboard, whatever the switch says (22 Nov 2026); the switch is in it
                        onNoteTap = { pickingNote = true },
                    )
                }
                PartialChips(
                    a4Hz = a4, baseHz = liveTarget, count = controller.highestPartial(liveTarget),
                    shown = if (cfg.strobeOne) read?.usedPartials?.toSet()?.takeIf { it.isNotEmpty() } ?: setOf(l.k) else shownTuning,
                    falseMarked = emptySet(),
                    tappable = (targets.map { it.k }.toSet() + audible) - l.k,
                    onTap = { k -> controller.tapPartial(k) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                }
                Box(Modifier.fillMaxWidth().height(keyboardH)) {
                    ProgressKeyboard(
                        done = t.tuned, current = t.midi, deviations = t.deviations, curve = t.curve, lowestMidi = t.lowestMidi,
                        marked = marked, modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            // ---- the strip: one, over the work's foot, a tap outside closing it ----
            if (open == "strobe") {
                Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = null })
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = keyboardH).fillMaxWidth().height(STRIP_HEIGHT)
                        .background(Color(0xF20B0B0B), RoundedCornerShape(8.dp)).border(1.dp, EdgeRule, RoundedCornerShape(8.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                        .padding(10.dp),
                ) {
                    StrobeChoice(cfg, onChangeSettings, Modifier.fillMaxSize(), pinPace = t.pace < 1.0)
                }
            }
        }

        if (pickingNote) FlowNotePicker(
            lowMidi = t.stepLowMidi, highMidi = t.stepHighMidi, current = t.midi, autoNote = cfg.autoNote,
            marked = marked, markedLine = markedLine.takeIf { marked.isNotEmpty() },
            onPick = { midi -> controller.selectNote(midi); onAutoNote(false); pickingNote = false },
            onAutoNote = { on -> onAutoNote(on); if (on) pickingNote = false },
            onDismiss = { pickingNote = false },
        )
    }
}

/** One item of the edge: a small label over its value, a box of its own, 48 dp to touch; outlined orange while open. */
@Composable
internal fun EdgeItem(label: String, value: String, open: Boolean, modifier: Modifier, onTap: () -> Unit) {
    Column(
        modifier.fillMaxHeight()
            .background(EdgeFill, RoundedCornerShape(6.dp))
            .border(if (open) 2.dp else 1.dp, if (open) Orange else EdgeRule, RoundedCornerShape(6.dp))
            .clickable(onClick = onTap).padding(horizontal = 6.dp, vertical = 1.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        FitText(label, 10.sp, Modifier.fillMaxWidth(), color = Muted, minSize = 8.sp)
        FitText(value, 15.sp, Modifier.fillMaxWidth(), color = White, minSize = 9.sp)
    }
}

/** The octave types low to high, bass to treble, the six places' among them: as the info line said them. */
internal fun octaveLine(s: TunerSettings): String =
    (listOf(s.octaveBass) + s.stretchPoints.filter { it.midi < s.temperamentLowMidi }.sortedBy { it.midi }.map { it.type } + s.octaveMiddle +
        s.stretchPoints.filter { it.midi > s.temperamentHighMidi }.sortedBy { it.midi }.map { it.type } + s.octaveTreble).joinToString(" | ") { it.label }

/**
 * What the key on screen is tuned to, in the engine's own terms ([TuningSession.Listening]): the reference, the temperament
 * on A4, or the octave type to its partner with the width; and, once the partner is measured, how far it stands from where
 * the curve puts it ([TuningSession.Listening.checkCents]: the check, never the source, Law 5). The words are those of
 * BasicHub's info line, without A4, which the edge shows. Nothing is computed here: every figure is the engine's.
 */
internal fun targetLines(l: TuningSession.Listening, a4: Double, cfg: TunerSettings): List<String> {
    fun width(c: Double) = when {
        c > 0.0 -> t(K.origin_wide, num(c, 1))
        c < 0.0 -> t(K.origin_narrow, num(-c, 1))
        else -> t(K.origin_beatless)
    }
    val source = when (l.source) {
        TuningSession.Source.REFERENCE -> t(K.origin_reference)
        // an edition's named temperament by its name; the free app is equal temperament only (23 Nov 2026)
        TuningSession.Source.TEMPERAMENT -> if (cfg.temperament == at.clavierhaus.claviertuner.tuning.NamedTemperament.EQUAL)
            t(K.origin_temperament, formatHz(a4)) else t(K.origin_named, temperamentName(cfg.temperament), formatHz(a4))
        TuningSession.Source.OCTAVE -> t(K.origin_octave, l.type!!.label, noteName(l.refMidi!!)) + width(l.widthCents)
        TuningSession.Source.PARTNER_UNTUNED -> t(K.origin_untuned, noteName(l.refMidi!!))
        TuningSession.Source.CURVE -> t(K.origin_curve, l.type!!.label, noteName(l.refMidi!!)) + width(l.widthCents)
    }
    // its own line under the source: the table's words begin with the info line's separator, which a line of its own drops
    val check = l.checkCents?.let { c ->
        t(K.origin_check, noteName(l.refMidi!!), num(kotlin.math.abs(c), 1), t(if (c >= 0.0) K.origin_wideWord else K.origin_narrowWord))
            .trimStart(' ', '·')
    }
    return listOfNotNull(source, check)
}

/** The strobe's mode in the words of its three choices. */
private fun strobeWord(s: TunerSettings): String = when (s.strobeMode) {
    StrobeMode.ONE -> t(K.tuning_strobeOne)
    StrobeMode.LIMIT -> t(K.tuning_strobeLimit) + " " + s.strobeCount
    StrobeMode.ALL -> t(K.tuning_strobeAll)
}

/**
 * A4's window, in the tuning screen's left panel: the pitch A4 is tuned to, with − and + a tenth of a hertz at a time (held to
 * run on) and the slide under them, as on the A4 page; the strobe beside it is the engine's reading of A4 as on any key. It
 * measures nothing: the pitch is the engine's reference ([TuningController.setReference]); a change moves every target,
 * the keys tuned with them, which the line says.
 */
@Composable
private fun A4Panel(controller: TuningController, a4: Double, onClose: () -> Unit) {
    val a4Now by androidx.compose.runtime.rememberUpdatedState(a4)
    Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            FitText("A4", 18.sp, Modifier.weight(1f), color = White, minSize = 12.sp)
            Box(Modifier.width(48.dp).height(48.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Text("\u2715", color = White, fontSize = 22.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp).fillMaxWidth()) {
            HoldStepBox("−") { controller.setReference(a4Tenths(a4Now - 0.1)) }
            FitText(formatHz(a4), 26.sp, Modifier.weight(1f).padding(horizontal = 8.dp), color = White, minSize = 12.sp,
                align = androidx.compose.ui.text.style.TextAlign.Center)
            HoldStepBox("+") { controller.setReference(a4Tenths(a4Now + 0.1)) }
        }
        Box(
            Modifier.fillMaxWidth().height(30.dp).background(Color(Brand.CONTROL), RoundedCornerShape(6.dp))
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
        ) { androidx.compose.material3.Text("‹   ›", color = Muted, fontSize = 13.sp) }
        at.clavierhaus.claviertuner.ui.TextBlock(listOf(t(K.a4_window)), Modifier.fillMaxWidth(), size = 13.sp, minSize = 10.sp, color = Muted)
    }
}

/**
 * The keyboard behind the note's name (NotePicker's keys, 22 Nov 2026), with the switch Auto note switching over them, the
 * same switch as in the settings. A tap on a key goes there and turns the switch off; the switch turned on closes the
 * keyboard and the screen switches to each note struck again.
 */
@Composable
private fun FlowNotePicker(
    lowMidi: Int, highMidi: Int, current: Int, autoNote: Boolean,
    /** An edition's marked keys, a red dot on each, and what they mean over the keys (TuningScreen.marked). */
    marked: Set<Int> = emptySet(), markedLine: String? = null,
    onPick: (Int) -> Unit, onAutoNote: (Boolean) -> Unit, onDismiss: () -> Unit,
) {
    val lowOctave = lowMidi / 12 - 1
    val highOctave = highMidi / 12 - 1
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize(0.92f).background(Color(Brand.NEAR_BLACK), RoundedCornerShape(10.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            at.clavierhaus.claviertuner.ui.SwitchRow(t(K.settings_followKey), autoNote) { on -> onAutoNote(on) }
            if (markedLine != null) FitText(markedLine, 15.sp, color = Muted)
            for (octave in lowOctave..highOctave) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (pc in 0 until 12) {
                        val midi = (octave + 1) * 12 + pc
                        val inRange = midi in lowMidi..highMidi
                        val isCurrent = midi == current
                        val black = pc in setOf(1, 3, 6, 8, 10)
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .background(
                                    when {
                                        !inRange -> Color.Transparent
                                        isCurrent -> Color(Brand.ORANGE)
                                        black -> Color(Brand.KEY_BLACK)
                                        else -> Color(Brand.KEY_WHITE)
                                    },
                                    RoundedCornerShape(6.dp),
                                )
                                .then(if (isCurrent) Modifier.border(2.dp, Color(Brand.WHITE), RoundedCornerShape(6.dp)) else Modifier)
                                .clickable(enabled = inRange, onClick = { onPick(midi) }),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (inRange && midi in marked) Box(
                                Modifier.align(Alignment.TopEnd).padding(5.dp).size(10.dp)
                                    .background(Color(Brand.ALERT_RED), androidx.compose.foundation.shape.CircleShape),
                            )
                            if (inRange) FitText(
                                noteName(midi), 16.sp,
                                color = Color(if (black && !isCurrent) Brand.WHITE else Brand.BLACK),
                                fontWeight = if (isCurrent) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                                align = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

