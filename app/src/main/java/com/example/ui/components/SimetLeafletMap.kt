package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.BrState
import java.util.Locale

/** Modos da página assets/simet_map/map.html (Leaflet 1.9.4 embutido, sem chave de API). */
enum class SimetMapMode(val param: String) { RAIN("rain"), SATELLITE("satellite"), TRAJECTORY("trajectory") }

/** Monta o HTML com o Leaflet inline (sem CDN) a partir dos assets. Cacheado em memória. */
object SimetMapHtml {
    @Volatile private var cached: String? = null

    fun get(context: Context): String = cached ?: synchronized(this) {
        cached ?: run {
            val am = context.assets
            fun read(name: String) = am.open("simet_map/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
            read("map.html")
                .replace("/*LEAFLET_CSS*/", read("leaflet.css"))
                .replace("/*LEAFLET_JS*/", read("leaflet.js"))
                .also { cached = it }
        }
    }

    fun query(mode: SimetMapMode, lat: Double, lon: Double, zoom: Int, state: BrState?, interactive: Boolean, satelliteBase: Boolean = false): String =
        buildString {
            append("?mode=").append(mode.param)
            append("&interactive=").append(if (interactive) "1" else "0")
            append(String.format(Locale.US, "&lat=%.4f&lon=%.4f&z=%d", lat, lon, zoom))
            if (state != null) append("&bbox=").append(state.bboxParam)
            if (satelliteBase) append("&base=sat")
        }
}

/**
 * Mapa Leaflet (radar RainViewer / satélite Esri + nuvens GOES / trajetória da chuva).
 * Em [interactive] = false o mapa fica estático no card: uma camada Compose transparente por cima recebe
 * os toques (abre o modo expandido em [onTap]) e deixa o gesto de rolagem para a lista — o WebView nunca
 * "rouba" a rolagem da tela nem desenha fora do card (clipToBounds + cantos arredondados).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SimetLeafletMap(
    mode: SimetMapMode,
    lat: Double,
    lon: Double,
    zoom: Int,
    state: BrState?,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    satelliteBase: Boolean = false,
    onTap: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var loadedCenter by remember { mutableStateOf<Triple<Double, Double, Int>?>(null) }

    fun load(v: WebView) {
        failed = false; loading = true
        val q = SimetMapHtml.query(mode, lat, lon, zoom, state, interactive, satelliteBase)
        val html = SimetMapHtml.get(v.context).replace("<head>", "<head><script>window.SIMET_QUERY='$q';</script>")
        v.loadDataWithBaseURL("https://appassets.androidplatform.net/simet_map/map.html$q", html, "text/html", "utf-8", null)
        loadedCenter = Triple(lat, lon, zoom)
    }

    // Recentraliza (troca de estado/cidade) sem recarregar a página
    LaunchedEffect(lat, lon, zoom, webView) {
        val v = webView ?: return@LaunchedEffect
        val c = loadedCenter
        if (c != null && (c.first != lat || c.second != lon || c.third != zoom)) {
            // Página ainda carregando: recarrega já com o novo centro (window.simet pode não existir ainda)
            if (loading) { load(v); return@LaunchedEffect }
            val bbox = state?.let { "[${it.minLat},${it.minLon},${it.maxLat},${it.maxLon}]" } ?: "null"
            v.evaluateJavascript(String.format(Locale.US, "window.simet&&window.simet.setView(%.4f,%.4f,%d,%s)", lat, lon, zoom, bbox), null)
            loadedCenter = Triple(lat, lon, zoom)
        }
    }

    DisposableEffect(lifecycleOwner, webView) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> { webView?.onPause(); webView?.evaluateJavascript("window.simet&&window.simet.pause()", null) }
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Box(modifier = modifier.clipToBounds().clip(SimetCardDefaults.InnerShape).background(Color(0xFF0A0F1D))) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.rgb(10, 15, 29))
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = android.view.View.OVER_SCROLL_NEVER
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        allowFileAccess = false
                        allowContentAccess = false
                        setSupportZoom(interactive)
                        builtInZoomControls = false
                        displayZoomControls = false
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) { loading = false }
                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            if (request?.isForMainFrame == true) { loading = false; failed = true }
                        }
                        // Links (atribuições) abrem fora do app
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val u = request?.url ?: return true
                            if (u.host == "appassets.androidplatform.net") return false
                            try {
                                ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, u).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Exception) { }
                            return true
                        }
                    }
                    webView = this
                    load(this)
                }
            },
            onRelease = { v -> v.stopLoading(); v.destroy(); if (webView === v) webView = null },
            modifier = Modifier.fillMaxSize()
        )

        if (!interactive) {
            // Camada de toque: impede que o WebView capture gestos dentro da lista rolável
            Box(Modifier.fillMaxSize().pointerInput(onTap) { detectTapGestures(onTap = { onTap?.invoke() }) })
        }

        if (loading && !failed) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(28.dp), strokeWidth = 3.dp, color = Color(0xFF38BDF8)
            )
        }
        if (failed) {
            Column(
                Modifier.align(Alignment.Center).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Mapa indisponível (sem conexão?)", color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { webView?.let { load(it) } }) { Text("Tentar novamente", color = Color(0xFF38BDF8)) }
            }
        }
    }
}
