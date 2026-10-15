package com.controlcal.core.nutrition

import com.controlcal.core.model.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CreateTareEntryUseCaseTest {

    private val useCase = CreateTareEntryUseCase()

    private val sampleProduct = Product(
        id = 42L,
        name = "Arroz blanco",
        kcal100g = 130.0,
        protein100g = 2.7,
        carbs100g = 28.0,
        fat100g = 0.3,
    )

    @Test
    fun createsTareEntryWithNegativeGramsAndNegativeProportionalMacros() {
        val entry = useCase(
            product = sampleProduct,
            leftoverGrams = 50.0,
            date = "2026-10-15",
            meal = "LUNCH",
            loggedAt = 123456789L,
        )

        assertEquals(42L, entry.productId)
        assertEquals("Arroz blanco", entry.productName)
        assertEquals("2026-10-15", entry.date)
        assertEquals("LUNCH", entry.meal)
        assertEquals(-50.0, entry.grams, 0.001)
        assertEquals(-65.0, entry.kcal, 0.001)
        assertEquals(-1.35, entry.protein, 0.001)
        assertEquals(-14.0, entry.carbs, 0.001)
        assertEquals(-0.15, entry.fat, 0.001)
        assertEquals(123456789L, entry.loggedAt)
    }

    @Test
    fun failsWhenLeftoverGramsIsZeroOrNegative() {
        assertThrows(IllegalArgumentException::class.java) {
            useCase(sampleProduct, leftoverGrams = 0.0, date = "2026-10-15", meal = "LUNCH")
        }

        assertThrows(IllegalArgumentException::class.java) {
            useCase(sampleProduct, leftoverGrams = -10.0, date = "2026-10-15", meal = "LUNCH")
        }
    }
}
