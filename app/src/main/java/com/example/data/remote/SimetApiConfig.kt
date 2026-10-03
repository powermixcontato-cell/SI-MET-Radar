package com.example.data.remote

import com.example.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Servidor próprio do SI-MET (pasta simet-server): proxy com cache da Open-Meteo e dos avisos do INMET.
 *
 * - BuildConfig.SIMET_API_BASE_URL vazio → o app chama Open-Meteo e INMET diretamente (comportamento antigo).
 * - Preenchido (ex.: "https://simet-api.fly.dev/") → previsão vai para <base>/v1/forecast (mesmo JSON da
 *   Open-Meteo) e avisos para <base>/v1/alerts/sp/rss (mesmo formato RSS do INMET, já filtrado para SP).
 */
object SimetApiConfig {
    const val HEADER_KEY = "X-Simet-Key"

    /** Garante esquema http(s) e barra final (exigência do Retrofit). Valor inválido = "" (desligado). */
    fun normalizeBaseUrl(raw: String?): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return ""
        if (!v.startsWith("https://", ignoreCase = true) && !v.startsWith("http://", ignoreCase = true)) return ""
        return if (v.endsWith("/")) v else "$v/"
    }

    val baseUrl: String = normalizeBaseUrl(BuildConfig.SIMET_API_BASE_URL)
    val enabled: Boolean get() = baseUrl.isNotEmpty()

    fun openMeteoBaseUrl(base: String = baseUrl): String =
        if (base.isNotEmpty()) base else OpenMeteoConfig.OPEN_METEO_BASE_URL

    fun inmetAlertsUrl(base: String = baseUrl): String =
        if (base.isNotEmpty()) "${base}v1/alerts/sp/rss" else InmetConfig.RSS_URL

    /** Envia o token do servidor (se configurado) só para o próprio servidor. */
    fun authInterceptor(base: String = baseUrl, key: String = BuildConfig.SIMET_API_KEY): Interceptor = Interceptor { chain ->
        val req = chain.request()
        val host = base.toHttpUrlHostOrNull()
        if (key.isNotBlank() && host != null && req.url.host == host) {
            chain.proceed(req.newBuilder().header(HEADER_KEY, key).build())
        } else chain.proceed(req)
    }

    /** Acrescenta o interceptor do token quando o servidor está ligado. */
    fun applyTo(builder: OkHttpClient.Builder): OkHttpClient.Builder =
        if (enabled) builder.addInterceptor(authInterceptor()) else builder

    private fun String.toHttpUrlHostOrNull(): String? =
        try { java.net.URI(this).host } catch (_: Exception) { null }
}
