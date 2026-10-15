package com.controlcal.core.ocr

import com.controlcal.core.ocr.model.ExpectedNutritionValues
import com.controlcal.core.ocr.model.OcrLabelMetadata
import kotlinx.serialization.json.Json

object OcrFixtureLoader {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun loadExpectedValues(): List<ExpectedNutritionValues> {
        val stream = javaClass.classLoader?.getResourceAsStream("ocr_fixtures/expected_labels.json")
            ?: error("Resource ocr_fixtures/expected_labels.json not found")
        val content = stream.bufferedReader().use { it.readText() }
        return json.decodeFromString(content)
    }

    fun loadFixture(fileName: String): OcrLabelMetadata {
        val stream = javaClass.classLoader?.getResourceAsStream("ocr_fixtures/$fileName")
            ?: error("Resource ocr_fixtures/$fileName not found")
        val content = stream.bufferedReader().use { it.readText() }
        return json.decodeFromString(content)
    }
}
