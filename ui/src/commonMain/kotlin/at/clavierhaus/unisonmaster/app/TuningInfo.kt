package at.clavierhaus.unisonmaster.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.ui.TextBlock
import at.clavierhaus.unisonmaster.i18n.K
import at.clavierhaus.unisonmaster.i18n.noteName
import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.settings.OctaveType
import at.clavierhaus.unisonmaster.settings.StretchPoint
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.tuning.OctaveSuggestion
import at.clavierhaus.unisonmaster.ui.Choices
import at.clavierhaus.unisonmaster.ui.InfoButton
import at.clavierhaus.unisonmaster.ui.DejaVuSerif
import at.clavierhaus.unisonmaster.ui.Stepper
import at.clavierhaus.unisonmaster.ui.SwitchRow
import at.clavierhaus.unisonmaster.ui.formatHz
import at.clavierhaus.unisonmaster.ui.num

/*
 * The tuning's own settings, at a glance and one tap away (Peter, 23 Oct 2026): a line between the
 * gear and the name says what this tuning is tuned to -- A4, the temperament octave, the octave type
 * of the bass, the middle and the treble -- and a tap on it opens a window with exactly the settings
 * a tuner may want to change while tuning: each region's octave type and width, and the temperament
 * octave. Everything else stays in Settings. It shows the settings in force and holds none of its own.
 */

/** "A4 = 443,0 Hz · gleichstufig A3–A4 · Oktaven 6:3 | 4:2 | 4:2": bass, middle, treble, low to high. */
fun tuningInfoLine(s: TunerSettings, a4Hz: Double): String =
    t(K.tuning_info, formatHz(a4Hz), noteName(s.temperamentLowMidi),
        (listOf(s.octaveBass) + s.stretchPoints.filter { it.midi < s.temperamentLowMidi }.sortedBy { it.midi }.map { it.type } + s.octaveMiddle +
            s.stretchPoints.filter { it.midi > TunerSettings.TEMPERAMENT_HIGH }.sortedBy { it.midi }.map { it.type } + s.octaveTreble).joinToString(" | ") { it.label })

/**
 * The window behind the line. [onChange] changes the settings in force; [onClose] closes it. [suggestion] is what the
 * sampling suggests for the bass (OctaveSuggestion, 10 Nov 2026); [sampled] the sampling is done, so that with no
 * suggestion the window says what to sample for one.
 */
@Composable
fun TuningInfoDialog(
    s: TunerSettings,
    onChange: ((TunerSettings) -> TunerSettings) -> Unit,
    onClose: () -> Unit,
    onStep: ((Int) -> Unit)? = null,
    suggestion: OctaveSuggestion.Suggestion? = null,
    sampled: Boolean = false,
) {
    // the widest region's choices lay out the heading's column; each region offers its own (30 Oct 2026)
    val octaves = widestOctaves
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.92f).background(Color(Brand.NEAR_BLACK), RoundedCornerShape(10.dp)).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t(K.tuning_infoTitle), color = Color(Brand.WHITE), fontSize = 18.sp)
                Spacer(Modifier.width(16.dp))
                // the sampling's suggestion for the bass, beside the tuner's choice and never set by itself (Peter,
                // 10 Nov 2026: "ein erfahrener Stimmer wird sich gegen unseren Vorschlag entscheiden"); its reason, in
                // hertz between the sampled strings, behind the (i). In the title's row: the window had no row to spare
                if (suggestion != null) {
                    val inUse = s.octaveBass == suggestion.type && s.stretchPoints.none { it.midi < s.temperamentLowMidi && it.type != suggestion.type }
                    val others = suggestion.hardestHz.filterKeys { it != suggestion.type }.entries.sortedBy { it.value }
                        .joinToString(", ") { "${it.key.label}: ${num(it.value, 1)} Hz" }
                    val short = t(K.tuning_suggestShort, suggestion.type.label)
                    TextBlock(listOf(short), Modifier.weight(1f), size = 14.sp, minSize = 11.sp, color = Color(Brand.WHITE))
                    InfoButton(short, t(K.tuning_suggest, suggestion.type.label, num(suggestion.hardestHz.getValue(suggestion.type), 1), others,
                        num(OctaveSuggestion.LEVEL_DB, 0)))
                    if (!inUse) Chip(t(K.tuning_suggestUse, suggestion.type.label), selected = false, enabled = true) { onChange { it.withBassOctave(suggestion.type) } }
                    Spacer(Modifier.width(12.dp))
                } else if (sampled) {
                    val short = t(K.tuning_suggestNoneShort)
                    TextBlock(listOf(short), Modifier.weight(1f), size = 14.sp, minSize = 11.sp, color = Color(Brand.WHITE_MUTED))
                    InfoButton(short, t(K.tuning_suggestNone))
                    Spacer(Modifier.width(12.dp))
                } else Spacer(Modifier.weight(1f))
                Chip(t(K.app_done), selected = true, enabled = true, onClick = onClose)
            }
            // the way back, here where the sampling has just ended, until a note has been tuned (Peter, 9 Nov 2026)
            if (onStep != null) { Spacer(Modifier.height(4.dp)); StepLine(3, onStep, Modifier.fillMaxWidth(0.6f)) }
            Spacer(Modifier.height(8.dp))
            // what this window is for, said once (Peter, 9 Nov 2026: every step before tuning has its instruction). The
            // switch for a grand of 210 cm or longer stood here until that day: the sampling is the piano's measure
            TextBlock(listOf(t(K.tuning_infoOrders)), Modifier.fillMaxWidth(), size = 14.sp, color = Color(Brand.WHITE_MUTED))
            Spacer(Modifier.height(4.dp))
            SwitchRow(t(K.settings_sixPoints), s.sixPoints, info = t(K.settings_note_sixPoints)) { on -> onChange { it.withSixPoints(on) } }
            Spacer(Modifier.height(8.dp))
            // with the six points on, the six places themselves stand here, where the three regions stood (Peter, 9 Nov
            // 2026: "it must be obvious for a tuner"): a switch alone left the table looking as it had
            if (s.sixPoints) StretchPlaces(s, onChange) else {
            // a table: the columns say "octave type" and "width" once, each region is a row of its name
            // and its two controls (24 Oct 2026: the name of the width, said three times, left the width
            // itself no room, and "schwebungsfrei" was cut)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                // each heading stands over its column at that column's own width: the control is laid out
                // unseen beneath it and takes the width, so heading and column cannot drift apart
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.alpha(0f).clearAndSetSemantics {}) { Choices(octaves.map { it.label }, -1) {} }
                    Text(t(K.settings_octaveType), color = Color(Brand.WHITE_MUTED), fontFamily = DejaVuSerif, fontSize = 13.sp, softWrap = false)
                }
                Spacer(Modifier.width(16.dp))
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.alpha(0f).clearAndSetSemantics {}) { Stepper(t(K.settings_beatless), true, true, {}, {}) }
                    Text(t(K.settings_octaveWidth), color = Color(Brand.WHITE_MUTED), fontFamily = DejaVuSerif, fontSize = 13.sp, softWrap = false)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                region(t(K.settings_page_bass), TunerSettings.BASS_OCTAVES, s.octaveBass, s.widthBass,
                    { o -> onChange { it.copy(octaveBass = o) } }, { w -> onChange { it.copy(widthBass = w) } })
                region(t(K.settings_page_middle), TunerSettings.MIDDLE_OCTAVES, s.octaveMiddle, s.widthMiddle,
                    { o -> onChange { it.copy(octaveMiddle = o) } }, { w -> onChange { it.copy(widthMiddle = w) } })
                region(t(K.settings_page_treble), TunerSettings.TREBLE_OCTAVES, s.octaveTreble, s.widthTreble,
                    { o -> onChange { it.copy(octaveTreble = o) } }, { w -> onChange { it.copy(widthTreble = w) } })
            }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t(K.settings_temperamentOctave), color = Color(Brand.WHITE), fontFamily = DejaVuSerif, fontSize = 15.sp, softWrap = false)
                Spacer(Modifier.width(16.dp))
                Stepper(
                    noteName(s.temperamentLowMidi) + "–A4",
                    canDecrease = s.temperamentLowMidi > TunerSettings.MIN_TEMPERAMENT_LOW,
                    canIncrease = s.temperamentLowMidi < TunerSettings.MAX_TEMPERAMENT_LOW,
                    onDecrease = { onChange { it.copy(temperamentLowMidi = it.temperamentLowMidi - 1) } },
                    onIncrease = { onChange { it.copy(temperamentLowMidi = it.temperamentLowMidi + 1) } },
                )
            }
        }
    }
}

@Composable
private fun region(
    name: String, octaves: List<OctaveType>, type: OctaveType, width: Double,
    onType: (OctaveType) -> Unit, onWidth: (Double) -> Unit,
) {
    // one row a region, low to high: its name, its octave type, its width
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), color = Color(Brand.WHITE), fontFamily = DejaVuSerif, fontSize = 15.sp, softWrap = false)
        // the regions offer different choices (30 Oct 2026): each sits in a box as wide as the widest row's, unseen
        // beneath, so that the width column stands straight
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.alpha(0f).clearAndSetSemantics {}) { Choices(widestOctaves.map { it.label }, -1) {} }
            Choices(octaves.map { it.label }, octaves.indexOf(type)) { onType(octaves[it]) }
        }
        Spacer(Modifier.width(16.dp))
        Stepper(
            widthLabel(width),
            canDecrease = width > TunerSettings.MIN_WIDTH_CENTS,
            canIncrease = width < TunerSettings.MAX_WIDTH_CENTS,
            onDecrease = { onWidth(tenths(maxOf(TunerSettings.MIN_WIDTH_CENTS, width - WIDTH_STEP))) },
            onIncrease = { onWidth(tenths(minOf(TunerSettings.MAX_WIDTH_CENTS, width + WIDTH_STEP))) },
        )
    }
}

/**
 * The six places of the six-point stretch, low to high, each with its octave type and extra stretch: the bass side and
 * the temperament octave on the left, the treble on the right. The same table in the window behind the info line and in
 * Settings, so there is one picture of them; [onChange] says which settings it changes.
 */
@Composable
fun StretchPlaces(s: TunerSettings, onChange: ((TunerSettings) -> TunerSettings) -> Unit) {
    val bass = TunerSettings.BASS_OCTAVES
    val treble = TunerSettings.TREBLE_OCTAVES
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            placesHeading(bass)
            place(noteName(s.lowestKeyMidi), bass, bass, s.octaveBass, s.widthBassFar ?: s.widthBass,
                { o -> onChange { it.copy(octaveBass = o) } }, { w -> onChange { it.copy(widthBassFar = w) } })
            s.stretchPoints.filter { it.midi < s.temperamentLowMidi }.sortedBy { it.midi }.forEach { pointRow(it, bass, onChange) }
            place(noteName(s.temperamentLowMidi) + "–A4", TunerSettings.MIDDLE_OCTAVES, bass, s.octaveMiddle, s.widthMiddle,
                { o -> onChange { it.copy(octaveMiddle = o) } }, { w -> onChange { it.copy(widthMiddle = w) } })
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            placesHeading(treble)
            s.stretchPoints.filter { it.midi > TunerSettings.TEMPERAMENT_HIGH }.sortedBy { it.midi }.forEach { pointRow(it, treble, onChange) }
            place("C8", treble, treble, s.octaveTreble, s.widthTrebleFar ?: s.widthTreble,
                { o -> onChange { it.copy(octaveTreble = o) } }, { w -> onChange { it.copy(widthTrebleFar = w) } })
        }
    }
}

@Composable
private fun pointRow(p: StretchPoint, offered: List<OctaveType>, onChange: ((TunerSettings) -> TunerSettings) -> Unit) {
    place(noteName(p.midi), offered, offered, p.type, p.widthCents,
        { o -> onChange { it.withPoint(p.copy(type = o)) } }, { w -> onChange { it.withPoint(p.copy(widthCents = w)) } })
}

/** The narrow stepper's width in the six places: room for "+3.5 c" and "−10.5 c". */
private val PLACE_VALUE = 58.dp

@Composable
private fun placesHeading(column: List<OctaveType>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.alpha(0f).clearAndSetSemantics {}) { Choices(column.map { it.label }, -1) {} }
            Text(t(K.settings_octaveType), color = Color(Brand.WHITE_MUTED), fontFamily = DejaVuSerif, fontSize = 13.sp, softWrap = false)
        }
        Spacer(Modifier.width(8.dp))
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.alpha(0f).clearAndSetSemantics {}) { Stepper("+3.5 c", true, true, {}, {}, valueMin = PLACE_VALUE) }
            Text(t(K.settings_octaveWidth), color = Color(Brand.WHITE_MUTED), fontFamily = DejaVuSerif, fontSize = 13.sp, softWrap = false)
        }
    }
}

/** One place: its key, its octave type, its extra stretch. Nought is written as nought here: "none (pure)" is too wide for half a window. */
@Composable
private fun place(
    name: String, octaves: List<OctaveType>, column: List<OctaveType>, type: OctaveType, width: Double,
    onType: (OctaveType) -> Unit, onWidth: (Double) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), color = Color(Brand.WHITE), fontFamily = DejaVuSerif, fontSize = 15.sp, softWrap = false)
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.alpha(0f).clearAndSetSemantics {}) { Choices(column.map { it.label }, -1) {} }
            Choices(octaves.map { it.label }, octaves.indexOf(type)) { onType(octaves[it]) }
        }
        Spacer(Modifier.width(8.dp))
        // as wide as the heading's, laid out unseen beneath it, so that "0 c" leaves the column of octave types straight
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.alpha(0f).clearAndSetSemantics {}) { Stepper("+3.5 c", true, true, {}, {}, valueMin = PLACE_VALUE) }
            Stepper(
                if (width == 0.0) "0 c" else widthLabel(width),
                canDecrease = width > TunerSettings.MIN_WIDTH_CENTS,
                canIncrease = width < TunerSettings.MAX_WIDTH_CENTS,
                onDecrease = { onWidth(tenths(maxOf(TunerSettings.MIN_WIDTH_CENTS, width - WIDTH_STEP))) },
                onIncrease = { onWidth(tenths(minOf(TunerSettings.MAX_WIDTH_CENTS, width + WIDTH_STEP))) },
                valueMin = PLACE_VALUE,
            )
        }
    }
}

/** The region whose octave-type choices lay out widest; the info window's column is as wide as they are. */
private val widestOctaves: List<OctaveType> = listOf(TunerSettings.BASS_OCTAVES, TunerSettings.MIDDLE_OCTAVES, TunerSettings.TREBLE_OCTAVES)
    .maxBy { o -> o.sumOf { it.label.length + 2 } }

/** A width as the tuner reads it: none, so many cents wide, or so many narrow (signed since 8 Nov 2026). */
fun widthLabel(width: Double): String = when {
    width == 0.0 -> t(K.settings_beatless)
    width > 0.0 -> t(K.settings_cWide, num(width, 1))
    else -> t(K.settings_cNarrow, num(-width, 1))
}

private fun tenths(x: Double) = kotlin.math.round(x * 10) / 10
private const val WIDTH_STEP = 0.5
