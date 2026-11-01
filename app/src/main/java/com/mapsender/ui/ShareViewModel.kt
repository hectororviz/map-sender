package com.mapsender.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mapsender.MapSenderApp
import com.mapsender.data.GotifyResult
import com.mapsender.domain.GeoDestination
import com.mapsender.domain.GotifyPayloadBuilder
import com.mapsender.domain.MapsLinkParser
import com.mapsender.domain.NavigationLinkBuilder
import com.mapsender.domain.ShortLinkResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

sealed interface ShareUiState {
    data object Idle : ShareUiState
    data object Parsing : ShareUiState
    data class Ready(val destination: GeoDestination, val navigationUrl: String) : ShareUiState
    data class NeedsResolution(val url: String) : ShareUiState
    data class Unrecognized(val reason: String, val raw: String?) : ShareUiState
    data object Sending : ShareUiState
    data class Sent(val summary: String) : ShareUiState
    data class Error(val message: String, val canRetry: Boolean = true) : ShareUiState
}

/** Flujo Sharesheet: parsea texto compartido, resuelve cortos y envía a Gotify. */
class ShareViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MapSenderApp).settingsRepository
    private val client = app.gotifyClient

    private val _state = MutableStateFlow<ShareUiState>(ShareUiState.Idle)
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    private var rawText: String? = null
    private var resolver: ShortLinkResolver? = null

    fun handleSharedText(text: String?) {
        rawText = text
        if (text.isNullOrBlank()) {
            _state.value = ShareUiState.Unrecognized("No se recibió texto para compartir.", null)
            return
        }
        viewModelScope.launch {
            _state.value = ShareUiState.Parsing
            val settings = repo.settings.first()
            resolver = ShortLinkResolver(settings.timeoutSeconds)
            when (val r = MapsLinkParser.parse(text)) {
                is MapsLinkParser.ParseResult.Success -> emitReady(r.destination)
                is MapsLinkParser.ParseResult.NeedsResolution -> {
                    // Intento automático de resolución (configurable por timeout).
                    when (val resolved = resolver!!.resolve(r.url)) {
                        is ShortLinkResolver.ResolveResult.Resolved -> {
                            when (val r2 = MapsLinkParser.parse(resolved.finalUrl)) {
                                is MapsLinkParser.ParseResult.Success -> emitReady(
                                    r2.destination.copy(originalText = text)
                                )
                                else -> _state.value = ShareUiState.NeedsResolution(r.url)
                            }
                        }
                        is ShortLinkResolver.ResolveResult.Failed ->
                            _state.value = ShareUiState.Error(
                                "No se pudo resolver el enlace corto (${resolved.reason}). " +
                                    "Abre el enlace con Internet o comparte el lugar con coordenadas."
                            )
                    }
                }
                is MapsLinkParser.ParseResult.Unrecognized ->
                    _state.value = ShareUiState.Unrecognized(r.reason, r.url)
            }
        }
    }

    private suspend fun emitReady(destination: GeoDestination) {
        val settings = repo.settings.first()
        val navUrl = NavigationLinkBuilder.buildPreferred(destination, settings.navApp, settings.wazeNavigate)
        _state.value = ShareUiState.Ready(destination, navUrl)
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
                current.destination, settings.deviceName.ifBlank { "móvil" },
                settings.priority, current.navigationUrl
            )
            val result = client.sendMessage(
                settings.gotifyBaseUrl, settings.gotifyToken, payload, settings.timeoutSeconds
            )
            when (result) {
                is GotifyResult.Success -> {
                    val summary = "${current.destination.shortLabel()} · " +
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
                    repo.saveLastSent(summary)
                    _state.value = ShareUiState.Sent(summary)
                }
                is GotifyResult.Error -> _state.value = ShareUiState.Error(result.userMessage)
            }
        }
    }

    fun retryParse() = handleSharedText(rawText)
    fun backToReady(destination: GeoDestination, navigationUrl: String) {
        _state.value = ShareUiState.Ready(destination, navigationUrl)
    }
}
