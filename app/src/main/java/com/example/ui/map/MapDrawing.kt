package com.example.ui.map

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Paleta do mapa-base por tema (0 = Black & Blue, 1 = Terrestre, 2 = White). */
data class MapTheme(
    val oceanTop: Color, val oceanBottom: Color,
    val outside: Color,
    val landInner: Color, val landOuter: Color,
    val outline: Color, val outlineGlow: Color,
    val grid: Color, val refText: Color,
    val river: Color, val road: Color,
    val labelBg: Color, val labelFg: Color, val labelBorder: Color,
    val isLight: Boolean
) {
    companion object {
        fun of(theme: Int): MapTheme = when (theme) {
            1 -> MapTheme(
                oceanTop = Color(0xFF0D3350), oceanBottom = Color(0xFF0A2236),
                outside = Color(0xFF1C2E22),
                landInner = Color(0xFF55703A), landOuter = Color(0xFF2F5530),
                outline = Color(0xFFFEF3C7), outlineGlow = Color(0xFF0C2417),
                grid = Color(0xFFFEF3C7).copy(alpha = 0.10f), refText = Color(0xFFE7E5C9).copy(alpha = 0.75f),
                river = Color(0xFF7DD3FC), road = Color(0xFFFBBF24),
                labelBg = Color(0xFF0C2417).copy(alpha = 0.90f), labelFg = Color(0xFFF0FDF4), labelBorder = Color(0xFF86EFAC).copy(alpha = 0.55f),
                isLight = false
            )
            2 -> MapTheme(
                oceanTop = Color(0xFFD9ECFB), oceanBottom = Color(0xFFC7E1F7),
                outside = Color(0xFFEDEFF2),
                landInner = Color(0xFFFFFFFF), landOuter = Color(0xFFF4F6F8),
                outline = Color(0xFF334155), outlineGlow = Color(0xFFFFFFFF),
                grid = Color(0xFF64748B).copy(alpha = 0.12f), refText = Color(0xFF64748B),
                river = Color(0xFF0284C7), road = Color(0xFFD97706),
                labelBg = Color.White.copy(alpha = 0.94f), labelFg = Color(0xFF0F172A), labelBorder = Color(0xFF94A3B8),
                isLight = true
            )
            else -> MapTheme(
                oceanTop = Color(0xFF061A30), oceanBottom = Color(0xFF020A16),
                outside = Color(0xFF0B1424),
                landInner = Color(0xFF15263D), landOuter = Color(0xFF0E1A2B),
                outline = Color(0xFF38BDF8), outlineGlow = Color(0xFF0EA5E9).copy(alpha = 0.25f),
                grid = Color(0xFF38BDF8).copy(alpha = 0.07f), refText = Color(0xFF7C93B0),
                river = Color(0xFF22D3EE), road = Color(0xFFF59E0B),
                labelBg = Color(0xFF0A192F).copy(alpha = 0.90f), labelFg = Color(0xFFE2E8F0), labelBorder = Color(0xFF38BDF8).copy(alpha = 0.45f),
                isLight = false
            )
        }
    }
}

/** Rio Tietê e Paranapanema (traçado simplificado, aproximado). */
val TIETE: List<Pair<Double, Double>> = listOf(
    -23.53 to -46.0, -23.45 to -46.8, -23.1 to -47.4, -22.7 to -48.4, -22.2 to -49.2, -21.6 to -49.9, -20.67 to -51.35
)
val PARANAPANEMA: List<Pair<Double, Double>> = listOf(
    -23.75 to -48.3, -23.2 to -49.4, -22.95 to -50.2, -22.65 to -51.4, -22.55 to -52.1, -22.5 to -52.95
)
val SP280: List<Pair<Double, Double>> = listOf(-23.53 to -46.7, -23.45 to -47.4, -22.95 to -48.4, -22.88 to -49.3, -22.95 to -49.8)

/**
 * Mapa-base comum: oceano/fundo, estados vizinhos, grade lat/lon sutil, estado de SP (contorno IBGE),
 * rios opcionais e rótulos de referência. Retorna o Path do estado (para recortar o campo).
 */
fun DrawScope.drawSpBasemap(
    proj: SpProjection,
    theme: MapTheme,
    textMeasurer: TextMeasurer,
    showRivers: Boolean = true,
    showRoads: Boolean = false,
    showGrid: Boolean = true,
    showRefLabels: Boolean = true
): Path {
    val w = size.width; val h = size.height
    // fundo = "fora do estado" (vizinhos). O oceano é um polígono aproximado a sudeste da costa.
    drawRect(color = theme.outside, size = Size(w, h))
    val ocean = Path().apply {
        moveTo(proj.x(-44.0), proj.y(-23.0)); lineTo(proj.x(-44.6), proj.y(-23.2)); lineTo(proj.x(-45.4), proj.y(-23.75))
        lineTo(proj.x(-46.3), proj.y(-23.95)); lineTo(proj.x(-47.0), proj.y(-24.4)); lineTo(proj.x(-47.9), proj.y(-25.05))
        lineTo(proj.x(-48.6), proj.y(-25.6)); lineTo(proj.x(-48.6), proj.y(-27.5)); lineTo(proj.x(-40.0), proj.y(-27.5)); lineTo(proj.x(-40.0), proj.y(-23.0))
        close()
    }
    drawPath(ocean, brush = Brush.verticalGradient(listOf(theme.oceanTop, theme.oceanBottom), startY = proj.y(-23.0), endY = proj.y(-26.0)))

    if (showGrid) {
        val gridStroke = 1.dp.toPx()
        var lon = -53.0
        while (lon <= -44.0) { val x = proj.x(lon); drawLine(theme.grid, Offset(x, 0f), Offset(x, h), gridStroke); lon += 1.0 }
        var lat = -25.0
        while (lat <= -20.0) { val y = proj.y(lat); drawLine(theme.grid, Offset(0f, y), Offset(w, y), gridStroke); lat += 1.0 }
    }

    val state = proj.statePath()
    val cx = proj.x(-48.6); val cy = proj.y(-22.4)
    drawPath(state, brush = Brush.radialGradient(listOf(theme.landInner, theme.landOuter), center = Offset(cx, cy), radius = (proj.pxPerKm * 450f).coerceAtLeast(100f)))

    if (showRivers) {
        val rw = (1.4.dp.toPx() * proj.zoom.coerceIn(1f, 2f))
        clipPath(state) {
            drawPath(proj.polyline(TIETE), theme.river.copy(alpha = 0.55f), style = Stroke(rw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawPath(proj.polyline(PARANAPANEMA), theme.river.copy(alpha = 0.45f), style = Stroke(rw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    if (showRoads) {
        drawPath(proj.polyline(SP280), theme.road.copy(alpha = 0.6f), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)))
    }

    if (showRefLabels) {
        val style = TextStyle(color = theme.refText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        SpGeo.NEIGHBOR_LABELS.forEach { (txt, lon, lat) ->
            val layout = textMeasurer.measure(txt, style)
            val x = proj.x(lon) - layout.size.width / 2f
            val y = proj.y(lat) - layout.size.height / 2f
            if (x > 2f && y > 2f && x + layout.size.width < w - 2f && y + layout.size.height < h - 2f) drawText(layout, topLeft = Offset(x, y))
        }
    }
    return state
}

/** Contorno do estado por cima do campo (com "glow" para destacar). */
fun DrawScope.drawSpOutline(state: Path, theme: MapTheme) {
    drawPath(state, theme.outlineGlow, style = Stroke(4.dp.toPx(), join = StrokeJoin.Round))
    drawPath(state, theme.outline.copy(alpha = 0.9f), style = Stroke(1.3.dp.toPx(), join = StrokeJoin.Round))
}

/** Desenha o campo interpolado recortado pelo contorno do estado. */
fun DrawScope.drawScalarField(image: ImageBitmap, proj: SpProjection, state: Path) {
    val l = proj.x(SpGeo.MIN_LON); val t = proj.y(SpGeo.MAX_LAT)
    val r = proj.x(SpGeo.MAX_LON); val b = proj.y(SpGeo.MIN_LAT)
    clipPath(state) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(l.roundToInt(), t.roundToInt()),
            dstSize = IntSize((r - l).roundToInt().coerceAtLeast(1), (b - t).roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.Low
        )
    }
}

/** Isolinhas (lon/lat) recortadas pelo estado. */
fun DrawScope.drawIsolines(lines: Map<Float, FloatArray>, proj: SpProjection, state: Path, color: Color) {
    val stroke = 1.dp.toPx()
    clipPath(state) {
        lines.values.forEach { seg ->
            val path = Path()
            var k = 0
            while (k + 3 < seg.size) {
                path.moveTo(proj.x(seg[k].toDouble()), proj.y(seg[k + 1].toDouble()))
                path.lineTo(proj.x(seg[k + 2].toDouble()), proj.y(seg[k + 3].toDouble()))
                k += 4
            }
            drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Rótulos de cidade com prevenção de colisão
// ---------------------------------------------------------------------------------------------

data class MapLabel(
    val key: String,
    val x: Float,
    val y: Float,
    val name: String,
    /** Valor da camada (ex.: "12,4 mm"); desenhado numa pílula PRÓPRIA abaixo do nome, nunca sobre ele. */
    val value: String?,
    val valueColor: Color,
    val priority: Int = 0,
    val selected: Boolean = false
)

/** Resultado da colocação (útil para testes). */
data class PlacedLabel(val label: MapLabel, val nameRect: Rect, val valueRect: Rect?)

/**
 * Coloca e desenha os rótulos:
 * - nome COMPLETO (mede com TextMeasurer; se passar de [maxNameWidthDp] quebra em até 2 linhas);
 * - valor numa pílula separada logo abaixo do nome;
 * - testa 6 posições ao redor do pino, desloca para dentro do canvas e descarta posições que colidem
 *   com rótulos já colocados, com outros pinos ou com [avoid] (ex.: botões de zoom);
 * - prioridade: selecionado > [MapLabel.priority]. Rótulo sem espaço fica só com o pino.
 * - [clipCircle] (mapa circular PPI): só aceita posições TOTALMENTE dentro do círculo visível
 *   (antes rótulos nos cantos do quadrado eram cortados pela máscara circular e se amontoavam).
 */
fun DrawScope.drawMapLabels(
    textMeasurer: TextMeasurer,
    labels: List<MapLabel>,
    theme: MapTheme,
    avoid: List<Rect> = emptyList(),
    pinRadiusDp: Float = 4f,
    maxNameWidthDp: Float = 104f,
    importantCount: Int = 6,
    clipCircle: ClipCircle? = null
): List<PlacedLabel> {
    val w = size.width; val h = size.height
    val margin = 3.dp.toPx()
    val padH = 4.dp.toPx(); val padV = 1.5.dp.toPx(); val gap = 1.5.dp.toPx()
    val pinR = pinRadiusDp.dp.toPx()
    val dist = pinR + 3.dp.toPx()
    val maxNameW = maxNameWidthDp.dp.toPx().roundToInt()
    val visible = labels.filter { it.x in 0f..w && it.y in 0f..h && (clipCircle == null || clipCircle.contains(Offset(it.x, it.y), pinR)) }
    fun insideClip(r: Rect) = clipCircle == null || clipCircle.contains(r, margin)
    val pinRects = visible.associate { it.key to Rect(it.x - pinR, it.y - pinR, it.x + pinR, it.y + pinR) }
    val placed = ArrayList<Rect>(avoid)
    val result = ArrayList<PlacedLabel>()

    val ordered = visible.sortedWith(compareByDescending<MapLabel> { it.selected }.thenByDescending { it.priority })
    val important = ordered.take(importantCount).map { it.key }.toSet()
    for (lb in ordered) {
        val nameStyle = TextStyle(
            color = if (lb.selected && !theme.isLight) Color.White else theme.labelFg,
            fontSize = if (lb.selected) 11.sp else 10.sp,
            fontWeight = if (lb.selected) FontWeight.Bold else FontWeight.SemiBold,
            lineHeight = if (lb.selected) 13.sp else 12.sp,
            textAlign = TextAlign.Center
        )
        val single = textMeasurer.measure(lb.name, nameStyle, softWrap = false, maxLines = 1)
        val nameLayout: TextLayoutResult = if (single.size.width <= maxNameW) single
        else textMeasurer.measure(lb.name, nameStyle, maxLines = 2, constraints = Constraints(maxWidth = maxNameW))
        val valueLayout = lb.value?.let {
            textMeasurer.measure(it, TextStyle(color = if (lb.valueColor.luminance() > 0.45f) Color(0xFF0B1220) else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold), softWrap = false, maxLines = 1)
        }
        val nameW = nameLayout.size.width + 2 * padH; val nameH = nameLayout.size.height + 2 * padV
        val valW = (valueLayout?.size?.width ?: 0) + 2 * padH; val valH = (valueLayout?.size?.height ?: 0) + 2 * padV
        val blockW = maxOf(nameW, if (valueLayout != null) valW else 0f)
        val blockH = nameH + if (valueLayout != null) gap + valH else 0f

        val candidates = listOf(
            Offset(lb.x + dist, lb.y - nameH / 2f),            // direita
            Offset(lb.x - dist - blockW, lb.y - nameH / 2f),   // esquerda
            Offset(lb.x - blockW / 2f, lb.y - dist - blockH),  // acima
            Offset(lb.x - blockW / 2f, lb.y + dist),           // abaixo
            Offset(lb.x + dist, lb.y - blockH - dist / 2f),    // direita-acima
            Offset(lb.x - dist - blockW, lb.y + dist / 2f),    // esquerda-abaixo
            Offset(lb.x - dist - blockW, lb.y - blockH - dist / 2f), // esquerda-acima
            Offset(lb.x + dist, lb.y + dist / 2f)              // direita-abaixo
        )
        val ownPin = pinRects[lb.key]!!
        var chosen: Rect? = null
        var fallback: Rect? = null
        for (c in candidates) {
            // desloca para dentro do canvas (nunca corta no meio do texto)
            val left = c.x.coerceIn(margin, (w - margin - blockW).coerceAtLeast(margin))
            val top = c.y.coerceIn(margin, (h - margin - blockH).coerceAtLeast(margin))
            val r = Rect(left, top, left + blockW, top + blockH)
            if (r.overlaps(ownPin) || !insideClip(r)) continue
            if (fallback == null) fallback = r
            val hit = placed.any { it.inflate(1.dp.toPx()).overlaps(r) } ||
                pinRects.any { (k, pr) -> k != lb.key && pr.overlaps(r) }
            if (!hit) { chosen = r; break }
        }
        // 2ª tentativa para os mais importantes: pode cobrir pinos de outras cidades, nunca outro rótulo
        if (chosen == null && lb.key in important) {
            for (c in candidates) {
                val left = c.x.coerceIn(margin, (w - margin - blockW).coerceAtLeast(margin))
                val top = c.y.coerceIn(margin, (h - margin - blockH).coerceAtLeast(margin))
                val r = Rect(left, top, left + blockW, top + blockH)
                if (r.overlaps(ownPin) || !insideClip(r)) continue
                if (placed.none { it.inflate(1.dp.toPx()).overlaps(r) }) { chosen = r; break }
            }
        }
        if (chosen == null && lb.selected) chosen = fallback
        if (chosen == null) continue
        placed += chosen

        // Nome
        val nameLeft = chosen.left + (blockW - nameW) / 2f
        val nameRect = Rect(nameLeft, chosen.top, nameLeft + nameW, chosen.top + nameH)
        val corner = CornerRadius(5.dp.toPx())
        drawRoundRect(theme.labelBg, nameRect.topLeft, nameRect.size, corner)
        drawRoundRect(
            if (lb.selected) (if (theme.isLight) Color(0xFF0F172A) else Color.White) else theme.labelBorder,
            nameRect.topLeft, nameRect.size, corner, style = Stroke(if (lb.selected) 1.4.dp.toPx() else 0.8.dp.toPx())
        )
        drawText(nameLayout, topLeft = Offset(nameRect.left + padH, nameRect.top + padV))

        // Valor (pílula separada, abaixo do nome)
        var valueRect: Rect? = null
        if (valueLayout != null) {
            val vLeft = chosen.left + (blockW - valW) / 2f
            val vTop = nameRect.bottom + gap
            valueRect = Rect(vLeft, vTop, vLeft + valW, vTop + valH)
            drawRoundRect(lb.valueColor, valueRect.topLeft, valueRect.size, CornerRadius(valH / 2f))
            drawRoundRect(Color.Black.copy(alpha = 0.25f), valueRect.topLeft, valueRect.size, CornerRadius(valH / 2f), style = Stroke(0.7.dp.toPx()))
            drawText(valueLayout, topLeft = Offset(vLeft + padH, vTop + padV))
        }
        result += PlacedLabel(lb, nameRect, valueRect)
    }
    return result
}

/** Círculo visível do mapa circular (PPI); usado para descartar rótulos que seriam cortados pela máscara. */
data class ClipCircle(val center: Offset, val radius: Float) {
    fun contains(p: Offset, inset: Float = 0f): Boolean = (p - center).getDistance() <= radius - inset
    fun contains(r: Rect, inset: Float = 0f): Boolean =
        contains(r.topLeft, inset) && contains(r.topRight, inset) && contains(r.bottomLeft, inset) && contains(r.bottomRight, inset)
}

/**
 * Desenha um texto "decorativo" (rótulo de anel, azimute) SÓ se houver espaço livre:
 * não pode colidir com [occupied] (rótulos de cidade, pinos, botões) nem sair do canvas/círculo.
 * Devolve o retângulo ocupado (já incluído em [occupied]) ou null se foi omitido (declutter).
 */
fun DrawScope.drawTextIfFree(
    textMeasurer: TextMeasurer,
    text: String,
    center: Offset,
    style: TextStyle,
    occupied: MutableList<Rect>,
    clipCircle: ClipCircle? = null,
    background: Color? = null
): Rect? {
    val layout = textMeasurer.measure(text, style, softWrap = false, maxLines = 1)
    val padH = if (background != null) 3.dp.toPx() else 0f
    val padV = if (background != null) 1.dp.toPx() else 0f
    val bw = layout.size.width + 2 * padH; val bh = layout.size.height + 2 * padV
    val r = Rect(center.x - bw / 2f, center.y - bh / 2f, center.x + bw / 2f, center.y + bh / 2f)
    val m = 2.dp.toPx()
    if (r.left < m || r.top < m || r.right > size.width - m || r.bottom > size.height - m) return null
    if (clipCircle != null && !clipCircle.contains(r, m)) return null
    if (occupied.any { it.inflate(2.dp.toPx()).overlaps(r) }) return null
    background?.let { drawRoundRect(it, r.topLeft, r.size, CornerRadius(bh / 2f)) }
    drawText(layout, topLeft = Offset(r.left + padH, r.top + padV))
    occupied += r
    return r
}

/** Pino de cidade: anel de contraste + núcleo colorido; selecionado ganha halo. */
fun DrawScope.drawCityPin(x: Float, y: Float, color: Color, selected: Boolean, theme: MapTheme) {
    val r = (if (selected) 5f else 3.6f).dp.toPx()
    if (selected) drawCircle(color.copy(alpha = 0.30f), radius = r * 2.6f, center = Offset(x, y))
    drawCircle(if (theme.isLight) Color(0xFF0F172A) else Color.White, radius = r + 1.3.dp.toPx(), center = Offset(x, y))
    drawCircle(color, radius = r, center = Offset(x, y))
}

/** Formata número em pt-BR com 1 casa (ex.: 12,4). Evita "12.399999999". */
fun fmt1(v: Double): String = String.format(java.util.Locale("pt", "BR"), "%.1f", v)
