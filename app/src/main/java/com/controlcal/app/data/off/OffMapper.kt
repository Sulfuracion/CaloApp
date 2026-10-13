package com.controlcal.app.data.off

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OffMapper @Inject constructor(
    private val json: Json = defaultJson
) {
    companion object {
        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        private const val KJ_TO_KCAL = 4.184
    }

    fun parseResponse(barcode: String, jsonString: String): OffSearchResult {
        val apiResponse = try {
            json.decodeFromString<OffApiResponseDto>(jsonString)
        } catch (e: Exception) {
            return OffSearchResult.Error("Error al procesar los datos de Open Food Facts", e)
        }

        val isFound = when (val s = apiResponse.status) {
            is JsonPrimitive -> s.content == "1"
            else -> apiResponse.product != null
        }

        if (!isFound || apiResponse.product == null) {
            return OffSearchResult.NotFound
        }

        val productDto = apiResponse.product
        val nutriments = productDto.nutriments

        val energyKcal = nutriments?.getDoubleOrNull("energy-kcal_100g")
        val energyKj = nutriments?.getDoubleOrNull("energy-kj_100g")
            ?: nutriments?.getDoubleOrNull("energy_100g")

        val kcal = energyKcal ?: energyKj?.let { it / KJ_TO_KCAL }

        val product = OffProduct(
            barcode = barcode,
            name = productDto.productName?.trim()?.takeIf { it.isNotEmpty() },
            brand = productDto.brands?.trim()?.takeIf { it.isNotEmpty() },
            kcal100g = kcal,
            protein100g = nutriments?.getDoubleOrNull("proteins_100g"),
            carbs100g = nutriments?.getDoubleOrNull("carbohydrates_100g"),
            fat100g = nutriments?.getDoubleOrNull("fat_100g"),
            fiber100g = nutriments?.getDoubleOrNull("fiber_100g"),
            sugars100g = nutriments?.getDoubleOrNull("sugars_100g"),
            satFat100g = nutriments?.getDoubleOrNull("saturated-fat_100g"),
            salt100g = nutriments?.getDoubleOrNull("salt_100g"),
            servingG = parseServingGrams(productDto.servingSize),
            isLiquid = parseIsLiquid(productDto.servingSize, productDto.quantity)
        )

        return OffSearchResult.Success(product)
    }

    private fun JsonObject.getDoubleOrNull(key: String): Double? {
        val element = this[key] ?: return null
        if (element is JsonPrimitive) {
            return element.content.trim().replace(',', '.').toDoubleOrNull()
        }
        return null
    }

    private fun parseServingGrams(servingSize: String?): Double? {
        if (servingSize.isNullOrBlank()) return null
        val regex = Regex("""(?i)(\d+(?:[.,]\d+)?)\s*(?:g|ml)\b""")
        val match = regex.find(servingSize) ?: return null
        return match.groupValues[1].replace(',', '.').toDoubleOrNull()
    }

    private fun parseIsLiquid(servingSize: String?, quantity: String?): Boolean {
        val combined = "${servingSize.orEmpty()} ${quantity.orEmpty()}".lowercase(Locale.ROOT)
        return combined.contains("ml") ||
            combined.contains("cl") ||
            combined.contains("litro") ||
            combined.contains("liter") ||
            Regex("""\b\d+(?:[.,]\d+)?\s*l\b""").containsMatchIn(combined)
    }
}
