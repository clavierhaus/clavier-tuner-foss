package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.i18n.K
import at.clavierhaus.unisonmaster.i18n.t
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sign

/*
 * The tuning screen of docs/SCREEN.md: one note, the beat as motion, a
 * scale magnified at the match window, one state word, the target with its
 * origin. Every part here is a pure function of numbers the controller
 * already publishes; nothing is smoothed or predicted.
 */

/** The one word at the top right. */
@Composable
fun StateWord(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = color,
        fontFamily = DejaVuSerif,
        fontSize = 30.sp,
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * The note, the largest thing on the screen, with its target and where the
 * target comes from under it. The name is also the control: a tap on its
 * left or right half is a semitone, a swipe across it an octave — the other
 * hand holds the hammer.
 */
@Composable
fun NoteBlock(
    name: String,
    targetHz: Double,
    origin: String,
    partial: Int? = null,
    onSemitone: (Int) -> Unit,
    onOctave: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val semitone by rememberUpdatedState(onSemitone)
    val octave by rememberUpdatedState(onOctave)
    Column(modifier) {
        Box(
            Modifier
                .width(260.dp)
                .height(120.dp)
                .pointerInput(Unit) {
                    detectTapGestures { pos -> semitone(if (pos.x < size.width / 2) -1 else +1) }
                }
                .pointerInput(Unit) {
                    var travelled = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { travelled = 0f },
                        onDragEnd = { if (abs(travelled) > 120f) octave(if (travelled > 0) +1 else -1) },
                        onHorizontalDrag = { change, amount -> travelled += amount; change.consume() },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                name,
                color = Color(Brand.WHITE),
                fontFamily = DejaVuSerif,
                fontSize = 84.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Text(
            t(K.tuning_target, num(targetHz, 2) + " Hz") + (if (partial != null) t(K.tuning_targetPartial, partial) else ""),
            color = Color(Brand.TARGET_BLUE),
            fontFamily = DejaVuSerif,
            fontSize = 18.sp,
            maxLines = 1,
        )
        Text(
            origin,
            color = Color(Brand.WHITE_MUTED),
            fontFamily = DejaVuSerif,
            fontSize = 15.sp,
            maxLines = 1,
        )
    }
}

/** The measured fundamental, one number, one decimal; "—" before the first reading. */
@Composable
fun MeasuredBlock(hz: Double?, partial: Int? = null, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(
            hz?.let { String.format(Locale.ROOT, if (partial != null) "%.2f" else "%.1f", it) } ?: "—",
            color = Color(Brand.WHITE),
            fontFamily = DejaVuSerif,
            fontSize = 66.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            if (partial != null) t(K.tuning_partialOne, partial) else t(K.tuning_fundamental),
            color = Color(Brand.WHITE_MUTED),
            fontFamily = DejaVuSerif,
            fontSize = 15.sp,
            maxLines = 1,
        )
    }
}

/** Marks per second above which the band would blur; the rate is shown up to here. */
private const val BAND_MAX_RATE = 8.0
/** Below this beat rate the band stands. */
private const val BAND_STILL_HZ = 0.05

/**
 * The beat as motion: a band of chevrons that slides at the beat rate
 * between the measured fundamental and its target — one mark per beat —
 * rightward when sharp, leftward when flat, and stands when they meet.
 * Green while the fundamental matches. Nothing moves while nothing sounds.
 */
@Composable
fun BeatBand(
    measuredHz: Double?,
    targetHz: Double,
    sounding: Boolean,
    matched: Boolean,
    modifier: Modifier = Modifier,
) {
    val rate = if (measuredHz != null && sounding) measuredHz - targetHz else 0.0
    val current by rememberUpdatedState(rate)
    var phase by remember { mutableFloatStateOf(0f) }        // marks, fractional
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1e9
                    val r = current
                    if (abs(r) >= BAND_STILL_HZ) {
                        val v = sign(r) * min(abs(r), BAND_MAX_RATE)
                        phase = (phase.toDouble() + v * dt).toFloat().mod(1f)
                    }
                }
                last = now
            }
        }
    }
    val color = if (matched) Color(Brand.GO_GREEN) else Color(Brand.ORANGE)
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val spacing = h * 0.62f
        val stroke = Stroke(width = h * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val n = (w / spacing).toInt() + 2
        val offset = phase * spacing
        for (i in -1..n) {
            val x = i * spacing + offset
            if (x < -spacing || x > w + spacing) continue
            // fade toward both edges so the band reads as a stream, not a ruler
            val edge = min(x, w - x) / (w * 0.12f)
            val alpha = edge.coerceIn(0f, 1f)
            if (alpha <= 0f) continue
            val p = Path().apply {
                moveTo(x - h * 0.16f, h * 0.12f)
                lineTo(x + h * 0.16f, h * 0.5f)
                lineTo(x - h * 0.16f, h * 0.88f)
            }
            drawPath(p, color.copy(alpha = alpha), style = stroke)
        }
    }
}

/**
 * The scale under the band, **in cents, linear**: ±[SCALE_C] cents end to end,
 * one cent the same width wherever it falls. A tick every five, labelled every
 * ten. The pointer is the reading as
 * measured, and stops at the end when the note is further out than that; the
 * match window (±[matchCents]) is the green band at the centre and fills while
 * the pointer stands in it.
 *
 * Two attempts before this one were wrong, both on 1 Oct 2026 and both at the
 * piano. The first magnified the match window — two tenths of a cent — into the
 * middle third of the screen, so the pointer slammed from end to end while the
 * string stood in tune. The second widened that middle third to two cents but
 * kept the log segments outside it, which put a semitone on a tuning scale and
 * made every distance on the screen mean something different from the one beside
 * it. A scale a tuner can read has to answer "how far is that" the same way
 * everywhere, so there is no magnification left here at all: thirty cents each
 * way at Peter's word, to try at the instrument, and the match window drawn at
 * the width it really has.
 *
 * Cents and not Hertz. A tenth of a Hertz is a cent at F3 and a twelfth of a
 * cent at C7, so a Hertz scale gave the same width to forty times different
 * musical distances across the compass, and at the top of the piano it magnified
 * a twelfth of a cent into a visible swing — a precision no measurement here
 * has. One scale, one meaning, everywhere on the keyboard.
 */
const val SCALE_C = 30.0

@Composable
fun TargetScale(
    measuredHz: Double?,
    targetHz: Double,
    matchCents: Double,
    sounding: Boolean,
    matched: Boolean,
    modifier: Modifier = Modifier,
) {
    val labelColor = Color(Brand.WHITE_MUTED).toArgb()
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val cx = w / 2f
        val half = (w / 2f - 8f).toDouble()
        val m = matchCents.coerceIn(0.02, SCALE_C)
        fun xOf(cents: Double): Float =
            (cx + (cents / SCALE_C).coerceIn(-1.0, 1.0) * half).toFloat()
        val axisY = h * 0.45f
        val green = Color(Brand.GO_GREEN)
        // The match window, at the width it really has on this scale, with a post at
        // each edge: the tuner can see where in the window the pointer stands, which
        // a colour alone never says. The posts and the fill are the same green as
        // the pointer and turn with it.
        val left = xOf(-m); val right = xOf(m)
        val top = axisY - h * 0.30f; val bot = axisY + h * 0.30f
        val edge = if (matched) green else Color(Brand.WHITE_MUTED)
        drawRect(green.copy(alpha = if (matched) 0.30f else 0.08f), Offset(left, top), androidx.compose.ui.geometry.Size((right - left).coerceAtLeast(2f), bot - top))
        for (x in listOf(left, right)) {
            drawLine(edge.copy(alpha = if (matched) 1f else 0.75f), Offset(x, top), Offset(x, bot), 4f)
            // feet, pointing in: the window is the space between the posts
            val inward = if (x == left) 1f else -1f
            drawLine(edge.copy(alpha = if (matched) 1f else 0.75f), Offset(x, top), Offset(x + inward * h * 0.08f, top), 4f)
            drawLine(edge.copy(alpha = if (matched) 1f else 0.75f), Offset(x, bot), Offset(x + inward * h * 0.08f, bot), 4f)
        }
        // and the window said in cents, above the posts, so the green is a number
        run {
            val wp = android.graphics.Paint().apply {
                color = edge.toArgb(); textSize = h * 0.18f; isAntiAlias = true
                textAlign = android.graphics.Paint.Align.LEFT
            }
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.ROOT, "\u00b1%.1f", m), right + h * 0.12f, top + h * 0.16f, wp,
            )
        }
        // axis and ticks
        drawLine(Color(Brand.WHITE).copy(alpha = 0.35f), Offset(8f, axisY), Offset(w - 8f, axisY), 2f)
        val paint = android.graphics.Paint().apply {
            color = labelColor; textSize = h * 0.2f; isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        var t = -SCALE_C
        while (t <= SCALE_C + 1e-9) {
            val x = xOf(t)
            val major = t == 0.0
            val labelled = abs(t) % 10.0 < 1e-9
            drawLine(Color(Brand.WHITE).copy(alpha = if (labelled) 0.5f else 0.3f), Offset(x, axisY - if (major) h * 0.2f else h * 0.1f), Offset(x, axisY + if (major) h * 0.2f else h * 0.1f), 2f)
            if (labelled) {
                val label = when {
                    t == 0.0 -> "0"
                    t == SCALE_C -> String.format(Locale.ROOT, "+%.0f c", t)
                    t > 0 -> String.format(Locale.ROOT, "+%.0f", t)
                    else -> String.format(Locale.ROOT, "−%.0f", abs(t))
                }
                drawContext.canvas.nativeCanvas.drawText(label, x, h * 0.95f, paint)
            }
            t += 5.0
        }
        // the pointer
        if (measuredHz != null && measuredHz > 0.0 && targetHz > 0.0) {
            val d = 1200.0 * ln(measuredHz / targetHz) / ln(2.0)
            val x = xOf(d)
            val c = (if (matched) green else Color(Brand.ORANGE)).copy(alpha = if (sounding) 1f else 0.45f)
            val tri = Path().apply {
                moveTo(x, axisY - h * 0.42f); lineTo(x + h * 0.09f, axisY - h * 0.26f); lineTo(x - h * 0.09f, axisY - h * 0.26f); close()
            }
            drawPath(tri, c)
            drawLine(c, Offset(x, axisY - h * 0.26f), Offset(x, axisY + h * 0.3f), 5f)
        }
    }
}

/** "Partials" opens Full Spectrum; "One partial" returns to the partial listened to. Outlined, like Progress. */
@Composable
fun PartialsButton(fullSpectrum: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        border = BorderStroke(1.dp, Color(Brand.WHITE_MUTED)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(Brand.WHITE)),
    ) {
        Text(label ?: if (fullSpectrum) t(K.app_onePartial) else t(K.app_partials), fontFamily = DejaVuSerif, fontSize = 16.sp, maxLines = 1)
    }
}

/**
 * The probe's play button, beside the recording button: an outlined triangle
 * while the microphone is live, a filled square and the recording's name while
 * a recording is being fed (docs/PROBE.md). Tapping it opens the file dialog,
 * or stops the feed and gives the microphone back. Shown only when the probe is
 * switched on in the settings, and never at a piano.
 */
@Composable
fun ProbeButton(feeding: String?, onPick: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = { if (feeding == null) onPick() else onStop() })
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (feeding != null) {
            Text(
                feeding.removeSuffix(".wav").takeLast(18),
                color = Color(Brand.ORANGE),
                fontFamily = DejaVuSerif,
                fontSize = 18.sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(10.dp))
        }
        Canvas(Modifier.size(22.dp)) {
            val c = androidx.compose.ui.graphics.Color(if (feeding == null) Brand.WHITE_MUTED else Brand.ORANGE)
            if (feeding == null) {
                // a triangle pointing right, outlined: nothing is playing yet
                val p = Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.12f)
                    lineTo(size.width * 0.88f, size.height * 0.5f)
                    lineTo(size.width * 0.18f, size.height * 0.88f)
                    close()
                }
                drawPath(p, c, style = Stroke(width = 2.dp.toPx()))
            } else {
                drawRect(c, topLeft = Offset(size.width * 0.2f, size.height * 0.2f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.6f, size.height * 0.6f))
            }
        }
    }
}
