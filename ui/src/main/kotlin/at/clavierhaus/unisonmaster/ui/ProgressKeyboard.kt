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

/** The 88 keys of a piano: A0 at the bottom, C8 at the top. */
private const val MIDI_A0 = 21
private const val MIDI_C8 = 108

private fun isBlack(midi: Int): Boolean = when ((midi % 12 + 12) % 12) {
    1, 3, 6, 8, 10 -> true
    else -> false
}

/** How many white keys lie below [midi], counting from A0. */
private fun whitesBelow(midi: Int): Int = (MIDI_A0 until midi).count { !isBlack(it) }

private val WHITE_KEYS = (MIDI_A0..MIDI_C8).count { !isBlack(it) }   // 52

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
    modifier: Modifier = Modifier,
) {
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
        val w = size.width / WHITE_KEYS
        val blackW = w * 0.62f
        val blackH = keyboardHeight * 0.62f

        // white keys first, the black ones over them, exactly as on the instrument
        for (midi in MIDI_A0..MIDI_C8) {
            if (isBlack(midi)) continue
            val x = whitesBelow(midi) * w
            drawRect(
                color = Color(if (midi in done) Brand.KEY_DONE_WHITE else Brand.KEY_WHITE),
                topLeft = Offset(x + 0.5f, keyboardTop),
                size = Size((w - 1f).coerceAtLeast(1f), keyboardHeight),
            )
        }
        for (midi in MIDI_A0..MIDI_C8) {
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
        if (deviations.isNotEmpty() && curveBand > 12f) {
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
            val up = maxOf(5.0, smooth.values.maxOf { it } * 1.15)
            val down = maxOf(5.0, -smooth.values.minOf { it } * 1.15)
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
            for (i in 1 until pts.size) {
                val a = pts[i - 1]
                val b = pts[i]
                if (b - a > CURVE_REACH) continue   // a gap in the measurements is left as a gap
                drawLine(
                    color = Color(Brand.WHITE),
                    start = Offset(xOf(a), yOf(smooth.getValue(a))),
                    end = Offset(xOf(b), yOf(smooth.getValue(b))),
                    strokeWidth = 2f,
                )
            }
            drawContext.canvas.nativeCanvas.drawText(
                "%+.0f c".format(roomUp / pxPerCent), 2f, mid - roomUp + 14f, captionPaint,
            )
            drawContext.canvas.nativeCanvas.drawText(
                "%+.0f c".format(-roomDown / pxPerCent), 2f, mid + roomDown, captionPaint,
            )
            drawContext.canvas.nativeCanvas.drawText(
                "measured — a result, not a target", 2f, curveBand - 2f, captionPaint,
            )
        }

        // where the session stands
        if (current != null && current in MIDI_A0..MIDI_C8) {
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
            for (midi in MIDI_A0..MIDI_C8) {
                if (midi % 12 != 0) continue          // every C
                val x = whitesBelow(midi) * w + w / 2f
                canvas.drawText("C${midi / 12 - 1}", x, size.height - 6f, labelPaint)
            }
        }
    }
}

/** Where equal temperament sits in the curve band, as a fraction of its height from the top. */
private const val CURVE_ZERO = 0.36f

/** Semitones either side of a note that its drawn value is smoothed over. */
private const val CURVE_REACH = 3
