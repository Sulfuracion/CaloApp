package com.controlcal.app.data.off

import com.controlcal.core.nutrition.OffParsingHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

        private const val MAX_SEARCH_RESULTS = 20
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

        val product = mapDtoToOffProduct(barcode, apiResponse.product)
        return OffSearchResult.Success(product)
    }

    fun parseSearchResponse(jsonString: String): OffSearchByNameResult {
        val searchResponse = try {
            json.decodeFromString<OffSearchResponseDto>(jsonString)
        } catch (e: Exception) {
            return OffSearchByNameResult.Error("Error al procesar la búsqueda en Open Food Facts", e)
        }

        if (searchResponse.products.isEmpty()) {
            return OffSearchByNameResult.Empty
        }

        val items = searchResponse.products.take(MAX_SEARCH_RESULTS).map { dto ->
            val barcode = dto.code?.trim().orEmpty()
            val offProduct = mapDtoToOffProduct(barcode, dto)
            OffSearchItem(
                code = barcode.takeIf { it.isNotEmpty() },
                name = offProduct.name,
                brand = offProduct.brand,
                kcal100g = offProduct.kcal100g,
                offProduct = offProduct,
            )
        }

        return if (items.isEmpty()) {
            OffSearchByNameResult.Empty
        } else {
            OffSearchByNameResult.Success(items)
        }
    }

    internal fun mapDtoToOffProduct(barcode: String, productDto: OffProductDto): OffProduct {
        val nutriments = productDto.nutriments

        val energyKcal = nutriments?.getDoubleOrNull("energy-kcal_100g")
        val energyKj = nutriments?.getDoubleOrNull("energy-kj_100g")
            ?: nutriments?.getDoubleOrNull("energy_100g")

        val kcal = energyKcal ?: energyKj?.let { OffParsingHelper.convertKjToKcal(it) }

        return OffProduct(
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
            servingG = OffParsingHelper.parseServingGrams(productDto.servingSize),
            isLiquid = OffParsingHelper.parseIsLiquid(productDto.servingSize, productDto.quantity)
        )
    }

    private fun JsonObject.getDoubleOrNull(key: String): Double? {
        val element = this[key] ?: return null
        if (element is JsonPrimitive) {
            return element.content.trim().replace(',', '.').toDoubleOrNull()
        }
        return null
    }
}
