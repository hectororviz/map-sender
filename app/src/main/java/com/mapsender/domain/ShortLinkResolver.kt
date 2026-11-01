package com.mapsender.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Resuelve enlaces cortos (maps.app.goo.gl, goo.gl/maps) siguiendo redirecciones
 * HTTP manualmente para obtener la URL final con coordenadas.
 */
class ShortLinkResolver(
    private val timeoutSeconds: Int = 15
) {
    // Sin followRedirects: leemos Location nosotros para limitar saltos.
    // DNS con preferencia IPv4: muchas redes móviles tienen IPv6 roto (agujero negro)
    // y OkHttp 4 marca en serie, quemando el timeout por intento, mientras el
    // navegador (happy eyeballs) cae a IPv4 al instante. Solo afecta a la
    // resolución de enlaces cortos; el cliente Gotify no se toca.
    private val ipv4FirstDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val all = Dns.SYSTEM.lookup(hostname)
            val v4 = all.filter { it is Inet4Address }
            return if (v4.isNotEmpty()) v4 else all
        }
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(ipv4FirstDns)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(timeoutSeconds.coerceIn(3, 120).toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.coerceIn(3, 120).toLong(), TimeUnit.SECONDS)
            .build()
    }

    sealed interface ResolveResult {
        /** Redirección seguida hasta una URL final (normalmente con coordenadas). */
        data class Redirect(val finalUrl: String) : ResolveResult
        /** HTTP 200 sin redirección: página del lugar (p. ej. maps.app.goo.gl). */
        data class Page(val html: String) : ResolveResult
        data class Failed(val reason: String) : ResolveResult
    }

    suspend fun resolve(shortUrl: String, maxHops: Int = 3): ResolveResult = withContext(Dispatchers.IO) {
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
                    return@withContext ResolveResult.Failed(describe(e, current))
                }
                response.use { resp ->
                    val location = resp.header("Location")
                    if (resp.isRedirect && location != null) {
                        current = if (location.startsWith("http")) location
                        else java.net.URL(java.net.URL(current), location).toString()
                        return@repeat
                    }
                    if (resp.isSuccessful) {
                        val body = resp.body
                        // Evita cargar binarios enormes; la página del lugar pesa ~200 KB.
                        if (body != null && body.contentLength() > 2_000_000) {
                            return@withContext ResolveResult.Redirect(current)
                        }
                        val html = try {
                            body?.string().orEmpty()
                        } catch (e: Exception) {
                            return@withContext ResolveResult.Failed("respuesta ilegible (${e.message})")
                        }
                        if (html.isBlank()) return@withContext ResolveResult.Redirect(current)
                        return@withContext ResolveResult.Page(html.take(1_000_000))
                    }
                    return@withContext ResolveResult.Failed("HTTP ${resp.code}")
                }
            }
            ResolveResult.Failed("demasiadas redirecciones")
        } catch (e: Exception) {
            ResolveResult.Failed(describe(e, current))
        }
    }

    /** Motivo legible para mostrar al usuario (sin datos sensibles). */
    private fun describe(e: Exception, url: String): String {
        val host = runCatching { java.net.URL(url).host }.getOrDefault("el servidor")
        return when (e) {
            is java.net.UnknownHostException -> "sin respuesta DNS de $host (¿sin Internet, DNS privado o VPN?)"
            is java.net.SocketTimeoutException -> "sin respuesta de $host (conexión colgada)"
            is java.io.IOException -> "error de red con $host (${e.message})"
            else -> e.message ?: "error desconocido"
        }
    }
}
