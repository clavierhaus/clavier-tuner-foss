package at.clavierhaus.claviertuner.app

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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.claviertuner.Brand
import at.clavierhaus.claviertuner.i18n.K
import at.clavierhaus.claviertuner.i18n.noteName
import at.clavierhaus.claviertuner.i18n.t
import at.clavierhaus.claviertuner.settings.StretchPreset
import at.clavierhaus.claviertuner.settings.OctaveType
import at.clavierhaus.claviertuner.settings.TunerSettings
import at.clavierhaus.claviertuner.tuning.OctaveSuggestion
import at.clavierhaus.claviertuner.ui.Choices
import at.clavierhaus.claviertuner.ui.DejaVuSerif
import at.clavierhaus.claviertuner.ui.InfoButton
import at.clavierhaus.claviertuner.ui.Stepper
import at.clavierhaus.claviertuner.ui.TextBlock
import at.clavierhaus.claviertuner.ui.WordButton
import at.clavierhaus.claviertuner.ui.num

/*
 * One tuning window for every step (Plus, 22 Nov 2026; in the free app since 23 Nov 2026, Peter: "the interface restructuring
 * is a winner. Please apply it to foss and Pro"). The edge, the work and the keyboard at the foot stay where they stand on
 * every step; a strip of the steps under the edge says where the tuner is, an earlier step one tap back.
 *
 * It measures nothing: the step strip says which step is drawn, and the octaves' work shows the settings in force and the
 * sampling's own suggestion for the bass (OctaveSuggestion, from the sampled strings only, Rule 9), as TuningInfoDialog
 * shows them, never set by itself.
 */

/**
 * The three steps, in order: A4, the sampling, the tuning. The octave types were a step of their own between the sampling
 * and the tuning until 22 Nov 2026, 14:11 (Peter: "One step less"): the sampling's suggestion is taken, and the octave types
 * open from the edge's Octaves.
 */
internal const val STEP_A4 = 1
internal const val STEP_SAMPLING = 2
internal const val STEP_TUNING = 3

/** The strip's height under the edge. */
internal val FLOW_STEP_STRIP = 40.dp

/**
 * The four steps as one line: the step drawn orange, the ones before it tappable ([back] says which of them can be gone
 * back to now), the ones after it dim, reached by the work's own Done.
 */
@Composable
internal fun FlowStepStrip(current: Int, back: (Int) -> Boolean, onStep: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(K.step_a4, K.step_sampling, K.step_tuning).forEachIndexed { i, key ->
            val n = i + 1
            val label = "$n ${t(key)}"
            val can = n < current && back(n)
            WordButton(
                label,
                fill = when { n == current -> Color(Brand.ORANGE); n < current -> Color(0xFF333333); else -> Color(0xFF1F1F1F) },
                textColor = when { n == current -> Color(Brand.BLACK); can -> Color(Brand.WHITE); else -> Color(Brand.WHITE_MUTED) },
                enabled = can, onClick = { onStep(n) },
                modifier = Modifier.weight(label.length + 3f),
                size = 14.sp, minSize = 10.sp, padding = 10.dp, minPadding = 4.dp, height = FLOW_STEP_STRIP - 4.dp,
            )
        }
    }
}

/**
 * The octave types' step: left, the regions or the six places with their types and widths; right, the sampling's
 * suggestion for the bass with its reason, the six-point switch, the temperament octave, and Start tuning.
 */
@Composable
internal fun OctavesWork(
    s: TunerSettings,
    onChange: ((TunerSettings) -> TunerSettings) -> Unit,
    suggestion: OctaveSuggestion.Suggestion?,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val white = Color(Brand.WHITE); val muted = Color(Brand.WHITE_MUTED)
    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(0.6f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (s.hasPlaces) StretchPlaces(s, onChange) else {
                RegionRow(t(K.settings_page_bass), TunerSettings.BASS_OCTAVES, s.octaveBass, s.widthBass,
                    { o -> onChange { it.copy(octaveBass = o) } }, { w -> onChange { it.copy(widthBass = w) } })
                RegionRow(t(K.settings_page_middle), TunerSettings.MIDDLE_OCTAVES, s.octaveMiddle, s.widthMiddle,
                    { o -> onChange { it.copy(octaveMiddle = o) } }, { w -> onChange { it.copy(widthMiddle = w) } })
                RegionRow(t(K.settings_page_treble), TunerSettings.TREBLE_OCTAVES, s.octaveTreble, s.widthTreble,
                    { o -> onChange { it.copy(octaveTreble = o) } }, { w -> onChange { it.copy(widthTreble = w) } })
            }
        }
        Column(Modifier.weight(0.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // the suggestion, strong and never set by itself (Peter, 10 Nov 2026): its reason in hertz behind the (i)
            if (suggestion != null) {
                val inUse = s.octaveBass == suggestion.type && s.stretchPoints.none { it.midi < s.temperamentLowMidi && it.type != suggestion.type }
                val others = suggestion.hardestHz.filterKeys { it != suggestion.type }.entries.sortedBy { it.value }
                    .joinToString(", ") { "${it.key.label}: ${num(it.value, 1)} Hz" }
                // the words since the sampling's Done sets the suggestion (Plus, 22 Nov 2026, 14:18)
                val short = t(K.octaves_suggestShort, suggestion.type.label)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextBlock(listOf(short), Modifier.weight(1f), size = 16.sp, minSize = 11.sp, color = Color(Brand.ORANGE))
                    InfoButton(short, t(K.octaves_suggest, suggestion.type.label, num(suggestion.hardestHz.getValue(suggestion.type), 1), others,
                        num(OctaveSuggestion.LEVEL_DB, 0)))
                }
                if (!inUse) Chip(t(K.tuning_suggestUse, suggestion.type.label), selected = false, enabled = true) { onChange { it.withBassOctave(suggestion.type) } }
            } else {
                val short = t(K.tuning_suggestNoneShort)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextBlock(listOf(short), Modifier.weight(1f), size = 14.sp, minSize = 11.sp, color = muted)
                    InfoButton(short, t(K.tuning_suggestNone))
                }
            }
            // the presets in place of the six points' switch (23 Nov 2026): three points and six
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Choices(stretchPresetLabels(), StretchPreset.of(s).ordinal) { i -> onChange { StretchPreset.entries[i].applyTo(it) } } }
                InfoButton(t(K.settings_sixPoints), listOf(t(K.settings_stretchThreeNote), t(K.settings_stretchSixNote)).joinToString("\n\n"))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t(K.settings_temperamentOctave), color = white, fontFamily = DejaVuSerif, fontSize = 15.sp, softWrap = false,
                    modifier = Modifier.weight(1f))
                Stepper(
                    noteName(s.temperamentLowMidi) + "–" + noteName(s.temperamentHighMidi),
                    canDecrease = s.temperamentLowMidi > TunerSettings.MIN_TEMPERAMENT_LOW,
                    canIncrease = s.temperamentLowMidi < TunerSettings.MAX_TEMPERAMENT_LOW,
                    onDecrease = { onChange { it.copy(temperamentLowMidi = it.temperamentLowMidi - 1) } },
                    onIncrease = { onChange { it.copy(temperamentLowMidi = it.temperamentLowMidi + 1) } },
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                WordButton(t(K.app_done), fill = Color(Brand.GO_GREEN), textColor = Color(Brand.BLACK), enabled = true, onClick = onStart)
            }
        }
    }
}

/** One region: its name, its octave type, its width (TuningInfoDialog's row, which is private to foss's file). */
@Composable
private fun RegionRow(name: String, octaves: List<OctaveType>, type: OctaveType, width: Double, onType: (OctaveType) -> Unit, onWidth: (Double) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), color = Color(Brand.WHITE), fontFamily = DejaVuSerif, fontSize = 15.sp, softWrap = false)
        Box(contentAlignment = Alignment.Center) { Choices(octaves.map { it.label }, octaves.indexOf(type)) { onType(octaves[it]) } }
        Spacer(Modifier.width(12.dp))
        Stepper(
            widthLabel(width),
            canDecrease = width > TunerSettings.MIN_WIDTH_CENTS, canIncrease = width < TunerSettings.MAX_WIDTH_CENTS,
            onDecrease = { onWidth(kotlin.math.round(maxOf(TunerSettings.MIN_WIDTH_CENTS, width - 0.5) * 10) / 10) },
            onIncrease = { onWidth(kotlin.math.round(minOf(TunerSettings.MAX_WIDTH_CENTS, width + 0.5) * 10) / 10) },
        )
    }
}

/** The presets' names, in [StretchPreset]'s order. */
internal fun stretchPresetLabels(): List<String> = StretchPreset.entries.map {
    when (it) {
        StretchPreset.THREE -> t(K.settings_stretchThree)
        StretchPreset.SIX -> t(K.settings_stretchSix)
    }
}
