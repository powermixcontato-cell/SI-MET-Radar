package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Posição do aparelho (graus) e precisão em metros (0 = desconhecida). */
data class MyLocationFix(val lat: Double, val lon: Double, val accuracyM: Float)

/** Estado do botão "Minha localização": [request] pede permissão (se preciso), lê o GPS e chama onFix. */
class MyLocationRequester internal constructor(
    private val trigger: () -> Unit,
    private val busyState: () -> Boolean
) {
    val busy: Boolean get() = busyState()
    fun request() = trigger()
}

private const val CURRENT_FIX_TIMEOUT_MS = 12_000L
private const val LAST_FIX_TIMEOUT_MS = 3_000L

private fun hasLocationPermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

/**
 * Lê a posição atual: FusedLocationProvider (getCurrentLocation, com timeout) → última posição conhecida do Fused →
 * última posição do LocationManager (aparelhos sem Google Play Services). Retorna null se nada vier a tempo.
 */
@SuppressLint("MissingPermission") // só é chamado depois de hasLocationPermission()
private suspend fun readLocation(ctx: Context): Location? {
    val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val fused = try { LocationServices.getFusedLocationProviderClient(ctx) } catch (_: Exception) { null }
    if (fused != null) {
        val current = withTimeoutOrNull(CURRENT_FIX_TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cts = CancellationTokenSource()
                cont.invokeOnCancellation { cts.cancel() }
                try {
                    fused.getCurrentLocation(if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                        .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                        .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        if (current != null) return current
        val last = withTimeoutOrNull(LAST_FIX_TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { cont ->
                try {
                    fused.lastLocation
                        .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        if (last != null) return last
    }
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return try {
        lm.getProviders(true).mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (_: Exception) { null } }
            .maxByOrNull { it.time }
    } catch (_: Exception) { null }
}

/**
 * Lógica compartilhada do botão "Minha localização" dos mapas (chuva, satélite e Windy).
 * Permissão em tempo de execução (fina ou aproximada), aviso se a localização do aparelho estiver desligada,
 * timeout de 12 s e mensagens em português. A coroutine é cancelada se o mapa sair da tela.
 */
@Composable
fun rememberMyLocationRequester(onFix: (MyLocationFix) -> Unit): MyLocationRequester {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnFix by rememberUpdatedState(onFix)
    var busy by remember { mutableStateOf(false) }

    fun fetch() {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null || !LocationManagerCompat.isLocationEnabled(lm)) {
            toast(context, "Localização do aparelho desligada. Ative a localização (GPS) e tente de novo.")
            return
        }
        busy = true
        scope.launch {
            try {
                val loc = readLocation(context)
                if (loc == null) {
                    toast(context, "Não foi possível obter sua localização agora (tempo esgotado). Tente ao ar livre ou mais tarde.")
                } else {
                    currentOnFix(MyLocationFix(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else 0f))
                }
            } catch (_: SecurityException) {
                toast(context, "Permissão de localização não concedida.")
            } finally {
                busy = false
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) fetch()
        else toast(context, "Permissão de localização negada. Para usar \"Minha localização\", permita o acesso nas configurações do app.")
    }

    return remember(launcher) {
        MyLocationRequester(
            trigger = {
                if (!busy) {
                    if (hasLocationPermission(context)) fetch()
                    else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            },
            busyState = { busy }
        )
    }
}

/** Botão padrão "Minha localização" (mesmo visual em todos os mapas; posicionado pelo chamador, canto inferior direito). */
@Composable
fun MyLocationButton(requester: MyLocationRequester, modifier: Modifier = Modifier) {
    SmallFloatingActionButton(
        onClick = { requester.request() },
        shape = CircleShape,
        containerColor = Color(0xF20F172A),
        contentColor = Color(0xFF60A5FA),
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
        modifier = modifier.size(40.dp).testTag("btn_my_location")
    ) {
        if (requester.busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF60A5FA))
        } else {
            Icon(Icons.Default.MyLocation, contentDescription = "Minha localização", modifier = Modifier.size(20.dp))
        }
    }
}

/** Posição comum do botão nos mapas: canto inferior direito, acima da linha de créditos. */
val MyLocationButtonPadding = androidx.compose.foundation.layout.PaddingValues(end = 8.dp, bottom = 26.dp)
