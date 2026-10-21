package com.controlcal.core.ocr

import com.controlcal.core.ocr.model.ExpectedNutritionValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class NutritionLabelParserParameterizedTest(
    private val expected: ExpectedNutritionValues
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> {
            return OcrFixtureLoader.loadExpectedValues().map { arrayOf(it) }
        }
    }

    @Test
    fun parseNutritionLabelMatchesExpectedValues() {
        val fixture = OcrFixtureLoader.loadFixture(expected.fileName)
        val result = NutritionLabelParser.parse(fixture.lines)

        assertNotNull("Parser returned null for ${expected.fileName} (${expected.productName})", result)
        requireNotNull(result)

        assertField("Kcal", expected.kcal, result.kcal, 1.0)
        assertField("Protein", expected.protein, result.protein, 0.5)
        assertField("Carbs", expected.carbs, result.carbs, 0.5)
        assertField("Fat", expected.fat, result.fat, 0.5)
        assertField("Sugars", expected.sugars, result.sugars, 0.5)
        assertField("Saturated fat", expected.satFat, result.satFat, 0.5)
        assertField("Fiber", expected.fiber, result.fiber, 0.5)
        assertField("Salt", expected.salt, result.salt, 0.1)
    }

    private fun assertField(fieldName: String, expectedValue: Double?, actualValue: Double?, delta: Double) {
        if (expectedValue == null) {
            assertNull("$fieldName should be null for ${expected.fileName}", actualValue)
        } else {
            assertNotNull("$fieldName should not be null for ${expected.fileName}", actualValue)
            assertEquals("$fieldName mismatch for ${expected.fileName}", expectedValue, actualValue!!, delta)
        }
    }
}
