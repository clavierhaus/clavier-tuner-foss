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
import at.clavierhaus.unisonmaster.i18n.K
import at.clavierhaus.unisonmaster.i18n.noteName
import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.settings.OctaveType
import at.clavierhaus.unisonmaster.settings.TunerSettings
import at.clavierhaus.unisonmaster.ui.Choices
import at.clavierhaus.unisonmaster.ui.DejaVuSerif
import at.clavierhaus.unisonmaster.ui.Stepper
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
        listOf(s.octaveBass, s.octaveMiddle, s.octaveTreble).joinToString(" | ") { it.label })

/** The window behind the line. [onChange] changes the settings in force; [onClose] closes it. */
@Composable
fun TuningInfoDialog(s: TunerSettings, onChange: ((TunerSettings) -> TunerSettings) -> Unit, onClose: () -> Unit) {
    // the widest region's choices lay out the heading's column; each region offers its own (30 Oct 2026)
    val octaves = widestOctaves
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.92f).background(Color(Brand.NEAR_BLACK), RoundedCornerShape(10.dp)).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t(K.tuning_infoTitle), color = Color(Brand.WHITE), fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                Chip(t(K.app_done), selected = true, enabled = true, onClick = onClose)
            }
            Spacer(Modifier.height(8.dp))
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
            if (width == 0.0) t(K.settings_beatless) else t(K.settings_cWide, num(width, 1)),
            canDecrease = width > 0.0,
            canIncrease = width < TunerSettings.MAX_WIDTH_CENTS,
            onDecrease = { onWidth(tenths(maxOf(0.0, width - WIDTH_STEP))) },
            onIncrease = { onWidth(tenths(minOf(TunerSettings.MAX_WIDTH_CENTS, width + WIDTH_STEP))) },
        )
    }
}

/** The region whose octave-type choices lay out widest; the info window's column is as wide as they are. */
private val widestOctaves: List<OctaveType> = listOf(TunerSettings.BASS_OCTAVES, TunerSettings.MIDDLE_OCTAVES, TunerSettings.TREBLE_OCTAVES)
    .maxBy { o -> o.sumOf { it.label.length + 2 } }

private fun tenths(x: Double) = kotlin.math.round(x * 10) / 10
private const val WIDTH_STEP = 0.5
