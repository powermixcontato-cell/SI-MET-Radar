package com.example.data.remote

import android.util.Xml
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * INMET – avisos meteorológicos oficiais (feed "Alert-AS RSS").
 * Feed: https://apiprevmet3.inmet.gov.br/avisos/rss  •  Portal: https://avisos.inmet.gov.br
 */
object InmetConfig {
    const val RSS_URL = "https://apiprevmet3.inmet.gov.br/avisos/rss"
    const val PORTAL_URL = "https://avisos.inmet.gov.br"
    const val SOURCE_LABEL = "INMET"
    fun alertLink(id: String) = "$PORTAL_URL/$id"
}

data class InmetAlert(
    val id: String,
    val title: String,
    val status: String?,
    val event: String?,
    val severity: String?,
    /** epoch millis, hora de Brasília interpretada em America/Sao_Paulo */
    val startMillis: Long?,
    val endMillis: Long?,
    val description: String?,
    val areas: List<String>,
    val link: String
)

/** Mesorregiões IBGE de SP usadas pelo INMET no campo "Área", por estação do app. */
object SpMesoRegions {
    val byStationId: Map<String, String> = mapOf(
        "bauru" to "Bauru",
        "botucatu" to "Bauru",
        "presidente_prudente" to "Presidente Prudente",
        "sao_paulo" to "Metropolitana de São Paulo",
        "santos" to "Metropolitana de São Paulo",
        "campinas" to "Campinas",
        "ribeirao_preto" to "Ribeirão Preto",
        "franca" to "Ribeirão Preto",
        "barretos" to "Ribeirão Preto",
        "bebedouro" to "Ribeirão Preto",
        "sao_jose_dos_campos" to "Vale do Paraíba Paulista",
        "ubatuba" to "Vale do Paraíba Paulista",
        "campos_do_jordao" to "Vale do Paraíba Paulista",
        "taubate" to "Vale do Paraíba Paulista",
        "sorocaba" to "Macro Metropolitana Paulista",
        "jundiai" to "Macro Metropolitana Paulista",
        "sao_jose_do_rio_preto" to "São José do Rio Preto",
        "olimpia" to "São José do Rio Preto",
        "catanduva" to "São José do Rio Preto",
        "votuporanga" to "São José do Rio Preto",
        "sao_carlos" to "Araraquara",
        "araraquara" to "Araraquara",
        "piracicaba" to "Piracicaba",
        "limeira" to "Piracicaba",
        "rio_claro" to "Piracicaba",
        "marilia" to "Marília",
        "registro" to "Litoral Sul Paulista",
        "aracatuba" to "Araçatuba",
        "assis" to "Assis",
        "ourinhos" to "Assis",
        "itapeva" to "Itapetininga"
    )

    val all: Set<String> = byStationId.values.toSet()

    fun normalize(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(Locale.ROOT).trim()

    /** Mesorregiões de SP citadas no texto de "Área" (comparação sem acento/caixa). */
    fun matchIn(areaText: String): Set<String> {
        val n = normalize(areaText)
        return all.filter { meso -> Regex("\\b" + Regex.escape(normalize(meso)) + "\\b").containsMatchIn(n) }.toSet()
    }
}

object InmetRssParser {
    private val dateFmt: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
            isLenient = false
        }

    /** "2026-10-03 00:00:00.0" (hora de Brasília, sem fuso no texto) → epoch millis. */
    fun parseInmetDate(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return try { dateFmt.parse(raw.trim().substringBefore('.'))?.time } catch (_: Exception) { null }
    }

    fun decodeEntities(s: String): String {
        var out = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        out = Regex("&#(\\d+);").replace(out) { m -> m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value }
        out = Regex("&#x([0-9a-fA-F]+);").replace(out) { m -> m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value }
        val named = mapOf("&aacute;" to "á", "&eacute;" to "é", "&iacute;" to "í", "&oacute;" to "ó", "&uacute;" to "ú",
            "&atilde;" to "ã", "&otilde;" to "õ", "&ccedil;" to "ç", "&acirc;" to "â", "&ecirc;" to "ê", "&ocirc;" to "ô",
            "&Aacute;" to "Á", "&Eacute;" to "É", "&Iacute;" to "Í", "&Oacute;" to "Ó", "&Uacute;" to "Ú", "&Ccedil;" to "Ç", "&Atilde;" to "Ã")
        named.forEach { (k, v) -> out = out.replace(k, v) }
        return out.replace("&amp;", "&")
    }

    private fun stripTags(s: String): String =
        decodeEntities(s.replace(Regex("(?i)<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), " "))
            .replace(Regex("[ \\t]+"), " ").trim()

    /** Extrai pares (rótulo → valor) das linhas <tr><th|td>rótulo</th|td><td>valor</td></tr> da tabela HTML. */
    fun parseDescriptionTable(html: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        Regex("(?is)<tr[^>]*>(.*?)</tr>").findAll(html).forEach { row ->
            val cells = Regex("(?is)<t[hd][^>]*>(.*?)</t[hd]>").findAll(row.groupValues[1]).map { stripTags(it.groupValues[1]) }.toList()
            if (cells.size >= 2) {
                val key = cells[0].trimEnd(':').trim()
                if (key.isNotEmpty()) result[key] = cells.drop(1).joinToString(" ").trim()
            }
        }
        return result
    }

    private fun field(map: Map<String, String>, vararg names: String): String? {
        for (n in names) {
            val hit = map.entries.firstOrNull { SpMesoRegions.normalize(it.key) == SpMesoRegions.normalize(n) }
            if (hit != null && hit.value.isNotBlank()) return hit.value
        }
        return null
    }

    fun parse(xml: String): List<InmetAlert> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))
        val alerts = mutableListOf<InmetAlert>()
        var inItem = false
        var title = ""; var guid = ""; var link = ""; var desc = ""
        var tag: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    if (parser.name == "item") { inItem = true; title = ""; guid = ""; link = ""; desc = "" }
                }
                XmlPullParser.TEXT -> if (inItem) {
                    val t = parser.text ?: ""
                    when (tag) { "title" -> title += t; "guid" -> guid += t; "link" -> link += t; "description" -> desc += t }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item") {
                        inItem = false
                        val table = parseDescriptionTable(desc)
                        val linkGraph = field(table, "Link Gráfico", "Link Grafico")
                        val id = Regex("(\\d{3,})").findAll(guid.ifBlank { link }).lastOrNull()?.value
                            ?: Regex("(\\d{3,})").findAll(linkGraph ?: "").lastOrNull()?.value
                            ?: guid.ifBlank { title }.hashCode().toString()
                        val area = field(table, "Área", "Area") ?: ""
                        alerts += InmetAlert(
                            id = id,
                            title = stripTags(title),
                            status = field(table, "Status"),
                            event = field(table, "Evento"),
                            severity = field(table, "Severidade"),
                            startMillis = parseInmetDate(field(table, "Início", "Inicio")),
                            endMillis = parseInmetDate(field(table, "Fim")),
                            description = field(table, "Descrição", "Descricao"),
                            areas = area.split(",", ";", "\n").map { it.trim() }.filter { it.isNotEmpty() },
                            link = InmetConfig.alertLink(id)
                        )
                    }
                    tag = null
                }
            }
            event = parser.next() // next() entrega CDATA como TEXT
        }
        return alerts
    }

    /** Mesorregiões de SP afetadas pelo aviso (vazio = aviso não é de SP). */
    fun spMesoRegionsOf(alert: InmetAlert): Set<String> = SpMesoRegions.matchIn(alert.areas.joinToString(", "))

    fun severityToAppLevel(sev: String?): String {
        val n = SpMesoRegions.normalize(sev ?: "")
        return when {
            n.contains("grande perigo") -> "ALERTA_VERMELHO"
            n.contains("perigo potencial") -> "ALERTA_AMARELO"
            n.contains("perigo") -> "ALERTA_LARANJA"
            else -> "AVISO_METEOROLOGICO"
        }
    }
}

class InmetAlertsService(
    private val client: OkHttpClient = SimetApiConfig.applyTo(
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
    ).build(),
    /** Servidor próprio ligado → <base>/v1/alerts/sp/rss (mesmo formato do RSS do INMET, só SP e vigentes). */
    private val url: String = SimetApiConfig.inmetAlertsUrl()
) {
    /** Baixa e faz o parse do feed. Lança exceção em erro HTTP/rede/parse. */
    fun fetch(): List<InmetAlert> {
        val req = Request.Builder().url(url).header("Accept", "application/rss+xml, application/xml").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("INMET HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw java.io.IOException("INMET resposta vazia")
            return InmetRssParser.parse(body)
        }
    }
}
