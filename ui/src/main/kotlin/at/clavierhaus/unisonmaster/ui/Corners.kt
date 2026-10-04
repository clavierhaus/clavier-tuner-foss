package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand

/*
 * The two corners of every screen (blueprint §18–§19, Peter's word of 4 Oct
 * 2026). Top left: the gear, alone — it opens the settings from anywhere, and
 * on the settings screen it stands orange and a tap on it closes them again
 * (the gear is the second hub). Top right: the app's name, *clavier tuner*
 * with the *vie* in orange, and the home button, which leads to the hub from
 * everywhere; on the hub itself it stands dim. What sits in a corner on one
 * screen sits there on all of them, at the same inset from the pane's edge;
 * no screen has a back arrow. This file draws the corners and holds no logic.
 */

/** Inset of the corners from the pane's edges; every screen the same. */
val CORNER_INSET = 16.dp
/** Height of the band the corners stand in: a screen's own content starts below it, or beside it between the corners. */
val CORNER_BAND = 44.dp
/** Width the top-left corner takes (the gear): content in the band starts right of it. */
val CORNER_LEFT_WIDTH = 60.dp
/** Width the top-right corner takes (the name and the home button): content in the band ends left of it. */
val CORNER_RIGHT_WIDTH = 250.dp

/** The edition's mark beside the name, in script: null in the free app, "Pro" in Pro. Set once by the app before its first screen. */
object Edition {
    var mark: String? = null
}

private val GEAR = 30.dp
private val HOME = 28.dp
private val CornerShadow = Shadow(Color.Black, Offset(2f, 2f), 8f)

/**
 * Both corners, in the screen's root [Box] — the one that fills the pane,
 * before any padding of the screen's own, so the corners stand where they
 * stand on every screen. [onSettings] opens the settings; [settingsOpen] says
 * this is the settings screen, where the gear is orange and [onSettings]
 * closes them. [onHome] leads to the hub; null on the hub, where the home
 * button stands dim. [shadowed] draws a shadow under the corners, for a
 * screen over a photograph.
 */
@Composable
fun BoxScope.Corners(
    onSettings: () -> Unit,
    onHome: (() -> Unit)?,
    settingsOpen: Boolean = false,
    shadowed: Boolean = false,
) {
    val density = LocalDensity.current
    val blur = with(density) { CornerShadow.blurRadius.toDp() }
    val shift = with(density) { CornerShadow.offset.x.toDp() }

    // top left: the gear, alone
    Box(Modifier.align(Alignment.TopStart).padding(start = CORNER_INSET - blur, top = CORNER_INSET - blur).padding(blur)) {
        if (shadowed) {
            Icon(painterResource(R.drawable.ui_settings_gear), null, tint = Color.Black, modifier = Modifier.offset(shift, shift).blur(blur).size(GEAR))
        }
        Icon(
            painterResource(R.drawable.ui_settings_gear), "Settings",
            tint = if (settingsOpen) Color(Brand.ORANGE) else Color(Brand.WHITE),
            modifier = Modifier.size(GEAR).clickable(onClick = onSettings),
        )
    }

    // top right: the name, and the home button
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.align(Alignment.TopEnd).padding(end = CORNER_INSET, top = CORNER_INSET - 4.dp),
    ) {
        AppName(shadowed)
        Spacer(Modifier.width(14.dp))
        HomeButton(onHome, shadowed)
    }
}

/** "clavier tuner", the vie in orange; the edition's mark after it in script, where there is one. */
@Composable
private fun AppName(shadowed: Boolean) {
    BasicText(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = Color(Brand.WHITE))) { append("cla") }
            withStyle(SpanStyle(color = Color(Brand.ORANGE))) { append("vie") }
            withStyle(SpanStyle(color = Color(Brand.WHITE))) { append("r tuner") }
            Edition.mark?.let { mark ->
                withStyle(SpanStyle(color = Color(Brand.ORANGE), fontFamily = AlluraScript, fontWeight = FontWeight.Normal, fontSize = 34.sp)) { append("  $mark") }
            }
        },
        style = TextStyle(
            fontFamily = DejaVuSerifFamily, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            shadow = if (shadowed) CornerShadow else null,
        ),
    )
}

/** A house, drawn in strokes: the way to the hub. Dim and inert where [onHome] is null — on the hub. */
@Composable
private fun HomeButton(onHome: (() -> Unit)?, shadowed: Boolean) {
    val color = if (onHome == null) Color(Brand.WHITE_MUTED).copy(alpha = 0.35f) else Color(Brand.WHITE)
    Box(Modifier.size(HOME + 8.dp).then(if (onHome != null) Modifier.clickable(onClick = onHome) else Modifier)) {
        if (shadowed) House(Color.Black, Modifier.align(Alignment.Center).offset(2.dp, 2.dp).blur(4.dp))
        House(color, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun House(color: Color, modifier: Modifier) {
    Canvas(modifier.size(HOME)) {
        val w = size.width; val h = size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // the roof: eaves a little wider than the walls
        drawPath(Path().apply { moveTo(w * 0.08f, h * 0.50f); lineTo(w * 0.50f, h * 0.12f); lineTo(w * 0.92f, h * 0.50f) }, color, style = stroke)
        // the walls, open at the top where the roof is
        drawPath(Path().apply { moveTo(w * 0.20f, h * 0.44f); lineTo(w * 0.20f, h * 0.90f); lineTo(w * 0.80f, h * 0.90f); lineTo(w * 0.80f, h * 0.44f) }, color, style = stroke)
        // the door
        drawPath(Path().apply { moveTo(w * 0.42f, h * 0.90f); lineTo(w * 0.42f, h * 0.64f); lineTo(w * 0.58f, h * 0.64f); lineTo(w * 0.58f, h * 0.90f) }, color, style = stroke)
    }
}

/** Room for the band: a screen whose content is a column from the top puts this first. */
@Composable
fun CornerBand() {
    Spacer(Modifier.height(CORNER_BAND))
}
