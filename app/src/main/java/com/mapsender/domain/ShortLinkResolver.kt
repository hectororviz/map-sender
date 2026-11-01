package com.mapsender.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Resuelve enlaces cortos (maps.app.goo.gl, goo.gl/maps) siguiendo redirecciones
 * HTTP manualmente para obtener la URL final con coordenadas.
 */
class ShortLinkResolver(
    private val timeoutSeconds: Int = 15
) {
    // Sin followRedirects: leemos Location nosotros para limitar saltos.
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(timeoutSeconds.coerceIn(3, 120).toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.coerceIn(3, 120).toLong(), TimeUnit.SECONDS)
            .build()
    }

    sealed interface ResolveResult {
        data class Resolved(val finalUrl: String) : ResolveResult
        data class Failed(val reason: String) : ResolveResult
    }

    suspend fun resolve(shortUrl: String, maxHops: Int = 5): ResolveResult = withContext(Dispatchers.IO) {
        var current = shortUrl
        try {
            repeat(maxHops) {
                // Solo GET: algunos frontales responden mal o lento a HEAD.
                // followRedirects=false: leemos Location nosotros para limitar saltos.
                val request = Request.Builder().url(current).get()
                    .header("Accept-Encoding", "identity").build()
                val response = try {
                    client.newCall(request).execute()
                } catch (e: Exception) {
                    return@withContext ResolveResult.Failed(e.message ?: "Error de red")
                }
                response.use { resp ->
                    val location = resp.header("Location")
                    if (resp.isRedirect && location != null) {
                        current = if (location.startsWith("http")) location
                        else java.net.URL(java.net.URL(current), location).toString()
                        return@repeat
                    }
                    if (resp.isSuccessful) return@withContext ResolveResult.Resolved(current)
                    return@withContext ResolveResult.Failed("HTTP ${resp.code}")
                }
            }
            ResolveResult.Resolved(current)
        } catch (e: Exception) {
            ResolveResult.Failed(e.message ?: "Error de red")
        }
    }
}
