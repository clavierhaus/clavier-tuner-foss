package at.clavierhaus.unisonmaster.ui

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
 * screen, the corners every screen has (Corners.kt: the gear top left, the
 * name and a dim home top right), a menu of large serif lines (the verb in
 * orange) each with its short note on the same line after a dot, in the same serif and
 * smaller (24 Oct 2026; a script note stood beneath until then), and the colophon bottom right.
 * Drawn for the reference device; OnePicture scales it on smaller screens.
 * Each app assembles its own hub from this: the entries are its own.
 */

/** One menu line: "lead [verb] rest" — the verb orange, the rest white, a [lead] word in white before it ("My Pianos") — with an optional short note after a dot on the same line. */
data class HomeHubItem(
    val verb: String,
    val rest: String,
    val note: String? = null,
    val lead: String = "",
    val onClick: () -> Unit,
)

private val HubOrange = Color(Brand.ORANGE)
private val HubWhite = Color(0xFFF5F5F5)


private val HubShadow = Shadow(Color.Black, Offset(2f, 2f), 8f)


private val EntryStyle = TextStyle(fontFamily = DejaVuSerifFamily, color = HubWhite, fontSize = 36.sp, shadow = HubShadow)
private val ColophonStyle = TextStyle(fontFamily = DejaVuSerifFamily, color = HubWhite, fontSize = 26.sp, shadow = HubShadow)

@Composable
fun HomeHub(
    entries: List<HomeHubItem>,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        HubBackground()

        // the corners, shadowed over the photograph; home stands dim — this is home
        Corners(onSettings = onSettings, onHome = null, shadowed = true)

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
        // Drawn for three entries; a fourth closes the gaps and lifts the column, a fifth
        // (Pro's hub since 4 Oct 2026: Continue, New, Pianos, Unisons, Recordings) steps the
        // type down one size so the last note stays on the reference screen — the hub's
        // measured layout under the laws (Pane, FitText) is the revamp's C′, still open.
        val crowded = entries.size > 3
        val tight = entries.size > 4
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 112.dp, top = if (crowded) 40.dp else 72.dp),
        ) {
            entries.forEachIndexed { i, e ->
                if (i > 0) Spacer(Modifier.height(if (crowded) 2.dp else 18.dp))
                HubEntry(e, tight)
            }
        }
    }
}

@Composable
private fun HubEntry(e: HomeHubItem, tight: Boolean) {
    // the entry as it was, and its note on the same line after a dot, in the same serif and smaller
    // (Peter, 24 Oct 2026: the script beneath the entries was a nice touch in GNU Backgammon, not right
    // for a professional piece of software)
    val entry = if (tight) EntryStyle.copy(fontSize = 30.sp) else EntryStyle
    BasicText(
        text = buildAnnotatedString {
            if (e.lead.isNotEmpty()) append(e.lead + " ")
            withStyle(SpanStyle(color = HubOrange)) { append(e.verb) }
            append(e.rest)
            if (!e.note.isNullOrEmpty()) withStyle(SpanStyle(fontSize = entry.fontSize * NOTE_SHARE, color = HubWhite.copy(alpha = 0.75f))) {
                append("  ·  " + e.note)
            }
        },
        style = entry,
        modifier = Modifier.clickable(onClick = e.onClick).padding(vertical = 4.dp),
    )
}

/** A note's size beside its entry, as a share of the entry's. */
private const val NOTE_SHARE = 0.5f

