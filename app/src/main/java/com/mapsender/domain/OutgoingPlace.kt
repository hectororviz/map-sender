package com.mapsender.domain

/**
 * Destino listo para enviar al receptor.
 * - [Located]: coordenadas exactas → Waze abre el destino listo para navegar.
 * - [Search]: solo nombre/dirección (p. ej. enlace corto cuya página no trae
 *   coordenadas) → Waze abre la búsqueda para que el receptor elija el resultado.
 *   Documentado en https://developers.google.com/waze/deeplinks (`q`).
 */
sealed interface OutgoingPlace {
    val displayTitle: String
    val displaySubtitle: String
    val navigationUrl: String

    data class Located(
        val destination: GeoDestination,
        override val navigationUrl: String
    ) : OutgoingPlace {
        override val displayTitle: String get() = destination.shortLabel()
        override val displaySubtitle: String
            get() = buildString {
                append("${destination.latitude},${destination.longitude}")
                destination.address?.takeIf { it.isNotBlank() }?.let { append("\n$it") }
            }
    }

    data class Search(
        val query: String,
        override val navigationUrl: String,
        val originalText: String? = null
    ) : OutgoingPlace {
        override val displayTitle: String get() = query
        override val displaySubtitle: String
            get() = "Búsqueda: al tocar se abre Waze para elegir el resultado."
    }
}
