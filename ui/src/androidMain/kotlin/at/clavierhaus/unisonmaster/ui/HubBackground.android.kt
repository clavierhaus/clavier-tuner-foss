package at.clavierhaus.unisonmaster.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The photograph (3504 x 2336, half the original) is decoded once, off the
 * main thread, at the largest power-of-two reduction that still covers the
 * screen, and it is darkened here, on screen, never in the file: evenly, and
 * a little more on the left where the menu sits.
 */
@Composable
internal actual fun HubBackground() {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val base = LocalBaseDensity.current ?: LocalDensity.current
    val targetPx = with(base) { maxOf(config.screenWidthDp, config.screenHeightDp).dp.roundToPx() }
    val image by produceState<ImageBitmap?>(null, targetPx) {
        value = withContext(Dispatchers.IO) { decodeCovering(context, R.raw.hub_background, targetPx) }
    }
    image?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            // Crop: the photograph fills any screen, the piano stays the subject.
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                0.0f to Color.Black.copy(alpha = 0.45f),
                0.55f to Color.Transparent,
            ),
        ),
    )
}

private fun decodeCovering(context: android.content.Context, rawId: Int, targetWidthPx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.resources.openRawResource(rawId).use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetWidthPx) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.resources.openRawResource(rawId).use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
}
