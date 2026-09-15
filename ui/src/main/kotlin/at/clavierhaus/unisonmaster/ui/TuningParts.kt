package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.tuning.LiveReference
import at.clavierhaus.unisonmaster.tuning.Notes
import at.clavierhaus.unisonmaster.tuning.PredictedPartial
import at.clavierhaus.unisonmaster.tuning.TuningSession
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/** Note name of partial [k] of a note whose first partial is [f1Hz]. */
fun partialNoteName(k: Int, f1Hz: Double, a4Hz: Double): String =
    Notes.name(Notes.nearestMidi(k * f1Hz, a4Hz))

private val ROW_ACTIVE = Color(0xFF2E2E2E)

/**
 * The tuning graph on a fixed axis, ±4 Hz around the fundamental's target,
 * labelled at every whole Hz. One static light-blue bell on the line stands
 * for every target; each shown partial is an orange bell of one common
 * height, numbered, placed by its own offset from its own target, green
 * within ±0.1 Hz; the [active] partial is drawn on top. The frequencies
 * are read in the [ReadoutColumn] beside the graph.
 */
@Composable
fun TuningGraph(
    liveHz: Double?,
    sounding: Boolean,
    targetHz: Double,
    shown: Set<Int>,
    predicted: List<PredictedPartial>,
    livePartials: List<LiveReference.LivePartial>,
    active: Int,
    matchHz: Double,
    modifier: Modifier = Modifier,
) {
    val topLabel = remember {
        android.graphics.Paint().apply {
            color = Color(Brand.WHITE).toArgb()
            textSize = 28f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    val axisPaint = remember {
        android.graphics.Paint().apply {
            color = Color(Brand.WHITE_MUTED).toArgb()
            textSize = 24f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    Canvas(
        modifier.padding(top = 24.dp),
    ) {
        val w = size.width
        val base = size.height - 36f
        val xc = w / 2f
        val sigma = w / 30f
        val half = w / 2f - 3f * sigma
        val green = Color(Brand.GO_GREEN)
        val muted = Color(Brand.WHITE_MUTED)
        val native = drawContext.canvas.nativeCanvas
        fun xOf(offsetHz: Double): Float =
            xc + (offsetHz / HZ_SPAN).coerceIn(-1.0, 1.0).toFloat() * half
        fun targetOf(k: Int): Double? = if (k == 1) targetHz else predicted.firstOrNull { it.k == k }?.hz
        fun liveOf(k: Int): Double? = if (k == 1) liveHz else livePartials.firstOrNull { it.k == k }?.hz

        val height = base * 0.85f
        val allMatched = shown.all { TuningSession.matched(liveOf(it), targetOf(it), matchHz) }
        bellOutline(xc, height, sigma, base, if (allMatched) green else Color(Brand.TARGET_BLUE))
        val order = shown.sorted().filter { it != active } + listOf(active).filter { it in shown }
        for (k in order) {
            val tk = targetOf(k) ?: continue
            val lk = liveOf(k) ?: continue
            if (!sounding) continue
            val match = TuningSession.matched(lk, tk, matchHz)
            val x = xOf(lk - tk)
            bellFilled(x, height, sigma, base, if (match) green else Color(Brand.ORANGE))
            if (shown.size > 1) native.drawText("$k", x, base - height - 10f, topLabel)
        }

        val first = ceil(targetHz - HZ_SPAN).toInt()
        val last = floor(targetHz + HZ_SPAN).toInt()
        for (n in first..last) {
            val x = xOf(n - targetHz)
            drawLine(muted, Offset(x, base), Offset(x, base + 10f), strokeWidth = 1.5f)
            native.drawText("$n", x, base + 34f, axisPaint)
        }
        drawLine(muted, Offset(0f, base), Offset(w, base), strokeWidth = 1f)
        drawLine(Color(Brand.WHITE), Offset(xc, 0f), Offset(xc, base), strokeWidth = 1.5f)
    }
}

private fun hz1(hz: Double): String = String.format(Locale.ROOT, "%.1f", hz)

/**
 * One line per shown partial: number, note, target (blue, static) and the
 * live frequency (white, green when matched). The active line is marked;
 * tapping a line makes that partial active.
 */
@Composable
fun ReadoutColumn(
    shown: Set<Int>,
    active: Int,
    targetHz: Double,
    liveHz: Double?,
    predicted: List<PredictedPartial>,
    livePartials: List<LiveReference.LivePartial>,
    a4Hz: Double,
    matchHz: Double,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.width(330.dp)) {
        for (k in shown.sorted()) {
            val tk = if (k == 1) targetHz else predicted.firstOrNull { it.k == k }?.hz ?: continue
            val lk = if (k == 1) liveHz else livePartials.firstOrNull { it.k == k }?.hz
            val match = TuningSession.matched(lk, tk, matchHz)
            val isActive = k == active
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isActive) ROW_ACTIVE else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .clickable { onSelect(k) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    "$k",
                    color = if (isActive) Color(Brand.ORANGE) else Color(Brand.WHITE_MUTED),
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 16.sp,
                    modifier = Modifier.width(26.dp),
                )
                Text(
                    partialNoteName(k, targetHz, a4Hz),
                    color = Color(Brand.WHITE_MUTED),
                    fontFamily = DejaVuSerif,
                    fontSize = 16.sp,
                    modifier = Modifier.width(48.dp),
                )
                Text(
                    hz1(tk),
                    color = Color(Brand.TARGET_BLUE),
                    fontFamily = DejaVuSerif,
                    fontSize = 16.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(96.dp),
                )
                Text(
                    lk?.let { hz1(it) } ?: "—",
                    color = if (match) Color(Brand.GO_GREEN) else Color(Brand.WHITE),
                    fontFamily = DejaVuSerif,
                    fontSize = if (isActive) 22.sp else 16.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(112.dp),
                )
            }
        }
    }
}

/** ◀ note ▶ — one semitone down or up. */
@Composable
fun NoteStepper(
    name: String,
    canDown: Boolean,
    canUp: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        StepArrow("◀", canDown, onDown)
        Text(
            name,
            color = Color(Brand.WHITE),
            fontFamily = DejaVuSerif,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp),
        )
        StepArrow("▶", canUp, onUp)
    }
}

@Composable
private fun StepArrow(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .background(
                color = if (enabled) Color(0xFF3A3A3A) else Color(0xFF1C1C1C),
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) Color(Brand.WHITE) else Color(Brand.WHITE_MUTED),
            fontSize = 18.sp,
        )
    }
}
