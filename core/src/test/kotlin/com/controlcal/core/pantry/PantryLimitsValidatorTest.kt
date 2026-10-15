package com.controlcal.core.pantry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryLimitsValidatorTest {

    @Test
    fun `all null inputs produce valid result with nulls`() {
        val result = PantryLimitsValidator.validate(
            minG = null,
            maxG = null,
            unitName = null,
            unitG = null,
        )

        assertTrue(result is PantryValidationResult.Valid)
        val valid = result as PantryValidationResult.Valid
        assertNull(valid.minG)
        assertNull(valid.maxG)
        assertNull(valid.unitName)
        assertNull(valid.unitG)
    }

    @Test
    fun `valid limits and custom unit are accepted and trimmed`() {
        val result = PantryLimitsValidator.validate(
            minG = 30.0,
            maxG = 150.0,
            unitName = "  huevo  ",
            unitG = 55.0,
        )

        assertTrue(result is PantryValidationResult.Valid)
        val valid = result as PantryValidationResult.Valid
        assertEquals(30.0, valid.minG!!, 0.001)
        assertEquals(150.0, valid.maxG!!, 0.001)
        assertEquals("huevo", valid.unitName)
        assertEquals(55.0, valid.unitG!!, 0.001)
    }

    @Test
    fun `min equal to max is valid`() {
        val result = PantryLimitsValidator.validate(
            minG = 100.0,
            maxG = 100.0,
            unitName = null,
            unitG = null,
        )

        assertTrue(result is PantryValidationResult.Valid)
        val valid = result as PantryValidationResult.Valid
        assertEquals(100.0, valid.minG!!, 0.001)
        assertEquals(100.0, valid.maxG!!, 0.001)
    }

    @Test
    fun `negative min grams fails with NEGATIVE_VALUE`() {
        val result = PantryLimitsValidator.validate(
            minG = -5.0,
            maxG = 100.0,
            unitName = null,
            unitG = null,
        )

        assertTrue(result is PantryValidationResult.Invalid)
        val invalid = result as PantryValidationResult.Invalid
        assertEquals(PantryError.NEGATIVE_VALUE, invalid.minGError)
        assertNull(invalid.maxGError)
    }

    @Test
    fun `negative max grams fails with NEGATIVE_VALUE`() {
        val result = PantryLimitsValidator.validate(
            minG = 0.0,
            maxG = -1.0,
            unitName = null,
            unitG = null,
        )

        assertTrue(result is PantryValidationResult.Invalid)
        val invalid = result as PantryValidationResult.Invalid
        assertEquals(PantryError.NEGATIVE_VALUE, invalid.maxGError)
    }

    @Test
    fun `min grams greater than max grams fails with MIN_EXCEEDS_MAX`() {
        val result = PantryLimitsValidator.validate(
            minG = 200.0,
            maxG = 100.0,
            unitName = null,
            unitG = null,
        )

        assertTrue(result is PantryValidationResult.Invalid)
        val invalid = result as PantryValidationResult.Invalid
        assertEquals(PantryError.MIN_EXCEEDS_MAX, invalid.minGError)
        assertEquals(PantryError.MIN_EXCEEDS_MAX, invalid.maxGError)
    }

    @Test
    fun `unit name without unit grams fails with UNIT_GRAMS_REQUIRED_OR_INVALID`() {
        val result = PantryLimitsValidator.validate(
            minG = null,
            maxG = null,
            unitName = "rebanada",
            unitG = null,
        )

        assertTrue(result is PantryValidationResult.Invalid)
        val invalid = result as PantryValidationResult.Invalid
        assertEquals(PantryError.UNIT_GRAMS_REQUIRED_OR_INVALID, invalid.unitGError)
    }

    @Test
    fun `unit name with zero or negative grams fails with UNIT_GRAMS_REQUIRED_OR_INVALID`() {
        val resultZero = PantryLimitsValidator.validate(
            minG = null,
            maxG = null,
            unitName = "vaso",
            unitG = 0.0,
        )
        assertTrue(resultZero is PantryValidationResult.Invalid)
        assertEquals(PantryError.UNIT_GRAMS_REQUIRED_OR_INVALID, (resultZero as PantryValidationResult.Invalid).unitGError)

        val resultNegative = PantryLimitsValidator.validate(
            minG = null,
            maxG = null,
            unitName = "vaso",
            unitG = -10.0,
        )
        assertTrue(resultNegative is PantryValidationResult.Invalid)
        assertEquals(PantryError.UNIT_GRAMS_REQUIRED_OR_INVALID, (resultNegative as PantryValidationResult.Invalid).unitGError)
    }

    @Test
    fun `unit grams without unit name fails with UNIT_NAME_REQUIRED`() {
        val result = PantryLimitsValidator.validate(
            minG = null,
            maxG = null,
            unitName = "   ",
            unitG = 50.0,
        )

        assertTrue(result is PantryValidationResult.Invalid)
        val invalid = result as PantryValidationResult.Invalid
        assertEquals(PantryError.UNIT_NAME_REQUIRED, invalid.unitNameError)
    }
}
