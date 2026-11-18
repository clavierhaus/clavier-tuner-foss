package at.clavierhaus.claviertuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.claviertuner.Brand
import at.clavierhaus.claviertuner.TuningController
import at.clavierhaus.claviertuner.i18n.K
import at.clavierhaus.claviertuner.i18n.noteName
import at.clavierhaus.claviertuner.i18n.t
import at.clavierhaus.claviertuner.tuning.StrikePitch
import at.clavierhaus.claviertuner.tuning.TuningSession

/**
 * The two bars in the strobe's place above C6 (OctaveBars, an experiment, 16 Nov 2026; Peter: "we switch to two separate
 * bars: Left one is the tuned reference tone, one string"). Left, the key an octave below against its own target; right,
 * the key on screen against its target, which that key below has set.
 *
 * Since 18 Nov 2026 each bar shows one pitch per strike (StrikePitch; Peter, 21:00: "keep it as a reference value that does
 * not change as long as the same note is struck with the same pitch"): a block standing above the target's line when sharp,
 * below when flat, over [CENTS_SPAN] either way as the tuning graph, with its cents under the bar and the change from the
 * strike before. Until 18 Nov the bars drifted at the pin's pace, as the strobe's band, and fluttered with the attack and the
 * fading tail of strings that ring half a second. The block is full while the strike sounds, dimmed once it has stopped
 * (held, not live: Law 3 set aside here), and drawn as an outline when the strike was read too briefly to be firm
 * (StrikePitch.ROUGH_S). A bar with nothing read stands empty.
 *
 * Since 18 Nov 2026, 21:19, the left bar is stiff once the key below is tuned (Peter: "a fixed place that represents the
 * tuned note ... a stiff bar that represents what was tuned before"): the key below as recorded when its strobe stood
 * still, drawn white and full, not moved by a strike of it; the right bar is tuned to stand with it. Until the key below
 * is tuned, the left bar is its strike, as before.
 */
@Composable
fun OctaveBarsView(octave: TuningController.OctaveView) {
    val refName = noteName(octave.refMidi)
    val name = noteName(octave.midi)
    val muted = Color(Brand.WHITE_MUTED)
    val stiff = octave.refTunedHz
    val left = if (stiff != null) StrikePitch.Value(stiff, StrikePitch.ROUGH_S, sounding = true, previousHz = null) else octave.refHeld
    val right = octave.held
    val leftC = left?.let { TuningSession.centsOff(it.hz, octave.refTargetHz) }
    val rightC = right?.let { TuningSession.centsOff(it.hz, octave.keyTargetHz) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextBlock(listOf(if (stiff != null) t(K.octave_tuned, refName, name) else t(K.octave_strike, refName, name)), Modifier.fillMaxWidth().weight(0.14f), size = 13.sp, color = muted)
        Row(Modifier.fillMaxWidth().weight(0.62f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(Modifier.weight(1f).fillMaxSize()) { bar(leftC, left, stiff = stiff != null) }
            Canvas(Modifier.weight(1f).fillMaxSize()) { bar(rightC, right) }
        }
        Row(Modifier.fillMaxWidth().weight(0.12f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FitText(figure(leftC, left), 16.sp, Modifier.weight(1f), color = if (left?.sounding == true) Color(Brand.WHITE) else muted, align = TextAlign.Center)
            FitText(figure(rightC, right), 16.sp, Modifier.weight(1f), color = if (right?.sounding == true) Color(Brand.WHITE) else muted, align = TextAlign.Center)
        }
        Row(Modifier.fillMaxWidth().weight(0.12f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FitText(refName, 18.sp, Modifier.weight(1f), color = Color(Brand.WHITE), fontWeight = FontWeight.Bold, align = TextAlign.Center)
            FitText(name, 18.sp, Modifier.weight(1f), color = if (octave.taken) Color(Brand.WHITE) else muted, fontWeight = FontWeight.Bold, align = TextAlign.Center)
        }
    }
}

/** The strike's cents and, in brackets, its change from the strike before; nothing with nothing read. */
private fun figure(cents: Double?, v: StrikePitch.Value?): String {
    if (cents == null || v == null) return " "
    val change = v.previousHz?.let { TuningSession.centsOff(v.hz, it) }
    return fmt("%+.1f", cents) + " c" + (change?.let { "  (" + fmt("%+.1f", it) + ")" } ?: "")
}

/** One bar: the target's line across the middle and the strike's block above it (sharp) or below (flat). */
private fun DrawScope.bar(cents: Double?, v: StrikePitch.Value?, stiff: Boolean = false) {
    val w = size.width * 0.72f
    val x = (size.width - w) / 2f
    val h = size.height
    val mid = h / 2f
    drawRect(Color(Brand.WHITE_MUTED).copy(alpha = 0.18f), Offset(x, 0f), Size(w, h), style = Stroke(1f))
    drawRect(Color(Brand.WHITE_MUTED).copy(alpha = 0.6f), Offset(x, mid - 1f), Size(w, 2f))
    if (cents == null || v == null) return
    val block = h * 0.08f
    val y = (mid - (cents.coerceIn(-CENTS_SPAN, CENTS_SPAN) / CENTS_SPAN).toFloat() * (mid - block / 2f)) - block / 2f
    val colour = if (stiff) Color(Brand.WHITE) else Color(Brand.ORANGE).copy(alpha = if (v.sounding) 1f else 0.45f)
    if (v.rough) drawRect(colour, Offset(x + 2f, y), Size(w - 4f, block), style = Stroke(3f))
    else drawRect(colour, Offset(x, y), Size(w, block))
}
