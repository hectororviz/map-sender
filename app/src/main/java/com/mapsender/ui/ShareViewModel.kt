package com.mapsender.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mapsender.MapSenderApp
import com.mapsender.data.GotifyResult
import com.mapsender.domain.GeoDestination
import com.mapsender.domain.GotifyPayloadBuilder
import com.mapsender.domain.NavigationLinkBuilder
import com.mapsender.domain.OutgoingPlace
import com.mapsender.domain.ResolvePlaceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
                val useCase = ResolvePlaceUseCase()
                val webCtx = activityRef?.get() ?: mapSenderApp.applicationContext
                when (val outcome = useCase.resolve(text, webCtx) { event ->
                    if (event is ResolvePlaceUseCase.Event.StageChanged) {
                        _state.value = when (val s = event.stage) {
                            ResolvePlaceUseCase.Stage.Parsing -> ShareUiState.Parsing
                            is ResolvePlaceUseCase.Stage.Resolving -> ShareUiState.Resolving(s.url)
                            is ResolvePlaceUseCase.Stage.OpeningPlace -> ShareUiState.OpeningPlace(s.url, s.name)
                            is ResolvePlaceUseCase.Stage.Geocoding -> ShareUiState.Geocoding(s.name)
                        }
                    }
                    // Los TraceStep solo los consume la pantalla de prueba.
                }) {
                    is ResolvePlaceUseCase.Outcome.Located ->
                        emitReady(outcome.destination.copy(originalText = text))
                    is ResolvePlaceUseCase.Outcome.Named -> emitSearch(outcome.name, text)
                    is ResolvePlaceUseCase.Outcome.Unrecognized ->
                        _state.value = ShareUiState.Unrecognized(outcome.reason, outcome.raw)
                    is ResolvePlaceUseCase.Outcome.Failed ->
                        _state.value = ShareUiState.Error(outcome.message, linkUrl = outcome.linkUrl)
                }
            } catch (e: Exception) {
                // Red de seguridad: nunca dejar la UI bloqueada en "Analizando…".
                _state.value = ShareUiState.Error("Error inesperado: ${e.message ?: e.javaClass.simpleName}")
            }
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
