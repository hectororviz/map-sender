package com.mapsender.domain

import org.junit.Assert.*
import org.junit.Test

class NavigationLinkBuilderTest {

    @Test fun wazeUniversal_navigateYes() {
        assertEquals(
            "https://waze.com/ul?ll=40.415363,-3.707346&navigate=yes",
            NavigationLinkBuilder.wazeUniversal(40.415363, -3.707346, true)
        )
    }

    @Test fun wazeUniversal_noNavigate() {
        assertEquals(
            "https://waze.com/ul?ll=40.415363,-3.707346",
            NavigationLinkBuilder.wazeUniversal(40.415363, -3.707346, false)
        )
    }

    @Test fun googleMapsUniversal() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=40.415363,-3.707346",
            NavigationLinkBuilder.googleMapsUniversal(40.415363, -3.707346)
        )
    }

    @Test fun geoIntent_withLabel() {
        val d = GeoDestination(40.0, -3.0, name = "Casa")
        assertTrue(NavigationLinkBuilder.buildPreferred(d, NavAppPreference.GEO).startsWith("geo:"))
    }

    @Test fun preferred_wazeDefault() {
        val d = GeoDestination(40.0, -3.0)
        assertTrue(NavigationLinkBuilder.buildPreferred(d, NavAppPreference.WAZE).startsWith("https://waze.com/ul"))
    }
}
