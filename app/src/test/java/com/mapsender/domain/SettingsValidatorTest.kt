package com.mapsender.domain

import org.junit.Assert.*
import org.junit.Test

class SettingsValidatorTest {

    @Test fun valid_ok() {
        // Token ficticio solo para probar longitudes; no es una credencial real.
        val v = SettingsValidator.validate("https://gotify.example.com", "0123456789abcdef", "movil", 5, 15)
        assertTrue(v.isValid)
    }

    @Test fun blankUrl_error() {
        val v = SettingsValidator.validate("", "0123456789abcdef", "movil", 5, 15)
        assertNotNull(v.errorFor("baseUrl"))
    }

    @Test fun shortToken_error() {
        val v = SettingsValidator.validate("https://x.example", "abc", "movil", 5, 15)
        assertNotNull(v.errorFor("token"))
    }

    @Test fun badPriority_error() {
        val v = SettingsValidator.validate("https://x.example", "0123456789abcdef", "movil", 99, 15)
        assertNotNull(v.errorFor("priority"))
    }

    @Test fun badTimeout_error() {
        val v = SettingsValidator.validate("https://x.example", "0123456789abcdef", "movil", 5, 1000)
        assertNotNull(v.errorFor("timeout"))
    }

    @Test fun insecureHttp_detected() {
        assertTrue(SettingsValidator.isInsecureHttp("http://192.168.1.2"))
    }
}
