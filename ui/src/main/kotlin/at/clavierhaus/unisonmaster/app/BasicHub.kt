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
import at.clavierhaus.unisonmaster.ui.AcceptButton
import at.clavierhaus.unisonmaster.ui.DejaVuSerif
import at.clavierhaus.unisonmaster.ui.DoneButton
import at.clavierhaus.unisonmaster.ui.HubHint
import at.clavierhaus.unisonmaster.ui.NotePicker
import at.clavierhaus.unisonmaster.ui.PartialChips
import at.clavierhaus.unisonmaster.ui.PartialRow
import at.clavierhaus.unisonmaster.ui.ProgressButton
import at.clavierhaus.unisonmaster.ui.ProgressKeyboard
import at.clavierhaus.unisonmaster.ui.ProbeButton
import at.clavierhaus.unisonmaster.ui.RecordButton
import at.clavierhaus.unisonmaster.ui.CORNER_RIGHT_WIDTH
import at.clavierhaus.unisonmaster.ui.CornerBand
import at.clavierhaus.unisonmaster.ui.Corners
import at.clavierhaus.unisonmaster.ui.SpectrumToggle
import at.clavierhaus.unisonmaster.ui.StateWord
import at.clavierhaus.unisonmaster.ui.TuningPictureView
import at.clavierhaus.unisonmaster.picture.TuningPicture
import at.clavierhaus.unisonmaster.ui.ToneGraph
import at.clavierhaus.unisonmaster.ui.formatHz
import java.util.Locale
import kotlin.math.abs

/**
 * The one main screen. Before A4 is set it is the hub (define A4 on one
 * string). After Done it is the tuning screen of docs/SCREEN.md: one note,
 * the partial listened to and its target, where the target comes from
 * (docs/ENGINE.md), the partial as read, the beat as motion, a scale
 * magnified at the match window, one state word — and Done. "Partials" opens Full Spectrum (the bells, the readout
 * column and the partial row); "Progress" the keyboard. The corners are
 * every screen's (ui/Corners.kt): the gear top left, the name and home top
 * right; no back arrow (blueprint §18–§19). In the band between them, the
 * state word and — in Pro, switched on in the settings — the red recording
 * button and the play button.
 */
@Composable
fun BasicHub(
    controller: TuningController,
    recorder: SessionRecorder?,
    onSettings: () -> Unit,
    /** Home: the hub, from the corner every screen shares. */
    onHome: () -> Unit,
    feeding: String? = null,
    onPickRecording: (() -> Unit)? = null,
    onUseMicrophone: () -> Unit = {},
    /** Switches TunerSettings.autoNote: off when the tuner names a key by hand, on when he taps the name again. */
    onAutoNote: ((Boolean) -> Unit)? = null,
    /** Opens Settings at the stretch of region 0..3 (TunerSettings.regionOf): the note's own octave type and width, one tap away. */
    onStretch: ((Int) -> Unit)? = null,
) {
    val hz by controller.liveHz.collectAsState()
    val level by controller.liveLevel.collectAsState()
    val partials by controller.livePartials.collectAsState()
    val audible by controller.liveAudible.collectAsState()
    val full by controller.fullSpectrum.collectAsState()
    var showProgress by remember { mutableStateOf(false) }
    // the one picture: built frame by frame from the engine's readings of the strike now sounding
    val pictureBuilder = remember { TuningPicture.Builder() }
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
    Box(Modifier.fillMaxSize().background(Color(Brand.BLACK)).safeDrawingPadding()) {
    Corners(onSettings = onSettings, onHome = onHome)
    Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
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
                CornerBand()
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
        val matched = live && TuningSession.matched(shownHz, shownTarget, cfg.matchCents)
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
        // Every number on this screen is derived from the A4 reference, and the
        // screen showed the target, the measurement, the cents, the octave and its
        // width -- and never the one figure the rest hangs from. Four rounds at the
        // piano on 1 Oct 2026 went into deducing it from where the bells landed. It
        // is said here, always, whatever it is and wherever it came from.
        val originLine = "A4 ${formatHz(a4)} \u00b7 $origin"
        val coarse = read?.coarseCents
        // The one picture: everything on it is the engine's reading of the strike now sounding,
        // mapped to a frame; the picture decides what is shown, the view how (docs/UI.md).
        // Built here, once, so the header's state word and the picture say the same thing.
        val picture = pictureBuilder.build(
            TuningPicture.Frame(
                note = name,
                listened = read?.k ?: l.k,
                live = live,
                listenedHz = shownHz,
                listenedTargetHz = shownTarget,
                partials = partials.associate { it.k to TuningPicture.Frame.Partial(it.hz, it.level, it.widthCents, it.overNoiseDb) },
                targets = targets.associate { it.k to it.hz },
                falsePartials = read?.falsePartials?.toSet() ?: emptySet(),
                dropped = emptySet(),
                matchCents = cfg.matchCents,
                nowMs = System.currentTimeMillis(),
            ),
        )
        val state: Pair<String, Color> = when {
            t.sampling -> "" to Color(Brand.WHITE_MUTED)
            t.complete && !live -> t(K.state_complete) to Color(Brand.GO_GREEN)
            coarse != null && !live ->
                t(K.state_off, String.format(Locale.ROOT, "%.0f", abs(coarse)), if (coarse < 0) t(K.state_flat) else t(K.state_sharp)) to Color(Brand.ORANGE)
            // another key struck, and the screen does not follow (off, or the temperament octave first)
            heard != null && heard != t.midi && !live -> t(K.state_heardOther, Notes.name(heard!!)) to Color(Brand.ORANGE)
            read?.settling == true -> t(K.state_listening) to Color(Brand.WHITE_MUTED)
            !live || shownHz == null -> t(K.state_listening) to Color(Brand.WHITE_MUTED)
            // near is not on: "on" is the picture's, earned by the hold
            picture.state == TuningPicture.State.ON -> t(K.state_matches) to Color(Brand.GO_GREEN)
            picture.state == TuningPicture.State.NEAR -> t(K.state_near) to Color(Brand.ORANGE)
            shownHz > shownTarget -> t(K.state_sharp) to Color(Brand.ORANGE)
            else -> t(K.state_flat) to Color(Brand.ORANGE)
        }
        val follows = cfg.autoNote && (!cfg.temperamentFirst || t.temperamentComplete) && !t.sampling
        // which partials the reading is made of, when the clean partials are read together; a false one is named
        val partialsLine = read?.takeIf { it.combined || it.falsePartials.isNotEmpty() }?.let { r ->
            (if (r.combined) "partials " + r.usedPartials.joinToString(" ") else "partial ${r.k}") +
                (if (r.falsePartials.isNotEmpty()) " · not " + r.falsePartials.joinToString(" ") else "")
        }
        val status = if (t.sampling) "" else (if (follows) "follows the key · " else "") + when {
            !cfg.temperamentFirst -> "leaving a note keeps it"
            t.temperamentComplete -> "A3–A4 done · leaving a note keeps it"
            else -> "A3–A4 first · Done keeps a note"
        } + (partialsLine?.let { " · $it" } ?: "")

        // the band between the corners: the state word, and Pro's two buttons
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = CORNER_RIGHT_WIDTH)) {
            if (!t.sampling) StateWord(state.first, state.second)
            if (recorder != null && !t.sampling) {
                val recording by recorder.recording.collectAsState()
                val seconds by recorder.seconds.collectAsState()
                Spacer(Modifier.width(18.dp))
                RecordButton(recording = recording, seconds = seconds, onToggle = {
                    if (recording) recorder.stop() else recorder.start()
                })
            }
            if (onPickRecording != null && !t.sampling) {
                Spacer(Modifier.width(4.dp))
                ProbeButton(feeding = feeding, onPick = onPickRecording, onStop = onUseMicrophone)
            }
        }

        // the note name is the picture's one control: following the key struck is the default; a tap
        // opens the picker and the key chosen there holds until the name is tapped again
        var pickingNote by remember { mutableStateOf(false) }
        when {
            !showProgress && !t.sampling -> {
                TuningPictureView(
                    picture,
                    matchCents = cfg.matchCents,
                    modifier = Modifier.fillMaxSize().padding(top = 52.dp, bottom = 84.dp),
                    follows = follows,
                    onNoteTap = { if (cfg.autoNote) pickingNote = true else onAutoNote?.invoke(true) },
                    onStretchTap = onStretch?.let { open -> { open(cfg.regionOf(t.midi)) } },
                )
                if (pickingNote) NotePicker(
                    lowMidi = t.stepLowMidi,
                    highMidi = t.stepHighMidi,
                    current = t.midi,
                    onPick = { midi -> controller.selectNote(midi); onAutoNote?.invoke(false); pickingNote = false },
                    onDismiss = { pickingNote = false },
                )
            }
            showProgress -> {
                ProgressKeyboard(
                    done = t.measured,
                    current = t.midi,
                    deviations = t.deviations,
                    curve = t.curve,
                    lowestMidi = t.lowestMidi,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 40.dp)
                        .width(640.dp)
                        .height(216.dp),
                )
            }
        }

        // the bottom row: Progress · the partials · Done
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(),
        ) {
            ProgressButton(showingProgress = showProgress, onClick = { showProgress = !showProgress })
            Spacer(Modifier.width(10.dp))
            if (t.sampling) {
                // sampling: the tuner chooses the key at the keyboard, not here
                Spacer(Modifier.weight(1f))
                AcceptButton(accepted = t.caughtAccepted, enabled = controller.acceptReady(), onClick = { controller.acceptLive() })
                Spacer(Modifier.width(10.dp))
                DoneButton(onClick = { controller.finishSampling() }, enabled = t.samplingReady)
                return@Row
            }
            if (!showProgress) {
                // the partials to tap: one row that always fits; a false one is violet, and a
                // tap on it takes it out of the bells
                PartialChips(
                    a4Hz = a4,
                    baseHz = liveTarget,
                    count = controller.highestPartial(liveTarget),
                    shown = shownTuning,
                    falseMarked = picture.falseMarked,
                    tappable = (targets.map { it.k }.toSet() + audible) - t.listening.k,
                    onTap = { k -> controller.tapPartial(k) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(status, color = Color(Brand.WHITE_MUTED), fontFamily = DejaVuSerif, fontSize = 13.sp, maxLines = 1)
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.width(10.dp))
            DoneButton(onClick = { controller.acceptLive() }, enabled = matched)
        }
    }
    }
}
