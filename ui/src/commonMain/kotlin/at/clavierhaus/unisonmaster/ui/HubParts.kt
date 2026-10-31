package at.clavierhaus.unisonmaster.ui

import at.clavierhaus.unisonmaster.i18n.noteName
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand
import at.clavierhaus.unisonmaster.i18n.K
import at.clavierhaus.unisonmaster.i18n.t
import at.clavierhaus.unisonmaster.measure.LivePartial
import at.clavierhaus.unisonmaster.measure.Partials
import at.clavierhaus.unisonmaster.tuning.Notes
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor


private val GREY_ON = Color(Brand.RULE)
private val GREY_OFF = Color(0xFF1C1C1C)

/** A line of guidance under the title. */
@Composable
fun HubHint(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text,
        color = Color(Brand.WHITE_MUTED),
        fontFamily = DejaVuSerif,
        fontSize = 14.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Frequency as shown to the tuner: 0.1 Hz, always a point. Finer digits are noise. */
fun formatHz(hz: Double): String = num(hz, 1) + " Hz"

/**
 * A number for the screen, [places] decimals, with the decimal sign of the language in force:
 * a comma in German ("440,0 Hz", "1,5 c"), a point in English. One place for it, so no screen
 * writes a number its own way (13 Oct 2026, German first).
 */
fun num(x: Double, places: Int): String {
    val s = fmt("%.${places}f", x)
    return if (at.clavierhaus.unisonmaster.i18n.Strings.language.value.startsWith("de")) s.replace('.', ',') else s
}

/** Note name of partial [k] of A4, the handle a tuner thinks in (e.g. 3 -> E6). */
fun partialNoteName(k: Int, a4Hz: Double): String = noteName(Notes.nearestMidi(k * a4Hz, a4Hz))

/** One partial button: note name inside, partial number below. */
@Composable
fun PartialButton(
    k: Int,
    note: String,
    on: Boolean,
    tappable: Boolean,
    onClick: () -> Unit,
    pulse: Boolean = false,
) {
    val glow = if (pulse) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "pulse",
        ).value
    } else 1f
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
                // the suggestion breathes as an orange ring; only a selected partial is solid
                .then(
                    if (pulse) Modifier.border(2.dp, Color(Brand.ORANGE).copy(alpha = glow), RoundedCornerShape(6.dp))
                    else Modifier,
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
    baseHz: Double = a4Hz,
    pulse: Int? = null,
    count: Int = Partials.MAX,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        for (k in 1..count.coerceIn(1, Partials.MAX)) {
            PartialButton(
                k = k,
                note = partialNoteName(k, baseHz, a4Hz),
                on = k in shown,
                tappable = k in tappable,
                onClick = { onTap(k) },
                pulse = k == pulse && k !in shown,
            )
        }
    }
}

/** Orange mode toggle: "Full Spectrum" <-> "Fundamental A4". */
@Composable
fun SpectrumToggle(
    fullSpectrum: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fundamentalLabel: String = t(K.hub_fundamentalA4),
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(Brand.ORANGE),
            contentColor = Color(Brand.BLACK),
        ),
    ) {
        Text(
            if (fullSpectrum) fundamentalLabel else t(K.hub_fullSpectrum),
            fontFamily = DejaVuSerif,
            fontSize = 16.sp,
            maxLines = 1,
        )
    }
}

/**
 * Opens the progress keyboard, and closes it again. Outlined rather than
 * filled: the spectrum toggle above already carries the orange block, and two
 * filled blocks in a column read as one control split in half.
 */
@Composable
fun ProgressButton(
    showingProgress: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        border = BorderStroke(1.dp, Color(Brand.ORANGE)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(Brand.ORANGE)),
    ) {
        Text(
            if (showingProgress) t(K.app_tuning) else t(K.app_progress),
            fontFamily = DejaVuSerif,
            fontSize = 16.sp,
            maxLines = 1,
        )
    }
}

/**
 * Sampling's Accept: dim orange until tapped, full orange once the caught
 * string is kept, dim again when another key is caught. Disabled while
 * nothing is caught.
 */
@Composable
fun AcceptButton(accepted: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val orange = Color(Brand.ORANGE)
    val on = enabled && !accepted
    // once the key is kept the button is spent and looks it: grey, not the orange that asks for a tap
    // (Peter, 22 Oct 2026: the kept key shows, and Accept greys out); its word never breaks (Elements.kt)
    WordButton(
        t(K.app_accept),
        fill = when { on -> orange; accepted -> Color(0xFF444444); else -> orange.copy(alpha = 0.3f) },
        textColor = when { on -> Color(Brand.BLACK); accepted -> Color(Brand.WHITE_MUTED); else -> Color(Brand.BLACK).copy(alpha = 0.6f) },
        enabled = on, onClick = onClick, modifier = modifier,
    )
}

/** The green confirmation button. */
@Composable
fun DoneButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    WordButton(
        t(K.app_done),
        fill = if (enabled) Color(Brand.GO_GREEN) else Color(0xFF1F1F1F),
        textColor = if (enabled) Color(Brand.BLACK) else Color(Brand.WHITE_MUTED),
        enabled = enabled, onClick = onClick, modifier = modifier,
    )
}
