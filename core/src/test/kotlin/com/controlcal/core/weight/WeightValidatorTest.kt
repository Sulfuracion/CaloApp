package com.controlcal.core.weight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightValidatorTest {

    @Test
    fun blankOrNullReturnsBlank() {
        assertEquals(WeightValidationResult.Invalid.Blank, WeightValidator.validate(null))
        assertEquals(WeightValidationResult.Invalid.Blank, WeightValidator.validate(""))
        assertEquals(WeightValidationResult.Invalid.Blank, WeightValidator.validate("   "))
        assertFalse(WeightValidator.isValid(null))
        assertFalse(WeightValidator.isValid(""))
    }

    @Test
    fun invalidFormatReturnsInvalidFormat() {
        assertEquals(WeightValidationResult.Invalid.InvalidFormat, WeightValidator.validate("abc"))
        assertEquals(WeightValidationResult.Invalid.InvalidFormat, WeightValidator.validate("78..5"))
        assertEquals(WeightValidationResult.Invalid.InvalidFormat, WeightValidator.validate("-"))
        assertFalse(WeightValidator.isValid("abc"))
    }

    @Test
    fun outOfRangeReturnsOutOfRange() {
        assertEquals(WeightValidationResult.Invalid.OutOfRange, WeightValidator.validate("19.9"))
        assertEquals(WeightValidationResult.Invalid.OutOfRange, WeightValidator.validate("0"))
        assertEquals(WeightValidationResult.Invalid.OutOfRange, WeightValidator.validate("-50"))
        assertEquals(WeightValidationResult.Invalid.OutOfRange, WeightValidator.validate("400.1"))
        assertEquals(WeightValidationResult.Invalid.OutOfRange, WeightValidator.validate("500"))
        assertFalse(WeightValidator.isValid("19.9"))
        assertFalse(WeightValidator.isValid("400.1"))
    }

    @Test
    fun validWeightsReturnValid() {
        val r1 = WeightValidator.validate("20")
        assertTrue(r1 is WeightValidationResult.Valid)
        assertEquals(20.0, (r1 as WeightValidationResult.Valid).weightKg, 0.001)

        val r2 = WeightValidator.validate("78,4")
        assertTrue(r2 is WeightValidationResult.Valid)
        assertEquals(78.4, (r2 as WeightValidationResult.Valid).weightKg, 0.001)

        val r3 = WeightValidator.validate("400,0")
        assertTrue(r3 is WeightValidationResult.Valid)
        assertEquals(400.0, (r3 as WeightValidationResult.Valid).weightKg, 0.001)

        assertTrue(WeightValidator.isValid("78,4"))
        assertTrue(WeightValidator.isValid("78.4"))
    }
}
