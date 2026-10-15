package com.controlcal.core.ocr

import com.controlcal.core.ocr.model.ExpectedNutritionValues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

        assertEquals("Kcal mismatch for ${expected.fileName}", expected.kcal, result.kcal ?: -1.0, 1.0)
        assertEquals("Protein mismatch for ${expected.fileName}", expected.protein, result.protein ?: -1.0, 0.5)
        assertEquals("Carbs mismatch for ${expected.fileName}", expected.carbs, result.carbs ?: -1.0, 0.5)
        assertEquals("Fat mismatch for ${expected.fileName}", expected.fat, result.fat ?: -1.0, 0.5)

        expected.sugars?.let {
            assertEquals("Sugars mismatch for ${expected.fileName}", it, result.sugars ?: -1.0, 0.5)
        }
        expected.satFat?.let {
            assertEquals("Saturated fat mismatch for ${expected.fileName}", it, result.satFat ?: -1.0, 0.5)
        }
        expected.fiber?.let {
            assertEquals("Fiber mismatch for ${expected.fileName}", it, result.fiber ?: -1.0, 0.5)
        }
        expected.salt?.let {
            assertEquals("Salt mismatch for ${expected.fileName}", it, result.salt ?: -1.0, 0.1)
        }
    }
}
