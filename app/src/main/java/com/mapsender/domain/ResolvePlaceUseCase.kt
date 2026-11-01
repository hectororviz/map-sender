package com.mapsender.domain

import android.content.Context
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Orquestación completa de resolución de un texto compartido, usada tanto por
 * el flujo real (ShareViewModel) como por la pantalla de prueba (dry-run).
 *
 * Cadena: parseo directo → HTTP (redirect/página) → WebView oculto →
 * Nominatim (con reintento degradado) → nombre para búsqueda.
 */
class ResolvePlaceUseCase(
    private val httpTimeoutSeconds: Int = 10,
    private val webViewTimeoutMs: Long = 25000L,
    private val geoTimeoutSeconds: Int = 10
) {

    sealed interface Stage {
        data object Parsing : Stage
        data class Resolving(val url: String) : Stage
        data class OpeningPlace(val url: String, val name: String) : Stage
        data class Geocoding(val name: String, val attempt: Int) : Stage
    }

    /** Paso de la traza (pantalla de prueba): nombre, éxito, detalle y ms. */
    data class Step(val name: String, val ok: Boolean, val detail: String, val ms: Long)

    sealed interface Event {
        data class StageChanged(val stage: Stage) : Event
        data class TraceStep(val step: Step) : Event
    }

    sealed interface Outcome {
        data class Located(val destination: GeoDestination, val via: String) : Outcome
        data class Named(val name: String) : Outcome
        data class Unrecognized(val reason: String, val raw: String?) : Outcome
        data class Failed(val message: String, val linkUrl: String? = null) : Outcome
    }

    suspend fun resolve(
        sharedText: String?,
        webViewContext: Context,
        onEvent: (Event) -> Unit = {}
    ): Outcome {
        fun stage(s: Stage) = onEvent(Event.StageChanged(s))

        if (sharedText.isNullOrBlank()) {
            return Outcome.Unrecognized("No se recibió texto para compartir.", null)
        }
        val text = sharedText.trim()
        stage(Stage.Parsing)

        val t0 = System.currentTimeMillis()
        val parsed = MapsLinkParser.parse(text)
        onEvent(Event.TraceStep(Step(
            "Parseo del texto",
            parsed !is MapsLinkParser.ParseResult.Unrecognized,
            text.take(160),
            System.currentTimeMillis() - t0
        )))
        return when (parsed) {
            is MapsLinkParser.ParseResult.Success ->
                Outcome.Located(parsed.destination, "coordenadas del enlace")
            is MapsLinkParser.ParseResult.NeedsResolution ->
                resolveShort(parsed.url, text, webViewContext, onEvent)
            is MapsLinkParser.ParseResult.Unrecognized ->
                Outcome.Unrecognized(parsed.reason, parsed.url)
        }
    }

    private suspend fun resolveShort(
        shortUrl: String,
        fullText: String,
        webViewContext: Context,
        onEvent: (Event) -> Unit
    ): Outcome {
        fun stage(s: Stage) = onEvent(Event.StageChanged(s))
        stage(Stage.Resolving(shortUrl))
        val timeoutMs = (httpTimeoutSeconds * 2L + 10L) * 1000L
        val resolved = try {
            withTimeoutOrNull(timeoutMs) {
                ShortLinkResolver(httpTimeoutSeconds).resolve(shortUrl)
            } ?: ShortLinkResolver.ResolveResult.Failed("tiempo de espera agotado")
        } catch (e: Exception) {
            ShortLinkResolver.ResolveResult.Failed(e.message ?: "error inesperado")
        }
        onEvent(Event.TraceStep(Step("HTTP al enlace corto", resolved !is ShortLinkResolver.ResolveResult.Failed, describeResolved(resolved), 0)))

        return when (resolved) {
            is ShortLinkResolver.ResolveResult.Redirect -> {
                when (val r = MapsLinkParser.parse(resolved.finalUrl)) {
                    is MapsLinkParser.ParseResult.Success ->
                        Outcome.Located(r.destination.copy(originalText = fullText), "redirección HTTP")
                    else -> {
                        val name = PlacePageParser.extractFromUrl(resolved.finalUrl)
                        if (name != null) geocodeOrName(name, fullText, onEvent)
                        else Outcome.Unrecognized(
                            "El enlace se resolvió pero no contiene coordenadas ni nombre.", shortUrl
                        )
                    }
                }
            }
            is ShortLinkResolver.ResolveResult.Page -> {
                val name = PlacePageParser.extractPlaceName(resolved.html)
                    ?: return Outcome.Unrecognized(
                        "La página del enlace no contiene coordenadas ni nombre reconocible.", shortUrl
                    )
                onEvent(Event.TraceStep(Step("Nombre en la página", true, name, 0)))
                // 1) WebView oculto (como el navegador) → URL final con coords.
                stage(Stage.OpeningPlace(shortUrl, name))
                val webUrl = try {
                    WebViewUrlResolver.resolveFinalUrl(webViewContext, shortUrl, webViewTimeoutMs)
                } catch (_: Exception) {
                    null
                }
                onEvent(Event.TraceStep(Step(
                    "WebView", webUrl != null,
                    webUrl ?: "sin coordenadas a tiempo o WebView no disponible", 0
                )))
                if (webUrl != null) {
                    when (val r = MapsLinkParser.parse(webUrl)) {
                        is MapsLinkParser.ParseResult.Success ->
                            return Outcome.Located(
                                r.destination.copy(
                                    name = r.destination.name ?: name,
                                    originalText = fullText
                                ),
                                "página abierta en WebView"
                            )
                        else -> { /* sin coords: sigue a Nominatim */ }
                    }
                }
                geocodeOrName(name, fullText, onEvent)
            }
            is ShortLinkResolver.ResolveResult.Failed ->
                Outcome.Failed(
                    "No se pudo resolver el enlace corto: ${resolved.reason}. " +
                        "Comprueba que este enlace abre en el navegador del móvil; " +
                        "si tampoco abre, es la red (datos, DNS privado o VPN).",
                    linkUrl = shortUrl
                )
        }
    }

    private suspend fun geocodeOrName(
        name: String,
        fullText: String,
        onEvent: (Event) -> Unit
    ): Outcome {
        fun stage(s: Stage) = onEvent(Event.StageChanged(s))
        stage(Stage.Geocoding(name, 1))
        val geoTimeout = geoTimeoutSeconds.coerceIn(3, 120)
        val geo = NominatimGeocoder(geoTimeout)
        val first = try {
            withTimeoutOrNull(geoTimeout * 1000L + 5000L) { geo.geocodeOutcome(name) }
                ?: NominatimGeocoder.GeocodeOutcome.Failed
        } catch (_: Exception) {
            NominatimGeocoder.GeocodeOutcome.Failed
        }
        onEvent(Event.TraceStep(Step("Nominatim (nombre completo)", first is NominatimGeocoder.GeocodeOutcome.Found, describeGeo(first), 0)))
        if (first is NominatimGeocoder.GeocodeOutcome.Found) {
            val r = first.result
            return Outcome.Located(GeoDestination(r.lat, r.lng, name = name, originalText = fullText), "geocodificación")
        }
        // Reintento degradado solo si hubo respuesta sin resultados (no si falló la red).
        if (first == NominatimGeocoder.GeocodeOutcome.NotFound) {
            val simplified = simplifyQuery(name)
            if (simplified != null) {
                stage(Stage.Geocoding(simplified, 2))
                val second = try {
                    withTimeoutOrNull(geoTimeout * 1000L + 5000L) { geo.geocodeOutcome(simplified) }
                        ?: NominatimGeocoder.GeocodeOutcome.Failed
                } catch (_: Exception) {
                    NominatimGeocoder.GeocodeOutcome.Failed
                }
                onEvent(Event.TraceStep(Step("Nominatim (consulta simple)", second is NominatimGeocoder.GeocodeOutcome.Found, describeGeo(second), 0)))
                if (second is NominatimGeocoder.GeocodeOutcome.Found) {
                    val r = second.result
                    return Outcome.Located(GeoDestination(r.lat, r.lng, name = name, originalText = fullText), "geocodificación (consulta simple)")
                }
            }
        }
        return Outcome.Named(name)
    }

    /**
     * "Coco Cases, Valparaíso 1509, Grand Bourg" → "Valparaíso 1509, Grand Bourg":
     * los nombres de locales suelen fallar en Nominatim, las direcciones no.
     */
    internal fun simplifyQuery(name: String): String? {
        val parts = name.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        return parts.drop(1).joinToString(", ").takeIf { it.length >= 8 }
    }

    private fun describeResolved(r: ShortLinkResolver.ResolveResult): String = when (r) {
        is ShortLinkResolver.ResolveResult.Redirect -> "redirección a ${r.finalUrl.take(120)}"
        is ShortLinkResolver.ResolveResult.Page -> "página HTML (${r.html.length} caracteres)"
        is ShortLinkResolver.ResolveResult.Failed -> r.reason
    }

    private fun describeGeo(g: NominatimGeocoder.GeocodeOutcome): String = when (g) {
        is NominatimGeocoder.GeocodeOutcome.Found -> "${g.result.lat},${g.result.lng}"
        NominatimGeocoder.GeocodeOutcome.NotFound -> "sin resultados"
        NominatimGeocoder.GeocodeOutcome.Failed -> "error de red/timeout"
    }
}
