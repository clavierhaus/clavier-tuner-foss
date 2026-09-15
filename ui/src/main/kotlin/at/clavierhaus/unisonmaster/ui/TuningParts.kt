package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.tuning.LiveReference
import at.clavierhaus.unisonmaster.tuning.Notes
import at.clavierhaus.unisonmaster.tuning.PredictedPartial
import kotlin.math.ceil
import kotlin.math.floor

/** Note name of partial [k] of a note whose first partial is [f1Hz]. */
fun partialNoteName(k: Int, f1Hz: Double, a4Hz: Double): String =
    Notes.name(Notes.nearestMidi(k * f1Hz, a4Hz))

/**
 * The tuning graph. The white centre line is the target of every shown
 * partial; the axis is fixed, ±4 Hz around the fundamental's target, and
 * labelled at every whole Hz. Light-blue bells are the calculated targets
 * (on the line, height from the neighbour's measured level); orange bells
 * are the live string. A string Δ Hz off shows its partial k about k·Δ Hz
 * from the line — the higher partials are the finer guide.
 */
@Composable
fun TuningGraph(
    liveHz: Double?,
    targetHz: Double,
    level: Float,
    shown: Set<Int>,
    predicted: List<PredictedPartial>,
    livePartials: List<LiveReference.LivePartial>,
    modifier: Modifier = Modifier,
) {
    val labelPaint = remember {
        android.graphics.Paint().apply {
            color = Color(Brand.WHITE_MUTED).toArgb()
            textSize = 26f
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
    Box(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Text(
                liveHz?.let { formatHz(it) } ?: "— Hz",
                color = Color(Brand.WHITE),
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
            val sigma = w / 80f
            val half = w / 2f - 5f * sigma
            val blue = Color(Brand.TARGET_BLUE)
            val orange = Color(Brand.ORANGE)
            val muted = Color(Brand.WHITE_MUTED)
            val native = drawContext.canvas.nativeCanvas
            fun xOf(offsetHz: Double): Float =
                xc + (offsetHz / HZ_SPAN).coerceIn(-1.0, 1.0).toFloat() * half

            // calculated targets, all on the line
            for (k in shown.sorted()) {
                val p = predicted.firstOrNull { it.k == k }
                val lvl = if (k == 1) 1.0 else ((p?.levelDb ?: continue) / LiveReference.RANGE_DB + 1.0)
                val peak = base * 0.9f * lvl.coerceIn(0.05, 1.0).toFloat()
                bellOutline(xc, peak, sigma, base, blue)
            }

            // the live string
            if (liveHz != null) {
                if (1 in shown) {
                    val peak = base * 0.9f * level.coerceIn(0f, 1f)
                    if (peak > 1f) {
                        val x = xOf(liveHz - targetHz)
                        bellFilled(x, peak, sigma, base, orange)
                        if (shown.size > 1) native.drawText("1", x, base - peak - 8f, labelPaint)
                    }
                }
                for (k in shown.filter { it > 1 }.sorted()) {
                    val live = livePartials.firstOrNull { it.k == k } ?: continue
                    val target = predicted.firstOrNull { it.k == k }?.hz ?: continue
                    val peak = base * 0.9f * live.level.toFloat()
                    if (peak <= 1f) continue
                    val x = xOf(live.hz - target)
                    bellFilled(x, peak, sigma, base, orange)
                    native.drawText("$k", x, base - peak - 8f, labelPaint)
                }
            }

            // fixed axis around the fundamental's target
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

/** The notes of the session: current in orange, finished ones ticked. */
@Composable
fun NoteStrip(
    notes: List<Int>,
    current: Int,
    measured: Set<Int>,
    selectable: (Int) -> Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        for (midi in notes) {
            val isCurrent = midi == current
            val done = midi in measured
            val canTap = selectable(midi) && !isCurrent
            Box(
                Modifier
                    .width(56.dp)
                    .height(30.dp)
                    .background(
                        color = when {
                            isCurrent -> Color(Brand.ORANGE)
                            done -> Color(0xFF2A2A2A)
                            else -> Color(0xFF1C1C1C)
                        },
                        shape = RoundedCornerShape(6.dp),
                    )
                    .clickable(enabled = canTap) { onSelect(midi) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    Notes.name(midi) + if (done) " ✓" else "",
                    color = when {
                        isCurrent -> Color(Brand.BLACK)
                        done -> Color(Brand.WHITE)
                        else -> Color(Brand.WHITE_MUTED)
                    },
                    fontFamily = DejaVuSerif,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
