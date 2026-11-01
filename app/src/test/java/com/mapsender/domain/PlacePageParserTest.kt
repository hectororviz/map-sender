package com.mapsender.domain

import org.junit.Assert.*
import org.junit.Test

class PlacePageParserTest {

    // Fragmento real (anonimizado en estructura) de una página maps.app.goo.gl (HTTP 200).
    private val placeSegmentHtml = """<a href="https://www.google.com/maps/place/Coco%2BCases,%2BValpara%25C3%25ADso%2B1509,%2BB1615%2BGrand%2BBourg,%2BProvincia%2Bde%2BBuenos%2BAires/data=...">Ver</a>"""
    private val cidBlobHtml = """[[\"0x95bca3e41f77dd31:0xc5806706ed84c9ab\",\"Coco Cases, Valparaíso 1509, B1615 Grand Bourg, Provincia de Buenos Aires\",[[6652037.08,51.75]]]}"""
    private val unicodeBlobHtml = """[[\"0x1234:0x5678\",\"Coco Caf\u00e9 Central, Calle Falsa 123\"]]"""

    @Test fun placeSegment_doubleEncoded() {
        assertEquals(
            "Coco Cases, Valparaíso 1509, B1615 Grand Bourg, Provincia de Buenos Aires",
            PlacePageParser.extractPlaceName(placeSegmentHtml)
        )
    }

    @Test fun cidPair_fallback() {
        assertEquals(
            "Coco Cases, Valparaíso 1509, B1615 Grand Bourg, Provincia de Buenos Aires",
            PlacePageParser.extractPlaceName(cidBlobHtml)
        )
    }

    @Test fun unicodeEscapes_decoded() {
        assertEquals(
            "Coco Café Central, Calle Falsa 123",
            PlacePageParser.extractPlaceName(unicodeBlobHtml)
        )
    }

    @Test fun garbage_returnsNull() {
        assertNull(PlacePageParser.extractPlaceName("<html><body>hola</body></html>"))
        assertNull(PlacePageParser.extractPlaceName(""))
    }

    @Test fun fromUrl_extractsName() {
        assertEquals(
            "Plaza Mayor",
            PlacePageParser.extractFromUrl("https://www.google.com/maps/place/Plaza+Mayor/@40.41,-3.70,17z")
        )
        assertNull(PlacePageParser.extractFromUrl("https://www.google.com/maps/search/?api=1&query=40.41,-3.70"))
    }
}
