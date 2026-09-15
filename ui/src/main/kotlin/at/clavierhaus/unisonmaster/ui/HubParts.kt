package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.tuning.LiveReference
import at.clavierhaus.unisonmaster.tuning.Notes
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor

/** DejaVu Serif, bundled: the typeface of the clavierhaus title. */
val DejaVuSerif = FontFamily(Font(R.font.dejavu_serif))

private val GREY_ON = Color(0xFF3A3A3A)
private val GREY_OFF = Color(0xFF1C1C1C)

/** "clavierhaustuner", white, with the "vie" in CI orange. */
@Composable
fun ClavierhausTitle(modifier: Modifier = Modifier, fontSize: TextUnit = 28.sp) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color(Brand.WHITE))) { append("cla") }
            withStyle(SpanStyle(color = Color(Brand.ORANGE))) { append("vie") }
            withStyle(SpanStyle(color = Color(Brand.WHITE))) { append("rhaustuner") }
        },
        fontFamily = DejaVuSerif,
        fontSize = fontSize,
        modifier = modifier,
    )
}

/** The settings gear, top left on every main screen (clavierhaus convention). */
@Composable
fun SettingsGear(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ui_settings_gear),
        contentDescription = "Settings",
        tint = Color(Brand.WHITE_MUTED),
        modifier = modifier
            .size(30.dp)
            .clickable(onClick = onClick),
    )
}

/** A line of guidance under the title. */
@Composable
fun HubHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color(Brand.WHITE_MUTED),
        fontFamily = DejaVuSerif,
        fontSize = 14.sp,
        modifier = modifier,
    )
}

/** Frequency as shown to the tuner: 0.1 Hz, always a point. Finer digits are noise. */
fun formatHz(hz: Double): String = String.format(Locale.ROOT, "%.1f Hz", hz)

/** Note name of partial [k] of A4, the handle a tuner thinks in (e.g. 3 -> E6). */
fun partialNoteName(k: Int, a4Hz: Double): String = Notes.name(Notes.nearestMidi(k * a4Hz, a4Hz))

private fun DrawScope.bell(xc: Float, peak: Float, sigma: Float, base: Float, color: Color) {
    val x0 = (xc - 4.5f * sigma).coerceAtLeast(0f)
    val x1 = (xc + 4.5f * sigma).coerceAtMost(size.width)
    val path = Path()
    path.moveTo(x0, base)
    val steps = 90
    for (i in 0..steps) {
        val x = x0 + (x1 - x0) * i / steps
        val d = (x - xc) / sigma
        path.lineTo(x, base - peak * exp(-0.5f * d * d))
    }
    path.lineTo(x1, base)
    path.close()
    drawPath(path, color.copy(alpha = 0.22f))
    drawPath(path, color, style = Stroke(width = 2f))
}

/**
 * The hub graph. The white centre line is the target and carries the live
 * frequency above it; a horizontal axis at the foot gives the scale.
 *
 * Fundamental mode: one bell, apex always on the line. The axis moves with
 * the reading: the centre is [centreHz], with a labelled tick at every whole
 * Hz within ±4 Hz, so the ticks slide under the line as the pin turns.
 *
 * Full Spectrum: one bell per shown partial, placed by its inharmonic
 * deviation in cents from k × f1 (±100 ct, wider if a partial needs it),
 * numbered above its apex; the axis is then in cents.
 */
@Composable
fun ToneGraph(
    hz: Double?,
    centreHz: Double,
    level: Float,
    fullSpectrum: Boolean,
    partials: List<LiveReference.LivePartial>,
    shown: Set<Int>,
    modifier: Modifier = Modifier,
) {
    val labelPaint = remember {
        android.graphics.Paint().apply {
            color = Color(Brand.WHITE_MUTED).toArgb()
            textSize = 26f
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
    Box(modifier) {
        Text(
            hz?.let { formatHz(it) } ?: "— Hz",
            color = Color(Brand.WHITE),
            fontFamily = DejaVuSerif,
            fontSize = 30.sp,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(top = 48.dp),
        ) {
            val w = size.width
            val base = size.height - 36f          // the horizontal axis
            val xc = w / 2f
            val sigma = w / 80f
            val half = w / 2f - 5f * sigma
            val orange = Color(Brand.ORANGE)
            val muted = Color(Brand.WHITE_MUTED)
            val native = drawContext.canvas.nativeCanvas

            if (!fullSpectrum) {
                val peak = base * 0.9f * level.coerceIn(0f, 1f)
                if (peak > 1f) bell(xc, peak, sigma, base, orange)
                val first = ceil(centreHz - HZ_SPAN).toInt()
                val last = floor(centreHz + HZ_SPAN).toInt()
                for (n in first..last) {
                    val x = xc + ((n - centreHz) / HZ_SPAN).toFloat() * half
                    drawLine(muted, Offset(x, base), Offset(x, base + 10f), strokeWidth = 1.5f)
                    native.drawText("$n", x, base + 34f, axisPaint)
                }
            } else {
                val visible = partials.filter { it.k in shown }
                val widest = visible.maxOfOrNull { abs(it.cents) } ?: 0.0
                val range = maxOf(100.0, widest * 1.15)
                for (p in visible) {
                    val x = xc + (p.cents / range).toFloat() * half
                    val peak = base * 0.9f * p.level.toFloat()
                    if (peak > 1f) {
                        bell(x, peak, sigma, base, orange)
                        native.drawText("${p.k}", x, base - peak - 8f, labelPaint)
                    }
                }
                val tick = if (range <= 150.0) 50 else 100
                val lim = (floor(range / tick) * tick).toInt()
                for (c in -lim..lim step tick) {
                    val x = xc + (c / range).toFloat() * half
                    drawLine(muted, Offset(x, base), Offset(x, base + 10f), strokeWidth = 1.5f)
                    val label = if (c == 0) "0 ct" else if (c > 0) "+$c" else "$c"
                    native.drawText(label, x, base + 34f, axisPaint)
                }
            }

            drawLine(muted, Offset(0f, base), Offset(w, base), strokeWidth = 1f)
            drawLine(
                color = Color(Brand.WHITE),
                start = Offset(xc, 0f),
                end = Offset(xc, base),
                strokeWidth = 1.5f,
            )
        }
    }
}

/** Half-width of the fundamental axis: ±4 Hz around the reading. */
const val HZ_SPAN = 4.0

/** One partial button: note name inside, partial number below. */
@Composable
fun PartialButton(k: Int, note: String, on: Boolean, tappable: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(46.dp)
                .height(30.dp)
                .background(
                    color = when {
                        on -> Color(Brand.ORANGE)
                        tappable -> GREY_ON
                        else -> GREY_OFF
                    },
                    shape = RoundedCornerShape(6.dp),
                )
                .clickable(enabled = tappable, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                note,
                color = if (on) Color(Brand.BLACK) else Color(Brand.WHITE_MUTED),
                fontFamily = DejaVuSerif,
                fontSize = 12.sp,
            )
        }
        Text("$k", color = Color(Brand.WHITE_MUTED), fontSize = 10.sp)
    }
}

/** Partials 1..12 of A4. [shown] are orange; only [tappable] ones react. */
@Composable
fun PartialRow(
    a4Hz: Double,
    shown: Set<Int>,
    tappable: Set<Int>,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        for (k in 1..LiveReference.PARTIALS) {
            PartialButton(
                k = k,
                note = partialNoteName(k, a4Hz),
                on = k in shown,
                tappable = k in tappable,
                onClick = { onTap(k) },
            )
        }
    }
}

/** Orange mode toggle: "Full Spectrum" <-> "Fundamental A4". */
@Composable
fun SpectrumToggle(fullSpectrum: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(Brand.ORANGE),
            contentColor = Color(Brand.BLACK),
        ),
    ) {
        Text(
            if (fullSpectrum) "Fundamental A4" else "Full Spectrum",
            fontFamily = DejaVuSerif,
            fontSize = 16.sp,
            maxLines = 1,
        )
    }
}

/** The green confirmation button. */
@Composable
fun DoneButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(Brand.GO_GREEN),
            contentColor = Color(Brand.BLACK),
        ),
    ) {
        Text("Done", fontFamily = DejaVuSerif, fontSize = 18.sp)
    }
}
