package com.example.domain

/**
 * Limiares de vazão (m³/s) por ponto, calculados em 07/10/2026 a partir da reanálise diária do GloFAS v4
 * (Open-Meteo Flood API, river_discharge; a API devolve valores de 1997 a 2025 = 29 anos completos):
 *  q2  ≈ mediana dos máximos anuais (cheia com retorno ~2 anos)
 *  q5  ≈ percentil 80 dos máximos anuais (~5 anos)
 *  q20 ≈ percentil 95 dos máximos anuais (~20 anos)
 * Script: /workspace/simet51-research/hist.py (não incluso no app). São referências do MODELO, não cotas oficiais.
 */
object FloodThresholds {
    data class Th(val q2: Double, val q5: Double, val q20: Double, val meanQ: Double, val years: Int, val maxDate: String, val maxQ: Double) {
        val period: String get() = "$years anos"
    }

    val byGauge: Map<String, Th> = mapOf(
        "rs_guaiba" to Th(9637.3, 11884.0, 15487.2, 2025.3, 29, "2023-11-21", 18662.8),
        "rs_taquari_lajeado" to Th(4622.8, 5713.8, 7378.0, 649.2, 29, "2023-11-20", 8071.4),
        "rs_taquari_mucum" to Th(4196.6, 5068.9, 6530.3, 526.5, 29, "2023-11-19", 7260.1),
        "rs_jacui_cachoeira" to Th(2510.9, 3853.8, 4964.9, 717.8, 29, "2023-11-21", 5469.2),
        "rs_sinos" to Th(532.3, 698.8, 892.3, 112.3, 29, "2008-10-27", 943.1),
        "rs_cai" to Th(720.1, 1001.3, 1171.2, 158.5, 29, "2024-05-14", 1379.4),
        "rs_uruguai_uruguaiana" to Th(18619.9, 28640.8, 32733.1, 4612.3, 29, "2014-07-01", 41590.1),
        "pr_iguacu_uniao" to Th(1360.4, 1818.7, 2137.0, 469.2, 29, "1998-10-09", 3117.9),
        "pr_iguacu_curitiba" to Th(159.7, 178.9, 217.3, 38.7, 29, "2024-12-10", 260.8),
        "pr_iguacu_foz" to Th(7327.2, 10689.5, 12792.8, 1559.9, 29, "2000-09-15", 17417.1),
        "pr_tibagi" to Th(1957.8, 2929.9, 3898.4, 362.2, 29, "1997-01-23", 4264.8),
        "pr_ivai" to Th(1121.0, 1431.2, 2141.9, 272.7, 29, "1997-01-23", 2516.7),
        "pr_parana_guaira" to Th(16981.4, 25175.8, 32061.7, 10085.3, 29, "1997-02-04", 58478.0),
        "sp_ribeira_registro" to Th(2112.5, 2696.7, 3103.6, 440.7, 29, "2004-01-27", 4035.4),
        "sp_tiete_sp" to Th(407.3, 557.5, 894.4, 93.6, 29, "2000-03-29", 1218.0),
        "sp_piracicaba" to Th(695.6, 882.1, 1212.4, 189.6, 29, "2000-01-05", 1464.5),
        "sp_paraiba_sjc" to Th(791.5, 1118.4, 1299.2, 207.4, 29, "2000-01-04", 2375.8),
    )
}
