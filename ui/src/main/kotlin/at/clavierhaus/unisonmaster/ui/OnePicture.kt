package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Layout law (as in clavierhaus backgammon): the screen is one picture.
 *
 * Every screen is drawn once, for the reference device, in dp and sp; those
 * numbers are the design. On a smaller pane all of it is scaled by a single
 * factor, min(width / REF_W, height / REF_H, 1), applied through the density
 * the screen reads, so layout, drawing and touch targets shrink together and
 * nothing overflows or scrolls. At or above the reference size nothing moves.
 * A screen that fits the reference device therefore fits every device. Below
 * [ScreenRef.MIN_SCALE] the app says so instead of drawing an unreadable one.
 *
 * Content already drawn relative to its own size opts out with [Unscaled].
 */

/** The reference device in landscape: Pixel 8 Pro, 2992 × 1344 px at 480 dpi. */
object ScreenRef {
    val WIDTH = 997.dp
    val HEIGHT = 448.dp
    const val MIN_SCALE = 0.55f
}

/** The device's own density, before any scaling. */
val LocalBaseDensity = compositionLocalOf<Density?> { null }

/** The factor the current screen is drawn at (1 at or above the reference size). */
val LocalScreenScale = compositionLocalOf { 1f }

@Composable
fun OnePicture(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val base = LocalBaseDensity.current ?: LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        // read here: the pane's size is not reachable from inside a nested layout
        val paneW = maxWidth
        val paneH = maxHeight
        val scale = minOf(paneW / ScreenRef.WIDTH, paneH / ScreenRef.HEIGHT, 1f)
        if (scale < ScreenRef.MIN_SCALE) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(
                    "This display is too small for Clavier Tuner " +
                        "(${paneW.value.toInt()} × ${paneH.value.toInt()} dp; needs at least " +
                        "${(ScreenRef.WIDTH * ScreenRef.MIN_SCALE).value.toInt()} × " +
                        "${(ScreenRef.HEIGHT * ScreenRef.MIN_SCALE).value.toInt()}).",
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }
            return@BoxWithConstraints
        }
        CompositionLocalProvider(
            LocalBaseDensity provides base,
            LocalScreenScale provides scale,
            LocalDensity provides Density(density = base.density * scale, fontScale = base.fontScale),
        ) {
            content()
        }
    }
}

/** Restores the device's own density for content that scales itself. */
@Composable
fun Unscaled(content: @Composable () -> Unit) {
    val base = LocalBaseDensity.current ?: LocalDensity.current
    CompositionLocalProvider(LocalDensity provides base) { content() }
}
