package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.picture.TuningPicture
import at.clavierhaus.unisonmaster.picture.TuningPicture.State
import java.util.Locale
import kotlin.math.abs

/** The bells' span either side of the anchor, cents. */
const val BELLS_SPAN_C = 8.0
/** The scale's span either side of the target, cents: linear, one strip. */
const val SCALE_SPAN_C = 30.0

/**
 * Draws a [TuningPicture] and nothing else (docs/UI.md): bells on the anchor
 * line to the left with the partial numbers in one row above them, the readout
 * table to the right, the linear scale with its gate across the bottom. Every
 * position is a fraction of the pane. The picture decides what is shown; this
 * decides only how.
 */
@Composable
fun TuningPictureView(p: TuningPicture, matchCents: Double, modifier: Modifier = Modifier) {
    val on = p.state == State.ON
    Pane(modifier) {
        // the note, what is tuned, the state word
        Place(0.00f, 0.00f, 0.14f, 0.18f) {
            FitText(p.note, 40.sp, Modifier.fillMaxSize(), color = Color(if (on) Brand.GO_GREEN else Brand.WHITE), fontWeight = FontWeight.Bold)
        }
        Place(0.15f, 0.06f, 0.55f, 0.10f) {
            val anchorNote = if (p.anchor != p.listened) " · bells on partial ${p.anchor}" else ""
            FitText("partial ${p.listened}$anchorNote", 13.sp, Modifier.fillMaxSize(), color = Color(Brand.WHITE_MUTED))
        }
        Place(0.00f, 0.20f, 0.56f, 0.46f) { Bells(p) }
        Place(0.58f, 0.20f, 0.42f, 0.46f) { Readout(p) }
        Place(0.00f, 0.70f, 1.00f, 0.28f) { CentsScale(p.pointerCents, matchCents, on, p.state != State.SILENT) }
    }
}

@Composable
private fun Bells(p: TuningPicture) {
    val white = Color(Brand.WHITE).toArgb()
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val base = h * 0.92f
        val top = h * 0.16f
        val cx = w / 2f
        val half = w / 2f * 0.9f
        fun xOf(cents: Double): Float = (cx + (cents / BELLS_SPAN_C).coerceIn(-1.0, 1.0) * half).toFloat()
        // the anchor line, fixed in the pane
        drawLine(Color(Brand.WHITE).copy(alpha = 0.6f), Offset(cx, top), Offset(cx, base), 2f)
        drawLine(Color(Brand.WHITE_MUTED), Offset(0f, base), Offset(w, base), 1f)
        val paint = android.graphics.Paint().apply { color = white; textSize = h * 0.07f; isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER }
        // the partial numbers in one row above the bells, evenly spaced: a label never sits on
        // its bell, so labels cannot overprint however close the bells stand
        val n = p.bells.size
        p.bells.forEachIndexed { i, b ->
            val lx = w * (0.5f + i) / n.coerceAtLeast(1)
            paint.color = colourOf(b, p).toArgb()
            drawContext.canvas.nativeCanvas.drawText(b.k.toString(), lx, h * 0.10f, paint)
        }
        // the bells: height says level, place says distance from the anchor
        for (b in p.bells.sortedBy { it.level }) {
            val x = xOf(b.offsetCents)
            val height = (base - top) * (0.25f + 0.75f * b.level.toFloat())
            val sigma = w / 26f
            val path = Path().apply {
                moveTo(x - 3 * sigma, base)
                cubicTo(x - sigma, base, x - sigma * 0.8f, base - height, x, base - height)
                cubicTo(x + sigma * 0.8f, base - height, x + sigma, base, x + 3 * sigma, base)
                close()
            }
            val c = colourOf(b, p)
            val dropped = b.k in p.dropped
            drawPath(path, c.copy(alpha = if (dropped) 0.08f else 0.22f))
            drawPath(path, c.copy(alpha = if (dropped) 0.6f else 1f), style = Stroke(width = 2f,
                pathEffect = if (dropped) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null))
        }
        // the span
        paint.color = Color(Brand.WHITE_MUTED).toArgb(); paint.textSize = h * 0.06f
        paint.textAlign = android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText("−${BELLS_SPAN_C.toInt()}", 4f, h * 0.99f, paint)
        paint.textAlign = android.graphics.Paint.Align.CENTER
        drawContext.canvas.nativeCanvas.drawText("0 c from the anchor", cx, h * 0.99f, paint)
        paint.textAlign = android.graphics.Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText("+${BELLS_SPAN_C.toInt()}", w - 4f, h * 0.99f, paint)
    }
}

private fun colourOf(b: TuningPicture.Bell, p: TuningPicture): Color = when {
    b.k in p.falseMarked || b.k in p.dropped -> Color(Brand.FALSE_VIOLET)
    else -> Color(Brand.ORANGE)
}

@Composable
private fun Readout(p: TuningPicture) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            for ((text, weight) in listOf("k" to 1f, "target" to 3f, "read" to 3f, "cents" to 2f)) {
                FitText(text, 11.sp, Modifier.weight(weight), color = Color(Brand.WHITE_MUTED), align = if (weight == 1f) TextAlign.Start else TextAlign.End)
            }
        }
        for (b in p.bells) {
            val colour = when {
                b.k in p.falseMarked || b.k in p.dropped -> Color(Brand.FALSE_VIOLET)
                b.k == p.listened -> Color(Brand.WHITE)
                else -> Color(Brand.WHITE).copy(alpha = 0.85f)
            }
            val weight = if (b.k == p.listened) FontWeight.Bold else FontWeight.Normal
            Row(Modifier.fillMaxWidth().weight(1f)) {
                FitText(b.k.toString(), 13.sp, Modifier.weight(1f), color = colour, fontWeight = weight)
                FitText(String.format(Locale.ROOT, "%.1f", b.targetHz), 13.sp, Modifier.weight(3f), color = Color(Brand.TARGET_BLUE), align = TextAlign.End)
                FitText(String.format(Locale.ROOT, "%.1f", b.hz), 13.sp, Modifier.weight(3f), color = colour, fontWeight = weight, align = TextAlign.End)
                val cents = if (b.k in p.dropped) "dropped" else if (b.k in p.falseMarked) "false" else String.format(Locale.ROOT, "%+.2f", b.errorCents)
                FitText(cents, 13.sp, Modifier.weight(2f), color = colour, fontWeight = weight, align = TextAlign.End)
            }
        }
    }
}

/** Linear, ±[SCALE_SPAN_C]: equal distance, equal cents. The gate at the window's true width; green across when on. */
@Composable
private fun CentsScale(pointerCents: Double?, matchCents: Double, on: Boolean, sounding: Boolean) {
    val labelColor = Color(Brand.WHITE_MUTED).toArgb()
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val cx = w / 2f
        val half = (w / 2f - 8f).toDouble()
        val m = matchCents.coerceIn(0.02, SCALE_SPAN_C)
        fun xOf(cents: Double): Float = (cx + (cents / SCALE_SPAN_C).coerceIn(-1.0, 1.0) * half).toFloat()
        val axisY = h * 0.45f
        val green = Color(Brand.GO_GREEN)
        if (on) drawRect(green.copy(alpha = 0.18f), Offset(0f, 0f), Size(w, h * 0.8f))
        val left = xOf(-m); val right = xOf(m)
        val top = axisY - h * 0.30f; val bot = axisY + h * 0.30f
        val edge = if (on) green else Color(Brand.WHITE_MUTED)
        drawRect(green.copy(alpha = if (on) 0.30f else 0.08f), Offset(left, top), Size((right - left).coerceAtLeast(2f), bot - top))
        for (x in listOf(left, right)) {
            val inward = if (x == left) 1f else -1f
            drawLine(edge, Offset(x, top), Offset(x, bot), 4f)
            drawLine(edge, Offset(x, top), Offset(x + inward * h * 0.08f, top), 4f)
            drawLine(edge, Offset(x, bot), Offset(x + inward * h * 0.08f, bot), 4f)
        }
        drawLine(Color(Brand.WHITE).copy(alpha = 0.35f), Offset(8f, axisY), Offset(w - 8f, axisY), 2f)
        val paint = android.graphics.Paint().apply { color = labelColor; textSize = h * 0.18f; isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER }
        var t = -SCALE_SPAN_C
        while (t <= SCALE_SPAN_C + 1e-9) {
            val x = xOf(t)
            val labelled = abs(t) % 10.0 < 1e-9
            drawLine(Color(Brand.WHITE).copy(alpha = if (labelled) 0.5f else 0.3f), Offset(x, axisY - h * 0.1f), Offset(x, axisY + h * 0.1f), 2f)
            if (labelled) {
                val label = when {
                    t == 0.0 -> "0"
                    t == SCALE_SPAN_C -> String.format(Locale.ROOT, "+%.0f c", t)
                    t > 0 -> String.format(Locale.ROOT, "+%.0f", t)
                    else -> String.format(Locale.ROOT, "−%.0f", abs(t))
                }
                drawContext.canvas.nativeCanvas.drawText(label, x, h * 0.95f, paint)
            }
            t += 5.0
        }
        paint.color = edge.toArgb(); paint.textAlign = android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(String.format(Locale.ROOT, "±%.1f", m), right + h * 0.12f, top + h * 0.16f, paint)
        if (pointerCents != null) {
            val x = xOf(pointerCents)
            val c = (if (on) green else Color(Brand.ORANGE)).copy(alpha = if (sounding) 1f else 0.45f)
            val tri = Path().apply {
                moveTo(x, axisY - h * 0.42f); lineTo(x + h * 0.09f, axisY - h * 0.26f); lineTo(x - h * 0.09f, axisY - h * 0.26f); close()
            }
            drawPath(tri, c)
            drawLine(c, Offset(x, axisY - h * 0.26f), Offset(x, axisY + h * 0.3f), 5f)
        }
    }
}
