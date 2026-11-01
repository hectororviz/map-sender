package com.mapsender.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mapsender.MapSenderApp
import com.mapsender.data.AppSettings
import com.mapsender.data.GotifyResult
import com.mapsender.domain.GeoDestination
import com.mapsender.domain.GotifyPayloadBuilder
import com.mapsender.domain.MapsLinkParser
import com.mapsender.domain.NavigationLinkBuilder
import com.mapsender.domain.NominatimGeocoder
import com.mapsender.domain.OutgoingPlace
import com.mapsender.domain.PlacePageParser
import com.mapsender.domain.ShortLinkResolver
import com.mapsender.domain.WebViewUrlResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

sealed interface ShareUiState {
    data object Idle : ShareUiState
    data object Parsing : ShareUiState
    /** Resolviendo un enlace corto por red (requiere Internet, unos segundos). */
    data class Resolving(val url: String) : ShareUiState
    /** Abriendo el lugar en un WebView oculto para obtener la URL final con coords. */
    data class OpeningPlace(val url: String, val name: String) : ShareUiState
    /** Buscando coordenadas del nombre vía Nominatim (último recurso). */
    data class Geocoding(val name: String) : ShareUiState
    data class Ready(val place: OutgoingPlace) : ShareUiState
    data class NeedsResolution(val url: String) : ShareUiState
    /** Entrada manual de coordenadas (alternativa si falla el análisis automático). */
    data class ManualEntry(val error: String? = null) : ShareUiState
    data class Unrecognized(val reason: String, val raw: String?) : ShareUiState
    data object Sending : ShareUiState
    data class Sent(val summary: String) : ShareUiState
    data class Error(val message: String, val canRetry: Boolean = true, val linkUrl: String? = null) : ShareUiState
}

/** Flujo Sharesheet: parsea texto compartido, resuelve cortos y envía a Gotify. */
class ShareViewModel(app: Application) : AndroidViewModel(app) {
    private val mapSenderApp = app as MapSenderApp
    private val repo = mapSenderApp.settingsRepository
    private val client = mapSenderApp.gotifyClient

    private val _state = MutableStateFlow<ShareUiState>(ShareUiState.Idle)
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    private var rawText: String? = null
    private var resolver: ShortLinkResolver? = null
    private var activityRef: WeakReference<Activity>? = null

    /** La pantalla registra la Activity para el WebView oculto (referencia débil). */
    fun attachActivity(activity: Activity?) {
        activityRef = activity?.let { WeakReference(it) }
    }

    fun handleSharedText(text: String?) {
        rawText = text
        if (text.isNullOrBlank()) {
            _state.value = ShareUiState.Unrecognized("No se recibió texto para compartir.", null)
            return
        }
        viewModelScope.launch {
            try {
                _state.value = ShareUiState.Parsing
                val settings = repo.settings.first()
                resolver = ShortLinkResolver(settings.timeoutSeconds)
                when (val r = MapsLinkParser.parse(text)) {
                    is MapsLinkParser.ParseResult.Success -> emitReady(r.destination)
                    is MapsLinkParser.ParseResult.NeedsResolution -> {
                        // Estado visible: la resolución por red puede tardar (cobertura escasa).
                        _state.value = ShareUiState.Resolving(r.url)
                        // Resolución con tiempos cortos: fallar rápido con motivo claro.
                        val resolveTimeout = minOf(settings.timeoutSeconds, 10).coerceIn(3, 120)
                        resolver = ShortLinkResolver(resolveTimeout)
                        val timeoutMs = (resolveTimeout * 2L + 10L) * 1000L
                        val resolved = try {
                            withTimeoutOrNull(timeoutMs) { resolver!!.resolve(r.url) }
                                ?: ShortLinkResolver.ResolveResult.Failed("tiempo de espera agotado")
                        } catch (e: Exception) {
                            ShortLinkResolver.ResolveResult.Failed(e.message ?: "error inesperado")
                        }
                        when (resolved) {
                            is ShortLinkResolver.ResolveResult.Redirect -> {
                                // URL final con coordenadas → flujo normal.
                                when (val r2 = MapsLinkParser.parse(resolved.finalUrl)) {
                                    is MapsLinkParser.ParseResult.Success -> emitReady(
                                        r2.destination.copy(originalText = text)
                                    )
                                    else -> {
                                        // URL final sin coords (p. ej. /maps/place/Nombre):
                                        // nombre → geocodificar → búsqueda.
                                        val name = PlacePageParser.extractFromUrl(resolved.finalUrl)
                                        if (name != null) emitNamed(name, text, settings)
                                        else _state.value = ShareUiState.Unrecognized(
                                            "El enlace se resolvió pero no contiene coordenadas ni nombre.",
                                            r.url
                                        )
                                    }
                                }
                            }
                            is ShortLinkResolver.ResolveResult.Page -> {
                                // HTTP 200 sin redirección: página del lugar.
                                // 1) WebView oculto (como el navegador): URL final con coords.
                                // 2) Nombre → Nominatim. 3) Búsqueda Waze.
                                val name = PlacePageParser.extractPlaceName(resolved.html)
                                if (name != null) {
                                    val webUrl = openInWebView(r.url, name)
                                    if (webUrl != null) {
                                        when (val r3 = MapsLinkParser.parse(webUrl)) {
                                            is MapsLinkParser.ParseResult.Success -> {
                                                emitReady(r3.destination.copy(
                                                    name = r3.destination.name ?: name,
                                                    originalText = text
                                                ))
                                                return@launch
                                            }
                                            else -> { /* sin coords: sigue a Nominatim */ }
                                        }
                                    }
                                    emitNamed(name, text, settings)
                                } else _state.value = ShareUiState.Unrecognized(
                                    "La página del enlace no contiene coordenadas ni nombre reconocible.",
                                    r.url
                                )
                            }
                            is ShortLinkResolver.ResolveResult.Failed ->
                                _state.value = ShareUiState.Error(
                                    "No se pudo resolver el enlace corto: ${resolved.reason}. " +
                                        "Comprueba que este enlace abre en el navegador del móvil; " +
                                        "si tampoco abre, es la red (datos, DNS privado o VPN).",
                                    linkUrl = r.url
                                )
                        }
                    }
                    is MapsLinkParser.ParseResult.Unrecognized ->
                        _state.value = ShareUiState.Unrecognized(r.reason, r.url)
                }
            } catch (e: Exception) {
                // Red de seguridad: nunca dejar la UI bloqueada en "Analizando…".
                _state.value = ShareUiState.Error("Error inesperado: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Abre la URL en un WebView oculto y espera la URL final con coordenadas.
     * Null si timeout, sin WebView o sin coordenadas.
     */
    private suspend fun openInWebView(shortUrl: String, name: String): String? {
        val ctx = activityRef?.get() ?: mapSenderApp.applicationContext
        _state.value = ShareUiState.OpeningPlace(shortUrl, name)
        return try {
            WebViewUrlResolver.resolveFinalUrl(ctx, shortUrl)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun emitReady(destination: GeoDestination) {
        val settings = repo.settings.first()
        val navUrl = NavigationLinkBuilder.buildPreferred(destination, settings.navApp, settings.wazeNavigate)
        _state.value = ShareUiState.Ready(OutgoingPlace.Located(destination, navUrl))
        // Envío automático (por defecto): sin confirmación va directo al estéreo.
        if (!settings.confirmBeforeSend) send()
    }

    private suspend fun emitSearch(name: String, originalText: String?) {
        val settings = repo.settings.first()
        val navUrl = NavigationLinkBuilder.buildSearchPreferred(name, settings.navApp)
        _state.value = ShareUiState.Ready(OutgoingPlace.Search(name, navUrl, originalText))
        if (!settings.confirmBeforeSend) send()
    }

    /**
     * Nombre sin coordenadas: intenta geocodificar (Nominatim) y si falla,
     * cae a búsqueda Waze para elegir en el receptor.
     */
    private suspend fun emitNamed(name: String, originalText: String?, settings: AppSettings) {
        _state.value = ShareUiState.Geocoding(name)
        val geoTimeout = minOf(settings.timeoutSeconds, 10).coerceIn(3, 120)
        val geo = try {
            withTimeoutOrNull(geoTimeout * 1000L + 5000L) {
                NominatimGeocoder(geoTimeout).geocode(name)
            }
        } catch (_: Exception) {
            null
        }
        if (geo != null) {
            emitReady(GeoDestination(geo.lat, geo.lng, name = name, originalText = originalText))
        } else {
            emitSearch(name, originalText)
        }
    }

    fun send() {
        val current = _state.value as? ShareUiState.Ready ?: return
        viewModelScope.launch {
            _state.value = ShareUiState.Sending
            val settings = repo.settings.first()
            if (!settings.isConfigured) {
                _state.value = ShareUiState.Error("Falta configuración: define URL y token en Ajustes.", canRetry = false)
                return@launch
            }
            val payload = GotifyPayloadBuilder.build(
                current.place, settings.deviceName.ifBlank { "móvil" }, settings.priority
            )
            val result = client.sendMessage(
                settings.gotifyBaseUrl, settings.gotifyToken, payload, settings.timeoutSeconds
            )
            when (result) {
                is GotifyResult.Success -> {
                    val summary = "${current.place.displayTitle} · " +
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
                    repo.saveLastSent(summary)
                    _state.value = ShareUiState.Sent(summary)
                }
                is GotifyResult.Error -> _state.value = ShareUiState.Error(result.userMessage)
            }
        }
    }

    fun retryParse() = handleSharedText(rawText)

    fun openManualEntry() {
        _state.value = ShareUiState.ManualEntry()
    }

    /** Acepta "40.41536" o "40,41536" (coma decimal española) en cada campo. */
    fun submitManual(latText: String, lngText: String, name: String) {
        val lat = latText.trim().replace(',', '.').toDoubleOrNull()
        val lng = lngText.trim().replace(',', '.').toDoubleOrNull()
        if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            _state.value = ShareUiState.ManualEntry("Coordenadas inválidas. Ejemplo: 40.41536 y -3.70734")
            return
        }
        viewModelScope.launch {
            try {
                val dest = GeoDestination(
                    lat, lng,
                    name = name.trim().takeIf { it.isNotEmpty() },
                    originalText = rawText
                )
                emitReady(dest)
            } catch (e: Exception) {
                _state.value = ShareUiState.ManualEntry("Error inesperado: ${e.message ?: "?"}")
            }
        }
    }
}
