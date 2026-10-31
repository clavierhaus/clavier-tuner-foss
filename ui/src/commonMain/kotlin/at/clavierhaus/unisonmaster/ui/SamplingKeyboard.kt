package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import at.clavierhaus.unisonmaster.Brand

/**
 * The piano's keyboard on the sampling page (Peter, 4 Oct 2026): every key
 * from the lowest of the range to C8, 88 on a real piano. A sampled key
 * stands green, as A4 — the reference — does once it is set; the key caught
 * from the last strike and not yet kept is outlined orange, and the
 * temperament octave is a line under its keys.
 *
 * It takes no touch. A key is 17 dp wide on a 891 dp screen — a sixth of
 * what a finger wants — and a picture that answers a tap invites one it
 * cannot place: the keyboard is the indicator of the key and the picture of
 * the progress, and nothing else (Peter, 5 Oct 2026, "the right side of the
 * sampling screen is the only interactive area"). Undo is a button there.
 * It draws what the engine holds and keeps nothing of its own.
 */
@Composable
fun SamplingKeyboard(
    lowestMidi: Int,
    sampled: Set<Int>,
    caught: Int?,
    referenceSet: Boolean,
    temperament: IntRange,
    modifier: Modifier = Modifier,
    /** Kept strings whose measurement the next strike cut short: yellow, until struck again in full. */
    short: Set<Int> = emptySet(),
) {
    val lowest = lowestMidi.coerceAtMost(A0)
    val keys = lowest..C8
    val whites = keys.count { !black(it) }
    Canvas(modifier) {
        val w = size.width / whites
        val labelBand = 30f
        val h = size.height - labelBand - 10f
        val orange = Color(Brand.ORANGE)
        // a string kept is done, and done is green, as A4 is (Peter, 4 Oct 2026: orange on a sampled key
        // said "look here" where nothing was wanted); orange is for the key caught and not yet kept, below
        fun fill(m: Int) = when {
            m == A4 && referenceSet -> Color(Brand.GO_GREEN)
            m in sampled && m in short -> Color(Brand.MEASURED_SHORT)
            m in sampled -> Color(Brand.GO_GREEN)
            black(m) -> Color(Brand.KEY_BLACK)
            else -> Color(Brand.KEY_WHITE)
        }
        for (m in keys) if (!black(m)) {
            val x = whitesBelow(lowest, m) * w
            drawRect(fill(m), Offset(x + 0.5f, 0f), Size((w - 1f).coerceAtLeast(1f), h))
            if (m == caught && m !in sampled) drawRect(orange, Offset(x + 2f, 2f), Size(w - 4f, h - 4f), style = Stroke(4f))
            if (m % 12 == 0) drawLabel(Notes12.name(m), x + w / 2f, h + labelBand - 4f, Color(Brand.WHITE_MUTED), 22f, LabelAlign.CENTER)
        }
        for (m in keys) if (black(m)) {
            val x = whitesBelow(lowest, m) * w - w * 0.31f
            drawRect(fill(m), Offset(x, 0f), Size(w * 0.62f, h * 0.62f))
            if (m == caught && m !in sampled) drawRect(orange, Offset(x, 0f), Size(w * 0.62f, h * 0.62f), style = Stroke(4f))
        }
        // the temperament octave: a line under its keys
        val x0 = whitesBelow(lowest, temperament.first) * w
        val x1 = (whitesBelow(lowest, temperament.last) + 1) * w
        drawRect(orange, Offset(x0, h + 3f), Size(x1 - x0, 4f))
    }
}

private const val A0 = 21
private const val A4 = 69
private const val C8 = 108

private fun black(m: Int) = when ((m % 12 + 12) % 12) { 1, 3, 6, 8, 10 -> true; else -> false }
private fun whitesBelow(lowest: Int, m: Int) = (lowest until m).count { !black(it) }

/** C-names for the keyboard's labels: C1, C2 … (only the Cs are labelled). */
private object Notes12 {
    fun name(m: Int) = "C" + (m / 12 - 1)
}
