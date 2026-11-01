package com.mapsender.data

import com.mapsender.domain.NavAppPreference

/** Preferencias persistidas (DataStore). Sin valores personales por defecto. */
data class AppSettings(
    val gotifyBaseUrl: String = "",
    val gotifyToken: String = "",
    val deviceName: String = "",
    val priority: Int = 5,
    val navApp: NavAppPreference = NavAppPreference.WAZE,
    val wazeNavigate: Boolean = true,
    val confirmBeforeSend: Boolean = true,
    val timeoutSeconds: Int = 15,
    val retryOnFailure: Boolean = true,
    val lastSentSummary: String = "",
    val lastConnectionOk: Boolean = false
) {
    val isConfigured: Boolean get() = gotifyBaseUrl.isNotBlank() && gotifyToken.isNotBlank()
}
