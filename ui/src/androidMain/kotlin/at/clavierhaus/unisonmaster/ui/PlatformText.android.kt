package at.clavierhaus.unisonmaster.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import java.util.Locale

actual fun DrawScope.drawLabel(text: String, x: Float, y: Float, color: Color, sizePx: Float, align: LabelAlign) {
    val paint = android.graphics.Paint().apply {
        this.color = color.toArgb()
        textSize = sizePx
        textAlign = if (align == LabelAlign.CENTER) android.graphics.Paint.Align.CENTER else android.graphics.Paint.Align.LEFT
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

actual fun fmt(pattern: String, x: Double): String = String.format(Locale.ROOT, pattern, x)

actual fun nowMs(): Long = System.currentTimeMillis()

actual fun formatDate(ms: Long, pattern: String, languageTag: String): String =
    java.text.SimpleDateFormat(pattern, Locale.forLanguageTag(languageTag)).format(java.util.Date(ms))

actual val NoFontPadding: androidx.compose.ui.text.PlatformTextStyle? = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
