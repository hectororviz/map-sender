package com.mapsender.domain

import org.junit.Assert.*
import org.junit.Test

class GotifyPayloadBuilderTest {

    @Test fun payloadStructure_hasClickUrl() {
        val d = GeoDestination(40.415363, -3.707346, name = "Plaza Mayor", originalText = "origen")
        val nav = NavigationLinkBuilder.wazeUniversal(d.latitude, d.longitude)
        val p = GotifyPayloadBuilder.build(d, "movil-x", 5, nav)
        val json = p.toJsonObject()
        val clickUrl = json["extras"] as kotlinx.serialization.json.JsonObject
        val notif = clickUrl["client::notification"] as kotlinx.serialization.json.JsonObject
        val click = notif["click"] as kotlinx.serialization.json.JsonObject
        assertEquals(nav, (click["url"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals(5, (json["priority"] as kotlinx.serialization.json.JsonPrimitive).content.toInt())
        assertTrue((json["title"] as kotlinx.serialization.json.JsonPrimitive).content.contains("Plaza Mayor"))
    }

    @Test fun priority_clamped() {
        val d = GeoDestination(1.0, 1.0)
        assertEquals(10, GotifyPayloadBuilder.build(d, "m", 99, "https://waze.com/ul?ll=1,1").priority)
        assertEquals(0, GotifyPayloadBuilder.build(d, "m", -5, "https://waze.com/ul?ll=1,1").priority)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDestination_rejected() {
        GotifyPayloadBuilder.build(GeoDestination(999.0, 0.0), "m", 5, "https://x")
    }
}
