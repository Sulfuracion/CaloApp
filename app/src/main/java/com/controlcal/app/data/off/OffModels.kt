package com.controlcal.app.data.off

import com.controlcal.core.model.Product
import com.controlcal.core.model.ProductSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class OffApiResponseDto(
    val code: String? = null,
    val status: JsonElement? = null,
    @SerialName("status_verbose")
    val statusVerbose: String? = null,
    val product: OffProductDto? = null,
)

@Serializable
internal data class OffProductDto(
    @SerialName("product_name")
    val productName: String? = null,
    @SerialName("brands")
    val brands: String? = null,
    @SerialName("quantity")
    val quantity: String? = null,
    @SerialName("serving_size")
    val servingSize: String? = null,
    @SerialName("nutriments")
    val nutriments: JsonObject? = null,
)

data class OffProduct(
    val barcode: String,
    val name: String?,
    val brand: String?,
    val kcal100g: Double?,
    val protein100g: Double?,
    val carbs100g: Double?,
    val fat100g: Double?,
    val fiber100g: Double? = null,
    val sugars100g: Double? = null,
    val satFat100g: Double? = null,
    val salt100g: Double? = null,
    val servingG: Double? = null,
    val isLiquid: Boolean = false,
) {
    val hasMissingMandatoryFields: Boolean
        get() = name.isNullOrBlank() ||
            kcal100g == null ||
            protein100g == null ||
            carbs100g == null ||
            fat100g == null

    /**
     * Converts to domain [Product] if all mandatory fields (name, kcal, protein, carbs, fat) are present.
     * Returns null if any mandatory macro or product name is missing.
     */
    fun toProductOrNull(): Product? {
        val validName = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val kcal = kcal100g ?: return null
        val p = protein100g ?: return null
        val c = carbs100g ?: return null
        val f = fat100g ?: return null
        return Product(
            barcode = barcode,
            name = validName,
            brand = brand?.trim()?.takeIf { it.isNotEmpty() },
            kcal100g = kcal,
            protein100g = p,
            carbs100g = c,
            fat100g = f,
            fiber100g = fiber100g,
            sugars100g = sugars100g,
            satFat100g = satFat100g,
            salt100g = salt100g,
            servingG = servingG,
            isLiquid = isLiquid,
            source = ProductSource.OFF
        )
    }
}

sealed interface OffSearchResult {
    data class Success(val product: OffProduct) : OffSearchResult
    data object NotFound : OffSearchResult
    data object Offline : OffSearchResult
    data class Error(val message: String, val cause: Throwable? = null) : OffSearchResult
}
