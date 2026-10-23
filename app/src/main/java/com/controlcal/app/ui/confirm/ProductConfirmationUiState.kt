package com.controlcal.app.ui.confirm

import com.controlcal.app.data.off.OffProduct
import com.controlcal.app.ui.feedback.UiText
import com.controlcal.core.model.ConfiguredMeal
import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.Product
import com.controlcal.core.model.ProductSource
import com.controlcal.core.nutrition.MealClassifier
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.ocr.LabelValidator
import com.controlcal.core.ocr.model.OcrParsedNutrients
import java.io.File
import java.time.LocalTime

data class ProductConfirmationUiState(
    val editingProductId: Long? = null,
    val inPantry: Boolean = false,
    val minG: Double? = null,
    val maxG: Double? = null,
    val createdAt: Long? = null,
    val source: ProductSource = ProductSource.OFF,
    val photoFile: File? = null,
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

    // Flags for mandatory fields that were originally missing in Open Food Facts or OCR
    val originallyMissingName: Boolean = false,
    val originallyMissingKcal: Boolean = false,
    val originallyMissingProtein: Boolean = false,
    val originallyMissingCarbs: Boolean = false,
    val originallyMissingFat: Boolean = false,

    // OCR specific quality indicators
    val dubiousFields: Set<String> = emptySet(),
    val approximations: Set<String> = emptySet(),
    val ocrConfidence: Map<String, Float> = emptyMap(),
    val validationIssues: Map<String, UiText> = emptyMap(),
    val generalValidationIssues: List<UiText> = emptyList(),

    // Validation errors
    val barcodeError: UiText? = null,
    val nameError: UiText? = null,
    val kcalError: UiText? = null,
    val proteinError: UiText? = null,
    val carbsError: UiText? = null,
    val fatError: UiText? = null,
    val fiberError: UiText? = null,
    val sugarsError: UiText? = null,
    val satFatError: UiText? = null,
    val saltError: UiText? = null,
    val servingError: UiText? = null,
    val unitGError: UiText? = null,
    val diaryGramsError: UiText? = null,
    val saveError: UiText? = null,

    // Diary logging integration
    val addToDiary: Boolean = false,
    val availableMeals: List<ConfiguredMeal> = emptyList(),
    val selectedMeal: String = DefaultMealIds.BREAKFAST,
    val diaryGrams: String = "100",
    val portionKcal: Double = 0.0,
    val portionProtein: Double = 0.0,
    val portionCarbs: Double = 0.0,
    val portionFat: Double = 0.0,

    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val targetDate: java.time.LocalDate = java.time.LocalDate.now(),
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

    fun toParsedNutrients(): OcrParsedNutrients {
        return OcrParsedNutrients(
            kcal = NumberFormatHelper.parseDecimal(kcal100g),
            protein = NumberFormatHelper.parseDecimal(protein100g),
            carbs = NumberFormatHelper.parseDecimal(carbs100g),
            fat = NumberFormatHelper.parseDecimal(fat100g),
            fiber = NumberFormatHelper.parseDecimal(fiber100g),
            sugars = NumberFormatHelper.parseDecimal(sugars100g),
            satFat = NumberFormatHelper.parseDecimal(satFat100g),
            salt = NumberFormatHelper.parseDecimal(salt100g),
            confidence = ocrConfidence,
            approximations = approximations
        )
    }
}

fun OffProduct.toConfirmationUiState(currentHour: Int = LocalTime.now().hour): ProductConfirmationUiState {
    val servingText = NumberFormatHelper.formatDecimal(servingG)
    val defaultGrams = if (servingText.isNotBlank()) servingText else "100"

    return ProductConfirmationUiState(
        source = ProductSource.OFF,
        photoFile = null,
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

fun toConfirmationUiStateFromOcr(
    photoFile: File,
    parsed: OcrParsedNutrients,
    barcode: String? = null,
    currentHour: Int = LocalTime.now().hour
): ProductConfirmationUiState {
    val validationResult = LabelValidator.validate(parsed)
    val validationMap = mutableMapOf<String, UiText>()
    val generalIssues = mutableListOf<UiText>()

    for (issue in validationResult.issues) {
        val field = issue.field
        if (field != null) {
            validationMap[field] = issue.toUiText()
        } else {
            generalIssues.add(issue.toUiText())
        }
    }

    val dubious = mutableSetOf<String>()
    parsed.confidence.forEach { (field, conf) ->
        if (conf < 0.85f) {
            dubious.add(field)
        }
    }
    dubious.addAll(parsed.approximations)

    return ProductConfirmationUiState(
        source = ProductSource.OCR,
        photoFile = photoFile,
        barcode = barcode.orEmpty(),
        name = "",
        brand = "",
        kcal100g = NumberFormatHelper.formatDecimal(parsed.kcal),
        protein100g = NumberFormatHelper.formatDecimal(parsed.protein),
        carbs100g = NumberFormatHelper.formatDecimal(parsed.carbs),
        fat100g = NumberFormatHelper.formatDecimal(parsed.fat),
        fiber100g = NumberFormatHelper.formatDecimal(parsed.fiber),
        sugars100g = NumberFormatHelper.formatDecimal(parsed.sugars),
        satFat100g = NumberFormatHelper.formatDecimal(parsed.satFat),
        salt100g = NumberFormatHelper.formatDecimal(parsed.salt),
        isLiquid = false,
        servingG = "",
        unitName = "",
        unitG = "",
        originallyMissingName = true,
        originallyMissingKcal = parsed.kcal == null,
        originallyMissingProtein = parsed.protein == null,
        originallyMissingCarbs = parsed.carbs == null,
        originallyMissingFat = parsed.fat == null,
        dubiousFields = dubious,
        approximations = parsed.approximations,
        ocrConfidence = parsed.confidence,
        validationIssues = validationMap,
        generalValidationIssues = generalIssues,
        selectedMeal = MealClassifier.suggestMealForHour(currentHour),
        diaryGrams = "100"
    )
}

fun toManualConfirmationUiState(
    barcode: String? = null,
    currentHour: Int = LocalTime.now().hour,
): ProductConfirmationUiState {
    return ProductConfirmationUiState(
        editingProductId = null,
        source = ProductSource.MANUAL,
        photoFile = null,
        barcode = barcode.orEmpty(),
        name = "",
        brand = "",
        kcal100g = "",
        protein100g = "",
        carbs100g = "",
        fat100g = "",
        fiber100g = "",
        sugars100g = "",
        satFat100g = "",
        salt100g = "",
        isLiquid = false,
        servingG = "",
        unitName = "",
        unitG = "",
        originallyMissingName = false,
        originallyMissingKcal = false,
        originallyMissingProtein = false,
        originallyMissingCarbs = false,
        originallyMissingFat = false,
        selectedMeal = MealClassifier.suggestMealForHour(currentHour),
        diaryGrams = "100",
    )
}

fun Product.toEditConfirmationUiState(): ProductConfirmationUiState {
    return ProductConfirmationUiState(
        editingProductId = id,
        inPantry = inPantry,
        minG = minG,
        maxG = maxG,
        createdAt = createdAt,
        source = source,
        photoFile = null,
        barcode = barcode.orEmpty(),
        name = name,
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
        servingG = NumberFormatHelper.formatDecimal(servingG),
        unitName = unitName.orEmpty(),
        unitG = NumberFormatHelper.formatDecimal(unitG),
        originallyMissingName = false,
        originallyMissingKcal = false,
        originallyMissingProtein = false,
        originallyMissingCarbs = false,
        originallyMissingFat = false,
        addToDiary = false,
    )
}
