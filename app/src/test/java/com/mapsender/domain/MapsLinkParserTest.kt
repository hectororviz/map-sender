package com.mapsender.domain

import org.junit.Assert.*
import org.junit.Test

class MapsLinkParserTest {

    @Test fun searchApiWithCoords() {
        val text = "https://www.google.com/maps/search/?api=1&query=40.415363,-3.707346"
        val r = MapsLinkParser.parse(text) as MapsLinkParser.ParseResult.Success
        assertEquals(40.415363, r.destination.latitude, 1e-6)
        assertEquals(-3.707346, r.destination.longitude, 1e-6)
    }

    @Test fun placeWithAtCoords_extractsName() {
        val text = "Plaza Mayor\nhttps://www.google.com/maps/place/Plaza+Mayor/@40.415363,-3.707346,17z"
        val r = MapsLinkParser.parse(text) as MapsLinkParser.ParseResult.Success
        assertEquals(40.415363, r.destination.latitude, 1e-6)
        assertNotNull(r.destination.name)
    }

    @Test fun mapsGoogleQuery() {
        val text = "https://maps.google.com/?q=40.4168,-3.7038"
        val r = MapsLinkParser.parse(text) as MapsLinkParser.ParseResult.Success
        assertEquals(40.4168, r.destination.latitude, 1e-4)
    }

    @Test fun geoUri() {
        val r = MapsLinkParser.parse("geo:40.4168,-3.7038?q=Plaza+Mayor") as MapsLinkParser.ParseResult.Success
        assertEquals(40.4168, r.destination.latitude, 1e-4)
    }

    @Test fun plainCoords() {
        val r = MapsLinkParser.parse("40.4168,-3.7038") as MapsLinkParser.ParseResult.Success
        assertTrue(r.destination.isValid())
    }

    @Test fun shortLink_needsResolution() {
        val r = MapsLinkParser.parse("https://maps.app.goo.gl/AbC123XyZ")
        assertTrue(r is MapsLinkParser.ParseResult.NeedsResolution)
    }

    @Test fun invalidCoords_rejected() {
        val r = MapsLinkParser.parse("https://www.google.com/maps/search/?api=1&query=999,999")
        assertTrue(r is MapsLinkParser.ParseResult.Unrecognized)
    }

    @Test fun empty_unrecognized() {
        assertTrue(MapsLinkParser.parse(null) is MapsLinkParser.ParseResult.Unrecognized)
        assertTrue(MapsLinkParser.parse("  ") is MapsLinkParser.ParseResult.Unrecognized)
    }

    @Test fun nameWithoutCoords_unrecognized() {
        // Nombre sin coordenadas no es un destino enviable (documentado en LIMITATIONS.md).
        val r = MapsLinkParser.parse("https://www.google.com/maps/search/?api=1&query=Plaza+Mayor")
        assertTrue(r is MapsLinkParser.ParseResult.Unrecognized)
    }
}
