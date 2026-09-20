package at.clavierhaus.unisonmaster.ui

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand

/*
 * The hub, on the clavierhaus backgammon stencil: the concert grand full
 * screen, the gear and title top left, a menu of large serif lines (the verb
 * in orange) with a script note beneath, and the colophon bottom right.
 * Drawn for the reference device; OnePicture scales it on smaller screens.
 * Each app assembles its own hub from this: the entries are its own.
 */

/** One menu line: "[verb] rest", with an optional note written beneath it. */
data class HomeHubItem(
    val verb: String,
    val rest: String,
    val note: String? = null,
    val onClick: () -> Unit,
)

private val HubOrange = Color(Brand.ORANGE)
private val HubWhite = Color(0xFFF5F5F5)

private const val SHADOW_OFFSET_PX = 2f
private const val SHADOW_BLUR_PX = 8f
private val GEAR_SIZE = 28.dp

private val HubShadow = Shadow(Color.Black, Offset(SHADOW_OFFSET_PX, SHADOW_OFFSET_PX), SHADOW_BLUR_PX)

/** DejaVu Serif in both weights: the clavierhaus face. */
val DejaVuSerifFamily = FontFamily(
    Font(R.font.dejavu_serif, FontWeight.Normal),
    Font(R.font.dejavu_serif_bold, FontWeight.Bold),
)

/** Allura (SIL OFL): the script for the edition mark and the menu notes. */
val AlluraScript = FontFamily(Font(R.font.allura_regular))

private val TitleStyle = TextStyle(fontFamily = DejaVuSerifFamily, fontSize = 34.sp, fontWeight = FontWeight.Bold, shadow = HubShadow)
private val EntryStyle = TextStyle(fontFamily = DejaVuSerifFamily, color = HubWhite, fontSize = 36.sp, shadow = HubShadow)
private val NoteStyle = TextStyle(fontFamily = AlluraScript, color = HubWhite.copy(alpha = 0.75f), fontSize = 30.sp, shadow = HubShadow)
private val ColophonStyle = TextStyle(fontFamily = DejaVuSerifFamily, color = HubWhite, fontSize = 26.sp, shadow = HubShadow)

@Composable
fun HomeHub(
    entries: List<HomeHubItem>,
    onSettings: () -> Unit,
    edition: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        HubBackground()

        // The gear, white over the same shadow the text carries (a blurred copy behind).
        val density = LocalDensity.current
        val shift = with(density) { SHADOW_OFFSET_PX.toDp() }
        val blur = with(density) { SHADOW_BLUR_PX.toDp() }
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 48.dp - blur, top = 18.dp - blur)
                .padding(blur),
        ) {
            Icon(
                painterResource(R.drawable.ui_settings_gear), null, tint = Color.Black,
                modifier = Modifier.offset(shift, shift).blur(blur).size(GEAR_SIZE),
            )
            Icon(
                painterResource(R.drawable.ui_settings_gear), "Settings", tint = HubWhite,
                modifier = Modifier.size(GEAR_SIZE).clickable(onClick = onSettings),
            )
        }

        // "clavierhaustuner", the "vie" in orange; the edition, if any, in orange script.
        BasicText(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = HubWhite)) { append("cla") }
                withStyle(SpanStyle(color = HubOrange)) { append("vie") }
                withStyle(SpanStyle(color = HubWhite)) { append("rhaustuner") }
                if (edition != null) {
                    withStyle(
                        SpanStyle(color = HubOrange, fontFamily = AlluraScript, fontWeight = FontWeight.Normal, fontSize = 48.sp),
                    ) { append("  $edition") }
                }
            },
            style = TitleStyle,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 92.dp, top = 12.dp),
        )

        // The colophon, where a book would put it.
        BasicText(
            text = buildAnnotatedString {
                append("powered by cla")
                withStyle(SpanStyle(color = HubOrange)) { append("vie") }
                append("rhaus.at")
            },
            style = ColophonStyle,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 18.dp),
        )

        // The menu. padding, not offset: the slot itself moves, so every line stays tappable.
        // Drawn for three entries; a fourth (Pro's Record Strikes) closes the gaps and
        // lifts the column so the last note stays on the reference screen.
        val crowded = entries.size > 3
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 112.dp, top = if (crowded) 40.dp else 72.dp),
        ) {
            entries.forEachIndexed { i, e ->
                if (i > 0) Spacer(Modifier.height(if (crowded) 2.dp else 18.dp))
                HubEntry(e)
            }
        }
    }
}

@Composable
private fun HubEntry(e: HomeHubItem) {
    Column(Modifier.clickable(onClick = e.onClick).padding(vertical = 4.dp)) {
        BasicText(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = HubOrange)) { append(e.verb) }
                append(e.rest)
            },
            style = EntryStyle,
        )
        if (e.note != null) {
            BasicText(e.note, style = NoteStyle, modifier = Modifier.padding(start = 28.dp))
        }
    }
}

/** "<-", white on the dark screens: back to the hub. */
@Composable
fun BackArrow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    BasicText(
        "<-",
        style = TextStyle(fontFamily = DejaVuSerifFamily, color = HubWhite, fontSize = 22.sp),
        modifier = modifier.clickable(onClick = onClick).padding(end = 18.dp, top = 2.dp, bottom = 2.dp),
    )
}

/*
 * The photograph (3504 x 2336, half the original) is decoded once, off the
 * main thread, at the largest power-of-two reduction that still covers the
 * screen, and it is darkened here, on screen, never in the file: evenly, and
 * a little more on the left where the menu sits.
 */
@Composable
private fun HubBackground() {
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
