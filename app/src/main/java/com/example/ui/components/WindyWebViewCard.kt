package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.domain.BrState
import java.util.Locale

/**
 * Card "Satélite e vento" (v5.1), logo abaixo do mapa de chuva.
 * - Satélite: imagem real Esri World Imagery + nomes/limites + nuvens GOES-East IR (NASA GIBS) + setas de vento (Open-Meteo).
 * - Windy: o embed original (vento ou radar), mantido como alternativa.
 * Mesmo padrão dos outros cards (cantos 18 dp, padding 16 dp, cabeçalho com ícone), altura fixa de 300 dp,
 * WebView recortado e sem gestos no card (toque = tela cheia interativa), para não quebrar a rolagem.
 */
@Composable
fun WindyWebViewCard(
    state: BrState,
    focusLat: Double,
    focusLon: Double,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable { mutableStateOf(0) }          // 0 = satélite, 1 = Windy
    var windyOverlay by rememberSaveable { mutableStateOf("wind") } // wind | radar
    var expanded by rememberSaveable { mutableStateOf(false) }

    SimetCard(modifier = modifier.testTag("card_windy_webview")) {
        SimetCardHeader(
            icon = if (tab == 0) Icons.Default.Satellite else Icons.Default.Air,
            title = if (tab == 0) "Satélite • ${state.uf}" else "Vento (Windy) • ${state.uf}",
            subtitle = if (tab == 0) "Esri World Imagery • nuvens GOES-East • vento Open-Meteo" else "Embed windy.com • toque para interagir"
        ) {
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.OpenInFull, contentDescription = "Ampliar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Satélite", fontSize = 12.sp) })
            FilterChip(selected = tab == 1 && windyOverlay == "wind", onClick = { tab = 1; windyOverlay = "wind" }, label = { Text("Vento (Windy)", fontSize = 12.sp) })
            FilterChip(selected = tab == 1 && windyOverlay == "radar", onClick = { tab = 1; windyOverlay = "radar" }, label = { Text("Radar (Windy)", fontSize = 12.sp) })
        }
        Spacer(Modifier.height(8.dp))
        val mapModifier = Modifier.fillMaxWidth().height(300.dp)
        if (tab == 0) {
            SimetLeafletMap(
                mode = SimetMapMode.SATELLITE, lat = state.centerLat, lon = state.centerLon, zoom = state.zoom, state = state,
                interactive = false, modifier = mapModifier, onTap = { expanded = true }
            )
        } else {
            WindyEmbed(lat = focusLat, lon = focusLon, overlay = windyOverlay, interactive = false, modifier = mapModifier, onTap = { expanded = true })
        }
    }

    if (expanded) {
        if (tab == 0) {
            ExpandedLeafletMapDialog(
                title = "Satélite • ${state.displayName}",
                subtitle = "Esri World Imagery • nuvens GOES-East IR (NASA GIBS) • vento Open-Meteo",
                mode = SimetMapMode.SATELLITE, state = state, lat = state.centerLat, lon = state.centerLon, zoom = state.zoom,
                onDismiss = { expanded = false }
            )
        } else {
            SimetFullscreenDialog(
                title = if (windyOverlay == "wind") "Vento • Windy" else "Radar • Windy",
                subtitle = "windy.com (embed público)",
                onDismiss = { expanded = false }
            ) {
                WindyEmbed(lat = focusLat, lon = focusLon, overlay = windyOverlay, interactive = true, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

fun windyEmbedUrl(lat: Double, lon: Double, overlay: String, zoom: Int = 6): String {
    val la = String.format(Locale.US, "%.4f", lat)
    val lo = String.format(Locale.US, "%.4f", lon)
    val product = if (overlay == "radar") "radar" else "ecmwf"
    return "https://embed.windy.com/embed2.html?lat=$la&lon=$lo&detailLat=$la&detailLon=$lo&width=650&height=400&zoom=$zoom" +
        "&level=surface&overlay=$overlay&product=$product&menu=&message=&marker=true&calendar=now&pressure=&type=map" +
        "&location=coordinates&detail=&metricWind=km%2Fh&metricTemp=%C2%B0C&radarRange=-1"
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WindyEmbed(
    lat: Double,
    lon: Double,
    overlay: String,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null
) {
    val url = remember(lat, lon, overlay) { windyEmbedUrl(lat, lon, overlay) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Box(modifier.clipToBounds().clip(SimetCardDefaults.InnerShape).background(Color(0xFF0A0F1D))) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.rgb(10, 15, 29))
                    overScrollMode = android.view.View.OVER_SCROLL_NEVER
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        allowFileAccess = false
                        allowContentAccess = false
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, u: String?) { loading = false }
                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            if (request?.isForMainFrame == true) { loading = false; failed = true }
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
            update = { v -> if (v.url != url && !failed) { loading = true; v.loadUrl(url) } },
            onRelease = { v -> v.stopLoading(); v.destroy() },
            modifier = Modifier.fillMaxSize()
        )
        if (!interactive) {
            Box(Modifier.fillMaxSize().pointerInput(onTap) { detectTapGestures(onTap = { onTap?.invoke() }) })
        }
        if (loading && !failed) CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), strokeWidth = 3.dp, color = Color(0xFF38BDF8))
        if (failed) {
            Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Windy indisponível (sem conexão?)", color = Color.White, fontSize = 13.sp)
                TextButton(onClick = { failed = false; loading = true; webView?.loadUrl(url) }) { Text("Tentar novamente", color = Color(0xFF38BDF8)) }
            }
        }
    }
}
