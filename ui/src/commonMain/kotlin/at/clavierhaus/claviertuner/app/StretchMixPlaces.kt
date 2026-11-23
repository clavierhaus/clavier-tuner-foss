package at.clavierhaus.claviertuner.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.claviertuner.Brand
import at.clavierhaus.claviertuner.i18n.noteName
import at.clavierhaus.claviertuner.i18n.K
import at.clavierhaus.claviertuner.i18n.t
import at.clavierhaus.claviertuner.settings.OctaveShare
import at.clavierhaus.claviertuner.settings.OctaveType
import at.clavierhaus.claviertuner.settings.TunerSettings
import at.clavierhaus.claviertuner.ui.FitText
import at.clavierhaus.claviertuner.ui.TextBlock
import kotlin.math.roundToInt

/*
 * The places of a mixed stretch, Ron Koval's Schubert concert tune (23 Nov 2026), as the settings hold them: each place's
 * key and what its octave is made of, "75 % 12:6 · 25 % 6:3". It reads nothing of the sound and works nothing out: the
 * words are the settings' own, and the targets stay the engine's. foss's StretchPlaces draws one type a place with its
 * chips; a mixed place has no chip to stand on, so these places are shown and not set here, and six points set them.
 */

/** What [type] with [mix] makes, the heavier first: "6:3", or "80 % 4:1 · 20 % 6:3". */
internal fun mixLabel(type: OctaveType, mix: OctaveShare?): String {
    if (mix == null || mix.type == type) return type.label
    val own = ((1 - mix.share) * 100).roundToInt()
    val parts = listOf(type.label to own, mix.type.label to 100 - own).sortedByDescending { it.second }
    return parts.joinToString(" · ") { "${it.second} % ${it.first}" }
}

@Composable
internal fun MixedPlaces(s: TunerSettings, modifier: Modifier = Modifier) {
    val white = Color(Brand.WHITE); val muted = Color(Brand.WHITE_MUTED)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextBlock(listOf(t(K.stretch_schubertTitle)), Modifier.fillMaxWidth(), size = 15.sp, minSize = 11.sp, color = Color(Brand.ORANGE))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                row(noteName(s.lowestKeyMidi), mixLabel(s.octaveBass, s.mixBass), white)
                s.stretchPoints.filter { it.midi < s.temperamentLowMidi }.sortedBy { it.midi }.forEach { row(noteName(it.midi), mixLabel(it.type, it.mix), white) }
                row(noteName(s.temperamentLowMidi) + "–" + noteName(s.temperamentHighMidi), mixLabel(s.octaveMiddle, s.mixMiddle), white)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.stretchPoints.filter { it.midi > s.temperamentHighMidi }.sortedBy { it.midi }.forEach { row(noteName(it.midi), mixLabel(it.type, it.mix), white) }
                row("C8", mixLabel(s.octaveTreble, s.mixTreble), white)
            }
        }
        TextBlock(listOf(t(K.stretch_schubertFixed)), Modifier.fillMaxWidth(), size = 13.sp, minSize = 10.sp, color = muted)
    }
}

@Composable
private fun row(key: String, made: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FitText(key, 15.sp, Modifier.weight(0.35f), color = color)
        FitText(made, 15.sp, Modifier.weight(0.65f), color = color, align = TextAlign.End, minSize = 10.sp)
    }
}
