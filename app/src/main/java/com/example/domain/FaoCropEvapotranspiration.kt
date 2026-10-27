package com.example.domain

import kotlin.math.roundToInt

/**
 * Cálculo da Evapotranspiração da Cultura (ETc) conforme diretrizes oficiais da FAO-56
 * (Crop evapotranspiration - Guidelines for computing crop water requirements, FAO Irrigation and drainage paper 56).
 *
 * Fórmula:
 *   ETc = ET0 × Kc  (mm/dia)
 *
 * Onde:
 *   - ET0: Evapotranspiração de referência calculada pelo método FAO Penman-Monteith (fonte: Open-Meteo).
 *   - Kc: Coeficiente de cultivo tabelado na Tabela 12 da FAO-56 para cada fase de desenvolvimento.
 */
enum class CropType(val label: String, val emoji: String, val scientificName: String) {
    CANA("Cana-de-açúcar", "🌱", "Saccharum officinarum"),
    CITROS("Citros (Laranja)", "🍊", "Citrus sinensis"),
    SOJA("Soja", "🌿", "Glycine max"),
    MILHO("Milho", "🌽", "Zea mays"),
    CAFE("Café", "☕", "Coffea arabica")
}

data class CropStageInfo(
    val id: String,
    val name: String,
    val description: String,
    val kc: Double
)

object FaoCropEvapotranspiration {

    val stagesByCrop: Map<CropType, List<CropStageInfo>> = mapOf(
        CropType.CANA to listOf(
            CropStageInfo("ini", "Fase Inicial", "Emergência e perfilhamento inicial (Kc 0.40)", 0.40),
            CropStageInfo("dev", "Desenvolvimento", "Perfilhamento e alongamento inicial (Kc 0.85)", 0.85),
            CropStageInfo("mid", "Fase Média (Pico)", "Grande crescimento / Cobertura total (Kc 1.25)", 1.25),
            CropStageInfo("end", "Fase Final", "Maturação e acúmulo de sacarose (Kc 0.75)", 0.75)
        ),
        CropType.CITROS to listOf(
            CropStageInfo("ini", "Fase Inicial", "Pós-colheita e repouso (Kc 0.70)", 0.70),
            CropStageInfo("flo", "Floração", "Floração e pegamento de frutos (Kc 0.75)", 0.75),
            CropStageInfo("mid", "Fase Média (Pico)", "Crescimento e expansão dos frutos (Kc 0.65)", 0.65),
            CropStageInfo("end", "Fase Final", "Maturação dos frutos e colheita (Kc 0.70)", 0.70)
        ),
        CropType.SOJA to listOf(
            CropStageInfo("ini", "Fase Inicial", "Emergência até V2 (Kc 0.40)", 0.40),
            CropStageInfo("dev", "Desenvolvimento", "Crescimento vegetativo V3 a R1 (Kc 0.80)", 0.80),
            CropStageInfo("mid", "Fase Média (Pico)", "Floração plena e enchimento R2 a R5 (Kc 1.15)", 1.15),
            CropStageInfo("end", "Fase Final", "Maturação fisiológica R6 a R8 (Kc 0.50)", 0.50)
        ),
        CropType.MILHO to listOf(
            CropStageInfo("ini", "Fase Inicial", "Emergência até 4 folhas V4 (Kc 0.35)", 0.35),
            CropStageInfo("dev", "Desenvolvimento", "Desenvolvimento vegetativo V6 a VT (Kc 0.80)", 0.80),
            CropStageInfo("mid", "Fase Média (Pico)", "Espigamento e enchimento de grãos R1 a R3 (Kc 1.20)", 1.20),
            CropStageInfo("end", "Fase Final", "Maturação fisiológica e secagem R4 a R6 (Kc 0.60)", 0.60)
        ),
        CropType.CAFE to listOf(
            CropStageInfo("ini", "Fase Inicial", "Pós-colheita e repouso de inverno (Kc 0.90)", 0.90),
            CropStageInfo("flo", "Floração", "Floração e fase chumbinho (Kc 1.05)", 1.05),
            CropStageInfo("mid", "Fase Média (Pico)", "Expansão e granação dos frutos (Kc 1.10)", 1.10),
            CropStageInfo("end", "Fase Final", "Maturação dos grãos / Pré-colheita (Kc 0.95)", 0.95)
        )
    )

    fun calculateEtc(et0Mm: Double?, kc: Double): Double? {
        if (et0Mm == null || et0Mm <= 0.0) return null
        return ((et0Mm * kc) * 10.0).roundToInt() / 10.0
    }
}
