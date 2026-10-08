package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import at.clavierhaus.unisonmaster.Brand

/** The keys of a piano: A0 at the bottom of most, F0 or C0 on the large Bösendorfers, C8 at the top. */
private const val MIDI_A0 = 21
private const val MIDI_C8 = 108

private fun isBlack(midi: Int): Boolean = when ((midi % 12 + 12) % 12) {
    1, 3, 6, 8, 10 -> true
    else -> false
}

/** How many white keys lie between [lowest] and [midi]. */
private fun whitesFrom(lowest: Int, midi: Int): Int = (lowest until midi).count { !isBlack(it) }

/**
 * The whole compass at a glance: every key that has been tuned is green.
 *
 * A tuner's own question mid-session is "where am I, and what is left" — the
 * keyboard answers it in the shape the instrument already has, so nothing has
 * to be read. White keys that are done take the confirmation green; black
 * keys that are done take the deeper green of [Brand.KEY_DONE_BLACK], so the
 * black-key pattern stays legible and the eye can still count octaves.
 *
 * [current] is marked by an orange tick beneath its key. The C of each octave
 * is labelled, because a keyboard without landmarks is hard to read at a
 * glance on a phone.
 */
@Composable
fun ProgressKeyboard(
    done: Set<Int>,
    current: Int?,
    deviations: Map<Int, Double> = emptyMap(),
    /** The curve this piano's measured inharmonicity asks for: every key, cents from equal temperament. Not drawn since 8 Oct 2026; kept so the band can still size itself when nothing is measured yet. */
    curve: Map<Int, Double> = emptyMap(),
    lowestMidi: Int = MIDI_A0,
    modifier: Modifier = Modifier,
) {
    val lowest = lowestMidi.coerceIn(0, MIDI_A0)
    val whiteKeys = (lowest..MIDI_C8).count { !isBlack(it) }
    fun whitesBelow(midi: Int) = whitesFrom(lowest, midi)
    val labelPaint = android.graphics.Paint().apply {
        color = Color(Brand.WHITE_MUTED).toArgb()
        textSize = 20f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    val captionPaint = android.graphics.Paint().apply {
        color = Color(Brand.WHITE_MUTED).toArgb()
        textSize = 18f
        isAntiAlias = true
    }
    Canvas(modifier) {
        // the curve stands above the keys, over the same note axis
        val curveBand = if (deviations.isEmpty()) 0f else size.height * 0.42f
        val labelBand = 26f
        val tickBand = 8f
        val keyboardTop = curveBand
        val keyboardHeight = (size.height - curveBand - labelBand - tickBand).coerceAtLeast(1f)
        val w = size.width / whiteKeys
        val blackW = w * 0.62f
        val blackH = keyboardHeight * 0.62f

        // white keys first, the black ones over them, exactly as on the instrument
        for (midi in lowest..MIDI_C8) {
            if (isBlack(midi)) continue
            val x = whitesBelow(midi) * w
            drawRect(
                color = Color(if (midi in done) Brand.KEY_DONE_WHITE else Brand.KEY_WHITE),
                topLeft = Offset(x + 0.5f, keyboardTop),
                size = Size((w - 1f).coerceAtLeast(1f), keyboardHeight),
            )
        }
        for (midi in lowest..MIDI_C8) {
            if (!isBlack(midi)) continue
            val x = whitesBelow(midi) * w - blackW / 2f
            drawRect(
                color = Color(if (midi in done) Brand.KEY_DONE_BLACK else Brand.KEY_BLACK),
                topLeft = Offset(x, keyboardTop),
                size = Size(blackW, blackH),
            )
        }


        // --- the tuning as executed -------------------------------------
        // How far the tuning stands from equal temperament across the
        // compass: the measured first partials, smoothed over the neighbouring
        // notes (a triangular window, ±3 semitones) and drawn as one line, no
        // point per note. It is a result, never followed — the target of a
        // string is its own measurement — and it is drawn so that no single
        // note stands out to be "corrected" from here: one note's reading
        // apart from its neighbours is not a wrong note, it is the octave
        // links doing their work, or a reading to be struck again.
        if ((deviations.isNotEmpty() || curve.isNotEmpty()) && curveBand > 12f) {
            val pts = deviations.keys.sorted()
            val smooth = pts.associateWith { midi ->
                var num = 0.0; var den = 0.0
                for (d in -CURVE_REACH..CURVE_REACH) {
                    val v = deviations[midi + d] ?: continue
                    val wgt = (CURVE_REACH + 1 - kotlin.math.abs(d)).toDouble()
                    num += wgt * v; den += wgt
                }
                num / den
            }
            // Equal temperament sits in the upper part of the band: a tuned
            // piano's curve falls away below it in the bass, by tens of
            // cents on a small instrument, and rises less in the treble.
            // One scale for both directions, chosen so that the whole curve
            // fits its room above and below.
            val mid = curveBand * CURVE_ZERO
            val roomUp = curveBand * (CURVE_ZERO - 0.08f)
            val roomDown = curveBand * (1f - CURVE_ZERO - 0.10f)
            // the scale is the measured line's own; the curve is not drawn (8 Oct 2026)
            val shown = smooth.values
            val up = maxOf(5.0, (shown.maxOrNull() ?: 0.0) * 1.15)
            val down = maxOf(5.0, -(shown.minOrNull() ?: 0.0) * 1.15)
            val pxPerCent = minOf(roomUp / up, roomDown / down).toFloat()
            fun yOf(cents: Double) = mid - (cents * pxPerCent).toFloat()
            fun xOf(midi: Int) =
                if (isBlack(midi)) whitesBelow(midi) * w else whitesBelow(midi) * w + w / 2f

            // equal temperament, for reference only
            drawRect(
                color = Color(Brand.WHITE_MUTED).copy(alpha = 0.25f),
                topLeft = Offset(0f, mid),
                size = Size(size.width, 1f),
            )
            // One line, one colour: the piano as tuned, note by note, smoothed over its neighbours.
            // Until 8 Oct 2026 the curve the sampled inharmonicity predicts was drawn under it in
            // blue, and the two read as one curve in two colours that disagree (Peter: "misleading
            // at best"). The prediction stays in the session and the targets are taken from it; it
            // is not drawn here, because a prediction must not share a line with a measurement, and
            // drawn alone where nothing was measured it would be the misleading thing. Each measured
            // note is also a dot, so the line's smoothing hides no reading.
            for (i in 1 until pts.size) {
                val a = pts[i - 1]
                val b = pts[i]
                if (b - a > CURVE_REACH) continue   // a gap in the measurements is left as a gap
                drawLine(
                    color = Color(Brand.WHITE),
                    start = Offset(xOf(a), yOf(smooth.getValue(a))),
                    end = Offset(xOf(b), yOf(smooth.getValue(b))),
                    strokeWidth = 2.5f,
                )
            }
            for (midi in pts) drawCircle(Color(Brand.WHITE), radius = 3f, center = Offset(xOf(midi), yOf(deviations.getValue(midi))))
            drawContext.canvas.nativeCanvas.drawText(
                "%+.0f c".format(roomUp / pxPerCent), 2f, mid - roomUp + 14f, captionPaint,
            )
            drawContext.canvas.nativeCanvas.drawText(
                "%+.0f c".format(-roomDown / pxPerCent), 2f, mid + roomDown, captionPaint,
            )
            drawContext.canvas.nativeCanvas.drawText("measured — the piano as tuned, in cents from equal temperament", 2f, curveBand - 2f, captionPaint)
        }

        // where the session stands
        if (current != null && current in lowest..MIDI_C8) {
            val centre = if (isBlack(current)) whitesBelow(current) * w
            else whitesBelow(current) * w + w / 2f
            drawRect(
                color = Color(Brand.ORANGE),
                topLeft = Offset(centre - w * 0.35f, keyboardTop + keyboardHeight + 2f),
                size = Size(w * 0.7f, tickBand - 3f),
            )
        }

        // one landmark per octave
        drawContext.canvas.nativeCanvas.let { canvas ->
            for (midi in lowest..MIDI_C8) {
                if (midi % 12 != 0) continue          // every C
                val x = whitesBelow(midi) * w + w / 2f
                canvas.drawText("C${midi / 12 - 1}", x, size.height - 6f, labelPaint)
            }
        }
    }
}

/** Where equal temperament sits in the curve band, as a fraction of its height from the top. */
private const val CURVE_ZERO = 0.36f

/**
 * Semitones either side of a note that its drawn value is smoothed over, triangular. Five since
 * 8 Oct 2026 (three before): the line is to show the piano's stretch, and strike-to-strike
 * repeatability in the bass is about 1.5 c (ENGINE.md), which three semitones left visible as
 * jitter. Every note's own reading is drawn as a dot beside the line, so nothing is hidden.
 */
private const val CURVE_REACH = 5
