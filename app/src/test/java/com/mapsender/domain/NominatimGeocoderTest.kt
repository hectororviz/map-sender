package com.mapsender.domain

import org.junit.Assert.*
import org.junit.Test

class NominatimGeocoderTest {

    private val geo = NominatimGeocoder()

    @Test fun parsesFirstResult() {
        val body = """[{"place_id":1,"lat":"-34.603722","lon":"-58.381592","display_name":"Casa, Buenos Aires, Argentina"}]"""
        val r = geo.parseFirst(body)
        assertNotNull(r)
        assertEquals(-34.603722, r!!.lat, 1e-6)
        assertEquals(-58.381592, r.lng, 1e-6)
    }

    @Test fun emptyArray_returnsNull() {
        assertNull(geo.parseFirst("[]"))
    }

    @Test fun invalidJson_returnsNull() {
        assertNull(geo.parseFirst("no-json"))
    }

    @Test fun outOfRange_returnsNull() {
        assertNull(geo.parseFirst("""[{"lat":"999","lon":"0"}]"""))
    }
}
