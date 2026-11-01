package com.mapsender

import android.app.Application
import com.mapsender.data.GotifyClient
import com.mapsender.data.SettingsRepository

/** Application: expone dependencias manuales (sin Hilt para mantener la app liviana). */
class MapSenderApp : Application() {
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val gotifyClient: GotifyClient by lazy { GotifyClient() }
}
