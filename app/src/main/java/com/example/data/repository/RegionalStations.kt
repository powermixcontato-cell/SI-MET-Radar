package com.example.data.repository

import com.example.data.local.entity.WeatherStationEntity

/**
 * Cidades do Paraná e do Rio Grande do Sul (v5.1). Gravadas como "aguardando atualização" (lastUpdated = 0):
 * nenhum valor meteorológico é inventado; tudo vem da Open-Meteo na primeira atualização.
 * Prefixo do id = estado (BrState.ofStationId).
 */
object RegionalStations {
    private fun st(id: String, name: String, region: String, lat: Double, lon: Double) = WeatherStationEntity(
        id = id, name = name, region = region, lat = lat, lon = lon,
        currentTemp = 0.0, minTemp = 0.0, maxTemp = 0.0, feelsLike = 0.0, humidity = 0, pressure = 0,
        windSpeed = 0.0, windDirection = "—", rainVolumeMm = 0.0, rainProbability = 0, dbzReflectivity = 0,
        weatherCondition = "Dados indisponíveis", iconType = "cloudy",
        synopticSummary = "Aguardando a primeira atualização (Open-Meteo).",
        sunrise = "—", sunset = "—", uvIndex = 0, aqi = 0, lastUpdated = 0L
    )

    val paranaAndRs: List<WeatherStationEntity> = listOf(
        // Paraná
        st("pr_curitiba", "Curitiba", "Metropolitana de Curitiba (PR)", -25.4284, -49.2733),
        st("pr_uniao_da_vitoria", "União da Vitória", "Sudeste Paranaense • Rio Iguaçu (PR)", -26.2273, -51.0873),
        st("pr_ponta_grossa", "Ponta Grossa", "Campos Gerais (PR)", -25.0945, -50.1633),
        st("pr_londrina", "Londrina", "Norte Central Paranaense (PR)", -23.3045, -51.1696),
        st("pr_maringa", "Maringá", "Norte Central Paranaense (PR)", -23.4205, -51.9333),
        st("pr_paranavai", "Paranavaí", "Noroeste Paranaense • cana (PR)", -23.0816, -52.4617),
        st("pr_cascavel", "Cascavel", "Oeste Paranaense (PR)", -24.9555, -53.4552),
        st("pr_foz_do_iguacu", "Foz do Iguaçu", "Oeste Paranaense (PR)", -25.5163, -54.5854),
        st("pr_guarapuava", "Guarapuava", "Centro-Sul Paranaense (PR)", -25.3935, -51.4562),
        st("pr_paranagua", "Paranaguá", "Litoral (PR)", -25.5161, -48.5225),
        // Rio Grande do Sul
        st("rs_porto_alegre", "Porto Alegre", "Metropolitana de Porto Alegre • Guaíba (RS)", -30.0346, -51.2177),
        st("rs_novo_hamburgo", "Novo Hamburgo", "Vale do Sinos (RS)", -29.6783, -51.1309),
        st("rs_lajeado", "Lajeado", "Vale do Taquari (RS)", -29.4669, -51.9644),
        st("rs_caxias_do_sul", "Caxias do Sul", "Serra Gaúcha (RS)", -29.1678, -51.1794),
        st("rs_santa_cruz_do_sul", "Santa Cruz do Sul", "Vale do Rio Pardo (RS)", -29.7175, -52.4258),
        st("rs_santa_maria", "Santa Maria", "Centro (RS)", -29.6842, -53.8069),
        st("rs_passo_fundo", "Passo Fundo", "Planalto Norte (RS)", -28.2620, -52.4064),
        st("rs_pelotas", "Pelotas", "Sul (RS)", -31.7654, -52.3376),
        st("rs_uruguaiana", "Uruguaiana", "Fronteira Oeste • Rio Uruguai (RS)", -29.7547, -57.0883),
        st("rs_santa_rosa", "Santa Rosa", "Noroeste (RS)", -27.8708, -54.4813)
    )
}
