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

    @Test fun searchPayload_hasClickUrl() {
        val place = OutgoingPlace.Search("Coco Cases, Grand Bourg", "https://waze.com/ul?q=Coco", "origen")
        val p = GotifyPayloadBuilder.build(place, "movil-x", 5)
        val json = p.toJsonObject()
        val click = (((json["extras"] as kotlinx.serialization.json.JsonObject)
            ["client::notification"] as kotlinx.serialization.json.JsonObject)
            ["click"] as kotlinx.serialization.json.JsonObject
        assertEquals("https://waze.com/ul?q=Coco", (click["url"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertTrue((json["title"] as kotlinx.serialization.json.JsonPrimitive).content.contains("Coco Cases"))
    }

    @Test fun outgoingPlace_locatedDisplay() {
        val place = OutgoingPlace.Located(GeoDestination(40.41, -3.70, name = "Plaza"), "https://waze.com/ul?ll=1,1")
        assertEquals("Plaza", place.displayTitle)
        assertTrue(place.displaySubtitle.contains("40.41"))
    }
}
