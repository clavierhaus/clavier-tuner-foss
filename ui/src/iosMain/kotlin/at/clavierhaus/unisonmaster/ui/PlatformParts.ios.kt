@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

/*
 * The iPhone's side of the screens' platform parts (31 Oct 2026, the first build for the phone). The gear is the same
 * Material path as Android's drawable. Since 2 Nov 2026 the bundled faces (DejaVu Serif, Allura) and the hub's
 * photograph are the same files as Android's res/, carried into the iPhone app by iosApp/project.yml and read from
 * the app's bundle here. Where a file is missing from the bundle, the system's serif and script stand in, and the hub
 * is drawn on black, as before.
 */

/** A file the iPhone app carries in its bundle (iosApp/project.yml: the files of Android's res/), or null. */
private fun bundled(name: String, type: String): ByteArray? {
    val path = NSBundle.mainBundle.pathForResource(name, type) ?: return null
    val d = NSData.dataWithContentsOfFile(path) ?: return null
    val n = d.length.toInt()
    if (n == 0) return null
    return ByteArray(n).also { out -> out.usePinned { memcpy(it.addressOf(0), d.bytes, d.length) } }
}

private fun face(name: String, weight: FontWeight = FontWeight.Normal) =
    bundled(name, "ttf")?.let { Font(identity = name, data = it, weight = weight) }

actual val DejaVuSerif: FontFamily = face("dejavu_serif")?.let { FontFamily(it) } ?: FontFamily.Serif

actual val DejaVuSerifFamily: FontFamily =
    listOfNotNull(face("dejavu_serif"), face("dejavu_serif_bold", FontWeight.Bold)).takeIf { it.isNotEmpty() }
        ?.let { FontFamily(it) } ?: FontFamily.Serif

actual val AlluraScript: FontFamily = face("allura_regular")?.let { FontFamily(it) } ?: FontFamily.Cursive

/** Material Design "settings" icon, Apache License 2.0 (Google): the path of Android's ui_settings_gear.xml. */
private const val GEAR_PATH = "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"

private val Gear: ImageVector by lazy {
    ImageVector.Builder("gear", 24.dp, 24.dp, 24f, 24f).addPath(
        PathParser().parsePathString(GEAR_PATH).toNodes(), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.White),
    ).build()
}

@Composable
actual fun gearPainter(): Painter = rememberVectorPainter(Gear)

/**
 * The hub's photograph, decoded off the main thread and cropped to fill the screen, darkened on screen as on Android:
 * evenly, and a little more on the left where the menu sits. Black until it is decoded, or when it is not in the bundle.
 */
@Composable
internal actual fun HubBackground() {
    val image by produceState<ImageBitmap?>(null) {
        value = withContext(Dispatchers.Default) {
            bundled("hub_background", "jpg")?.let { runCatching { org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black))
    image?.let { Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(0.0f to Color.Black.copy(alpha = 0.45f), 0.55f to Color.Transparent),
        ),
    )
}

/** The iPhone's microphone is opened in measurement mode, Apple's unprocessed input (IosAudioSource). */
@Composable
actual fun microphoneDeclaresUnprocessed(): Boolean = remember { true }
