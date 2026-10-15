package com.controlcal.core.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The enum and ID values are persisted as TEXT by Room (section 4 of docs/PLAN.md).
 * These tests pin the values so a rename cannot silently change the stored data.
 */
class EnumsTest {

    @Test
    fun `default meal ids are the schema contract`() {
        assertArrayEquals(
            arrayOf("BREAKFAST", "LUNCH", "SNACK", "DINNER"),
            DefaultMealIds.ALL.toTypedArray(),
        )
    }

    @Test
    fun `day type names are the schema contract`() {
        assertArrayEquals(
            arrayOf("TRAINING", "REST"),
            DayType.entries.map { it.name }.toTypedArray(),
        )
    }

    @Test
    fun `product source names are the schema contract`() {
        assertArrayEquals(
            arrayOf("MANUAL", "OFF", "OCR"),
            ProductSource.entries.map { it.name }.toTypedArray(),
        )
    }

    @Test
    fun `goal origin names are the schema contract`() {
        assertArrayEquals(
            arrayOf("MANUAL", "TDEE"),
            GoalOrigin.entries.map { it.name }.toTypedArray(),
        )
    }
}
