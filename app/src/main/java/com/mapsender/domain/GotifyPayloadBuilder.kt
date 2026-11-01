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
    ): Payload {
        require(destination.isValid()) { "Destino con coordenadas inválidas" }
        val place = destination.name?.takeIf { it.isNotBlank() } ?: "Destino recibido"
        val device = deviceName.trim().takeIf { it.isNotEmpty() } ?: "móvil"
        val title = "$place (de $device)".take(180)
        val lines = mutableListOf(place, "${destination.latitude},${destination.longitude}")
        destination.address?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
        destination.originalText?.takeIf { it.isNotBlank() && it.length < 500 }?.let {
            if (!lines.contains(it)) lines.add("Origen: $it")
        }
        return Payload(
            title = title,
            message = lines.joinToString("\n"),
            priority = priority.coerceIn(0, 10),
            clickUrl = navigationUrl
        )
    }
}
