package com.controlcal.core.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberFormatHelperTest {

    @Test
    fun formatDecimal_nullAndNonFiniteReturnsEmpty() {
        assertEquals("", NumberFormatHelper.formatDecimal(null))
        assertEquals("", NumberFormatHelper.formatDecimal(Double.NaN))
        assertEquals("", NumberFormatHelper.formatDecimal(Double.POSITIVE_INFINITY))
        assertEquals("", NumberFormatHelper.formatDecimal(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun formatDecimal_wholeNumbersHaveNoDecimals() {
        assertEquals("0", NumberFormatHelper.formatDecimal(0.0))
        assertEquals("0", NumberFormatHelper.formatDecimal(-0.0))
        assertEquals("100", NumberFormatHelper.formatDecimal(100.0))
        assertEquals("250", NumberFormatHelper.formatDecimal(250.0))
    }

    @Test
    fun formatDecimal_usesCommaForDecimalsAndConfigurableMaxDecimals() {
        assertEquals("12,5", NumberFormatHelper.formatDecimal(12.5))
        assertEquals("0,75", NumberFormatHelper.formatDecimal(0.75))
        assertEquals("1,23", NumberFormatHelper.formatDecimal(1.23))
        assertEquals("12,35", NumberFormatHelper.formatDecimal(12.346, maxDecimals = 2))
        assertEquals("12,3", NumberFormatHelper.formatDecimal(12.34, maxDecimals = 1))
    }

    @Test
    fun parseDecimal_handlesBothCommaAndDot() {
        assertEquals(12.5, NumberFormatHelper.parseDecimal("12,5")!!, 0.001)
        assertEquals(12.5, NumberFormatHelper.parseDecimal("12.5")!!, 0.001)
        assertEquals(100.0, NumberFormatHelper.parseDecimal("100")!!, 0.001)
        assertEquals(0.0, NumberFormatHelper.parseDecimal("0,0")!!, 0.001)
    }

    @Test
    fun parseDecimal_invalidOrBlankReturnsNull() {
        assertNull(NumberFormatHelper.parseDecimal(null))
        assertNull(NumberFormatHelper.parseDecimal(""))
        assertNull(NumberFormatHelper.parseDecimal("   "))
        assertNull(NumberFormatHelper.parseDecimal("abc"))
        assertNull(NumberFormatHelper.parseDecimal("12.3.4"))
        assertNull(NumberFormatHelper.parseDecimal("NaN"))
    }
}
