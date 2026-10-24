package at.clavierhaus.unisonmaster.ui

import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.i18n.K
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
import kotlin.math.abs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** The bells' span either side of the anchor, cents: narrow enough that a bell's movement is seen undistorted. */
const val BELLS_SPAN_C = 6.0
/** A bell is never drawn narrower than this, cents, whatever its width says: a needle must stay visible. */
const val MIN_BELL_WIDTH_C = 0.2
/** The panels' corner radius, dp: the two regions of the picture are boxes on the ground, not areas of it. */
private const val PANEL_RADIUS_DP = 12

/**
 * Draws a [TuningPicture] and nothing else (docs/UI.md): a split window. Left,
 * the tuning — the note, the partial, the bells in their panel. Right,
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
    /**
     * Whether a band is coloured by what the engine claims of its partial -- red-violet for a
     * measured false beat, greyed for one the tuner took out. Off while sampling: the string is
     * being measured there, not tuned, and the stripes keep one colour (Peter, 7 Oct 2026, 10:18);
     * the claims themselves are the picture's and are untouched.
     */
    marks: Boolean = true,
    onNoteTap: (() -> Unit)? = null,
    /** A tap on the partial line opens the stretch of the region the note is in (blueprint §3: one tap). */
    onStretchTap: (() -> Unit)? = null,
    /** Whether the bells are drawn. Not on the A4 page: there the strobe alone (Peter, 22 Oct 2026). */
    bells: Boolean = true,
    /** What an edition draws in the left panel, blank since 23 Oct 2026. */
    left: (@Composable () -> Unit)? = null,
    /** Bands an edition draws green, and bands it dims (24 Oct 2026): the picture's colours, its own reasons. */
    greenBands: Set<Int> = emptySet(),
    dimBands: Set<Int> = emptySet(),
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
        // the partial line belongs to the bells; without them (the A4 page) it stood under the strobe and
        // was cut to "parti" (Peter, 23 Oct 2026)
        if (bells) Place(0.17f, 0.08f, 0.30f, 0.10f) {
            val anchorNote = if (p.anchor != p.listened) t(K.tuning_bellsOn, p.anchor) else ""
            FitText(
                t(K.tuning_partialOne, p.listened) + anchorNote, 13.sp,
                Modifier.fillMaxSize().then(if (onStretchTap != null) Modifier.clickable(onClick = onStretchTap) else Modifier),
                color = Color(Brand.WHITE_MUTED),
            )
        }
        // Two regions, two panels (Peter, 7 Oct 2026, 15:29): the strobe says when the string has
        // arrived -- it stops -- and the bells say where it stands and whether its partials agree,
        // a clean string's bells stacked on the anchor. The linear scale and its arrow said the
        // same as the bells with less, and are gone. Each region is a box with round corners on a
        // ground one step lighter than the page, so the eye finds it as a region; all black read as
        // unordered.
        if (bells) {
            // The bells panel stands empty since 23 Oct 2026 (Peter, at the Steinway B, D2: the bells danced
            // over the panel on a single string and on the unison alike, while the strobe of the same
            // partials came to a complete standstill on new Heller strings; "even if it worked, what help
            // would it give to a tuner?"). The strobe stays where it is; the left side is left blank, and
            // Bells() is kept for the unison boxes Pro may draw later. Why the bells moved where the strobe
            // stood is not yet shown from a recording (SAMICK findings §33).
            if (left != null) Place(0.00f, 0.24f, 0.48f, 0.74f) { Panel { left() } }
            Place(0.52f, 0.00f, 0.48f, 0.98f) { Panel { Bands(p, marks, greenBands, dimBands) } }
        } else Place(0.00f, 0.24f, 1.00f, 0.76f) { Panel { Bands(p, marks, greenBands, dimBands) } }   // the full width, column over the button below it
    }
}

/** A region of the picture: a rounded box on a ground one step off the page's. */
@Composable
private fun Panel(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Color(Brand.PANEL), RoundedCornerShape(PANEL_RADIUS_DP.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) { content() }
}

/**
 * The bells: one string seen several ways. Each partial a bell on one baseline, its place its
 * distance from the anchor in cents, its height its level, its width how well it was read (its own
 * measured spread, never narrower than MIN_BELL_WIDTH_C). A clean string's bells stand on one line
 * (Law 1); a false partial's bell stands out of it, and is coloured when [marks] is on. Drawn as
 * they were until 6 Oct 2026, back by Peter's word of 7 Oct, 15:29, beside the strobe instead of
 * instead of it.
 */
@Composable
private fun Bells(p: TuningPicture, marks: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val base = h * 0.90f
        val top = h * 0.06f
        val cx = w / 2f
        val half = w / 2f * 0.9f
        fun xOf(cents: Double): Float = (cx + (cents / BELLS_SPAN_C).coerceIn(-1.0, 1.0) * half).toFloat()
        // the anchor line, fixed in the panel
        drawLine(Color(Brand.WHITE).copy(alpha = 0.6f), Offset(cx, top), Offset(cx, base), 2f)
        drawLine(Color(Brand.WHITE_MUTED), Offset(0f, base), Offset(w, base), 1f)
        val perCent = half / BELLS_SPAN_C.toFloat()
        // Every bell is drawn inside its panel (Peter, 10 Oct 2026: bells left the box). A bell's
        // centre is held to the span, but its skirt reaches 3 widths either side of it and a wide
        // bell near the edge, or one at the edge, ran past the panel's sides; a Canvas does not
        // clip by itself. Only the drawing is cut at the panel's edge: where each bell stands, how
        // high and how wide, is what the picture says, as before.
        clipRect(0f, 0f, w, h) {
        for (b in p.bells.filter { it.k !in p.dropped }.sortedBy { it.level }) {
            val x = xOf(b.offsetCents)
            val height = (base - top) * (0.25f + 0.75f * b.level.toFloat())
            val sigma = perCent * (b.widthCents ?: MIN_BELL_WIDTH_C).coerceIn(MIN_BELL_WIDTH_C, BELLS_SPAN_C).toFloat()
            val path = Path().apply {
                moveTo(x - 3 * sigma, base)
                cubicTo(x - sigma, base, x - sigma * 0.8f, base - height, x, base - height)
                cubicTo(x + sigma * 0.8f, base - height, x + sigma, base, x + 3 * sigma, base)
                close()
            }
            val c = if (marks && b.k in p.falseMarked) Color(Brand.FALSE_VIOLET) else Color(Brand.ORANGE)
            drawPath(path, c.copy(alpha = 0.22f))
            drawPath(path, c, style = Stroke(width = 2f))
        }
        }
        // the span, in the panel's foot
        val paint = android.graphics.Paint().apply { color = Color(Brand.WHITE_MUTED).toArgb(); textSize = h * 0.07f; isAntiAlias = true }
        paint.textAlign = android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText("−${BELLS_SPAN_C.toInt()}", 2f, h * 0.99f, paint)
        paint.textAlign = android.graphics.Paint.Align.CENTER
        drawContext.canvas.nativeCanvas.drawText(t(K.tuning_fromAnchor), cx, h * 0.99f, paint)
        paint.textAlign = android.graphics.Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText("+${BELLS_SPAN_C.toInt()}", w - 2f, h * 0.99f, paint)
    }
}

@Composable
private fun Bands(p: TuningPicture, marks: Boolean, greens: Set<Int> = emptySet(), dims: Set<Int> = emptySet()) {
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
        // the gaps are what keep bands apart; a lone band has nothing to keep apart from and takes
        // more of its column (Peter, 7 Oct 2026: the one band of the A4 screen, larger)
        val bandShare = if (ks.size == 1) 0.72f else 0.52f
        for ((i, k) in ks.withIndex()) {
            val bw = cw * bandShare
            val bx = i * cw + (cw - bw) / 2f
            val bh = base - top
            val b = p.bells.firstOrNull { it.k == k }
            if (b == null) {
                // a fixed seat with nothing in it: its frame stands, so the row never closes up
                drawRect(Color(Brand.WHITE_MUTED).copy(alpha = 0.18f), Offset(bx, top), Size(bw, bh), style = Stroke(1f))
                continue
            }
            val sh = bh / k
            val shift = ((phases[k] ?: 0.0) * sh).toFloat()
            // Every band the same: one colour, solid, the segments crisp -- a tuner reads the strobe
            // for its motion and two kinds of band in it read as two kinds of thing (Peter, 7 Oct
            // 2026, 16:10: "two different colours and thus sizes"). How well a partial was read is
            // the bells' to say, as a bell's width, in the other panel. A partial the tuner took out
            // is not here at all: the picture gives it no seat (8 Oct 2026).
            val c = when {
                k in dims -> Color(Brand.WHITE_MUTED).copy(alpha = 0.25f)
                k in greens -> Color(Brand.GO_GREEN)
                marks && k in p.falseMarked -> Color(Brand.FALSE_VIOLET)
                else -> Color(Brand.ORANGE)
            }
            clipRect(bx, top, bx + bw, base) {
                val solid = sh * 0.46f
                for (seg in 0..k) {
                    val y = top + seg * sh + shift - sh
                    drawRect(c, Offset(bx, y), Size(bw, solid))
                }
            }
            drawRect(Color(Brand.WHITE_MUTED).copy(alpha = 0.30f), Offset(bx, top), Size(bw, bh), style = Stroke(1f))
        }
    }
}

