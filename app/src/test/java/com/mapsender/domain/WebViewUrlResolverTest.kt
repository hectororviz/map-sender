package com.mapsender.domain

import org.junit.Assert.*
import org.junit.Test

class WebViewUrlResolverTest {

    @Test fun hasCoords_pin() {
        assertTrue(WebViewUrlResolver.hasCoords("https://www.google.com/maps/place/Casa/data=!4m1!1s0x0!8m2!3d-34.48!4d-58.71"))
    }

    @Test fun hasCoords_at() {
        assertTrue(WebViewUrlResolver.hasCoords("https://www.google.com/maps/place/Casa/@-34.46,-58.72,15z"))
    }

    @Test fun hasCoords_query() {
        assertTrue(WebViewUrlResolver.hasCoords("https://www.google.com/maps/search/?api=1&query=-34.46,-58.72"))
    }

    @Test fun hasCoords_rejectsNameOnly() {
        assertFalse(WebViewUrlResolver.hasCoords("https://www.google.com/maps/place/Coco+Cases,+Grand+Bourg"))
        assertFalse(WebViewUrlResolver.hasCoords("https://maps.app.goo.gl/yUNQbu3WgYCwmhDv7"))
        // q con nombre (búsqueda) no son coordenadas.
        assertFalse(WebViewUrlResolver.hasCoords("https://waze.com/ul?q=Coco+Cases"))
    }

    @Test fun consent_extractsContinue() {
        val target = WebViewUrlResolver.extractConsentTarget(
            "https://consent.google.com/m?continue=https://www.google.com/maps/place/Casa/@-34.4,-58.7&gl=AR"
        )
        assertEquals("https://www.google.com/maps/place/Casa/@-34.4,-58.7", target)
    }

    @Test fun consent_nonConsent_null() {
        assertNull(WebViewUrlResolver.extractConsentTarget("https://www.google.com/maps/place/Casa"))
    }
}
