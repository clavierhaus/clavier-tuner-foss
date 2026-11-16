package at.clavierhaus.claviertuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.claviertuner.Brand
import at.clavierhaus.claviertuner.TuningController
import at.clavierhaus.claviertuner.i18n.K
import at.clavierhaus.claviertuner.i18n.noteName
import at.clavierhaus.claviertuner.i18n.t
import at.clavierhaus.claviertuner.picture.BandPhase

/**
 * The two bars in the strobe's place above C6 (OctaveBars, an experiment, 16 Nov 2026; Peter: "we switch to two separate
 * bars: Left one is the tuned reference tone, one string ... both bars still, not moving"). Left, the key an octave below
 * as read while it sounds alone, against its own target; right, the key on screen against its target, which that strike
 * of the key below has set. Each drifts as the strobe's band of partial 1 does, at the pin's pace ([pace]), and stands
 * still on its target; a bar with nothing read stands empty, outlined. The key's name under each, white once the key
 * below has set the target, muted until then.
 */
@Composable
fun OctaveBarsView(octave: TuningController.OctaveView, reading: TuningController.Reading?, pace: Double) {
    val millis by produceState(0L) { while (true) withFrameMillis { value = it } }
    val carried = remember(octave.midi) { BandPhase() }
    val refName = noteName(octave.refMidi)
    val name = noteName(octave.midi)
    val leftHz = octave.refHz
    val right = reading?.takeIf { it.live && it.hz != null }
    val rates = buildMap {
        if (leftHz != null) put(0, -(leftHz - octave.refTargetHz) * pace)
        if (right != null) put(1, -(right.hz!! - right.targetHz) / right.k * pace)
    }
    val phases = carried.advance(millis, rates)
    val muted = Color(Brand.WHITE_MUTED)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextBlock(listOf(t(K.octave_strike, refName, name)), Modifier.fillMaxWidth().weight(0.16f), size = 13.sp, color = muted)
        Row(Modifier.fillMaxWidth().weight(0.72f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(Modifier.weight(1f).fillMaxSize()) { bar(phases[0], leftHz != null) }
            Canvas(Modifier.weight(1f).fillMaxSize()) { bar(phases[1], right != null) }
        }
        Row(Modifier.fillMaxWidth().weight(0.12f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FitText(refName, 18.sp, Modifier.weight(1f), color = Color(Brand.WHITE), fontWeight = FontWeight.Bold, align = TextAlign.Center)
            FitText(name, 18.sp, Modifier.weight(1f), color = if (octave.taken) Color(Brand.WHITE) else muted, fontWeight = FontWeight.Bold, align = TextAlign.Center)
        }
    }
}

/** One bar: one segment, as the strobe's band of partial 1, shifted by [phase]; outlined and empty with nothing read. */
private fun DrawScope.bar(phase: Double?, read: Boolean) {
    val w = size.width * 0.72f
    val x = (size.width - w) / 2f
    val h = size.height
    if (read) clipRect(x, 0f, x + w, h) {
        val shift = ((phase ?: 0.0) * h).toFloat()
        val solid = h * 0.46f
        for (seg in 0..1) drawRect(Color(Brand.ORANGE), Offset(x, seg * h + shift - h), Size(w, solid))
    }
    drawRect(Color(Brand.WHITE_MUTED).copy(alpha = if (read) 0.30f else 0.18f), Offset(x, 0f), Size(w, h), style = Stroke(1f))
}
