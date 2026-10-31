package at.clavierhaus.unisonmaster.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.clavierhaus.unisonmaster.Brand

/**
 * A choice on a row of choices, orange when it is the one taken; a part of the
 * library, so an edition's own screens draw the same chip (Pro's book does).
 *
 * It stood in `RecordStrikesScreen.kt` until 5 Oct 2026, when the recorder left
 * foss and took the chip with it — the sampling page and Pro's book both lost
 * it, and `:ui` would not compile. A part every edition draws does not live
 * inside one edition's screen, so it has a file of its own. Its package is kept
 * so that the screens which draw it need no import, here or in Pro.
 */
@Composable
fun Chip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    // a button of the library's (Elements.kt): its word never breaks, and the serif of every screen
    at.clavierhaus.unisonmaster.ui.WordButton(
        label,
        fill = when {
            selected -> Color(Brand.ORANGE)
            enabled -> Color(0xFF333333)
            else -> Color(0xFF1F1F1F)
        },
        textColor = when {
            selected -> Color(Brand.BLACK)
            enabled -> Color(Brand.WHITE)
            else -> Color(Brand.WHITE_MUTED)
        },
        enabled = enabled, onClick = onClick,
        size = 16.sp, padding = 16.dp, height = 40.dp,
    )
}
