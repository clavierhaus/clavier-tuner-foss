package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.picture.BandPhase
import at.clavierhaus.unisonmaster.picture.TuningPicture
import at.clavierhaus.unisonmaster.picture.TuningPicture.State
import java.util.Locale
import kotlin.math.abs

/** The bells' span either side of the anchor, cents: narrow enough that a bell's movement is seen undistorted. */
const val BELLS_SPAN_C = 6.0
/** A bell is never drawn narrower than this, cents, whatever its width says: a needle must stay visible. */
const val MIN_BELL_WIDTH_C = 0.2
/** The scale's span either side of the target, cents: linear, one strip. */
const val SCALE_SPAN_C = 30.0

/**
 * Draws a [TuningPicture] and nothing else (docs/UI.md): a split window. Left,
 * the tuning — the note, the partial, the linear scale with its gate. Right,
 * nothing but the bells on their centre line — no numbers over them, the chip
 * row below naming the partials instead. Every position is a fraction of the
 * pane. The picture decides what is shown; this decides only how.
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
    /** A tap on the partial line opens the stretch of the region the note is in (blueprint §3: one tap). */
    onStretchTap: (() -> Unit)? = null,
) {
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
                FitText(p.note, 40.sp, Modifier.fillMaxSize(), color = Color(Brand.WHITE), fontWeight = FontWeight.Bold)
            }
        }
        Place(0.17f, 0.08f, 0.30f, 0.10f) {
            val anchorNote = if (p.anchor != p.listened) " · bells on ${p.anchor}" else ""
            FitText(
                "partial ${p.listened}$anchorNote", 13.sp,
                Modifier.fillMaxSize().then(if (onStretchTap != null) Modifier.clickable(onClick = onStretchTap) else Modifier),
                color = Color(Brand.WHITE_MUTED),
            )
        }
        Place(0.00f, 0.34f, 0.47f, 0.40f) { CentsScale(p.pointerCents, matchCents, p.state != State.SILENT) }
        // right: the bands, and nothing else
        Place(0.52f, 0.00f, 0.48f, 0.98f) { Bands(p) }
    }
}

@Composable
private fun Bands(p: TuningPicture) {
    // The clock the bands drift on is wall time, not the rate at which the engine publishes: a band's
    // speed must be the string's own beat and nothing else.
    val millis by produceState(0L) { while (true) withFrameMillis { value = it } }
    // Where the segments stand is carried forward frame by frame and is never computed from the
    // frame clock's absolute time. The phase says nothing on its own — only the speed at which it
    // changes is the beat — and multiplying a live rate by an hours-old clock tied the whole
    // elapsed session to every whisper in the reading, so the pattern teleported between frames
    // and stillness at the target could not be drawn (picture/BandPhase.kt has the measurement).
    val carried = remember(p.note) { BandPhase() }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val top = h * 0.05f; val base = h * 0.93f
        val ks = p.bands
        if (ks.isEmpty()) return@Canvas
        val cw = w / ks.size
        // The drift **is** the beat: the partial as read against the partial as asked, in hertz.
        // Band k carries k segments, so the pattern rate is Δf/k — the same for every partial of a
        // string uniformly off, which makes a clean string one moving object and a false partial
        // the only thing out of step. Law 1 drawn rather than asserted (blueprint, 6 Oct 2026).
        // Negated once, here: down is flat, so a string under its target carries the segments down.
        val rates = ks.mapNotNull { k ->
            p.bells.firstOrNull { it.k == k }?.let { k to -(it.hz - it.targetHz) / k }
        }.toMap()
        val phases = carried.advance(millis, rates)
        for ((i, k) in ks.withIndex()) {
            val b = p.bells.firstOrNull { it.k == k } ?: continue
            val bw = cw * 0.52f
            val bx = i * cw + (cw - bw) / 2f
            val bh = base - top
            val sh = bh / k
            val shift = ((phases[k] ?: 0.0) * sh).toFloat()
            // The width law, drawn at last. A partial read near the room's floor has a jittery
            // frequency, so its band would drift at a jittery rate and could stand out of step on a
            // perfectly clean string — the very false indication ReadingSpread was built to confess
            // (Mistral, 6 Oct 2026). Rule 0 forbids dropping it for being quiet, so instead the band
            // says how well it was read: a needle is crisp and full, a partial at the floor is pale
            // with soft ends. "I am quiet" — not "the string moved".
            val sharp = (MIN_BELL_WIDTH_C / (b.widthCents ?: MIN_BELL_WIDTH_C)
                .coerceAtLeast(MIN_BELL_WIDTH_C)).toFloat().coerceIn(0.18f, 1f)
            val c = (if (k in p.falseMarked) Color(Brand.FALSE_VIOLET) else Color(Brand.ORANGE))
                .copy(alpha = (if (k in p.dropped) 0.20f else 0.35f + 0.65f * sharp))
            clipRect(bx, top, bx + bw, base) {
                val solid = sh * 0.46f
                val feather = solid * (1f - sharp) * 0.6f
                for (seg in 0..k) {
                    val y = top + seg * sh + shift - sh
                    drawRect(c, Offset(bx, y), Size(bw, solid))
                    if (feather > 0.5f) {
                        drawRect(c.copy(alpha = c.alpha * 0.35f), Offset(bx, y - feather), Size(bw, feather))
                        drawRect(c.copy(alpha = c.alpha * 0.35f), Offset(bx, y + solid), Size(bw, feather))
                    }
                }
            }
            drawRect(Color(Brand.WHITE_MUTED).copy(alpha = 0.30f), Offset(bx, top), Size(bw, bh), style = Stroke(1f))
        }
    }
}

/** Linear, ±[SCALE_SPAN_C]: equal distance, equal cents. The gate at the window's true width; green across when on. */
@Composable
private fun CentsScale(pointerCents: Double?, matchCents: Double, sounding: Boolean) {
    val labelColor = Color(Brand.WHITE_MUTED).toArgb()
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val cx = w / 2f
        val half = (w / 2f - 8f).toDouble()
        val m = matchCents.coerceIn(0.02, SCALE_SPAN_C)
        fun xOf(cents: Double): Float = (cx + (cents / SCALE_SPAN_C).coerceIn(-1.0, 1.0) * half).toFloat()
        val axisY = h * 0.45f
        // No wash, and no green: the screen reports and does not pronounce. A tuner reads stillness
        // and calls the arrival himself, and in the bass he may leave a string flat on purpose
        // (Peter, 6 Oct 2026). The gate is drawn because its width is a measurement, never a verdict.
        val left = xOf(-m); val right = xOf(m)
        val top = axisY - h * 0.30f; val bot = axisY + h * 0.30f
        val edge = Color(Brand.WHITE_MUTED)
        drawRect(edge.copy(alpha = 0.10f), Offset(left, top), Size((right - left).coerceAtLeast(2f), bot - top))
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
            val c = Color(Brand.ORANGE).copy(alpha = if (sounding) 1f else 0.45f)
            val tri = Path().apply {
                moveTo(x, axisY - h * 0.42f); lineTo(x + h * 0.09f, axisY - h * 0.26f); lineTo(x - h * 0.09f, axisY - h * 0.26f); close()
            }
            drawPath(tri, c)
            drawLine(c, Offset(x, axisY - h * 0.26f), Offset(x, axisY + h * 0.3f), 5f)
        }
    }
}
