package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.tuning.LiveReference
import at.clavierhaus.unisonmaster.tuning.Notes
import at.clavierhaus.unisonmaster.tuning.PredictedPartial
import at.clavierhaus.unisonmaster.tuning.TuningSession
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/** Note name of partial [k] of a note whose first partial is [f1Hz]. */
fun partialNoteName(k: Int, f1Hz: Double, a4Hz: Double): String =
    Notes.name(Notes.nearestMidi(k * f1Hz, a4Hz))

/**
 * The tuning graph: pairs of bells on a fixed axis, ±4 Hz around the
 * fundamental's target, labelled at every whole Hz.
 *
 * Each shown partial is one pair of equal, fixed height: the light-blue
 * target on the white line and the orange live string beside it. Only the
 * horizontal distance changes; the orange bell stays at full height while
 * the string sounds. Within ±0.1 Hz of its target a pair turns green.
 * The fundamental's pair is the tallest; partials the tuner adds sit lower,
 * in order, so pairs never hide each other. A string Δ Hz off shows its
 * partial k about k·Δ Hz from the line.
 */
@Composable
fun TuningGraph(
    liveHz: Double?,
    sounding: Boolean,
    targetHz: Double,
    shown: Set<Int>,
    predicted: List<PredictedPartial>,
    livePartials: List<LiveReference.LivePartial>,
    modifier: Modifier = Modifier,
) {
    val labelPaint = remember {
        android.graphics.Paint().apply {
            color = Color(Brand.WHITE_MUTED).toArgb()
            textSize = 26f
            textAlign = android.graphics.Paint.Align.RIGHT
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
    Box(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Text(
                liveHz?.let { formatHz(it) } ?: "— Hz",
                color = if (TuningSession.matched(liveHz, targetHz)) Color(Brand.GO_GREEN) else Color(Brand.WHITE),
                fontFamily = DejaVuSerif,
                fontSize = 30.sp,
            )
            Text(
                "target " + formatHz(targetHz),
                color = Color(Brand.TARGET_BLUE),
                fontFamily = DejaVuSerif,
                fontSize = 14.sp,
            )
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(top = 64.dp),
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

            for ((i, k) in shown.sorted().withIndex()) {
                val height = base * 0.85f * 0.75f.pow(i)
                val targetK = if (k == 1) targetHz else predicted.firstOrNull { it.k == k }?.hz ?: continue
                val liveK = if (k == 1) liveHz else livePartials.firstOrNull { it.k == k }?.hz
                val match = TuningSession.matched(liveK, targetK)
                bellOutline(xc, height, sigma, base, if (match) green else Color(Brand.TARGET_BLUE))
                if (sounding && liveK != null) {
                    bellFilled(xOf(liveK - targetK), height, sigma, base, if (match) green else Color(Brand.ORANGE))
                }
                if (shown.size > 1) native.drawText("$k", xc - sigma * 1.3f, base - height + 4f, labelPaint)
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
