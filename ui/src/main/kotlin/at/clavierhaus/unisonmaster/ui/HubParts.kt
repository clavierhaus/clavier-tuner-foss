package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import java.util.Locale
import kotlin.math.exp

/** DejaVu Serif, bundled: the typeface of the clavierhaus title. */
val DejaVuSerif = FontFamily(Font(R.font.dejavu_serif))

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

/** Frequency as shown to the tuner: 0.1 Hz, always a point. Finer digits are noise. */
fun formatHz(hz: Double): String = String.format(Locale.ROOT, "%.1f Hz", hz)

/**
 * The fundamental of one string. The white centre line is the target; the
 * bell's apex always sits on it and its height follows the live level. The
 * measured frequency is shown above the line — the only thing that moves.
 */
@Composable
fun FundamentalBell(hz: Double?, level: Float, modifier: Modifier = Modifier) {
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
            val h = size.height
            val xc = w / 2f
            val peak = h * 0.9f * level.coerceIn(0f, 1f)
            if (peak > 1f) {
                val sigma = w / 80f
                val x0 = (xc - 4.5f * sigma).coerceAtLeast(0f)
                val x1 = (xc + 4.5f * sigma).coerceAtMost(w)
                val path = Path()
                path.moveTo(x0, h)
                val steps = 90
                for (i in 0..steps) {
                    val x = x0 + (x1 - x0) * i / steps
                    val d = (x - xc) / sigma
                    path.lineTo(x, h - peak * exp(-0.5f * d * d))
                }
                path.lineTo(x1, h)
                path.close()
                val col = Color(Brand.ORANGE)
                drawPath(path, col.copy(alpha = 0.22f))
                drawPath(path, col, style = Stroke(width = 2f))
            }
            drawLine(
                color = Color(Brand.WHITE),
                start = Offset(xc, 0f),
                end = Offset(xc, h),
                strokeWidth = 1.5f,
            )
        }
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
