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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.TuningController
import at.clavierhaus.unisonmaster.tuning.Notes
import at.clavierhaus.unisonmaster.tuning.PartialSelection
import at.clavierhaus.unisonmaster.tuning.TuningSession
import at.clavierhaus.unisonmaster.ui.ClavierhausTitle
import at.clavierhaus.unisonmaster.ui.DejaVuSerif
import at.clavierhaus.unisonmaster.ui.DoneButton
import at.clavierhaus.unisonmaster.ui.HubHint
import at.clavierhaus.unisonmaster.ui.NoteStrip
import at.clavierhaus.unisonmaster.ui.PartialRow
import at.clavierhaus.unisonmaster.ui.SettingsGear
import at.clavierhaus.unisonmaster.ui.SpectrumToggle
import at.clavierhaus.unisonmaster.ui.ToneGraph
import at.clavierhaus.unisonmaster.ui.TuningGraph
import at.clavierhaus.unisonmaster.ui.partialNoteName
import kotlin.math.abs

/**
 * The one main screen. Before A4 is set it is the hub (define A4 on one
 * string); after Done it keeps its layout but tunes the octave A4..A3,
 * single strings, against calculated targets.
 */
@Composable
fun BasicHub(controller: TuningController) {
    val hz by controller.liveHz.collectAsState()
    val level by controller.liveLevel.collectAsState()
    val partials by controller.livePartials.collectAsState()
    val audible by controller.liveAudible.collectAsState()
    val full by controller.fullSpectrum.collectAsState()
    val hidden by controller.hiddenPartials.collectAsState()
    val a4 by controller.referenceA4Hz.collectAsState()
    val tuning by controller.tuning.collectAsState()
    val shownTuning by controller.shownPartials.collectAsState()
    val suggested by controller.suggested.collectAsState()

    val t = tuning
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(Brand.BLACK))
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (t == null) {
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
            Header("Define your A4 here by tuning a single string to the desired pitch.", null)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
            ) {
                SpectrumToggle(fullSpectrum = full, onClick = { controller.toggleFullSpectrum() })
                Spacer(Modifier.width(10.dp))
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
        } else {
            val name = Notes.name(t.midi)
            val onTarget = hz?.let {
                abs(TuningSession.centsOff(it, t.targetHz)) <= TuningController.DONE_WITHIN_CENTS
            } ?: false
            val hint = if (t.complete) "Octave A3–A4 complete." else "Tune $name, single string, to the blue target."
            val advice = suggested?.takeIf { it !in shownTuning }?.let { k ->
                "Add ${partialNoteName(k, t.targetHz, a4)} (partial $k) for a finer match."
            }
            TuningGraph(
                liveHz = hz,
                targetHz = t.targetHz,
                level = level.toFloat(),
                shown = shownTuning,
                predicted = t.predicted,
                livePartials = partials,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 124.dp),
            )
            Header(hint, advice)
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
            ) {
                NoteStrip(
                    notes = TuningSession.sequence,
                    current = t.midi,
                    measured = t.measured,
                    selectable = { it != TuningSession.MIDI_A4 },
                    onSelect = { controller.selectNote(it) },
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SpectrumToggle(
                        fullSpectrum = full,
                        onClick = { controller.toggleFullSpectrum() },
                        fundamentalLabel = "Fundamental $name",
                    )
                    Spacer(Modifier.width(10.dp))
                    PartialRow(
                        a4Hz = a4,
                        baseHz = t.targetHz,
                        shown = shownTuning,
                        tappable = (t.predicted.map { it.k }.toSet() + audible) - 1,
                        onTap = { k -> controller.tapPartial(k) },
                        pulse = suggested,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    DoneButton(onClick = { controller.acceptLive() }, enabled = onTarget)
                }
            }
        }
    }
}

/** Gear, title, an empty line, the hint — and an orange piece of advice if there is one. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.Header(hint: String, advice: String?) {
    Column(
        Modifier
            .align(Alignment.TopStart)
            .width(300.dp),
    ) {
        SettingsGear(onClick = { })
        Spacer(Modifier.height(10.dp))
        ClavierhausTitle()
        Spacer(Modifier.height(18.dp))
        HubHint(hint)
        if (advice != null) {
            Spacer(Modifier.height(8.dp))
            Text(advice, color = Color(Brand.ORANGE), fontFamily = DejaVuSerif, fontSize = 14.sp)
        }
    }
}
