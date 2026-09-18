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
import at.clavierhaus.unisonmaster.ui.formatHz
import at.clavierhaus.unisonmaster.tuning.Notes
import at.clavierhaus.unisonmaster.tuning.PartialSelection
import at.clavierhaus.unisonmaster.tuning.TuningSession
import at.clavierhaus.unisonmaster.ui.BackArrow
import at.clavierhaus.unisonmaster.ui.ClavierhausTitle
import at.clavierhaus.unisonmaster.ui.DejaVuSerif
import at.clavierhaus.unisonmaster.ui.DoneButton
import at.clavierhaus.unisonmaster.ui.HubHint
import at.clavierhaus.unisonmaster.ui.NoteStepper
import at.clavierhaus.unisonmaster.ui.PartialRow
import at.clavierhaus.unisonmaster.ui.ReadoutColumn
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.ui.SettingsGear
import at.clavierhaus.unisonmaster.ui.SpectrumToggle
import at.clavierhaus.unisonmaster.ui.ToneGraph
import at.clavierhaus.unisonmaster.ui.TuningGraph
import at.clavierhaus.unisonmaster.ui.partialNoteName

/**
 * The one main screen. Before A4 is set it is the hub (define A4 on one
 * string); after Done it keeps its layout but tunes the octave down to A3,
 * single strings: the fundamental first, to green, then partials by choice.
 */
@Composable
fun BasicHub(
    controller: TuningController,
    onSettings: () -> Unit,
    onBack: () -> Unit,
) {
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
    val active by controller.activePartial.collectAsState()
    val targets by controller.targets.collectAsState()
    val cfg by controller.settings.collectAsState()
    val liveTarget by controller.targetHz.collectAsState()

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
            Header(onBack, onSettings, "Define your A4 here by tuning a single string to the desired pitch.", null) {
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
        } else {
            val name = Notes.name(t.midi)
            val matched = TuningSession.matched(hz, liveTarget, cfg.matchHz)
            val link = t.link
            val hint = when {
                t.complete -> "Compass complete, ${Notes.name(t.lowestMidi)} upward."
                matched -> "$name matches. Tap Done, or refine with a partial."
                link != null -> "Tune $name, single string: ${link.type.label} octave against ${Notes.name(link.refMidi)}."
                t.midi < cfg.temperamentLowMidi -> "Tune $name, single string. ${Notes.name(t.midi + cfg.octaveTypeFor(t.midi).semitones)} is not tuned yet, so the target is equal temperament."
                t.midi > TunerSettings.TEMPERAMENT_HIGH -> "Tune $name, single string. ${Notes.name(t.midi - cfg.octaveTypeFor(t.midi).semitones)} is not tuned yet, so the target is equal temperament."
                else -> "Tune $name, single string, until both bells turn green."
            }
            val advice = if (matched) {
                suggested?.takeIf { it !in shownTuning }?.let { k ->
                    if (link != null && k == link.ownK)
                        "Add ${partialNoteName(k, liveTarget, a4)} (partial $k): the ${link.type.label} octave turns green there."
                    else
                        "Add ${partialNoteName(k, liveTarget, a4)} (partial $k) for a finer match."
                }
            } else null
            TuningGraph(
                liveHz = hz,
                sounding = level > 0.0,
                targetHz = liveTarget,
                shown = shownTuning,
                predicted = targets,
                livePartials = partials,
                active = active,
                matchHz = cfg.matchHz,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
            )
            Header(onBack, onSettings, hint, advice) {
                SpectrumToggle(
                    fullSpectrum = full,
                    onClick = { controller.toggleFullSpectrum() },
                    fundamentalLabel = "Fundamental $name",
                )
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
                modifier = Modifier.align(Alignment.TopEnd),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
            ) {
                NoteStepper(
                    name = name,
                    canDown = t.midi > t.lowestMidi,
                    canUp = t.midi < TuningSession.MIDI_A4 - 1,
                    onDown = { controller.stepNote(-1) },
                    onUp = { controller.stepNote(+1) },
                )
                Spacer(Modifier.width(10.dp))
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
                Spacer(Modifier.width(10.dp))
                DoneButton(onClick = { controller.acceptLive() }, enabled = matched)
            }
        }
    }
}

/**
 * Gear, title, an empty line, the hint, an orange piece of advice if there
 * is one — and, a line below, the mode toggle.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.Header(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    hint: String,
    advice: String?,
    toggle: @Composable () -> Unit,
) {
    Column(
        Modifier
            .align(Alignment.TopStart)
            .width(300.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackArrow(onClick = onBack)
            SettingsGear(onClick = onSettings)
        }
        Spacer(Modifier.height(10.dp))
        ClavierhausTitle()
        Spacer(Modifier.height(18.dp))
        HubHint(hint)
        if (advice != null) {
            Spacer(Modifier.height(8.dp))
            Text(advice, color = Color(Brand.ORANGE), fontFamily = DejaVuSerif, fontSize = 14.sp)
        }
        Spacer(Modifier.height(18.dp))
        toggle()
    }
}
