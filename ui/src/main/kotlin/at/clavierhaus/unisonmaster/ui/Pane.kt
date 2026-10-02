package at.clavierhaus.unisonmaster.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * The one way to place anything on a screen: by fractions of the pane
 * (docs/UI.md, law 1). A `Pane` fills what it is given and hands its children
 * a [PaneScope]; a child is put at ([Place]) a fraction of the width and
 * height, with a fraction of each for its size. No `dp` offset appears in a
 * screen, and a screen drawn this way fits every pane of its aspect ratio.
 */
@Composable
fun Pane(modifier: Modifier = Modifier, content: @Composable PaneScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        PaneScope(maxWidth, maxHeight).content()
    }
}

class PaneScope(val width: Dp, val height: Dp) {
    /** [fx], [fy]: the top-left corner as fractions of the pane; [fw], [fh]: the size as fractions. */
    @Composable
    fun Place(fx: Float, fy: Float, fw: Float, fh: Float, content: @Composable BoxScope.() -> Unit) {
        Box(
            Modifier
                .offset(x = width * fx, y = height * fy)
                .size(width * fw, height * fh),
            content = content,
        )
    }
}
