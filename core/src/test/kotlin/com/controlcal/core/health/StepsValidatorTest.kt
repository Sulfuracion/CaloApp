package com.controlcal.core.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepsValidatorTest {

    @Test
    fun validStepsInputs() {
        val result1 = StepsValidator.validate("8000")
        assertTrue(result1 is StepsValidationResult.Valid)
        assertEquals(8000, (result1 as StepsValidationResult.Valid).steps)

        val result2 = StepsValidator.validate("8.000")
        assertTrue(result2 is StepsValidationResult.Valid)
        assertEquals(8000, (result2 as StepsValidationResult.Valid).steps)

        val result3 = StepsValidator.validate("0")
        assertTrue(result3 is StepsValidationResult.Valid)
        assertEquals(0, (result3 as StepsValidationResult.Valid).steps)

        val result4 = StepsValidator.validate("200000")
        assertTrue(result4 is StepsValidationResult.Valid)
        assertEquals(200000, (result4 as StepsValidationResult.Valid).steps)
    }

    @Test
    fun invalidStepsInputs() {
        assertEquals(StepsValidationResult.Invalid.Blank, StepsValidator.validate(null))
        assertEquals(StepsValidationResult.Invalid.Blank, StepsValidator.validate(""))
        assertEquals(StepsValidationResult.Invalid.Blank, StepsValidator.validate("   "))

        assertEquals(StepsValidationResult.Invalid.InvalidFormat, StepsValidator.validate("abc"))
        assertEquals(StepsValidationResult.Invalid.InvalidFormat, StepsValidator.validate("12a3"))

        assertEquals(StepsValidationResult.Invalid.Negative, StepsValidator.validate("-50"))
        assertEquals(StepsValidationResult.Invalid.OutOfRange, StepsValidator.validate("200001"))
    }

    @Test
    fun isValidHelper() {
        assertTrue(StepsValidator.isValid("8000"))
        assertTrue(StepsValidator.isValid("0"))
        assertFalse(StepsValidator.isValid("-1"))
        assertFalse(StepsValidator.isValid("abc"))
        assertFalse(StepsValidator.isValid(""))
    }
}
