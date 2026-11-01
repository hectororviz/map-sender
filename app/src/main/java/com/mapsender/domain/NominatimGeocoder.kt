package com.mapsender.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Geocodificación de secours vía Nominatim (OpenStreetMap), sin API key.
 * Último recurso cuando hay nombre del lugar pero no coordenadas.
 * Uso moderado conforme a la política de Nominatim (User-Agent identificativo).
 */
class NominatimGeocoder(private val timeoutSeconds: Int = 8) {

    data class Result(val lat: Double, val lng: Double, val displayName: String?)

    @Serializable
    private data class Item(
        val lat: String = "",
        val lon: String = "",
        val display_name: String? = null
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds.coerceIn(3, 120).toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.coerceIn(3, 120).toLong(), TimeUnit.SECONDS)
            .build()
    }

    suspend fun geocode(query: String): Result? =
        (geocodeOutcome(query) as? GeocodeOutcome.Found)?.result

    sealed interface GeocodeOutcome {
        data class Found(val result: Result) : GeocodeOutcome
        /** Petición OK pero sin resultados (reintentable con consulta simplificada). */
        data object NotFound : GeocodeOutcome
        /** Error de red/HTTP/timeout (reintentar no ayuda a corto plazo). */
        data object Failed : GeocodeOutcome
    }

    suspend fun geocodeOutcome(query: String): GeocodeOutcome = withContext(Dispatchers.IO) {
        val q = query.trim().take(200)
        if (q.length < 3) return@withContext GeocodeOutcome.Failed
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("nominatim.openstreetmap.org")
            .addPathSegment("search")
            .addQueryParameter("q", q)
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("limit", "1")
            .build()
        try {
            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "MapSender/1.0 (Android; +https://github.com/hectororviz/map-sender)"
                )
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext GeocodeOutcome.Failed
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@withContext GeocodeOutcome.Failed
                val parsed = parseFirst(body) ?: return@withContext GeocodeOutcome.NotFound
                return@withContext GeocodeOutcome.Found(parsed)
            }
        } catch (_: Exception) {
            GeocodeOutcome.Failed
        }
    }

    internal fun parseFirst(body: String): Result? {
        val items = runCatching { json.decodeFromString<List<Item>>(body) }.getOrNull()
            ?: return null
        val first = items.firstOrNull() ?: return null
        val lat = first.lat.toDoubleOrNull() ?: return null
        val lng = first.lon.toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return Result(lat, lng, first.display_name)
    }
}
