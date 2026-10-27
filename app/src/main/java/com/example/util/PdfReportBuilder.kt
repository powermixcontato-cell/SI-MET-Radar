package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Montador de relatórios PDF (v5.1): página A4, cabeçalho com nome do app, título, estado e data/hora (BRT),
 * rodapé "Página X de N". O conteúdo é diagramado primeiro (com quebra de página automática) e desenhado
 * no fim, quando o total de páginas é conhecido.
 */
class PdfReportBuilder(
    private val title: String,
    private val subtitle: String,
    private val generatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val APP_NAME = "SI-MET Radar (IPMet Radar SP)"
        private const val W = 595f
        private const val H = 842f
        private const val M = 36f
        private const val TOP = 92f
        private const val BOTTOM = H - 46f
        val BRT: TimeZone = TimeZone.getTimeZone("America/Sao_Paulo")
        fun brt(ms: Long, pattern: String = "dd/MM/yyyy HH:mm"): String =
            SimpleDateFormat(pattern, Locale("pt", "BR")).apply { timeZone = BRT }.format(Date(ms)) + " BRT"
    }

    private val pages = mutableListOf<MutableList<(Canvas) -> Unit>>()
    private var y = TOP

    private val body = Paint().apply { isAntiAlias = true; color = Color.rgb(30, 41, 59); textSize = 10f }
    private val bold = Paint(body).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val small = Paint(body).apply { textSize = 8.5f; color = Color.rgb(100, 116, 139) }
    private val section = Paint(bold).apply { textSize = 13f; color = Color.rgb(2, 132, 199) }

    init { newPage() }

    private fun newPage() { pages.add(mutableListOf()); y = TOP }
    private fun ensure(h: Float) { if (y + h > BOTTOM) newPage() }
    private fun add(op: (Canvas) -> Unit) { pages.last().add(op) }

    private fun wrap(text: String, paint: Paint, width: Float): List<String> {
        val out = mutableListOf<String>()
        text.split('\n').forEach { para ->
            if (para.isBlank()) { out += ""; return@forEach }
            var line = ""
            para.split(' ').forEach { word ->
                val cand = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(cand) <= width) line = cand
                else {
                    if (line.isNotEmpty()) out += line
                    var w = word
                    while (paint.measureText(w) > width && w.length > 1) {
                        val n = paint.breakText(w, true, width, null).coerceAtLeast(1)
                        out += w.substring(0, n); w = w.substring(n)
                    }
                    line = w
                }
            }
            out += line
        }
        return out
    }

    fun section(text: String) {
        ensure(34f)
        y += 10f
        val yy = y + 13f
        add { c ->
            c.drawText(text, M, yy, section)
            c.drawLine(M, yy + 5f, W - M, yy + 5f, Paint().apply { color = Color.rgb(186, 230, 253); strokeWidth = 1f })
        }
        y += 24f
    }

    fun paragraph(text: String, style: String = "body", indent: Float = 0f, color: Int? = null) {
        val base = when (style) { "bold" -> bold; "small" -> small; else -> body }
        val p = if (color != null) Paint(base).apply { this.color = color } else base
        val lh = p.textSize * 1.35f
        wrap(text, p, W - 2 * M - indent).forEach { line ->
            ensure(lh)
            val yy = y + p.textSize
            add { c -> c.drawText(line, M + indent, yy, p) }
            y += lh
        }
        y += 3f
    }

    fun bullet(text: String, color: Int? = null) {
        val lh = body.textSize * 1.35f
        val p = if (color != null) Paint(body).apply { this.color = color } else body
        val lines = wrap(text, p, W - 2 * M - 12f)
        lines.forEachIndexed { i, line ->
            ensure(lh)
            val yy = y + p.textSize
            add { c -> if (i == 0) c.drawText("•", M + 2f, yy, p); c.drawText(line, M + 12f, yy, p) }
            y += lh
        }
        y += 2f
    }

    /** Tabela simples; [weights] = proporção de cada coluna. */
    fun table(headers: List<String>, rows: List<List<String>>, weights: List<Float> = headers.map { 1f }) {
        val total = weights.sum()
        val widths = weights.map { (W - 2 * M) * it / total }
        val rowH = 15f
        fun drawRow(cells: List<String>, header: Boolean, zebra: Boolean) {
            ensure(rowH)
            val yy = y
            val p = if (header) Paint(bold).apply { textSize = 9f; color = Color.WHITE } else Paint(body).apply { textSize = 9f }
            add { c ->
                if (header) c.drawRect(M, yy, W - M, yy + rowH, Paint().apply { color = Color.rgb(2, 132, 199) })
                else if (zebra) c.drawRect(M, yy, W - M, yy + rowH, Paint().apply { color = Color.rgb(241, 245, 249) })
                var x = M + 4f
                cells.forEachIndexed { i, t ->
                    val w = widths.getOrElse(i) { 60f } - 6f
                    var s = t
                    while (p.measureText(s) > w && s.length > 1) s = s.dropLast(2) + "…"
                    c.drawText(s, x, yy + 11f, p)
                    x += widths.getOrElse(i) { 60f }
                }
            }
            y += rowH
        }
        drawRow(headers, header = true, zebra = false)
        rows.forEachIndexed { i, r -> drawRow(r, header = false, zebra = i % 2 == 1) }
        y += 6f
    }

    fun image(bmp: Bitmap, caption: String?) {
        val maxW = W - 2 * M
        val scale = minOf(maxW / bmp.width, 330f / bmp.height)
        val w = bmp.width * scale; val h = bmp.height * scale
        ensure(h + 18f)
        val yy = y
        val left = M + (maxW - w) / 2
        add { c -> c.drawBitmap(bmp, null, RectF(left, yy, left + w, yy + h), Paint(Paint.FILTER_BITMAP_FLAG)) }
        y += h + 4f
        caption?.let { paragraph(it, "small") }
    }

    fun spacer(h: Float = 6f) { y += h }

    fun write(file: File): File? {
        val doc = PdfDocument()
        val headerTitle = Paint(bold).apply { textSize = 15f; color = Color.rgb(15, 23, 42) }
        val headerApp = Paint(bold).apply { textSize = 9f; color = Color.rgb(2, 132, 199) }
        val headerSub = Paint(body).apply { textSize = 9.5f; color = Color.rgb(71, 85, 105) }
        val footer = Paint(small)
        val line = Paint().apply { color = Color.rgb(203, 213, 225); strokeWidth = 1f }
        val n = pages.size
        return try {
            pages.forEachIndexed { i, ops ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), i + 1).create())
                val c = page.canvas
                c.drawRect(0f, 0f, W, 6f, Paint().apply { color = Color.rgb(2, 132, 199) })
                c.drawText(APP_NAME, M, 28f, headerApp)
                c.drawText(title, M, 48f, headerTitle)
                c.drawText(subtitle + " • Gerado em " + brt(generatedAt), M, 64f, headerSub)
                c.drawLine(M, 74f, W - M, 74f, line)
                ops.forEach { it(c) }
                c.drawLine(M, H - 34f, W - M, H - 34f, line)
                c.drawText("Página ${i + 1} de $n", W - M - footer.measureText("Página ${i + 1} de $n"), H - 20f, footer)
                c.drawText("Informativo. Em emergência siga a Defesa Civil (199) e os avisos oficiais.", M, H - 20f, footer)
                doc.finishPage(page)
            }
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { doc.writeTo(it) }
            file
        } catch (_: Exception) {
            null
        } finally {
            doc.close()
        }
    }
}
