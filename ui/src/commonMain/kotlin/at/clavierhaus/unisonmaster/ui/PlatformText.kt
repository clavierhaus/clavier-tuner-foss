package at.clavierhaus.unisonmaster.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/*
 * Text and numbers as the platform makes them (31 Oct 2026, the port to the iPhone). Android keeps exactly what the
 * screens did before -- java's String.format in Locale.ROOT, the canvas's own Paint, SimpleDateFormat -- so nothing
 * changes there; the iPhone does the same with its own means.
 */

/** Where a label drawn on a canvas stands against its x. */
enum class LabelAlign { LEFT, CENTER }

/** A line of text drawn on the canvas at [x], its baseline at [y]. */
expect fun DrawScope.drawLabel(text: String, x: Float, y: Float, color: Color, sizePx: Float, align: LabelAlign = LabelAlign.LEFT)

/** One number by a printf pattern of one %f (with an optional + flag and a precision), in the root locale: "%+.2f". */
expect fun fmt(pattern: String, x: Double): String

/** The wall clock, milliseconds since 1970. */
expect fun nowMs(): Long

/** A date by a SimpleDateFormat / ICU pattern, in the language [languageTag]. */
expect fun formatDate(ms: Long, pattern: String, languageTag: String): String

/** A text style's platform part that sets a letter without the font's padding (Android); none where there is no such padding. */
expect val NoFontPadding: androidx.compose.ui.text.PlatformTextStyle?
