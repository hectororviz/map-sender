package com.mapsender.domain

/**
 * Destino geográfico normalizado, independiente de la fuente (Google Maps, geo:, texto...).
 *
 * @param latitude -90..90, @param longitude -180..180.
 * @param name nombre del lugar cuando se puede inferir; null si se desconoce.
 * @param address dirección o texto descriptivo adicional; null si se desconoce.
 * @param originalText texto compartido original (para mostrar / depurar).
 */
data class GeoDestination(
    val latitude: Double,
    val longitude: Double,
    val name: String? = null,
    val address: String? = null,
    val originalText: String? = null
) {
    fun isValid(): Boolean =
        latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            !latitude.isNaN() && !longitude.isNaN()

    fun shortLabel(): String =
        name?.takeIf { it.isNotBlank() } ?: "$latitude,$longitude"
}
