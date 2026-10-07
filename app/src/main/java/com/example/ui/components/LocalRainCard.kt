package com.example.ui.components

import android.content.Context
import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppSettings
import com.example.data.remote.BrtTime
import com.example.data.remote.LocalRainForecast
import com.example.data.remote.LocalRainService
import com.example.data.remote.RainHour
import com.example.data.remote.RainPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

private val Rain = Color(0xFF0284C7)
private val WEEK = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
private val PtBr = Locale.forLanguageTag("pt-BR")

/** Nome do lugar mais próximo pelo Geocoder do aparelho; null se indisponível (sem inventar). */
private fun reverseLabel(ctx: Context, lat: Double, lon: Double): RainPlace? = try {
    if (!Geocoder.isPresent()) null else {
        @Suppress("DEPRECATION")
        val a = Geocoder(ctx, PtBr).getFromLocation(lat, lon, 1)?.firstOrNull()
        val city = a?.subAdminArea ?: a?.locality
        if (city.isNullOrBlank()) null else RainPlace(city, a?.adminArea, lat, lon)
    }
} catch (_: Exception) { null }

private fun errorText(e: Throwable): String = when (e) {
    is IOException -> "Sem conexão com a Open-Meteo agora. Verifique a internet e toque em Atualizar."
    else -> "Não foi possível ler a previsão da Open-Meteo agora."
}

/**
 * v5.1 — "Chuva no seu local": previsão de chuva da Open-Meteo para a localização do aparelho
 * ou qualquer cidade do Brasil (busca com sugestões). Último lugar e favoritos ficam em AppSettings.
 */
@Composable
fun LocalRainCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    var place by remember { mutableStateOf(RainPlace.decode(settings.localRainPlace)) }
    var favorites by remember { mutableStateOf(settings.localRainFavorites.mapNotNull { RainPlace.decode(it) }) }
    var forecast by remember { mutableStateOf<LocalRainForecast?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<RainPlace>>(emptyList()) }
    var searchMsg by remember { mutableStateOf<String?>(null) }
    var days by rememberSaveable { mutableIntStateOf(7) }

    fun choose(p: RainPlace) {
        place = p
        settings.localRainPlace = p.encode()
        query = ""; suggestions = emptyList(); searchMsg = null; reload = 0
        focus.clearFocus()
    }

    val locator = rememberMyLocationRequester { fix ->
        scope.launch {
            val p = withContext(Dispatchers.IO) { reverseLabel(context.applicationContext, fix.lat, fix.lon) }
                ?: RainPlace("Sua localização", null, fix.lat, fix.lon)
            choose(p)
        }
    }

    // Autocomplete (debounce 400 ms; rede em IO; cache de 10 min no serviço)
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { suggestions = emptyList(); searchMsg = null; return@LaunchedEffect }
        delay(400)
        try {
            val r = withContext(Dispatchers.IO) { LocalRainService.searchCities(q) }
            suggestions = r
            searchMsg = if (r.isEmpty()) "Nenhuma cidade do Brasil encontrada para \"$q\"." else null
        } catch (e: Exception) {
            suggestions = emptyList()
            searchMsg = if (e is IOException) "Sem conexão: não foi possível buscar cidades." else "Busca de cidades indisponível agora."
        }
    }

    LaunchedEffect(place, reload) {
        val p = place ?: return@LaunchedEffect
        loading = true; error = null
        try {
            forecast = withContext(Dispatchers.IO) { LocalRainService.forecast(p, force = reload > 0) }
        } catch (e: Exception) {
            error = errorText(e)
            if (forecast?.place != p) forecast = null
        } finally { loading = false }
    }

    SimetCard(modifier.testTag("card_local_rain"), accent = Rain) {
        SimetCardHeader(
            icon = Icons.Default.Umbrella,
            title = "Chuva no seu local",
            subtitle = place?.label ?: "Busque uma cidade ou use sua localização",
            accent = Rain
        ) {
            place?.let { p ->
                val fav = favorites.any { it.label == p.label }
                IconButton(onClick = {
                    favorites = if (fav) favorites.filterNot { it.label == p.label } else (listOf(p) + favorites).take(8)
                    settings.localRainFavorites = favorites.map { it.encode() }
                }, modifier = Modifier.testTag("btn_local_rain_fav")) {
                    Icon(if (fav) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (fav) "Remover dos favoritos" else "Adicionar aos favoritos", tint = Color(0xFFF59E0B))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it.take(60) },
            modifier = Modifier.fillMaxWidth().testTag("field_local_rain_search"),
            singleLine = true,
            placeholder = { Text("Buscar cidade (qualquer cidade do Brasil)", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, contentDescription = "Limpar busca") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { suggestions.firstOrNull()?.let { choose(it) } }),
            shape = RoundedCornerShape(12.dp)
        )
        if (suggestions.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))) {
                suggestions.forEach { s ->
                    Text(s.label, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().clickable { choose(s) }
                        .padding(horizontal = 12.dp, vertical = 9.dp).testTag("suggestion_local_rain"))
                }
            }
        }
        searchMsg?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { locator.request() }, enabled = !locator.busy, modifier = Modifier.testTag("btn_local_rain_gps")) {
                if (locator.busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Usar minha localização", fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            if (place != null) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Atualizar", fontSize = 12.sp, color = Rain, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { reload++ }.padding(8.dp).testTag("btn_local_rain_refresh"))
            }
        }
        if (favorites.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                items(favorites, key = { it.label }) { f ->
                    FilterChip(selected = f.label == place?.label, onClick = { choose(f) }, label = { Text(f.label, fontSize = 11.sp) })
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
        val fc = forecast?.takeIf { it.place == place }
        if (fc != null) LocalRainContent(fc, days, onDays = { days = it })
        else if (place == null) {
            Spacer(Modifier.height(6.dp))
            Text("Escolha um lugar para ver a chuva prevista hora a hora (48 h) e por dia (7 ou 15 dias).",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LocalRainContent(fc: LocalRainForecast, days: Int, onDays: (Int) -> Unit) {
    val now = remember(fc) { LocalRainService.nowBrt() }
    val next = remember(fc, now) { LocalRainService.nextHours(fc, now, 48) }
    val summary = remember(next, now) { LocalRainService.rainSummary(next, now) }
    Spacer(Modifier.height(10.dp))
    Text(summary, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Rain, modifier = Modifier.testTag("text_local_rain_summary"))
    val total24 = next.take(24).mapNotNull { it.mm }.sum()
    val total48 = next.mapNotNull { it.mm }.sum()
    Text("Total previsto: %.1f mm em 24 h • %.1f mm em 48 h".format(PtBr, total24, total48),
        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Text("Próximas 48 h (mm por hora • probabilidade)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
        items(next, key = { it.time.toString() }) { h -> HourBar(h, now) }
    }
    Spacer(Modifier.height(10.dp))
    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Text("Por dia", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        listOf(7, 15).forEach { d ->
            FilterChip(selected = days == d, onClick = { onDays(d) }, label = { Text("$d dias", fontSize = 11.sp) },
                modifier = Modifier.padding(start = 6.dp).testTag("chip_local_rain_$d"))
        }
    }
    val today = now.date
    fc.daily.filter { it.date >= today }.take(days).forEach { d ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            val name = if (d.date == today) "Hoje" else if (d.date == today.plusDays(1)) "Amanhã"
                else WEEK[d.date.dayOfWeek - 1] + " %02d/%02d".format(d.date.day, d.date.month)
            Text(name, fontSize = 12.sp, modifier = Modifier.width(92.dp))
            val mm = d.mm
            Box(Modifier.weight(1f).height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                if (mm != null && mm > 0) Box(Modifier.fillMaxWidth((mm / 40.0).coerceIn(0.03, 1.0).toFloat()).height(8.dp)
                    .background(Rain, RoundedCornerShape(4.dp)))
            }
            Text(if (mm == null) "—" else "%.1f mm".format(PtBr, mm), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(62.dp).padding(start = 6.dp))
            Text(d.prob?.let { "$it%" } ?: "—", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp))
        }
    }
    if (days == 15 && fc.daily.size >= 16) Text("Dias 8–15: tendência, menor confiabilidade.", fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    val f = LocalRainService.epochToBrt(fc.fetchedAtMs / 1000)
    val runs = fc.runs.joinToString(" • ") { r ->
        val i = LocalRainService.epochToBrt(r.initUtcEpoch)
        val a = LocalRainService.epochToBrt(r.availableUtcEpoch)
        "${r.model} rodada %02d/%02d %02dh, publicada %02dh%02d".format(i.day, i.month, i.hour, a.hour, a.minute)
    }
    Text("Fonte: Open-Meteo (open-meteo.com, CC BY 4.0), modelo best_match. Consultado às %02dh%02d BRT.".format(f.hour, f.minute) +
        (if (runs.isNotEmpty()) " Rodadas mais recentes (BRT): $runs." else ""),
        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun HourBar(h: RainHour, now: BrtTime) {
    val mm = h.mm
    val wet = (mm ?: 0.0) >= LocalRainService.RAIN_MM
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(30.dp)) {
        Text(h.prob?.let { "$it%" } ?: "—", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.width(14.dp).height(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.BottomCenter) {
            if (mm != null && mm > 0) Box(Modifier.width(14.dp).height((44 * (mm / 10.0).coerceIn(0.06, 1.0)).dp)
                .background(if (wet) Rain else Rain.copy(alpha = 0.4f), RoundedCornerShape(3.dp)))
        }
        Text(if (mm == null) "—" else if (mm < 0.05) "0" else "%.1f".format(PtBr, mm), fontSize = 9.sp,
            fontWeight = if (wet) FontWeight.Bold else FontWeight.Normal)
        Text(if (h.time.hour == 0 && h.time.date != now.date) "%02d/%02d".format(h.time.day, h.time.month)
            else "${h.time.hour}h", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
