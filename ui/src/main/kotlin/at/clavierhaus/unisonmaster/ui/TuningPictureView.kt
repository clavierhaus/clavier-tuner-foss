package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.picture.TuningPicture
import at.clavierhaus.unisonmaster.picture.TuningPicture.State
import java.util.Locale
import kotlin.math.abs

/** The bells' span either side of the anchor, cents: narrow enough that a bell's movement is seen undistorted. */
const val BELLS_SPAN_C = 6.0
/** The scale's span either side of the target, cents: linear, one strip. */
const val SCALE_SPAN_C = 30.0

/**
 * Draws a [TuningPicture] and nothing else (docs/UI.md): a split window. Left,
 * the tuning — the note, the partial, the linear scale with its gate. Right,
 * nothing but the bells on their centre line, the partial numbers in one row
 * above them. Every position is a fraction of the pane. The picture decides
 * what is shown; this decides only how.
 *
 * The note name is the one control on the picture: the screen follows the key
 * struck by default, and a tap on the name ([onNoteTap]) lets the tuner name
 * a key by hand — the picker — after which the screen stays on it; a tap on
 * the name then hands the choice back to the strike. While the key is set by
 * hand ([follows] false) a thin line under the name says so.
 */
@Composable
fun TuningPictureView(
    p: TuningPicture,
    matchCents: Double,
    modifier: Modifier = Modifier,
    follows: Boolean = true,
    onNoteTap: (() -> Unit)? = null,
) {
    val on = p.state == State.ON
    Pane(modifier) {
        // left: the tuning
        Place(0.00f, 0.00f, 0.16f, 0.22f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (onNoteTap != null) Modifier.clickable(onClick = onNoteTap) else Modifier)
                    .drawBehind {
                        if (!follows) drawLine(Color(Brand.WHITE_MUTED), Offset(0f, size.height - 2f), Offset(size.width, size.height - 2f), 3f)
                    },
            ) {
                FitText(p.note, 40.sp, Modifier.fillMaxSize(), color = Color(if (on) Brand.GO_GREEN else Brand.WHITE), fontWeight = FontWeight.Bold)
            }
        }
        Place(0.17f, 0.08f, 0.30f, 0.10f) {
            val anchorNote = if (p.anchor != p.listened) " · bells on ${p.anchor}" else ""
            FitText("partial ${p.listened}$anchorNote", 13.sp, Modifier.fillMaxSize(), color = Color(Brand.WHITE_MUTED))
        }
        Place(0.00f, 0.34f, 0.47f, 0.40f) { CentsScale(p.pointerCents, matchCents, on, p.state != State.SILENT) }
        // right: the bells, and nothing else
        Place(0.52f, 0.00f, 0.48f, 0.98f) { Bells(p) }
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
        val shown = p.bells.filter { it.k !in p.dropped }
        val n = shown.size
        shown.forEachIndexed { i, b ->
            val lx = w * (0.5f + i) / n.coerceAtLeast(1)
            paint.color = colourOf(b, p).toArgb()
            drawContext.canvas.nativeCanvas.drawText(b.k.toString(), lx, h * 0.10f, paint)
        }
        // the bells: height says level, place says distance from the anchor
        for (b in p.bells.filter { it.k !in p.dropped }.sortedBy { it.level }) {
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
            drawPath(path, c.copy(alpha = 0.22f))
            drawPath(path, c, style = Stroke(width = 2f))
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
