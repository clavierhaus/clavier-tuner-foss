package at.clavierhaus.unisonmaster.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.tuning.Notes
import at.clavierhaus.unisonmaster.tuning.PartialSelection
import at.clavierhaus.unisonmaster.tuning.TuningSession
import at.clavierhaus.unisonmaster.ui.BackArrow
import at.clavierhaus.unisonmaster.ui.BeatBand
import at.clavierhaus.unisonmaster.ui.ClavierhausTitle
import at.clavierhaus.unisonmaster.ui.DejaVuSerif
import at.clavierhaus.unisonmaster.ui.DoneButton
import at.clavierhaus.unisonmaster.ui.HubHint
import at.clavierhaus.unisonmaster.ui.MeasuredBlock
import at.clavierhaus.unisonmaster.ui.NoteBlock
import at.clavierhaus.unisonmaster.ui.NoteStepper
import at.clavierhaus.unisonmaster.ui.PartialRow
import at.clavierhaus.unisonmaster.ui.PartialsButton
import at.clavierhaus.unisonmaster.ui.ProgressButton
import at.clavierhaus.unisonmaster.ui.ProgressKeyboard
import at.clavierhaus.unisonmaster.ui.ReadoutColumn
import at.clavierhaus.unisonmaster.ui.RecordButton
import at.clavierhaus.unisonmaster.ui.SettingsGear
import at.clavierhaus.unisonmaster.ui.SpectrumToggle
import at.clavierhaus.unisonmaster.ui.StateWord
import at.clavierhaus.unisonmaster.ui.TargetScale
import at.clavierhaus.unisonmaster.ui.ToneGraph
import at.clavierhaus.unisonmaster.ui.TuningGraph
import at.clavierhaus.unisonmaster.ui.formatHz
import java.util.Locale
import kotlin.math.round

/**
 * The one main screen. Before A4 is set it is the hub (define A4 on one
 * string). After Done it is the tuning screen of docs/SCREEN.md: one note,
 * its target and where the target comes from, the measured fundamental,
 * the beat as motion, a scale magnified at the match window, one state
 * word — and Done. "Partials" opens Full Spectrum (the bells, the readout
 * column and the partial row); "Progress" the keyboard. The gear is the
 * settings screen, and back. Top right, beside the state word, the red
 * recording button when recording is switched on in the settings.
 */
@Composable
fun BasicHub(
    controller: TuningController,
    recorder: SessionRecorder?,
    onSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val hz by controller.liveHz.collectAsState()
    val level by controller.liveLevel.collectAsState()
    val partials by controller.livePartials.collectAsState()
    val audible by controller.liveAudible.collectAsState()
    val full by controller.fullSpectrum.collectAsState()
    var showProgress by remember { mutableStateOf(false) }
    val hidden by controller.hiddenPartials.collectAsState()
    val a4 by controller.referenceA4Hz.collectAsState()
    val tuning by controller.tuning.collectAsState()
    val shownTuning by controller.shownPartials.collectAsState()
    val suggested by controller.suggested.collectAsState()
    val active by controller.activePartial.collectAsState()
    val targets by controller.targets.collectAsState()
    val cfg by controller.settings.collectAsState()
    val liveTarget by controller.targetHz.collectAsState()
    val heard by controller.heardMidi.collectAsState()

    val t = tuning
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(Brand.BLACK))
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (t == null) {
            // ---- the hub: define A4 on one string ----
            val shown = PartialSelection.shown(full, audible, hidden)
            ToneGraph(
                hz = hz,
                centreHz = hz ?: a4,
                level = level.toFloat(),
                fullSpectrum = full,
                partials = partials,
                shown = shown,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
            )
            Column(Modifier.align(Alignment.TopStart).width(300.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BackArrow(onClick = onBack)
                    SettingsGear(onClick = onSettings)
                }
                Spacer(Modifier.height(10.dp))
                ClavierhausTitle()
                Spacer(Modifier.height(18.dp))
                HubHint("Define your A4 here by tuning a single string to the desired pitch.", maxLines = 3)
                Spacer(Modifier.height(18.dp))
                SpectrumToggle(fullSpectrum = full, onClick = { controller.toggleFullSpectrum() })
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
            ) {
                PartialRow(
                    a4Hz = a4,
                    shown = shown,
                    tappable = if (full) audible else emptySet(),
                    onTap = { k -> controller.tapPartial(k) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                DoneButton(onClick = { controller.acceptLive() }, enabled = hz != null)
            }
            return@Box
        }

        // ---- the tuning screen ----
        val name = Notes.name(t.midi)
        val f = hz                                  // a local: delegated state cannot be smart-cast
        val sounding = level > 0.0
        val matched = TuningSession.matched(f, liveTarget, cfg.matchHz)
        val link = t.link
        val curve = t.curve
        val origin = when {
            t.midi == TuningSession.MIDI_A4 -> "the reference, set on the hub"
            link != null && link.calculated -> "${link.type.label} octave against ${Notes.name(link.refMidi)}, calculated stretch"
            link != null -> "${link.type.label} octave against ${Notes.name(link.refMidi)}, measured"
            t.midi < cfg.temperamentLowMidi ->
                "equal temperament — ${Notes.name(t.midi + cfg.octaveTypeFor(t.midi).semitones)} not tuned yet"
            t.midi > TunerSettings.TEMPERAMENT_HIGH ->
                "equal temperament — ${Notes.name(t.midi - cfg.octaveTypeFor(t.midi).semitones)} not tuned yet"
            else -> "equal temperament on A4 ${formatHz(a4)}"
        }
        val heardElsewhere = heard?.takeIf { it != t.midi && sounding }
        val state: Pair<String, Color> = when {
            t.calibrating && heardElsewhere != null && (heardElsewhere < t.stepLowMidi || heardElsewhere > t.stepHighMidi) ->
                "${Notes.name(heardElsewhere)}: outside ${Notes.name(t.stepLowMidi)}–${Notes.name(t.stepHighMidi)}" to Color(Brand.ORANGE)
            t.calibrating && heardElsewhere != null -> "heard ${Notes.name(heardElsewhere)}" to Color(Brand.WHITE_MUTED)
            t.calibrating && sounding && f != null -> "sampling" to Color(Brand.ORANGE)
            t.calibrating -> "listening" to Color(Brand.WHITE_MUTED)
            t.complete -> "complete" to Color(Brand.GO_GREEN)
            heardElsewhere != null && !(cfg.autoNote && t.temperamentComplete) ->
                "that's ${Notes.name(heardElsewhere)}" to Color(Brand.ORANGE)
            !sounding || f == null -> "listening" to Color(Brand.WHITE_MUTED)
            matched -> "matches" to Color(Brand.GO_GREEN)
            f > liveTarget -> "sharp" to Color(Brand.ORANGE)
            else -> "flat" to Color(Brand.ORANGE)
        }
        val status = buildString {
            append(if (cfg.autoNote && t.temperamentComplete) "follows the key" else "arrows")
            append(" · ")
            if (cfg.temperamentFirst) {
                append(if (t.temperamentComplete) "A3–A4 done" else "A3–A4 first")
            } else if (t.calibrating) {
                append("Stretch Definition: ${t.anchors} of ${t.anchorsWanted} single strings")
            } else {
                // Pro: the sampling report of docs/INHARMONICITY.md
                append(
                    when {
                        curve.anchors == 0 -> "no anchors yet"
                        curve.worstCents == null -> "${curve.anchors} anchors, need 3 across two octaves"
                        curve.representative -> String.format(Locale.ROOT, "curve representative: %d anchors, ±%.1f c", curve.anchors, curve.worstCents)
                        else -> String.format(Locale.ROOT, "curve: %d anchors, worst ±%.1f c", curve.anchors, curve.worstCents)
                    },
                )
            }
        }

        // header: back and gear only; the title lives on the hub
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.TopStart)) {
            BackArrow(onClick = onBack)
            SettingsGear(onClick = onSettings)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp)) {
            StateWord(state.first, state.second)
            if (recorder != null) {
                val recording by recorder.recording.collectAsState()
                val seconds by recorder.seconds.collectAsState()
                Spacer(Modifier.width(18.dp))
                RecordButton(recording = recording, seconds = seconds, onToggle = {
                    if (recording) recorder.stop() else recorder.start()
                })
            }
        }

        when {
            showProgress -> {
                ProgressKeyboard(
                    done = t.measured,
                    current = t.midi,
                    deviations = t.deviations,
                    lowestMidi = t.lowestMidi,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 40.dp)
                        .width(640.dp)
                        .height(216.dp),
                )
            }
            full -> {
                // Full Spectrum: the bells, the readout column and the row
                TuningGraph(
                    liveHz = hz,
                    sounding = sounding,
                    targetHz = liveTarget,
                    shown = shownTuning,
                    predicted = targets,
                    livePartials = partials,
                    active = active,
                    matchHz = cfg.matchHz,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 56.dp, bottom = 80.dp),
                )
                Column(Modifier.align(Alignment.TopStart).padding(top = 60.dp)) {
                    Text(name, color = Color(Brand.WHITE), fontFamily = DejaVuSerif, fontSize = 34.sp, maxLines = 1)
                    Text("target " + String.format(Locale.ROOT, "%.2f Hz", liveTarget), color = Color(Brand.TARGET_BLUE), fontFamily = DejaVuSerif, fontSize = 15.sp, maxLines = 1)
                    Text(origin, color = Color(Brand.WHITE_MUTED), fontFamily = DejaVuSerif, fontSize = 13.sp, maxLines = 1)
                }
                ReadoutColumn(
                    shown = shownTuning,
                    active = active,
                    targetHz = liveTarget,
                    liveHz = hz,
                    predicted = targets,
                    livePartials = partials,
                    a4Hz = a4,
                    matchHz = cfg.matchHz,
                    onSelect = { k -> controller.activatePartial(k) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 52.dp),
                )
            }
            t.calibrating -> {
                // Pro, Stretch Definition: single strings across the range, no target yet
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxSize()
                        .padding(top = 52.dp, bottom = 84.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column {
                            Text("Stretch Definition", color = Color(Brand.ORANGE), fontFamily = DejaVuSerif, fontSize = 34.sp, maxLines = 1)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Play single strings across the whole range — the lowest plain wire, every major third up to C5, and either side of each break or strut. Leaving a note keeps it.",
                                color = Color(Brand.WHITE_MUTED), fontFamily = DejaVuSerif, fontSize = 15.sp, maxLines = 3,
                                modifier = Modifier.width(520.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        MeasuredBlock(f)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text(name, color = Color(Brand.WHITE), fontFamily = DejaVuSerif, fontSize = 64.sp, maxLines = 1)
                        Spacer(Modifier.width(28.dp))
                        Text(
                            "${t.anchors} of ${t.anchorsWanted}",
                            color = Color(Brand.WHITE), fontFamily = DejaVuSerif, fontSize = 64.sp, maxLines = 1,
                        )
                        Spacer(Modifier.width(18.dp))
                        Text(
                            if (curve.worstCents != null) String.format(Locale.ROOT, "anchors · curve ±%.1f c", curve.worstCents) else "anchors",
                            color = Color(Brand.WHITE_MUTED), fontFamily = DejaVuSerif, fontSize = 18.sp, maxLines = 1,
                            modifier = Modifier.padding(bottom = 14.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            else -> {
                // Fundamental: one note, one motion, one colour
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxSize()
                        .padding(top = 52.dp, bottom = 84.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        NoteBlock(
                            name = name,
                            targetHz = liveTarget,
                            origin = origin,
                            onSemitone = { d -> controller.stepNote(d) },
                            onOctave = { d -> controller.selectNote(t.midi + 12 * d) },
                        )
                        Spacer(Modifier.weight(1f))
                        MeasuredBlock(hz)
                    }
                    Spacer(Modifier.weight(1f))
                    BeatBand(
                        measuredHz = hz,
                        targetHz = liveTarget,
                        sounding = sounding,
                        matched = matched,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    TargetScale(
                        measuredHz = hz,
                        targetHz = liveTarget,
                        matchHz = cfg.matchHz,
                        sounding = sounding,
                        matched = matched,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                    )
                }
            }
        }

        // the bottom row: Progress · arrows · Partials · status · Done
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(),
        ) {
            ProgressButton(showingProgress = showProgress, onClick = { showProgress = !showProgress })
            Spacer(Modifier.width(10.dp))
            NoteStepper(
                name = name,
                canDown = t.midi > t.stepLowMidi,
                canUp = t.midi < t.stepHighMidi,
                onDown = { controller.stepNote(-1) },
                onUp = { controller.stepNote(+1) },
            )
            Spacer(Modifier.width(10.dp))
            if (full && !showProgress) {
                PartialRow(
                    a4Hz = a4,
                    baseHz = liveTarget,
                    count = controller.highestPartial(liveTarget),
                    shown = shownTuning,
                    tappable = (targets.map { it.k }.toSet() + audible) - 1,
                    onTap = { k -> controller.tapPartial(k) },
                    pulse = if (matched) suggested else null,
                    modifier = Modifier.weight(1f),
                )
            } else {
                PartialsButton(fullSpectrum = full, onClick = { controller.toggleFullSpectrum() })
                Spacer(Modifier.width(14.dp))
                Text(status, color = Color(Brand.WHITE_MUTED), fontFamily = DejaVuSerif, fontSize = 13.sp, maxLines = 1)
                Spacer(Modifier.weight(1f))
            }
            if (full && !showProgress) {
                Spacer(Modifier.width(10.dp))
                PartialsButton(fullSpectrum = full, onClick = { controller.toggleFullSpectrum() })
            }
            Spacer(Modifier.width(10.dp))
            DoneButton(onClick = { controller.acceptLive() }, enabled = matched)
        }
    }
}
