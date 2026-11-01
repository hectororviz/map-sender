package com.mapsender.domain

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.URLDecoder
import kotlin.coroutines.resume

/**
 * Resuelve un enlace corto cargándolo en un WebView oculto con JavaScript,
 * igual que hace el navegador: la web de Maps geocodifica el lugar en el
 * cliente y la URL final contiene las coordenadas (!3d/!4d, /@, q=lat,lng…).
 *
 * Devuelve la URL final con coordenadas, o null si hay timeout, no hay
 * WebView disponible o la página nunca muestra coordenadas.
 */
object WebViewUrlResolver {

    const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /** Marca de consentimiento de Google: la URL real viene en ?continue=. */
    internal fun extractConsentTarget(url: String): String? {
        if (!url.contains("consent.google.")) return null
        val m = Regex("[?&]continue=([^&]+)").find(url) ?: return null
        return runCatching { URLDecoder.decode(m.groupValues[1], "UTF-8") }.getOrNull()
    }

    /** Detecta coordenadas en una URL (pin !3d/!4d, q/ll/query/destination o /@). */
    internal fun hasCoords(url: String): Boolean =
        Regex("""!3d-?\d+\.\d+!4d-?\d+\.\d+|[?&](?:q|ll|query|destination)=-?\d{1,3}\.\d+,-?\d{1,3}\.\d+|/@-?\d{1,3}\.\d+,-?\d{1,3}\.\d+""")
            .containsMatchIn(url)

    suspend fun resolveFinalUrl(context: Context, startUrl: String, timeoutMs: Long = 15000L): String? =
        suspendCancellableCoroutine { cont ->
            val main = Handler(Looper.getMainLooper())
            var webView: WebView? = null
            var settled = false

            fun destroy() {
                try { webView?.stopLoading() } catch (_: Exception) { /* noop */ }
                try { webView?.destroy() } catch (_: Exception) { /* noop */ }
                webView = null
            }

            fun finish(result: String?) {
                if (settled || !cont.isActive) return
                settled = true
                main.post { destroy() }
                cont.resume(result)
            }

            val checker = object : Runnable {
                override fun run() {
                    if (settled) return
                    val current = try { webView?.url } catch (_: Exception) { null }
                    if (current != null) {
                        extractConsentTarget(current)?.let { finish(it); return }
                        if (current != startUrl && hasCoords(current)) { finish(current); return }
                    }
                    main.postDelayed(this, 500)
                }
            }

            main.post {
                try {
                    @SuppressLint("SetJavaScriptEnabled")
                    val wv = WebView(context)
                    webView = wv
                    wv.settings.javaScriptEnabled = true
                    wv.settings.domStorageEnabled = true
                    wv.settings.userAgentString = MOBILE_UA
                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            extractConsentTarget(url)?.let { finish(it); return }
                            if (url != startUrl && hasCoords(url)) finish(url)
                        }
                    }
                    wv.loadUrl(startUrl)
                    main.postDelayed(checker, 500)
                    main.postDelayed({ finish(null) }, timeoutMs)
                } catch (_: Exception) {
                    // Sin WebView disponible (p. ej. estéreos sin System WebView).
                    finish(null)
                }
            }

            cont.invokeOnCancellation {
                settled = true
                main.post { destroy() }
            }
        }
}
