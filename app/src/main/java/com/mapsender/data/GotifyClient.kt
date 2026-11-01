package com.mapsender.data

import android.util.Log
import com.mapsender.domain.GotifyPayloadBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Resultado de operaciones Gotify con mensajes aptos para UI. */
sealed interface GotifyResult {
    data object Success : GotifyResult
    data class Error(val userMessage: String) : GotifyResult
}

/**
 * Cliente Gotify: POST {baseUrl}/message con application/json.
 * Nunca registra el token. Timeouts configurables.
 */
class GotifyClient {

    private fun clientFor(timeoutSeconds: Int): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds.coerceIn(3, 120).toLong(), TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds.coerceIn(3, 120).toLong(), TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds.coerceIn(3, 120).toLong(), TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun sendMessage(
        baseUrl: String,
        token: String,
        payload: GotifyPayloadBuilder.Payload,
        timeoutSeconds: Int
    ): GotifyResult = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trim().trimEnd('/')}/message"
        try {
            val body = payload.toJsonString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .header("X-Gotify-Key", token.trim())
                .post(body)
                .build()
            clientFor(timeoutSeconds).newCall(request).execute().use { resp ->
                if (resp.isSuccessful) return@withContext GotifyResult.Success
                return@withContext GotifyResult.Error(mapHttpError(resp.code))
            }
        } catch (e: java.net.UnknownHostException) {
            GotifyResult.Error("Servidor inaccesible. Revisa la URL y tu conexión.")
        } catch (e: java.net.SocketTimeoutException) {
            GotifyResult.Error("Tiempo de espera agotado. Aumenta el timeout o reintenta.")
        } catch (e: Exception) {
            Log.w("GotifyClient", "send failed: ${e.javaClass.simpleName}")
            GotifyResult.Error("Error de conexión: ${e.message ?: "desconocido"}")
        }
    }

    /** Prueba de conexión: GET {baseUrl}/version con el token. */
    suspend fun testConnection(baseUrl: String, token: String, timeoutSeconds: Int): GotifyResult =
        withContext(Dispatchers.IO) {
            val url = "${baseUrl.trim().trimEnd('/')}/version"
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("X-Gotify-Key", token.trim())
                    .get()
                    .build()
                clientFor(timeoutSeconds).newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) return@withContext GotifyResult.Success
                    return@withContext GotifyResult.Error(mapHttpError(resp.code))
                }
            } catch (e: java.net.UnknownHostException) {
                GotifyResult.Error("Servidor inaccesible. Revisa la URL y tu conexión.")
            } catch (e: java.net.SocketTimeoutException) {
                GotifyResult.Error("Tiempo de espera agotado.")
            } catch (e: Exception) {
                Log.w("GotifyClient", "test failed: ${e.javaClass.simpleName}")
                GotifyResult.Error("Error de conexión: ${e.message ?: "desconocido"}")
            }
        }

    private fun mapHttpError(code: Int): String = when (code) {
        401, 403 -> "Token inválido o sin permiso (HTTP $code). Revisa el token de aplicación."
        404 -> "Ruta no encontrada (HTTP 404). Revisa la URL base (sin /message al final)."
        in 500..599 -> "Error del servidor Gotify (HTTP $code). Reintenta más tarde."
        else -> "Error HTTP $code. Revisa la configuración."
    }
}
