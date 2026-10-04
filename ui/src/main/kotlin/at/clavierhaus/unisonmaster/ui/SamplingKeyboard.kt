package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import at.clavierhaus.unisonmaster.Brand

/**
 * The piano's keyboard on the sampling page (Peter, 4 Oct 2026): every key
 * from the lowest of the compass to C8, 88 on a real piano. A sampled key
 * stands orange, the key caught from the last strike and not yet kept is
 * outlined orange, A4 — the reference — is green once set, and the
 * temperament octave is a line under its keys. A tap on a key says which:
 * the page offers the sampled one its undo. It draws what the engine holds
 * and keeps nothing of its own.
 */
@Composable
fun SamplingKeyboard(
    lowestMidi: Int,
    sampled: Set<Int>,
    caught: Int?,
    referenceSet: Boolean,
    temperament: IntRange,
    modifier: Modifier = Modifier,
    onKey: (Int) -> Unit = {},
) {
    val lowest = lowestMidi.coerceAtMost(A0)
    val keys = lowest..C8
    val whites = keys.count { !black(it) }
    val labelPaint = android.graphics.Paint().apply {
        color = Color(Brand.WHITE_MUTED).toArgb(); textSize = 22f
        textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
    }
    Canvas(
        modifier.pointerInput(lowest) {
            detectTapGestures { p ->
                val w = size.width.toFloat() / whites
                val labelBand = 30f
                val h = size.height - labelBand - 10f
                // a black key wins where it covers the white one
                val hitBlack = if (p.y <= h * 0.62f) keys.firstOrNull { m ->
                    black(m) && kotlin.math.abs(p.x - whitesBelow(lowest, m) * w) <= w * 0.31f
                } else null
                val key = hitBlack ?: keys.firstOrNull { m -> !black(m) && p.x >= whitesBelow(lowest, m) * w && p.x < (whitesBelow(lowest, m) + 1) * w }
                if (key != null) onKey(key)
            }
        },
    ) {
        val w = size.width / whites
        val labelBand = 30f
        val h = size.height - labelBand - 10f
        val orange = Color(Brand.ORANGE)
        fun fill(m: Int) = when {
            m == A4 && referenceSet -> Color(Brand.GO_GREEN)
            m in sampled -> orange
            black(m) -> Color(Brand.KEY_BLACK)
            else -> Color(Brand.KEY_WHITE)
        }
        for (m in keys) if (!black(m)) {
            val x = whitesBelow(lowest, m) * w
            drawRect(fill(m), Offset(x + 0.5f, 0f), Size((w - 1f).coerceAtLeast(1f), h))
            if (m == caught && m !in sampled) drawRect(orange, Offset(x + 2f, 2f), Size(w - 4f, h - 4f), style = Stroke(4f))
            if (m % 12 == 0) drawContext.canvas.nativeCanvas.drawText(Notes12.name(m), x + w / 2f, h + labelBand - 4f, labelPaint)
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
