package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import at.clavierhaus.unisonmaster.Brand
import kotlin.math.exp
import kotlin.math.ln

/**
 * The A4 string as one bell on the sampling page (Peter, 4 Oct 2026): a
 * linear ±[SPAN_C] cent strip around the pitch aimed at, the match window
 * drawn at its true width, and the string's live reading as a single bell —
 * green inside the window, orange outside, nothing at all when no string
 * sounds (the reading dies with its sound). It draws the engine's reading
 * and the tuner's aim; it computes nothing of its own beyond the cents
 * between them.
 */
@Composable
fun A4Bell(hz: Double?, aimHz: Double, matchCents: Double, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val mid = w / 2f
        val perCent = w / (2 * SPAN_C).toFloat()
        val base = h - 4f
        // the strip and its window
        drawRect(Color(0xFF2A2A2A), Offset(0f, base), Size(w, 2f))
        val gate = (matchCents * perCent).toFloat()
        drawRect(Color(Brand.GO_GREEN).copy(alpha = 0.18f), Offset(mid - gate, 0f), Size(2 * gate, base))
        for (c in listOf(-20, -10, 10, 20)) drawRect(Color(0xFF444444), Offset(mid + c * perCent, base - 8f), Size(1f, 8f))
        drawRect(Color(Brand.WHITE_MUTED), Offset(mid - 0.5f, 0f), Size(1f, base))
        // the bell, where the string stands
        val f = hz ?: return@Canvas
        val cents = 1200.0 * ln(f / aimHz) / ln(2.0)
        val x = mid + (cents.coerceIn(-SPAN_C, SPAN_C) * perCent).toFloat()
        val colour = if (kotlin.math.abs(cents) <= matchCents) Color(Brand.GO_GREEN) else Color(Brand.ORANGE)
        val sigma = (BELL_C * perCent).toFloat()
        val path = Path().apply {
            moveTo(x - 4 * sigma, base)
            var dx = -4 * sigma
            while (dx <= 4 * sigma) { lineTo(x + dx, base - (base - 6f) * exp(-(dx * dx) / (2 * sigma * sigma))); dx += 2f }
            lineTo(x + 4 * sigma, base)
        }
        drawPath(path, colour.copy(alpha = 0.35f))
        drawPath(path, colour, style = Stroke(3f))
    }
}

/** The strip's half width in cents: the blueprint's one ±30 c scale. */
private const val SPAN_C = 30.0
/** The bell's drawn width in cents: a needle that can be seen, not a measured spread. */
private const val BELL_C = 0.8
