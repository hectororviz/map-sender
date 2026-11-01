package com.mapsender.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Construye el payload JSON para POST /message de Gotify.
 * Usa Content-Type application/json (obligatorio para extras).
 */
object GotifyPayloadBuilder {

    data class Payload(val title: String, val message: String, val priority: Int, val clickUrl: String) {
        fun toJsonString(): String = buildJsonObject {
            put("title", title)
            put("message", message)
            put("priority", priority)
            putJsonObject("extras") {
                putJsonObject("client::notification") {
                    putJsonObject("click") { put("url", clickUrl) }
                }
            }
        }.toString()

        /** Parse inverso para tests (verifica estructura). */
        fun toJsonObject(): JsonObject = Json.parseToJsonElement(toJsonString()) as JsonObject
    }

    fun build(
        destination: GeoDestination,
        deviceName: String,
        priority: Int,
        navigationUrl: String
    ): Payload = build(
        OutgoingPlace.Located(destination, navigationUrl), deviceName, priority
    )

    fun build(place: OutgoingPlace, deviceName: String, priority: Int): Payload {
        val device = deviceName.trim().takeIf { it.isNotEmpty() } ?: "móvil"
        val prio = priority.coerceIn(0, 10)
        return when (place) {
            is OutgoingPlace.Located -> {
                val destination = place.destination
                require(destination.isValid()) { "Destino con coordenadas inválidas" }
                val label = destination.name?.takeIf { it.isNotBlank() } ?: "Destino recibido"
                val lines = mutableListOf(label, "${destination.latitude},${destination.longitude}")
                destination.address?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
                destination.originalText?.takeIf { it.isNotBlank() && it.length < 500 }?.let {
                    if (!lines.contains(it)) lines.add("Origen: $it")
                }
                Payload(
                    title = "$label (de $device)".take(180),
                    message = lines.joinToString("\n"),
                    priority = prio,
                    clickUrl = place.navigationUrl
                )
            }
            is OutgoingPlace.Search -> {
                val label = place.query.takeIf { it.isNotBlank() } ?: "Destino recibido"
                val lines = mutableListOf(
                    label,
                    "Búsqueda: al tocar se abre Waze para elegir el destino."
                )
                place.originalText?.takeIf { it.isNotBlank() && it.length < 500 }?.let {
                    if (!lines.contains(it)) lines.add("Origen: $it")
                }
                Payload(
                    title = "$label (de $device)".take(180),
                    message = lines.joinToString("\n"),
                    priority = prio,
                    clickUrl = place.navigationUrl
                )
            }
        }
    }
}
