package com.controlcal.app.ui.confirm

import com.controlcal.app.data.off.OffProduct
import com.controlcal.core.model.Meal
import com.controlcal.core.nutrition.MealClassifier
import com.controlcal.core.nutrition.NumberFormatHelper
import java.time.LocalTime

data class ProductConfirmationUiState(
    val barcode: String = "",
    val name: String = "",
    val brand: String = "",
    val kcal100g: String = "",
    val protein100g: String = "",
    val carbs100g: String = "",
    val fat100g: String = "",
    val fiber100g: String = "",
    val sugars100g: String = "",
    val satFat100g: String = "",
    val salt100g: String = "",
    val isLiquid: Boolean = false,
    val servingG: String = "",
    val unitName: String = "",
    val unitG: String = "",

    // Flags for mandatory fields that were originally missing in Open Food Facts
    val originallyMissingName: Boolean = false,
    val originallyMissingKcal: Boolean = false,
    val originallyMissingProtein: Boolean = false,
    val originallyMissingCarbs: Boolean = false,
    val originallyMissingFat: Boolean = false,

    // Validation errors
    val nameError: String? = null,
    val kcalError: String? = null,
    val proteinError: String? = null,
    val carbsError: String? = null,
    val fatError: String? = null,
    val fiberError: String? = null,
    val sugarsError: String? = null,
    val satFatError: String? = null,
    val saltError: String? = null,
    val servingError: String? = null,
    val unitGError: String? = null,
    val diaryGramsError: String? = null,

    // Diary logging integration
    val addToDiary: Boolean = false,
    val selectedMeal: Meal = MealClassifier.suggestMealForHour(LocalTime.now().hour),
    val diaryGrams: String = "100",
    val portionKcal: Double = 0.0,
    val portionProtein: Double = 0.0,
    val portionCarbs: Double = 0.0,
    val portionFat: Double = 0.0,

    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
) {
    val hasOriginallyMissingFields: Boolean
        get() = originallyMissingName || originallyMissingKcal || originallyMissingProtein ||
            originallyMissingCarbs || originallyMissingFat

    val isSaveEnabled: Boolean
        get() = name.isNotBlank() &&
            NumberFormatHelper.parseDecimal(kcal100g)?.let { it >= 0 } == true &&
            NumberFormatHelper.parseDecimal(protein100g)?.let { it >= 0 } == true &&
            NumberFormatHelper.parseDecimal(carbs100g)?.let { it >= 0 } == true &&
            NumberFormatHelper.parseDecimal(fat100g)?.let { it >= 0 } == true &&
            (!addToDiary || NumberFormatHelper.parseDecimal(diaryGrams)?.let { it > 0 } == true)
}

fun OffProduct.toConfirmationUiState(currentHour: Int = LocalTime.now().hour): ProductConfirmationUiState {
    val servingText = NumberFormatHelper.formatDecimal(servingG)
    val defaultGrams = if (servingText.isNotBlank()) servingText else "100"

    return ProductConfirmationUiState(
        barcode = barcode,
        name = name.orEmpty(),
        brand = brand.orEmpty(),
        kcal100g = NumberFormatHelper.formatDecimal(kcal100g),
        protein100g = NumberFormatHelper.formatDecimal(protein100g),
        carbs100g = NumberFormatHelper.formatDecimal(carbs100g),
        fat100g = NumberFormatHelper.formatDecimal(fat100g),
        fiber100g = NumberFormatHelper.formatDecimal(fiber100g),
        sugars100g = NumberFormatHelper.formatDecimal(sugars100g),
        satFat100g = NumberFormatHelper.formatDecimal(satFat100g),
        salt100g = NumberFormatHelper.formatDecimal(salt100g),
        isLiquid = isLiquid,
        servingG = servingText,
        unitName = "",
        unitG = "",
        originallyMissingName = name.isNullOrBlank(),
        originallyMissingKcal = kcal100g == null,
        originallyMissingProtein = protein100g == null,
        originallyMissingCarbs = carbs100g == null,
        originallyMissingFat = fat100g == null,
        selectedMeal = MealClassifier.suggestMealForHour(currentHour),
        diaryGrams = defaultGrams,
    )
}
