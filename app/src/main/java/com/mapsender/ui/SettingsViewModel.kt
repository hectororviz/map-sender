package com.mapsender.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mapsender.MapSenderApp
import com.mapsender.data.AppSettings
import com.mapsender.data.GotifyResult
import com.mapsender.domain.NavAppPreference
import com.mapsender.domain.SettingsValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Pantalla de ajustes: edita y guarda DataStore + prueba conexión. */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val mapSenderApp = app as MapSenderApp
    private val repo = mapSenderApp.settingsRepository
    private val client = mapSenderApp.gotifyClient

    val saved: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    // Campos editables (inicializados una vez desde DataStore).
    val baseUrl = MutableStateFlow("")
    val token = MutableStateFlow("")
    val deviceName = MutableStateFlow("")
    val priority = MutableStateFlow("5")
    val navApp = MutableStateFlow(NavAppPreference.WAZE)
    val wazeNavigate = MutableStateFlow(true)
    val confirmBeforeSend = MutableStateFlow(true)
    val timeout = MutableStateFlow("15")
    val retry = MutableStateFlow(true)

    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()
    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    private var loaded = false

    suspend fun loadOnce() {
        if (loaded) return
        val s = repo.settings.first()
        baseUrl.value = s.gotifyBaseUrl
        // Por seguridad no rellenamos el token si ya existe: mostramos placeholder vacío
        // pero lo conservamos al guardar si el usuario no lo toca (ver save()).
        token.value = ""
        deviceName.value = s.deviceName
        priority.value = s.priority.toString()
        navApp.value = s.navApp
        wazeNavigate.value = s.wazeNavigate
        confirmBeforeSend.value = s.confirmBeforeSend
        timeout.value = s.timeoutSeconds.toString()
        retry.value = s.retryOnFailure
        loaded = true
        pendingToken = s.gotifyToken
    }

    private var pendingToken: String = ""

    fun save(onSaved: () -> Unit) {
        val prio = priority.value.toIntOrNull() ?: -1
        val tout = timeout.value.toIntOrNull() ?: -1
        val effectiveToken = token.value.ifBlank { pendingToken }
        val v = SettingsValidator.validate(baseUrl.value, effectiveToken, deviceName.value, prio, tout)
        _errors.value = v.errors
        if (!v.isValid) return
        viewModelScope.launch {
            repo.save(
                AppSettings(
                    gotifyBaseUrl = SettingsValidator.normalizeBaseUrl(baseUrl.value),
                    gotifyToken = effectiveToken.trim(),
                    deviceName = deviceName.value.trim(),
                    priority = prio,
                    navApp = navApp.value,
                    wazeNavigate = wazeNavigate.value,
                    confirmBeforeSend = confirmBeforeSend.value,
                    timeoutSeconds = tout,
                    retryOnFailure = retry.value,
                    lastSentSummary = saved.value.lastSentSummary,
                    lastConnectionOk = saved.value.lastConnectionOk
                )
            )
            pendingToken = effectiveToken.trim()
            token.value = ""
            _status.value = buildString {
                append("Ajustes guardados.")
                if (SettingsValidator.isInsecureHttp(baseUrl.value)) append(" Aviso: usas HTTP sin cifrar; se recomienda HTTPS.")
            }
            onSaved()
        }
    }

    fun testAndSave() {
        val prio = priority.value.toIntOrNull() ?: -1
        val tout = timeout.value.toIntOrNull() ?: -1
        val effectiveToken = token.value.ifBlank { pendingToken }
        val v = SettingsValidator.validate(baseUrl.value, effectiveToken, deviceName.value, prio, tout)
        _errors.value = v.errors
        if (!v.isValid) return
        _testing.value = true
        viewModelScope.launch {
            val result = client.testConnection(
                SettingsValidator.normalizeBaseUrl(baseUrl.value), effectiveToken.trim(), tout
            )
            _testing.value = false
            when (result) {
                is GotifyResult.Success -> {
                    repo.saveLastConnection(true)
                    _status.value = "Conexión OK. Guarda para conservar los cambios."
                }
                is GotifyResult.Error -> {
                    repo.saveLastConnection(false)
                    _status.value = result.userMessage
                }
            }
        }
    }

    fun clearStatus() { _status.value = null }
}
