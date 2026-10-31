package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/*
 * The iPhone's side of the screens' platform parts (31 Oct 2026, the first build for the phone). The bundled faces and
 * the hub's photograph are Android resources and not yet carried over: the system's serif and script stand in for
 * DejaVu Serif and Allura, and the hub is drawn on black with the same darkening. The gear is the same Material path
 * as Android's drawable.
 */

actual val DejaVuSerif: FontFamily = FontFamily.Serif

actual val DejaVuSerifFamily: FontFamily = FontFamily.Serif

actual val AlluraScript: FontFamily = FontFamily.Cursive

/** Material Design "settings" icon, Apache License 2.0 (Google): the path of Android's ui_settings_gear.xml. */
private const val GEAR_PATH = "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"

private val Gear: ImageVector by lazy {
    ImageVector.Builder("gear", 24.dp, 24.dp, 24f, 24f).addPath(
        PathParser().parsePathString(GEAR_PATH).toNodes(), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.White),
    ).build()
}

@Composable
actual fun gearPainter(): Painter = rememberVectorPainter(Gear)

@Composable
internal actual fun HubBackground() {
    Box(Modifier.fillMaxSize().background(Color.Black))
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(0.0f to Color.Black.copy(alpha = 0.45f), 0.55f to Color.Transparent),
        ),
    )
}

/** The iPhone's microphone is opened in measurement mode, Apple's unprocessed input (IosAudioSource). */
@Composable
actual fun microphoneDeclaresUnprocessed(): Boolean = remember { true }
