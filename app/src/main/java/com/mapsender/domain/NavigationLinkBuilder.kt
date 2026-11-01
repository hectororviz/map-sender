package com.mapsender.domain

import java.net.URLEncoder
import java.util.Locale

/**
 * Construye enlaces de navegación. Por defecto Waze (universal link, funciona
 * aunque Waze no esté instalado: cae al navegador; ver LIMITATIONS.md).
 */
object NavigationLinkBuilder {

    /** Enlace universal Waze: abre destino listo para navegar (sin auto-iniciar). */
    fun wazeUniversal(latitude: Double, longitude: Double, navigate: Boolean = true): String {
        val ll = formatLl(latitude, longitude)
        return if (navigate) "https://waze.com/ul?ll=$ll&navigate=yes"
        else "https://waze.com/ul?ll=$ll"
    }

    /** Esquema de app Waze (solo si está instalada; el receptor lo intenta si se configura). */
    fun wazeAppScheme(latitude: Double, longitude: Double, navigate: Boolean = true): String {
        val ll = formatLl(latitude, longitude)
        return if (navigate) "waze://?ll=$ll&navigate=yes" else "waze://?ll=$ll"
    }

    fun googleMapsUniversal(latitude: Double, longitude: Double): String {
        val ll = formatLl(latitude, longitude)
        return "https://www.google.com/maps/search/?api=1&query=$ll"
    }

    fun geoIntent(latitude: Double, longitude: Double, label: String? = null): String {
        val ll = formatLl(latitude, longitude)
        return if (label.isNullOrBlank()) "geo:$ll" else "geo:$ll?q=$ll(${label.trim()})"
    }

    /**
     * Búsqueda por nombre (sin coordenadas). Oficial:
     * https://developers.google.com/waze/deeplinks (`q`).
     * Se usa `q` SIN `navigate=yes` a propósito: navegar al primer resultado
     * sin confirmación podría guiar al destino equivocado; el receptor elige.
     */
    fun wazeSearch(query: String): String =
        "https://waze.com/ul?q=${URLEncoder.encode(query.trim(), "UTF-8")}"

    fun googleMapsSearch(query: String): String =
        "https://www.google.com/maps/search/?api=1&query=${URLEncoder.encode(query.trim(), "UTF-8")}"

    fun geoSearch(query: String): String =
        "geo:0,0?q=${URLEncoder.encode(query.trim(), "UTF-8")}"

    fun buildSearchPreferred(query: String, preference: NavAppPreference): String = when (preference) {
        NavAppPreference.WAZE -> wazeSearch(query)
        NavAppPreference.GOOGLE_MAPS -> googleMapsSearch(query)
        NavAppPreference.GEO -> geoSearch(query)
        NavAppPreference.BROWSER -> googleMapsSearch(query)
    }

    fun buildPreferred(
        destination: GeoDestination,
        preference: NavAppPreference,
        wazeNavigateParam: Boolean = true
    ): String = when (preference) {
        NavAppPreference.WAZE -> wazeUniversal(destination.latitude, destination.longitude, wazeNavigateParam)
        NavAppPreference.GOOGLE_MAPS -> googleMapsUniversal(destination.latitude, destination.longitude)
        NavAppPreference.GEO -> geoIntent(destination.latitude, destination.longitude, destination.name)
        NavAppPreference.BROWSER -> googleMapsUniversal(destination.latitude, destination.longitude)
    }

    internal fun formatLl(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.6f,%.6f", latitude, longitude)
}

enum class NavAppPreference { WAZE, GOOGLE_MAPS, GEO, BROWSER }
