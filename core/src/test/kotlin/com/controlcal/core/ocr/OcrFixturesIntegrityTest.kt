package com.controlcal.core.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrFixturesIntegrityTest {

    @Test
    fun expectedValuesLoadCorrectlyWith19Cases() {
        val expectedList = OcrFixtureLoader.loadExpectedValues()
        assertEquals(19, expectedList.size)

        for (expected in expectedList) {
            assertNotNull("Product name must not be null", expected.productName)
            assertTrue("Kcal must be positive", expected.kcal > 0.0)
            assertTrue("Protein must be non-negative", expected.protein >= 0.0)
            assertTrue("Carbs must be non-negative", expected.carbs >= 0.0)
            assertTrue("Fat must be non-negative", expected.fat >= 0.0)

            expected.sugars?.let {
                assertTrue("Sugars must be <= Carbs", it <= expected.carbs)
            }
            expected.satFat?.let {
                assertTrue("Saturated fat must be <= Fat", it <= expected.fat)
            }
        }
    }

    @Test
    fun all19ValidFixturesExistAndHaveOcrLines() {
        val expectedList = OcrFixtureLoader.loadExpectedValues()
        for (expected in expectedList) {
            val metadata = OcrFixtureLoader.loadFixture(expected.fileName)
            assertEquals(expected.fileName.replace(".json", ".jpg"), metadata.imageFileName)
            assertTrue("Image width should be positive", metadata.imageWidth > 0)
            assertTrue("Image height should be positive", metadata.imageHeight > 0)
            assertFalse(
                "Fixture ${expected.fileName} should have detected OCR lines",
                metadata.lines.isEmpty()
            )
        }
    }

    @Test
    fun discardedBlurryFixturesHaveEmptyLines() {
        val discarded = listOf("label_06.json", "label_09.json", "label_19.json")
        for (fileName in discarded) {
            val metadata = OcrFixtureLoader.loadFixture(fileName)
            assertTrue(
                "Discarded blurry fixture $fileName should have empty lines",
                metadata.lines.isEmpty()
            )
        }
    }
}
