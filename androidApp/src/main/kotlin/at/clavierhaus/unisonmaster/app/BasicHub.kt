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
import at.clavierhaus.unisonmaster.i18n.K
import at.clavierhaus.unisonmaster.i18n.Strings
import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.TuningController
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
import kotlin.math.abs

/**
 * The one main screen. Before A4 is set it is the hub (define A4 on one
 * string). After Done it is the tuning screen of docs/SCREEN.md: one note,
 * the partial listened to and its target, where the target comes from
 * (docs/ENGINE.md), the partial as read, the beat as motion, a scale
 * magnified at the match window, one state word — and Done. "Partials" opens Full Spectrum (the bells, the readout
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
    val reading by controller.reading.collectAsState()
    val language by Strings.language.collectAsState()          // read so the screen recomposes on a change
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
        // What is tuned by: the note's listened partial (or one the tuner
        // tapped) against its target, read by phase (docs/ENGINE.md)
        val read = reading
        val l = t.listening
        val shownHz = read?.hz
        val shownTarget = read?.targetHz ?: l.targetHz
        val live = read?.live == true
        val matched = live && TuningSession.matched(shownHz, shownTarget, cfg.matchHz)
        val origin = when (l.source) {
            TuningSession.Source.REFERENCE -> t(K.origin_reference)
            TuningSession.Source.TEMPERAMENT -> t(K.origin_temperament, formatHz(a4))
            TuningSession.Source.OCTAVE -> t(K.origin_octave, l.type!!.label, Notes.name(l.refMidi!!)) +
                (if (l.widthCents > 0.0) t(K.origin_wide, String.format(Locale.ROOT, "%.1f", l.widthCents)) else t(K.origin_beatless))
            TuningSession.Source.PARTNER_UNTUNED -> t(K.origin_untuned, Notes.name(l.refMidi!!))
            TuningSession.Source.CURVE -> t(K.origin_curve, l.type!!.label, Notes.name(l.refMidi!!)) +
                (if (l.widthCents > 0.0) t(K.origin_wide, String.format(Locale.ROOT, "%.1f", l.widthCents)) else t(K.origin_beatless)) +
                (l.checkCents?.let { t(K.origin_check, Notes.name(l.refMidi!!), String.format(Locale.ROOT, "%.1f", abs(it)), if (it >= 0) t(K.origin_wideWord) else t(K.origin_narrowWord)) } ?: "")
        }
        val coarse = read?.coarseCents
        val state: Pair<String, Color> = when {
            t.sampling -> "" to Color(Brand.WHITE_MUTED)
            t.complete && !live -> t(K.state_complete) to Color(Brand.GO_GREEN)
            coarse != null && !live ->
                t(K.state_off, String.format(Locale.ROOT, "%.0f", abs(coarse)), if (coarse < 0) t(K.state_flat) else t(K.state_sharp)) to Color(Brand.ORANGE)
            // another key struck, and the screen does not follow (off, or the temperament octave first)
            heard != null && heard != t.midi && !live -> t(K.state_heardOther, Notes.name(heard!!)) to Color(Brand.ORANGE)
            read?.settling == true -> t(K.state_listening) to Color(Brand.WHITE_MUTED)
            !live || shownHz == null -> t(K.state_listening) to Color(Brand.WHITE_MUTED)
            matched -> t(K.state_matches) to Color(Brand.GO_GREEN)
            shownHz > shownTarget -> t(K.state_sharp) to Color(Brand.ORANGE)
            else -> t(K.state_flat) to Color(Brand.ORANGE)
        }
        val follows = cfg.autoNote && (!cfg.temperamentFirst || t.temperamentComplete) && !t.sampling
        val status = if (t.sampling) "" else (if (follows) "follows the key · " else "") + when {
            !cfg.temperamentFirst -> "leaving a note keeps it"
            t.temperamentComplete -> "A3–A4 done · leaving a note keeps it"
            else -> "A3–A4 first · Done keeps a note"
        }
        // Full Spectrum: the partial read by phase replaces the finder's place for it
        val spectrum = partials.map { p -> if (read != null && p.k == read.k && shownHz != null) p.copy(hz = shownHz) else p }
        val p1Hz = spectrum.firstOrNull { it.k == 1 }?.hz

        // header: back and gear only; the title lives on the hub
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.TopStart)) {
            BackArrow(onClick = onBack)
            SettingsGear(onClick = onSettings)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp)) {
            if (!t.sampling) StateWord(state.first, state.second)
            if (recorder != null && !t.sampling) {
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
                    liveHz = p1Hz,
                    sounding = live,
                    targetHz = liveTarget,
                    shown = shownTuning,
                    predicted = targets,
                    livePartials = spectrum,
                    active = active,
                    matchHz = cfg.matchHz,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 56.dp, bottom = 80.dp),
                )
                Column(Modifier.align(Alignment.TopStart).padding(top = 60.dp)) {
                    Text(name, color = Color(Brand.WHITE), fontFamily = DejaVuSerif, fontSize = 34.sp, maxLines = 1)
                    Text("target " + String.format(Locale.ROOT, "%.2f Hz", shownTarget) + if (active > 1) ", partial $active" else "", color = Color(Brand.TARGET_BLUE), fontFamily = DejaVuSerif, fontSize = 15.sp, maxLines = 1)
                    Text(origin, color = Color(Brand.WHITE_MUTED), fontFamily = DejaVuSerif, fontSize = 13.sp, maxLines = 1)
                }
                ReadoutColumn(
                    shown = shownTuning,
                    active = active,
                    targetHz = liveTarget,
                    liveHz = p1Hz,
                    predicted = targets,
                    livePartials = spectrum,
                    a4Hz = a4,
                    matchHz = cfg.matchHz,
                    onSelect = { k -> controller.activatePartial(k) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 52.dp),
                )
            }
            t.sampling -> {
                // Sampling: the note, one instruction, the count. It walks by itself.
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxSize()
                        .padding(top = 52.dp, bottom = 84.dp),
                ) {
                    Text(t(K.sampling_title), color = Color(Brand.ORANGE), fontFamily = DejaVuSerif, fontSize = 22.sp, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        t(K.sampling_line, t.samplesTotal, Notes.name(controller.sampleNotes().first()), Notes.name(controller.sampleNotes().last())),
                        color = Color(Brand.WHITE_MUTED), fontFamily = DejaVuSerif, fontSize = 15.sp, maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = Color(Brand.WHITE), fontFamily = DejaVuSerif, fontSize = 96.sp, maxLines = 1)
                        Spacer(Modifier.width(28.dp))
                        Text(
                            if (live) t(K.sampling_heard) else t(K.sampling_strike, name),
                            color = if (live) Color(Brand.GO_GREEN) else Color(Brand.WHITE), fontFamily = DejaVuSerif, fontSize = 24.sp, maxLines = 1,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(t(K.sampling_count, t.samplesDone, t.samplesTotal), color = Color(Brand.WHITE_MUTED), fontFamily = DejaVuSerif, fontSize = 24.sp, maxLines = 1)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        buildString {
                            for (m in controller.sampleNotes()) append(if (m in t.sampled) "●" else "○").append(" ").append(Notes.name(m)).append("   ")
                        },
                        color = Color(Brand.WHITE_MUTED), fontFamily = DejaVuSerif, fontSize = 13.sp, maxLines = 2,
                    )
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
                            targetHz = shownTarget,
                            partial = read?.k?.takeIf { it > 1 } ?: l.k.takeIf { it > 1 },
                            origin = origin,
                            onSemitone = { d -> controller.stepNote(d) },
                            onOctave = { d -> controller.selectNote(t.midi + 12 * d) },
                        )
                        Spacer(Modifier.weight(1f))
                        MeasuredBlock(shownHz, partial = read?.k?.takeIf { it > 1 } ?: l.k.takeIf { it > 1 })
                    }
                    Spacer(Modifier.weight(1f))
                    BeatBand(
                        measuredHz = shownHz,
                        targetHz = shownTarget,
                        sounding = live,
                        matched = matched,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    TargetScale(
                        measuredHz = shownHz,
                        targetHz = shownTarget,
                        matchHz = cfg.matchHz,
                        sounding = live,
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
                    tappable = (targets.map { it.k }.toSet() + audible) - t.listening.k,
                    onTap = { k -> controller.tapPartial(k) },
                    pulse = if (active != suggested) suggested else null,
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
            DoneButton(onClick = { controller.acceptLive() }, enabled = if (t.sampling) live else matched)
        }
    }
}
