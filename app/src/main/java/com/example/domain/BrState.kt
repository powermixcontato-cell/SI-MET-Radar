package com.example.domain

import java.util.Locale

/**
 * Estados atendidos pelo app (seletor SP / PR / RS da tela inicial).
 * Centro/zoom/limites usados para recentralizar os mapas; mesorregiões IBGE usadas pelo INMET no campo "Área" dos avisos.
 */
enum class BrState(
    val uf: String,
    val displayName: String,
    val centerLat: Double,
    val centerLon: Double,
    val zoom: Int,
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
    /** Estação (cidade) aberta por padrão ao trocar para o estado. */
    val defaultStationId: String,
    /** Mesorregiões geográficas do IBGE (nomes usados pelo INMET nos avisos). */
    val mesoRegions: List<String>
) {
    SP(
        "SP", "São Paulo", -22.35, -48.55, 6, -25.35, -53.15, -19.75, -44.15, "sao_paulo",
        listOf(
            "São José do Rio Preto", "Ribeirão Preto", "Araçatuba", "Bauru", "Araraquara", "Piracicaba", "Campinas",
            "Presidente Prudente", "Marília", "Assis", "Itapetininga", "Macro Metropolitana Paulista",
            "Vale do Paraíba Paulista", "Litoral Sul Paulista", "Metropolitana de São Paulo"
        )
    ),
    PR(
        "PR", "Paraná", -24.65, -51.45, 6, -26.75, -54.65, -22.50, -48.00, "pr_curitiba",
        listOf(
            "Noroeste Paranaense", "Centro Ocidental Paranaense", "Norte Central Paranaense", "Norte Pioneiro Paranaense",
            "Centro Oriental Paranaense", "Oeste Paranaense", "Sudoeste Paranaense", "Centro-Sul Paranaense",
            "Sudeste Paranaense", "Metropolitana de Curitiba"
        )
    ),
    RS(
        "RS", "Rio Grande do Sul", -29.75, -53.25, 6, -33.75, -57.65, -27.05, -49.65, "rs_porto_alegre",
        listOf(
            "Noroeste Rio-grandense", "Nordeste Rio-grandense", "Centro Ocidental Rio-grandense",
            "Centro Oriental Rio-grandense", "Metropolitana de Porto Alegre", "Sudoeste Rio-grandense",
            "Sudeste Rio-grandense"
        )
    );

    /** "minLat,minLon,maxLat,maxLon" para a página do mapa. */
    val bboxParam: String get() = String.format(Locale.US, "%.2f,%.2f,%.2f,%.2f", minLat, minLon, maxLat, maxLon)

    fun contains(lat: Double, lon: Double): Boolean = lat in minLat..maxLat && lon in minLon..maxLon

    /** Mesorregiões deste estado citadas no texto de "Área" de um aviso (sem acento/caixa). */
    fun matchMesoRegions(areaText: String): Set<String> {
        val n = normalize(areaText)
        return mesoRegions.filter { meso -> Regex("\\b" + Regex.escape(normalize(meso)) + "\\b").containsMatchIn(n) }.toSet()
    }

    companion object {
        fun fromUf(uf: String?): BrState = entries.firstOrNull { it.uf.equals(uf, ignoreCase = true) } ?: SP

        /** Estações novas usam prefixo "pr_" / "rs_"; as 31 originais (sem prefixo) são de SP. */
        fun ofStationId(id: String): BrState = when {
            id.startsWith("pr_") -> PR
            id.startsWith("rs_") -> RS
            else -> SP
        }

        fun normalize(s: String): String =
            java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .replace('-', ' ')
                .lowercase(Locale.ROOT).trim()
    }
}
