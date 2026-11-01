package com.mapsender.domain

import org.junit.Assert.*
import org.junit.Test

class ResolvePlaceUseCaseTest {

    private val useCase = ResolvePlaceUseCase()

    @Test fun simplify_dropsVenueName() {
        assertEquals(
            "Valparaíso 1509, B1615 Grand Bourg",
            useCase.simplifyQuery("Coco Cases, Valparaíso 1509, B1615 Grand Bourg")
        )
    }

    @Test fun simplify_singlePart_null() {
        assertNull(useCase.simplifyQuery("SoloNombre"))
        assertNull(useCase.simplifyQuery("A, B"))
    }
}
