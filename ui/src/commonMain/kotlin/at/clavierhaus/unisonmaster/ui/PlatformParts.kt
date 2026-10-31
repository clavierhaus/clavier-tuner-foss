package at.clavierhaus.unisonmaster.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontFamily

/*
 * What the screens take from the platform they draw on (31 Oct 2026, the port to the iPhone): the bundled faces, the
 * gear, the hub's photograph and what the phone says of its microphone. Android keeps them as its resources, exactly
 * as before; iOS gives its own. Everything else of the screens is common code.
 */

/** DejaVu Serif, bundled: the typeface of the clavierhaus title. */
expect val DejaVuSerif: FontFamily

/** DejaVu Serif in both weights: the clavierhaus face. */
expect val DejaVuSerifFamily: FontFamily

/** Allura (SIL OFL): the script for the edition mark and the menu notes. */
expect val AlluraScript: FontFamily

/** The settings gear in the top left corner. */
@Composable
expect fun gearPainter(): Painter

/** The hub's photograph, darkened on screen. */
@Composable
internal expect fun HubBackground()

/** Whether the phone itself declares an unprocessed microphone (Android CDD 5.11); the iPhone's measurement mode is. */
@Composable
expect fun microphoneDeclaresUnprocessed(): Boolean
