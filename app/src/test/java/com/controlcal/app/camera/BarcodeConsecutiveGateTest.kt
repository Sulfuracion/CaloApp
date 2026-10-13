package com.controlcal.app.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BarcodeConsecutiveGateTest {

    private lateinit var gate: BarcodeConsecutiveGate

    @Before
    fun setUp() {
        gate = BarcodeConsecutiveGate(requiredConsecutiveMatches = 2, maxIntervalMs = 1500L)
    }

    @Test
    fun firstReading_doesNotAccept() {
        val accepted = gate.onBarcodeRead("8410000000001", timestamp = 1000L)
        assertFalse(accepted)
    }

    @Test
    fun twoConsecutiveIdenticalReadings_accepts() {
        val first = gate.onBarcodeRead("8410000000001", timestamp = 1000L)
        assertFalse(first)

        val second = gate.onBarcodeRead("8410000000001", timestamp = 1100L)
        assertTrue(second)
    }

    @Test
    fun differentReading_resetsStreak() {
        assertFalse(gate.onBarcodeRead("8410000000001", timestamp = 1000L))
        // Different barcode reading resets the consecutive streak
        assertFalse(gate.onBarcodeRead("8410000000002", timestamp = 1100L))

        // Returning to the first barcode requires 2 consecutive reads again
        assertFalse(gate.onBarcodeRead("8410000000001", timestamp = 1200L))
        assertTrue(gate.onBarcodeRead("8410000000001", timestamp = 1300L))
    }

    @Test
    fun intervalExceeded_resetsStreak() {
        assertFalse(gate.onBarcodeRead("8410000000001", timestamp = 1000L))

        // Interval is 2000ms > maxIntervalMs (1500ms)
        assertFalse(gate.onBarcodeRead("8410000000001", timestamp = 3000L))

        // Subsequent immediate reading matches and completes streak
        assertTrue(gate.onBarcodeRead("8410000000001", timestamp = 3100L))
    }

    @Test
    fun blankCode_isIgnored() {
        assertFalse(gate.onBarcodeRead("", timestamp = 1000L))
        assertFalse(gate.onBarcodeRead("   ", timestamp = 1100L))
    }

    @Test
    fun reset_clearsState() {
        assertFalse(gate.onBarcodeRead("8410000000001", timestamp = 1000L))
        gate.reset()

        // After reset, previous candidate is cleared
        assertFalse(gate.onBarcodeRead("8410000000001", timestamp = 1050L))
        assertTrue(gate.onBarcodeRead("8410000000001", timestamp = 1100L))
    }

    @Test
    fun customConsecutiveThreshold_requiresConfiguredCount() {
        val customGate = BarcodeConsecutiveGate(requiredConsecutiveMatches = 3, maxIntervalMs = 1500L)

        assertFalse(customGate.onBarcodeRead("8410000000001", timestamp = 1000L))
        assertFalse(customGate.onBarcodeRead("8410000000001", timestamp = 1100L))
        assertTrue(customGate.onBarcodeRead("8410000000001", timestamp = 1200L))
    }
}
