package com.example.ui.components

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.data.remote.RealtimeObsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.lang.ref.WeakReference

/**
 * Ponte JS → Kotlin do mapa Leaflet (só nos mapas em tela cheia, que carregam apenas o asset local map.html).
 * - request(kind, arg, id): busca METAR / CEMADEN fora da thread principal e devolve em window.simet.onNative(id, json).
 * - bottomInset(px): altura do painel inferior do mapa, para o botão "Minha localização" não ficar por baixo dele.
 * A coroutine é do escopo do Composable: ao fechar o mapa, as buscas pendentes são canceladas.
 */
class SimetMapBridge(
    context: Context,
    private val scope: CoroutineScope,
    webView: WebView,
    private val onBottomInset: (Int) -> Unit
) {
    private val appContext = context.applicationContext
    private val webRef = WeakReference(webView)
    private val idPattern = Regex("^r\\d{1,6}$")

    @JavascriptInterface
    fun request(kind: String?, arg: String?, id: String?) {
        val reqId = id?.takeIf { idPattern.matches(it) } ?: return
        scope.launch(Dispatchers.IO) {
            val json = try {
                when (kind) {
                    "metar" -> RealtimeObsService.metarsJson()
                    "cemaden" -> RealtimeObsService.cemadenJson(appContext, arg ?: "")
                    else -> errorJson("tipo desconhecido")
                }
            } catch (e: Exception) {
                errorJson(e.message ?: "falha de rede")
            }
            withContext(Dispatchers.Main) {
                webRef.get()?.evaluateJavascript("window.simet&&window.simet.onNative('$reqId',$json)", null)
            }
        }
    }

    @JavascriptInterface
    fun bottomInset(px: Int) {
        scope.launch(Dispatchers.Main) { onBottomInset(px.coerceIn(0, 600)) }
    }

    private fun errorJson(msg: String) = JSONObject().put("error", msg.take(120)).toString()
}
