package com.controlcal.core.ocr

import com.controlcal.core.ocr.model.OcrBox
import com.controlcal.core.ocr.model.OcrLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionLabelParserTest {

    @Test
    fun `parse returns null for empty OCR lines`() {
        assertNull(NutritionLabelParser.parse(emptyList()))
    }

    @Test
    fun `parse returns null when text has no nutrition keywords`() {
        val lines = listOf(
            OcrLine("Ingredientes: agua, harina de trigo, levadura.", OcrBox(10, 10, 400, 40)),
            OcrLine("Conservar en lugar fresco y seco.", OcrBox(10, 50, 350, 80)),
            OcrLine("Peso neto: 500 g", OcrBox(10, 90, 200, 120)),
        )
        assertNull(NutritionLabelParser.parse(lines))
    }

    @Test
    fun `parse converts kJ to kcal when only kJ is present`() {
        val lines = listOf(
            OcrLine("Valor energético 836,8 kJ", OcrBox(10, 100, 300, 130)),
            OcrLine("Proteínas 10 g", OcrBox(10, 140, 250, 170)),
        )
        val result = NutritionLabelParser.parse(lines)
        assertNotNull(result)
        requireNotNull(result)
        assertEquals(200.0, result.kcal!!, 0.5)
        assertEquals(10.0, result.protein!!, 0.01)
    }

    @Test
    fun `parse handles approximation symbol and records field in approximations`() {
        val lines = listOf(
            OcrLine("Valor energético 150 kcal", OcrBox(10, 100, 300, 130)),
            OcrLine("Hidratos de carbono < 0,5 g", OcrBox(10, 140, 320, 170)),
            OcrLine("de los cuales azúcares <0,5g", OcrBox(10, 180, 320, 210)),
        )
        val result = NutritionLabelParser.parse(lines)
        assertNotNull(result)
        requireNotNull(result)
        assertEquals(0.5, result.carbs!!, 0.01)
        assertEquals(0.5, result.sugars!!, 0.01)
        assertTrue(result.approximations.contains("carbs"))
        assertTrue(result.approximations.contains("sugars"))
    }

    @Test
    fun `parse handles trazas as zero with approximation flag`() {
        val lines = listOf(
            OcrLine("Valor energético 100 kcal", OcrBox(10, 100, 300, 130)),
            OcrLine("Grasas Trazas", OcrBox(10, 140, 250, 170)),
            OcrLine("Sal trazas", OcrBox(10, 180, 250, 210)),
        )
        val result = NutritionLabelParser.parse(lines)
        assertNotNull(result)
        requireNotNull(result)
        assertEquals(0.0, result.fat!!, 0.01)
        assertEquals(0.0, result.salt!!, 0.01)
        assertTrue(result.approximations.contains("fat"))
        assertTrue(result.approximations.contains("salt"))
    }

    @Test
    fun `parse lowers confidence when decimal point is inferred`() {
        val lines = listOf(
            OcrLine("Por 100 g", OcrBox(200, 60, 280, 90)),
            OcrLine("Valor energético", OcrBox(10, 100, 160, 130)),
            OcrLine("100 kcal", OcrBox(200, 100, 280, 130)),
            OcrLine("Proteínas", OcrBox(10, 140, 120, 170)),
            OcrLine("1720", OcrBox(200, 140, 260, 170)),
        )
        val result = NutritionLabelParser.parse(lines)
        assertNotNull(result)
        requireNotNull(result)
        assertEquals(17.2, result.protein!!, 0.01)
        val proteinConfidence = result.confidence["protein"] ?: 1.0f
        assertFalse("Inferred decimal should have confidence < 0.85", proteinConfidence >= 0.85f)
    }
}
