package at.clavierhaus.unisonmaster.ui

import at.clavierhaus.unisonmaster.i18n.noteName
import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.i18n.K
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.measure.LivePartial
import at.clavierhaus.unisonmaster.measure.Partials
import at.clavierhaus.unisonmaster.tuning.Notes
import at.clavierhaus.unisonmaster.tuning.PredictedPartial
import at.clavierhaus.unisonmaster.tuning.TuningSession
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/** Note name of partial [k] of a note whose first partial is [f1Hz]. */
fun partialNoteName(k: Int, f1Hz: Double, a4Hz: Double): String =
    noteName(Notes.nearestMidi(k * f1Hz, a4Hz))

private val ROW_ACTIVE = Color(0xFF2E2E2E)

/**
 * The tuning graph on a fixed axis, ±8 cents around the targets, labelled
 * every 2 cents: in cents every partial of one string shows the same error. One static light-blue bell on the line stands
 * for every target; each shown partial is an orange bell of one common
 * height, numbered, placed by its own offset from its own target, green
 * within ±0.1 Hz; the [active] partial is drawn on top. The frequencies
 * are read in the [ReadoutColumn] beside the graph.
 */
@Composable
fun TuningGraph(
    liveHz: Double?,
    sounding: Boolean,
    targetHz: Double,
    shown: Set<Int>,
    predicted: List<PredictedPartial>,
    livePartials: List<LivePartial>,
    active: Int,
    matchCents: Double,
    modifier: Modifier = Modifier,
) {
    val topLabel = remember {
        android.graphics.Paint().apply {
            color = Color(Brand.WHITE).toArgb()
            textSize = 28f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    val axisPaint = remember {
        android.graphics.Paint().apply {
            color = Color(Brand.WHITE_MUTED).toArgb()
            textSize = 24f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    // the predicted partials are anchored on the one being tuned, so the fundamental
    // comes from them too: the note's own target is only the fallback before they exist
    fun targetOf(k: Int): Double? = predicted.firstOrNull { it.k == k }?.hz ?: (if (k == 1) targetHz else null)
    fun liveOf(k: Int): Double? = if (k == 1) liveHz else livePartials.firstOrNull { it.k == k }?.hz

    // A measured reading arrives every 21 ms; the bell moves to it within one
    // reading (no averaging, only the step between two readings is drawn).
    val glide = HashMap<Int, Float>()
    for (k in shown.sorted()) {
        val tk = targetOf(k)
        val lk = liveOf(k)
        key(k) {
            val target = if (tk != null && lk != null) (1200.0 * kotlin.math.ln(lk / tk) / kotlin.math.ln(2.0)).toFloat() else 0f
            glide[k] = animateFloatAsState(target, tween(20, easing = LinearEasing), label = "bell$k").value
        }
    }

    Canvas(
        modifier.padding(top = 24.dp),
    ) {
        val w = size.width
        val base = size.height - 36f
        val xc = w / 2f
        val sigma = w / 30f
        val half = w / 2f - 3f * sigma
        val green = Color(Brand.GO_GREEN)
        val muted = Color(Brand.WHITE_MUTED)
        val native = drawContext.canvas.nativeCanvas
        fun xOf(offsetCents: Double): Float =
            xc + (offsetCents / CENTS_SPAN).coerceIn(-1.0, 1.0).toFloat() * half

        val height = base * 0.85f
        val allMatched = shown.all { TuningSession.matched(liveOf(it), targetOf(it), matchCents) }
        bellOutline(xc, height, sigma, base, if (allMatched) green else Color(Brand.TARGET_BLUE))
        val order = shown.sorted().filter { it != active } + listOf(active).filter { it in shown }
        for (k in order) {
            val tk = targetOf(k) ?: continue
            val lk = liveOf(k) ?: continue
            if (!sounding) continue
            val match = TuningSession.matched(lk, tk, matchCents)
            val x = xOf((glide[k] ?: (1200.0 * kotlin.math.ln(lk / tk) / kotlin.math.ln(2.0)).toFloat()).toDouble())
            bellFilled(x, height, sigma, base, if (match) green else Color(Brand.ORANGE))
            if (shown.size > 1) native.drawText("$k", x, base - height - 10f, topLabel)
        }

        // the axis in cents: on one string every partial has the same error in
        // cents, so the bells of a string coincide — a bell apart is a false
        // beat, or a string that is not what its model says
        var n = -CENTS_SPAN.toInt()
        while (n <= CENTS_SPAN.toInt()) {
            val x = xOf(n.toDouble())
            drawLine(muted, Offset(x, base), Offset(x, base + 10f), strokeWidth = 1.5f)
            native.drawText(if (n == 0) "0" else "%+d c".format(n), x, base + 34f, axisPaint)
            n += 2
        }
        drawLine(muted, Offset(0f, base), Offset(w, base), strokeWidth = 1f)
        drawLine(Color(Brand.WHITE), Offset(xc, 0f), Offset(xc, base), strokeWidth = 1.5f)
    }
}

/** The tuning graph's span either side of the target, cents. */
const val CENTS_SPAN = 8.0

/**
 * The reading against its target, in cents to a hundredth. Hz to one decimal
 * cannot show what a tuner works to: 0.1 Hz is 0.39 cents at A4's fundamental
 * and 0.08 at C#7, so the same column had four times the resolution at the
 * top of the table as at the bottom (30 Sept). Cents are the same everywhere.
 */
private fun cents2(measuredHz: Double, targetHz: Double): String {
    val c = 1200.0 * kotlin.math.ln(measuredHz / targetHz) / kotlin.math.ln(2.0)
    return String.format(Locale.ROOT, "%+.2f", c)
}

private fun hz1(hz: Double): String = String.format(Locale.ROOT, "%.1f", hz)

/**
 * One line per shown partial: number, note, target (blue, static) and the
 * live frequency (white, green when matched). The active line is marked;
 * tapping a line makes that partial active.
 */
@Composable
fun ReadoutColumn(
    shown: Set<Int>,
    active: Int,
    targetHz: Double,
    liveHz: Double?,
    predicted: List<PredictedPartial>,
    livePartials: List<LivePartial>,
    a4Hz: Double,
    matchCents: Double,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.width(372.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Spacer(Modifier.width(74.dp))
            Text(t(K.table_target), color = Color(Brand.TARGET_BLUE).copy(alpha = 0.7f), fontSize = 12.sp,
                textAlign = TextAlign.End, modifier = Modifier.width(96.dp))
            Text(t(K.table_measured), color = Color(Brand.WHITE_MUTED), fontSize = 12.sp,
                textAlign = TextAlign.End, modifier = Modifier.width(78.dp))
            Text(t(K.table_cents), color = Color(Brand.WHITE_MUTED), fontSize = 12.sp,
                textAlign = TextAlign.End, modifier = Modifier.width(76.dp))
        }
        for (k in shown.sorted()) {
            // every row from one series, the fundamental included: taking row 1 from
            // the note's target and the rest from the predicted partials put two
            // different f1 on one screen, and row 2 could sit below twice row 1
            val tk = predicted.firstOrNull { it.k == k }?.hz ?: (if (k == 1) targetHz else continue)
            val lk = if (k == 1) liveHz else livePartials.firstOrNull { it.k == k }?.hz
            val match = TuningSession.matched(lk, tk, matchCents)
            val isActive = k == active
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isActive) ROW_ACTIVE else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .clickable { onSelect(k) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    "$k",
                    color = if (isActive) Color(Brand.ORANGE) else Color(Brand.WHITE_MUTED),
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 16.sp,
                    modifier = Modifier.width(26.dp),
                )
                Text(
                    partialNoteName(k, targetHz, a4Hz),
                    color = Color(Brand.WHITE_MUTED),
                    fontFamily = DejaVuSerif,
                    fontSize = 16.sp,
                    modifier = Modifier.width(48.dp),
                )
                Text(
                    hz1(tk),
                    color = Color(Brand.TARGET_BLUE),
                    fontFamily = DejaVuSerif,
                    fontSize = 16.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(96.dp),
                )
                Text(
                    lk?.let { hz1(it) } ?: "—",
                    color = if (match) Color(Brand.GO_GREEN) else Color(Brand.WHITE),
                    fontFamily = DejaVuSerif,
                    fontSize = 16.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(78.dp),
                )
                // the number the tuner works to: hundredths of a cent, the same resolution on every partial
                Text(
                    lk?.let { cents2(it, tk) } ?: "—",
                    color = if (match) Color(Brand.GO_GREEN) else Color(Brand.WHITE),
                    fontFamily = DejaVuSerif,
                    fontSize = if (isActive) 22.sp else 16.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(76.dp),
                )
            }
        }
    }
}
