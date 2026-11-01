package com.mapsender.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mapsender.domain.NavAppPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "map_sender_prefs")

/** Repositorio de ajustes sobre DataStore. El token nunca se registra en logs. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("gotify_base_url")
        val TOKEN = stringPreferencesKey("gotify_token")
        val DEVICE = stringPreferencesKey("device_name")
        val PRIORITY = intPreferencesKey("priority")
        val NAV_APP = stringPreferencesKey("nav_app")
        val WAZE_NAVIGATE = booleanPreferencesKey("waze_navigate")
        val CONFIRM = booleanPreferencesKey("confirm_before_send")
        val TIMEOUT = intPreferencesKey("timeout_seconds")
        val RETRY = booleanPreferencesKey("retry_on_failure")
        val LAST_SENT = stringPreferencesKey("last_sent_summary")
        val LAST_CONN = booleanPreferencesKey("last_connection_ok")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            gotifyBaseUrl = p[Keys.BASE_URL].orEmpty(),
            gotifyToken = p[Keys.TOKEN].orEmpty(),
            deviceName = p[Keys.DEVICE].orEmpty(),
            priority = p[Keys.PRIORITY] ?: 5,
            navApp = runCatching { NavAppPreference.valueOf(p[Keys.NAV_APP] ?: "WAZE") }
                .getOrDefault(NavAppPreference.WAZE),
            wazeNavigate = p[Keys.WAZE_NAVIGATE] ?: true,
            confirmBeforeSend = p[Keys.CONFIRM] ?: false,
            timeoutSeconds = p[Keys.TIMEOUT] ?: 15,
            retryOnFailure = p[Keys.RETRY] ?: true,
            lastSentSummary = p[Keys.LAST_SENT].orEmpty(),
            lastConnectionOk = p[Keys.LAST_CONN] ?: false
        )
    }

    suspend fun save(settings: AppSettings) {
        context.dataStore.edit { p ->
            p[Keys.BASE_URL] = settings.gotifyBaseUrl.trim().trimEnd('/')
            p[Keys.TOKEN] = settings.gotifyToken.trim()
            p[Keys.DEVICE] = settings.deviceName.trim()
            p[Keys.PRIORITY] = settings.priority.coerceIn(0, 10)
            p[Keys.NAV_APP] = settings.navApp.name
            p[Keys.WAZE_NAVIGATE] = settings.wazeNavigate
            p[Keys.CONFIRM] = settings.confirmBeforeSend
            p[Keys.TIMEOUT] = settings.timeoutSeconds.coerceIn(3, 120)
            p[Keys.RETRY] = settings.retryOnFailure
        }
    }

    suspend fun saveLastSent(summary: String) {
        context.dataStore.edit { it[Keys.LAST_SENT] = summary.take(500) }
    }

    suspend fun saveLastConnection(ok: Boolean) {
        context.dataStore.edit { it[Keys.LAST_CONN] = ok }
    }

    suspend fun clearToken() {
        context.dataStore.edit { it.remove(Keys.TOKEN) }
    }
}
