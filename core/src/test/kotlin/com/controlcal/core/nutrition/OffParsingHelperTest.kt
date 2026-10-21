package com.controlcal.core.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OffParsingHelperTest {

    @Test
    fun `convertKjToKcal divides by 4_184`() {
        assertEquals(100.0, OffParsingHelper.convertKjToKcal(418.4), 0.01)
    }

    @Test
    fun `parseServingGrams extracts grams or ml with dot or comma`() {
        assertEquals(30.0, OffParsingHelper.parseServingGrams("1 porción (30 g)")!!, 0.01)
        assertEquals(250.0, OffParsingHelper.parseServingGrams("1 vaso 250ml")!!, 0.01)
        assertEquals(12.5, OffParsingHelper.parseServingGrams("12,5 g")!!, 0.01)
        assertNull(OffParsingHelper.parseServingGrams(null))
        assertNull(OffParsingHelper.parseServingGrams("sin datos"))
    }

    @Test
    fun `parseIsLiquid detects ml cl litro liter and l`() {
        assertTrue(OffParsingHelper.parseIsLiquid("200 ml", null))
        assertTrue(OffParsingHelper.parseIsLiquid(null, "1,5 l"))
        assertTrue(OffParsingHelper.parseIsLiquid(null, "33 cl"))
        assertTrue(OffParsingHelper.parseIsLiquid(null, "1 litro"))
        assertFalse(OffParsingHelper.parseIsLiquid("30 g", "500 g"))
        assertFalse(OffParsingHelper.parseIsLiquid(null, null))
    }
}
