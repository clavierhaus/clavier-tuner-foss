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
            "target " + String.format(Locale.ROOT, "%.2f Hz", targetHz) + (if (partial != null) ", partial $partial" else ""),
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
            if (partial != null) "measured, partial $partial" else "measured, fundamental",
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
 * The scale under the band, magnified at the target: the match window
 * (±[matchHz]) takes the middle third of the width, ±1 Hz the next third
 * either side, ±5 Hz the rest, log-spaced between the anchors. The pointer
 * is the measured fundamental as measured; the window fills green while it
 * stands inside.
 */
@Composable
fun TargetScale(
    measuredHz: Double?,
    targetHz: Double,
    matchHz: Double,
    sounding: Boolean,
    matched: Boolean,
    modifier: Modifier = Modifier,
) {
    val labelColor = Color(Brand.WHITE_MUTED).toArgb()
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val cx = w / 2f
        val z = ((w / 2f - 8f) / 3f).toDouble()
        val m = matchHz.coerceIn(0.01, 0.9)
        fun xOf(d: Double): Float {
            val a = abs(d)
            val f: Double = when {
                a <= m -> (a / m) * z
                a <= 1.0 -> z + z * (ln(a / m) / ln(1.0 / m))
                a <= 5.0 -> 2.0 * z + z * (ln(a) / ln(5.0))
                else -> 3.0 * z
            }
            return (cx.toDouble() + sign(d) * f).toFloat()
        }
        val axisY = h * 0.45f
        val green = Color(Brand.GO_GREEN)
        // the match window
        val left = xOf(-m); val right = xOf(m)
        drawRect(green.copy(alpha = if (matched) 0.35f else 0.12f), Offset(left, axisY - h * 0.28f), androidx.compose.ui.geometry.Size(right - left, h * 0.56f))
        drawRect(green.copy(alpha = 0.5f), Offset(left, axisY - h * 0.28f), androidx.compose.ui.geometry.Size(right - left, h * 0.56f), style = Stroke(width = 2f))
        // axis and ticks
        drawLine(Color(Brand.WHITE).copy(alpha = 0.35f), Offset(8f, axisY), Offset(w - 8f, axisY), 2f)
        val paint = android.graphics.Paint().apply {
            color = labelColor; textSize = h * 0.2f; isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val ticks = listOf(-5.0, -1.0, -0.3, -m, 0.0, m, 0.3, 1.0, 5.0)
        for (t in ticks) {
            val x = xOf(t)
            val major = t == 0.0
            drawLine(Color(Brand.WHITE).copy(alpha = 0.5f), Offset(x, axisY - if (major) h * 0.2f else h * 0.1f), Offset(x, axisY + if (major) h * 0.2f else h * 0.1f), 2f)
            val label = when {
                t == 0.0 -> "0"
                abs(t) == 5.0 -> if (t > 0) "+5 Hz" else "−5"
                abs(t) == m -> String.format(Locale.ROOT, "%s%.1f", if (t > 0) "+" else "−", m)
                else -> String.format(Locale.ROOT, "%s%.1f", if (t > 0) "+" else "−", abs(t))
            }
            drawContext.canvas.nativeCanvas.drawText(label, x, h * 0.95f, paint)
        }
        // the pointer
        if (measuredHz != null) {
            val d = measuredHz - targetHz
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

/** "Partials" opens Full Spectrum; "Fundamental" returns. Outlined, like Progress. */
@Composable
fun PartialsButton(fullSpectrum: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        border = BorderStroke(1.dp, Color(Brand.WHITE_MUTED)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(Brand.WHITE)),
    ) {
        Text(if (fullSpectrum) "Fundamental" else "Partials", fontFamily = DejaVuSerif, fontSize = 16.sp, maxLines = 1)
    }
}

/**
 * The recording button, top right: a red ring while idle, a red disc with
 * the duration as mm:ss while the session is being written. Shown only when
 * recording is switched on in the settings.
 */
@Composable
fun RecordButton(recording: Boolean, seconds: Int, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (recording) {
            Text(
                String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60),
                color = Color(Brand.ALERT_RED),
                fontFamily = DejaVuSerif,
                fontSize = 22.sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(10.dp))
        }
        Box(
            Modifier
                .size(RECORD_DOT)
                .background(if (recording) Color(Brand.ALERT_RED) else Color.Transparent, CircleShape)
                .border(3.dp, Color(Brand.ALERT_RED), CircleShape),
        )
    }
}

private val RECORD_DOT = 26.dp
