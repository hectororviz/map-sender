package com.mapsender.domain

import java.net.URLDecoder

/**
 * Extrae el nombre/dirección del lugar desde la página HTML que devuelve un
 * enlace corto (HTTP 200 sin redirección) o desde una URL /maps/place/.
 *
 * No intenta obtener coordenadas del HTML: los números que aparecen son
 * proyección interna del mapa y no son fiables (verificado con un caso real).
 */
object PlacePageParser {

    private val PLACE_SEGMENT = Regex("""/maps/place/([^/"'\s<>\\]+)""")
    private val CID_PAIR = Regex(""""0x[0-9a-fA-F]+:0x[0-9a-fA-F]+""")

    /** Nombre desde el HTML de la página del lugar. Null si no se encuentra. */
    fun extractPlaceName(html: String): String? {
        // 1. Segmento /maps/place/<nombre-urlencoded>/...
        PLACE_SEGMENT.find(html)?.groupValues?.getOrNull(1)?.let { seg ->
            decodeSegment(seg)?.let { return it }
        }
        // 2. Par ["CID","Nombre completo"] del estado JS de la página.
        extractAfterCid(html)?.let { return it }
        return null
    }

    /** Nombre desde una URL final tipo google.com/maps/place/<nombre>/... */
    fun extractFromUrl(url: String): String? {
        PLACE_SEGMENT.find(url)?.groupValues?.getOrNull(1)?.let { return decodeSegment(it) }
        return null
    }

    private fun decodeSegment(seg: String): String? {
        // Doble encoding habitual (%25C3 → %C3 → í). Intenta doble y simple.
        val decoded = runCatching { URLDecoder.decode(URLDecoder.decode(seg, "UTF-8"), "UTF-8") }
            .getOrElse { runCatching { URLDecoder.decode(seg, "UTF-8") }.getOrNull() }
            ?.replace('+', ' ')?.trim()
        if (decoded.isNullOrBlank() || decoded.length < 3 || decoded.length > 200) return null
        if (decoded.startsWith("http") || !decoded.any { it.isLetter() }) return null
        return decoded
    }

    private fun extractAfterCid(html: String): String? {
        val m = CID_PAIR.find(html) ?: return null
        var i = m.range.last + 1
        val n = html.length
        // Secuencia esperada: ["CID"\,"Nombre"...] con escapes \" literales.
        if (i < n && html[i] == '\\') i++
        if (i >= n || html[i] != ',') return null
        i++
        if (i < n && html[i] == '\\') i++
        if (i >= n || html[i] != '"') return null
        i++
        val sb = StringBuilder()
        while (i < n && sb.length < 200) {
            val c = html[i]
            if (c == '\\' && i + 1 < n) {
                val next = html[i + 1]
                if (next == 'u' && i + 5 < n) {
                    val hex = html.substring(i + 2, i + 6)
                    val code = hex.toIntOrNull(16)
                    if (code != null) {
                        sb.append(code.toChar())
                        i += 6
                        continue
                    }
                }
                // \" \\ \/ etc: conserva el carácter escapado.
                sb.append(next)
                i += 2
                continue
            }
            if (c == '"') break
            sb.append(c)
            i++
        }
        val name = sb.toString().trim()
        if (name.length < 3 || !name.any { it.isLetter() }) return null
        return name
    }
}
