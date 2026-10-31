package at.clavierhaus.unisonmaster.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

actual val DejaVuSerif: FontFamily = FontFamily(Font(R.font.dejavu_serif))

actual val DejaVuSerifFamily: FontFamily = FontFamily(
    Font(R.font.dejavu_serif, FontWeight.Normal),
    Font(R.font.dejavu_serif_bold, FontWeight.Bold),
)

actual val AlluraScript: FontFamily = FontFamily(Font(R.font.allura_regular))

@Composable
actual fun gearPainter(): Painter = painterResource(R.drawable.ui_settings_gear)

@Composable
actual fun microphoneDeclaresUnprocessed(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            (context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager)
                .getProperty(android.media.AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) != null
        }.getOrDefault(false)
    }
}
