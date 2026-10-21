package com.controlcal.core.ocr

import com.controlcal.core.ocr.model.ExpectedNutritionValues
import com.controlcal.core.ocr.model.OcrBox
import com.controlcal.core.ocr.model.OcrLabelMetadata
import com.controlcal.core.ocr.model.OcrLine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object OcrFixtureLoader {

    @Serializable
    private data class OcrBoxDto(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    @Serializable
    private data class OcrLineDto(
        val text: String,
        val box: OcrBoxDto,
    )

    @Serializable
    private data class OcrLabelMetadataDto(
        val imageFileName: String,
        val timestamp: String,
        val imageWidth: Int,
        val imageHeight: Int,
        val lines: List<OcrLineDto>,
    )

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
        val dto = json.decodeFromString<OcrLabelMetadataDto>(content)
        return OcrLabelMetadata(
            imageFileName = dto.imageFileName,
            timestamp = dto.timestamp,
            imageWidth = dto.imageWidth,
            imageHeight = dto.imageHeight,
            lines = dto.lines.map { line ->
                OcrLine(
                    text = line.text,
                    box = OcrBox(
                        left = line.box.left,
                        top = line.box.top,
                        right = line.box.right,
                        bottom = line.box.bottom,
                    ),
                )
            },
        )
    }
}

