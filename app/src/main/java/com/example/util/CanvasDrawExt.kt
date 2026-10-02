package com.example.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText

/**
 * Safe wrapper around Compose DrawScope.drawText to prevent crashes when coordinates
 * are outside visible canvas bounds (e.g. topLeft.x > size.width or topLeft.y > size.height).
 * Compose internal TextPainter computes maxWidth = size.width - topLeft.x, which throws
 * java.lang.IllegalArgumentException: maxWidth(-X) must be >= than minWidth(0)
 * whenever topLeft.x is greater than size.width.
 */
fun DrawScope.safeDrawText(
    textMeasurer: TextMeasurer,
    text: String,
    topLeft: Offset,
    style: TextStyle = TextStyle.Default
) {
    if (text.isEmpty()) return

    // Quick bounds check: if label is completely to the right or below the visible canvas
    if (topLeft.x >= size.width || topLeft.y >= size.height) return
    // If label is way off to the left or above the canvas
    if (topLeft.x < -300f || topLeft.y < -150f) return

    try {
        // Pre-measuring avoids Compose's internal `maxWidth = size.width - topLeft.x` calculation
        val layoutResult: TextLayoutResult = textMeasurer.measure(
            text = text,
            style = style
        )

        // If completely off-screen to left or top, skip
        if (topLeft.x + layoutResult.size.width < 0 || topLeft.y + layoutResult.size.height < 0) {
            return
        }

        drawText(
            textLayoutResult = layoutResult,
            topLeft = topLeft
        )
    } catch (_: Throwable) {
        // Guard against any unforeseen font or constraint measurement exceptions
    }
}
