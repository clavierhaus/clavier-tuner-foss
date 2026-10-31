package at.clavierhaus.unisonmaster.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.TextLine
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

private val labelFace by lazy { FontMgr.default.matchFamilyStyle(null, FontStyle.NORMAL) }

actual fun DrawScope.drawLabel(text: String, x: Float, y: Float, color: Color, sizePx: Float, align: LabelAlign) {
    val line = TextLine.make(text, Font(labelFace, sizePx))
    val left = if (align == LabelAlign.CENTER) x - line.width / 2f else x
    drawContext.canvas.nativeCanvas.drawTextLine(line, left, y, Paint().apply { this.color = color.toArgb(); isAntiAlias = true })
}

/** The patterns the screens use: a literal prefix, %[+].Nf, a literal suffix. Half away from zero, as java rounds. */
actual fun fmt(pattern: String, x: Double): String {
    val m = Regex("%(\\+?)\\.(\\d+)f").find(pattern) ?: return pattern
    val plus = m.groupValues[1] == "+"
    val places = m.groupValues[2].toInt()
    val scale = 10.0.pow(places)
    val scaled = floor(abs(x) * scale + 0.5).toLong()
    val whole = scaled / scale.toLong()
    val frac = (scaled % scale.toLong()).toString().padStart(places, '0')
    val digits = if (places > 0) "$whole.$frac" else "$whole"
    val negative = x < 0 && scaled != 0L || (x == 0.0 && 1.0 / x < 0)
    val sign = if (negative) "-" else if (plus) "+" else ""
    return pattern.substring(0, m.range.first) + sign + digits + pattern.substring(m.range.last + 1)
}

actual fun nowMs(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun formatDate(ms: Long, pattern: String, languageTag: String): String {
    val f = NSDateFormatter()
    f.dateFormat = pattern
    f.locale = NSLocale(localeIdentifier = languageTag.replace('-', '_'))
    return f.stringFromDate(NSDate.dateWithTimeIntervalSince1970(ms / 1000.0))
}

actual val NoFontPadding: androidx.compose.ui.text.PlatformTextStyle? = null
