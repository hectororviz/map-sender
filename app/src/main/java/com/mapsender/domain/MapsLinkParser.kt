package com.mapsender.domain

import java.net.URI
import java.net.URLDecoder

/**
 * Interpreta texto compartido desde Google Maps (u otras apps) y extrae coordenadas.
 *
 * Formatos soportados (ver tests para ejemplos reales):
 * - URLs Google Maps: /maps/search/?api=1&query=lat,lng | /maps/place/Nombre/@lat,lng,z
 *   | maps.google.com/?q=lat,lng | ?q=nombre | ?daddr=... | ?ll=... | ?destination=...
 * - URIs geo: geo:lat,lng?q=... / geo:0,0?q=Nombre
 * - Texto suelto "lat,lng" (p. ej. "40.4168,-3.7038").
 * - Enlaces cortos (maps.app.goo.gl / goo.gl/maps): NO contienen coordenadas;
 *   se detectan con [isShortLink] para resolverlos aparte vía [ShortLinkResolver].
 *
 * Seguridad: solo extrae lat/lng en rango válido; nunca ejecuta URLs.
 */
object MapsLinkParser {

    private val COORD_PATTERN = Regex("""(-?\d{1,3}\.\d+)\s*[,;]\s*(-?\d{1,3}\.\d+)""")
    private val AT_PATTERN = Regex("""@(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)""")
    private val PLACE_PATTERN = Regex("""/place/([^/@?]+)""")
    private val URL_PATTERN = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    fun isShortLink(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("maps.app.goo.gl") || lower.contains("goo.gl/maps")
    }

    fun extractFirstUrl(text: String): String? = URL_PATTERN.find(text)?.value
        ?.trimEnd('.', ',', ')', ']', '!', '?')

    fun parse(sharedText: String?): ParseResult {
        if (sharedText.isNullOrBlank()) return ParseResult.Unrecognized(null, "Contenido vacío")
        val text = sharedText.trim()

        // 1. geo: URI
        if (text.lowercase().startsWith("geo:")) {
            parseGeoUri(text)?.let { return ParseResult.Success(it) }
        }

        val url = extractFirstUrl(text)

        // 2. Enlace corto: no se puede resolver sin red -> NeedsResolution
        if (isShortLink(text) || (url != null && isShortLink(url))) {
            // Puede que el texto incluya coords además del corto; intenta extraerlas primero.
            extractCoords(text)?.let { (lat, lng) ->
                return ParseResult.Success(
                    GeoDestination(lat, lng, name = guessName(null, text), originalText = text)
                )
            }
            return ParseResult.NeedsResolution(url ?: text)
        }

        // 3. URL larga de Google Maps
        if (url != null) {
            parseMapsUrl(url, fullText = text)?.let { return ParseResult.Success(it) }
        }

        // 4. Texto suelto "lat,lng"
        extractCoords(text)?.let { (lat, lng) ->
            return ParseResult.Success(GeoDestination(lat, lng, originalText = text))
        }

        return ParseResult.Unrecognized(url ?: text, "No se reconocieron coordenadas en el enlace")
    }

    // --- Internals (internal para tests) ---

    internal fun extractCoords(text: String): Pair<Double, Double>? {
        val m = COORD_PATTERN.find(text) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lng = m.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return lat to lng
    }

    internal fun parseGeoUri(text: String): GeoDestination? {
        // geo:lat,lng?q=nombre  |  geo:0,0?q=lat,lng(nombre)
        val body = text.substringAfter("geo:", text).substringBefore(" ")
        val coordsPart = body.substringBefore("?")
        val query = substringQueryParam(body, "q")
        val coords = extractCoords(coordsPart)
            ?: query?.let { extractCoords(it) }
            ?: return null
        val name = query
            ?.substringAfter("(", missingDelimiterValue = "")
            ?.substringBefore(")", missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() && !COORD_PATTERN.containsMatchIn(it) }
            ?: query?.takeIf { !COORD_PATTERN.containsMatchIn(it) }
        return GeoDestination(coords.first, coords.second, name = decode(name), originalText = text)
    }

    internal fun parseMapsUrl(url: String, fullText: String): GeoDestination? {
        val uri = try { URI(url) } catch (_: Exception) { return null }
        val params = parseQueryParams(uri.rawQuery.orEmpty() + "&" + uri.fragment.orEmpty())

        // Orden: query/q/daddr/destination/ll/sll, luego @lat,lng, luego coords sueltas.
        val candidateKeys = listOf("query", "q", "daddr", "destination", "ll", "sll")
        var nameFromQuery: String? = null
        for (key in candidateKeys) {
            val raw = params[key] ?: continue
            val decoded = decode(raw)
            extractCoords(decoded)?.let { (lat, lng) ->
                val name = nameFromPlaceSegment(url) ?: guessName(decoded, fullText)
                return GeoDestination(lat, lng, name = name, originalText = fullText)
            }
            if (nameFromQuery == null && decoded.isNotBlank()) nameFromQuery = decoded
        }

        AT_PATTERN.find(url)?.let {
            val lat = it.groupValues[1].toDoubleOrNull()
            val lng = it.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                return GeoDestination(
                    lat, lng,
                    name = nameFromPlaceSegment(url) ?: nameFromQuery?.takeIf { !COORD_PATTERN.containsMatchIn(it) },
                    originalText = fullText
                )
            }
        }

        extractCoords(decode(url))?.let { (lat, lng) ->
            return GeoDestination(
                lat, lng,
                name = nameFromPlaceSegment(url) ?: nameFromQuery,
                originalText = fullText
            )
        }

        // Nombre sin coordenadas (p. ej. ?q=Plaza+Mayor): sin lat/lng no hay destino válido.
        return null
    }

    internal fun nameFromPlaceSegment(url: String): String? {
        val raw = PLACE_PATTERN.find(url)?.groupValues?.getOrNull(1) ?: return null
        val cleaned = decode(raw).replace('+', ' ').trim()
        if (cleaned.isBlank() || COORD_PATTERN.containsMatchIn(cleaned)) return null
        return cleaned
    }

    private fun guessName(decodedQuery: String?, fullText: String): String? {
        decodedQuery?.let {
            if (it.isNotBlank() && !COORD_PATTERN.containsMatchIn(it) && !it.startsWith("http")) return it
        }
        // Primera línea del texto compartido que no sea URL (Google Maps antepone el nombre).
        fullText.lines().map { it.trim() }.firstOrNull { line ->
            line.isNotBlank() && !line.startsWith("http") && !COORD_PATTERN.containsMatchIn(line)
        }?.let { return it }
        return null
    }

    private fun parseQueryParams(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split("&").mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i) to part.substring(i + 1)
        }.toMap()
    }

    private fun substringQueryParam(text: String, key: String): String? {
        val qIndex = text.indexOf('?')
        if (qIndex < 0) return null
        return parseQueryParams(text.substring(qIndex + 1))[key]?.let { decode(it) }
    }

    private fun decode(s: String?): String? {
        if (s == null) return null
        return try {
            URLDecoder.decode(s.replace('+', ' '), "UTF-8").trim().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            s.trim().takeIf { it.isNotEmpty() }
        }
    }

    sealed interface ParseResult {
        data class Success(val destination: GeoDestination) : ParseResult
        /** Enlace corto que requiere resolución HTTP antes de extraer coordenadas. */
        data class NeedsResolution(val url: String) : ParseResult
        data class Unrecognized(val url: String?, val reason: String) : ParseResult
    }
}
