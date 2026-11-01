package com.mapsender.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mapsender.MapSenderApp
import com.mapsender.data.AppSettings
import com.mapsender.data.GotifyResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConnectionUiState(val testing: Boolean = false, val message: String? = null, val ok: Boolean? = null)

/** Pantalla principal: estado de conexión + último envío. */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as MapSenderApp).settingsRepository
    private val client = app.gotifyClient

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _conn = MutableStateFlow(ConnectionUiState())
    val conn: StateFlow<ConnectionUiState> = _conn.asStateFlow()

    fun testConnection() {
        val s = settings.value
        if (!s.isConfigured) {
            _conn.value = ConnectionUiState(false, "Configura primero URL y token en Ajustes.", false)
            return
        }
        _conn.value = ConnectionUiState(testing = true)
        viewModelScope.launch {
            val result = client.testConnection(s.gotifyBaseUrl, s.gotifyToken, s.timeoutSeconds)
            when (result) {
                is GotifyResult.Success -> {
                    repo.saveLastConnection(true)
                    _conn.value = ConnectionUiState(false, "Conexión OK con Gotify.", true)
                }
                is GotifyResult.Error -> {
                    repo.saveLastConnection(false)
                    _conn.value = ConnectionUiState(false, result.userMessage, false)
                }
            }
        }
    }

    fun dismiss() { _conn.value = ConnectionUiState() }
}
