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
    modifier: Modifier = Modifier,
) {
    val labelPaint = android.graphics.Paint().apply {
        color = Color(Brand.WHITE_MUTED).toArgb()
        textSize = 20f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    Canvas(modifier) {
        val labelBand = 26f
        val tickBand = 8f
        val keyboardHeight = (size.height - labelBand - tickBand).coerceAtLeast(1f)
        val w = size.width / WHITE_KEYS
        val blackW = w * 0.62f
        val blackH = keyboardHeight * 0.62f

        // white keys first, the black ones over them, exactly as on the instrument
        for (midi in MIDI_A0..MIDI_C8) {
            if (isBlack(midi)) continue
            val x = whitesBelow(midi) * w
            drawRect(
                color = Color(if (midi in done) Brand.KEY_DONE_WHITE else Brand.KEY_WHITE),
                topLeft = Offset(x + 0.5f, 0f),
                size = Size((w - 1f).coerceAtLeast(1f), keyboardHeight),
            )
        }
        for (midi in MIDI_A0..MIDI_C8) {
            if (!isBlack(midi)) continue
            val x = whitesBelow(midi) * w - blackW / 2f
            drawRect(
                color = Color(if (midi in done) Brand.KEY_DONE_BLACK else Brand.KEY_BLACK),
                topLeft = Offset(x, 0f),
                size = Size(blackW, blackH),
            )
        }

        // where the session stands
        if (current != null && current in MIDI_A0..MIDI_C8) {
            val centre = if (isBlack(current)) whitesBelow(current) * w
            else whitesBelow(current) * w + w / 2f
            drawRect(
                color = Color(Brand.ORANGE),
                topLeft = Offset(centre - w * 0.35f, keyboardHeight + 2f),
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
