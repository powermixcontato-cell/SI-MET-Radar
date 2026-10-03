package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Mini-Markdown inline (sem dependência): `**negrito**` e `*itálico*`.
 * Apenas formatação visual de texto; não altera nenhum dado.
 */
fun mdInline(source: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    var bold = false
    var italic = false
    val sb = StringBuilder()
    fun flush() {
        if (sb.isEmpty()) return
        val style = SpanStyle(
            fontWeight = if (bold) FontWeight.Bold else null,
            fontStyle = if (italic) FontStyle.Italic else null
        )
        withStyle(style) { append(sb.toString()) }
        sb.clear()
    }
    while (i < source.length) {
        if (source.startsWith("**", i)) {
            flush(); bold = !bold; i += 2
        } else if (source[i] == '*') {
            flush(); italic = !italic; i += 1
        } else {
            sb.append(source[i]); i += 1
        }
    }
    flush()
}

/**
 * Bloco Markdown compacto: título em negrito + marcadores "•" curtos.
 * Cada marcador aceita `**negrito**` / `*itálico*` inline.
 */
@Composable
fun MdText(
    title: String? = null,
    bullets: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    bodyColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleSize: TextUnit = 14.sp,
    bodySize: TextUnit = 11.sp,
    maxLines: Int = Int.MAX_VALUE
) {
    val text = buildAnnotatedString {
        title?.let {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = titleColor, fontSize = titleSize)) {
                append(mdInline(it))
            }
        }
        bullets.forEachIndexed { idx, b ->
            if (title != null || idx > 0) append("\n")
            withStyle(SpanStyle(color = bodyColor, fontSize = bodySize)) {
                append("• ")
                append(mdInline(b))
            }
        }
    }
    Text(
        text = text,
        modifier = modifier,
        lineHeight = 14.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}
