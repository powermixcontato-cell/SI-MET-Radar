package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service to consult Gemini API with Google Maps Grounding tool
 * Provides high-precision geospatial rain and storm diagnostic for São Paulo State locations.
 */
class GeminiMapsWeatherService {

    companion object {
        private const val TAG = "GeminiMapsWeather"
        /** Nomes de modelo mantidos como no projeto original; não trocar sem confirmar que existem. */
        const val PRIMARY_MODEL = "gemini-3.5-flash"
        const val FALLBACK_MODEL = "gemini-2.5-flash"
        const val UNAVAILABLE_LABEL = "Diagnóstico por IA indisponível"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getRainPrecisionReport(
        query: String,
        latitude: Double?,
        longitude: Double?,
        activeStormsSummary: String = "",
        nearbyStationSummary: String = ""
    ): MapsRainPrecisionResult = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            Log.w(TAG, "GEMINI_API_KEY ausente no BuildConfig", e)
            ""
        }

        // If API key is available, call Gemini 3.5 Flash with googleMaps Grounding tool
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val result = callGeminiWithMapsGrounding(
                    model = PRIMARY_MODEL,
                    apiKey = apiKey,
                    query = query,
                    latitude = latitude,
                    longitude = longitude,
                    activeStormsSummary = activeStormsSummary,
                    nearbyStationSummary = nearbyStationSummary
                )
                if (result != null) {
                    return@withContext result
                }
            } catch (e: Exception) {
                Log.w(TAG, "$PRIMARY_MODEL falhou, tentando $FALLBACK_MODEL", e)
                try {
                    val fallbackResult = callGeminiWithMapsGrounding(
                        model = FALLBACK_MODEL,
                        apiKey = apiKey,
                        query = query,
                        latitude = latitude,
                        longitude = longitude,
                        activeStormsSummary = activeStormsSummary,
                        nearbyStationSummary = nearbyStationSummary
                    )
                    if (fallbackResult != null) {
                        return@withContext fallbackResult
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "$FALLBACK_MODEL falhou", e2)
                }
            }
        }

        // Sem IA disponível: nenhum texto interpretativo inventado, só os dados reais da estação
        return@withContext generateLocalPrecisionDiagnostic(
            query = query,
            latitude = latitude,
            longitude = longitude,
            activeStormsSummary = activeStormsSummary,
            nearbyStationSummary = nearbyStationSummary
        )
    }

    private fun callGeminiWithMapsGrounding(
        model: String,
        apiKey: String,
        query: String,
        latitude: Double?,
        longitude: Double?,
        activeStormsSummary: String,
        nearbyStationSummary: String
    ): MapsRainPrecisionResult? {
        // Chave no header x-goog-api-key (não na URL, que aparece em logs/proxies)
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

        val promptText = buildString {
            append("Você é o especialista meteorológico oficial do SI Met RADAR integrado com Google Maps.\n")
            append("OBJETIVO: Fornecer um diagnóstico de altíssima precisão geográfica sobre CHUVA e RISCO DE TEMPESTADE para o local solicitado.\n")
            append("LOCAL CONSULTADO: $query\n")
            if (latitude != null && longitude != null) {
                append("COORDENADAS EXATAS DO USUÁRIO (GPS): Latitude: $latitude, Longitude: $longitude\n")
            }
            if (nearbyStationSummary.isNotBlank()) {
                append("DADOS REAIS DA ESTAÇÃO MAIS PRÓXIMA (Open-Meteo): $nearbyStationSummary\n")
            }
            if (activeStormsSummary.isNotBlank()) {
                append("AVISOS OFICIAIS VIGENTES (INMET): $activeStormsSummary\n")
            }
            append("\nDIRETRIZES:")
            append("\n1. Identifique no Google Maps o ponto exato, bairro, acessos viários ou marcos conhecidos.")
            append("\n2. Indique a probabilidade e intensidade de chuva (Sem Chuva, Chuva Fraca, Pancadas Moderadas, Tempestade Forte com Granizo).")
            append("\n3. Alerte sobre riscos pontuais (alagamento em vias baixas, rajadas de vento, visibilidade em rodovias).")
            append("\n4. Forneça uma recomendação prática direta e objetiva.")
            append("\nMantenha a resposta estruturada com tópicos claros, elegante e sem prolixidade.")
        }

        val requestJson = JSONObject()

        // Contents
        val partsArray = JSONArray().apply {
            put(JSONObject().apply { put("text", promptText) })
        }
        val contentsArray = JSONArray().apply {
            put(JSONObject().apply { put("parts", partsArray) })
        }
        requestJson.put("contents", contentsArray)

        // Tools: Grounding with Google Maps
        val toolsArray = JSONArray().apply {
            put(JSONObject().apply {
                put("googleMaps", JSONObject())
            })
        }
        requestJson.put("tools", toolsArray)

        // Tool Config: Geolocation context if coordinates present
        if (latitude != null && longitude != null) {
            val latLng = JSONObject().apply {
                put("latitude", latitude)
                put("longitude", longitude)
            }
            val retrievalConfig = JSONObject().apply {
                put("latLng", latLng)
            }
            val toolConfig = JSONObject().apply {
                put("retrievalConfig", retrievalConfig)
            }
            requestJson.put("toolConfig", toolConfig)
        }

        val request = Request.Builder()
            .url(endpoint)
            .header("x-goog-api-key", apiKey)
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()

        val (textPart, firstCandidate) = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                Log.w(TAG, "API Error HTTP ${response.code}: ${errBody.take(300)}")
                return null
            }
            val respBody = response.body?.string() ?: return null
            val root = JSONObject(respBody)
            val candidates = root.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val first = candidates.getJSONObject(0)
            val content = first.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            if (parts.length() == 0) return null
            val t = parts.getJSONObject(0).optString("text", "")
            if (t.isBlank()) return null
            Pair(t, first)
        }

        // Extract grounding chunks or metadata if returned
        var groundedPlaces = listOf<String>()
        val groundingMetadata = firstCandidate.optJSONObject("groundingMetadata")
        if (groundingMetadata != null) {
            val chunks = groundingMetadata.optJSONArray("groundingChunks")
            if (chunks != null) {
                val placesList = mutableListOf<String>()
                for (i in 0 until chunks.length()) {
                    val chunk = chunks.getJSONObject(i)
                    val mapsChunk = chunk.optJSONObject("maps")
                    val title = mapsChunk?.optString("title") ?: chunk.optJSONObject("web")?.optString("title")
                    if (!title.isNullOrBlank()) {
                        placesList.add(title)
                    }
                }
                groundedPlaces = placesList
            }
        }

        return MapsRainPrecisionResult(
            locationName = query,
            reportText = textPart,
            isGroundedWithMaps = true,
            modelUsed = model,
            groundedPlaces = groundedPlaces,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun generateLocalPrecisionDiagnostic(
        query: String,
        latitude: Double?,
        longitude: Double?,
        activeStormsSummary: String,
        nearbyStationSummary: String
    ): MapsRainPrecisionResult {
        val report = buildString {
            append("**$UNAVAILABLE_LABEL** (sem chave do Gemini ou falha na API).\n\n")
            append("Dados reais disponíveis para $query")
            if (latitude != null && longitude != null) append(" (Lat ${"%.4f".format(latitude)}, Lon ${"%.4f".format(longitude)})")
            append(":\n")
            append(if (nearbyStationSummary.isNotBlank()) "• $nearbyStationSummary (Fonte: Open-Meteo)\n" else "• Sem dados da estação.\n")
            if (activeStormsSummary.isNotBlank()) append("• $activeStormsSummary\n")
        }
        return MapsRainPrecisionResult(
            locationName = query,
            reportText = report,
            isGroundedWithMaps = false,
            modelUsed = UNAVAILABLE_LABEL,
            groundedPlaces = emptyList(),
            timestamp = System.currentTimeMillis()
        )
    }
}

data class MapsRainPrecisionResult(
    val locationName: String,
    val reportText: String,
    val isGroundedWithMaps: Boolean,
    val modelUsed: String,
    val groundedPlaces: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
