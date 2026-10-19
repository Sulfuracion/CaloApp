package com.controlcal.app.ui.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlcal.app.data.off.OffProduct
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.Product
import com.controlcal.core.model.ProductSource
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.nutrition.NutritionCalculator
import com.controlcal.core.ocr.LabelValidator
import com.controlcal.core.ocr.model.OcrParsedNutrients
import com.controlcal.core.repo.MealEntryRepository
import com.controlcal.core.repo.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.nutrition.MealClassifier
import com.controlcal.core.repo.MealConfigRepository
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class ProductConfirmationViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val mealEntryRepository: MealEntryRepository,
    private val mealConfigRepository: MealConfigRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductConfirmationUiState())
    val uiState: StateFlow<ProductConfirmationUiState> = _uiState.asStateFlow()

    init {
        loadMealConfig()
    }

    private fun loadMealConfig() {
        viewModelScope.launch {
            val meals = mealConfigRepository.getMealConfigs()
            val active = meals.filter { it.isActive }.sortedBy { it.startTime }
            val suggested = MealClassifier.suggestMeal(active, LocalTime.now(clock))?.id
                ?: active.firstOrNull()?.id
                ?: DefaultMealIds.BREAKFAST
            _uiState.update {
                it.copy(
                    availableMeals = active,
                    selectedMeal = suggested,
                )
            }
        }
    }

    fun initialize(offProduct: OffProduct, targetDate: LocalDate? = null, targetMeal: String? = null) {
        val activeMeals = _uiState.value.availableMeals
        val selected = targetMeal ?: _uiState.value.selectedMeal
        val date = targetDate ?: LocalDate.now(clock)
        _uiState.update {
            val state = offProduct.toConfirmationUiState().copy(
                availableMeals = activeMeals,
                selectedMeal = selected,
                targetDate = date,
                addToDiary = targetMeal != null,
            )
            recalculatePortion(state)
        }
    }

    fun initializeFromOcr(
        photoFile: File,
        parsedNutrients: OcrParsedNutrients,
        barcode: String? = null,
        targetDate: LocalDate? = null,
        targetMeal: String? = null,
    ) {
        val activeMeals = _uiState.value.availableMeals
        val selected = targetMeal ?: _uiState.value.selectedMeal
        val date = targetDate ?: LocalDate.now(clock)
        _uiState.update {
            val state = toConfirmationUiStateFromOcr(photoFile, parsedNutrients, barcode).copy(
                availableMeals = activeMeals,
                selectedMeal = selected,
                targetDate = date,
            )
            recalculatePortion(state)
        }
    }

    fun onBarcodeChanged(value: String) {
        _uiState.update { it.copy(barcode = value) }
    }

    fun onNameChanged(value: String) {
        _uiState.update { current ->
            val error = if (value.isBlank()) "Campo obligatorio" else null
            current.copy(name = value, nameError = error)
        }
    }

    fun onBrandChanged(value: String) {
        _uiState.update { it.copy(brand = value) }
    }

    fun onKcalChanged(value: String) {
        _uiState.update { current ->
            val parsed = NumberFormatHelper.parseDecimal(value)
            val error = when {
                value.isBlank() -> "Campo obligatorio"
                parsed == null -> "Número no válido"
                parsed < 0 -> "No puede ser negativo"
                else -> null
            }
            val updated = current.copy(kcal100g = value, kcalError = error)
            recalculatePortion(updated)
        }
    }

    fun onProteinChanged(value: String) {
        _uiState.update { current ->
            val parsed = NumberFormatHelper.parseDecimal(value)
            val error = when {
                value.isBlank() -> "Campo obligatorio"
                parsed == null -> "Número no válido"
                parsed < 0 -> "No puede ser negativo"
                else -> null
            }
            val updated = current.copy(protein100g = value, proteinError = error)
            recalculatePortion(updated)
        }
    }

    fun onCarbsChanged(value: String) {
        _uiState.update { current ->
            val parsed = NumberFormatHelper.parseDecimal(value)
            val error = when {
                value.isBlank() -> "Campo obligatorio"
                parsed == null -> "Número no válido"
                parsed < 0 -> "No puede ser negativo"
                else -> null
            }
            val updated = current.copy(carbs100g = value, carbsError = error)
            recalculatePortion(updated)
        }
    }

    fun onFatChanged(value: String) {
        _uiState.update { current ->
            val parsed = NumberFormatHelper.parseDecimal(value)
            val error = when {
                value.isBlank() -> "Campo obligatorio"
                parsed == null -> "Número no válido"
                parsed < 0 -> "No puede ser negativo"
                else -> null
            }
            val updated = current.copy(fat100g = value, fatError = error)
            recalculatePortion(updated)
        }
    }

    fun onFiberChanged(value: String) {
        _uiState.update { current ->
            val parsed = NumberFormatHelper.parseDecimal(value)
            val error = if (value.isNotBlank() && (parsed == null || parsed < 0)) "Número no válido" else null
            val updated = current.copy(fiber100g = value, fiberError = error)
            revalidateOcr(updated)
        }
    }

    fun onSugarsChanged(value: String) {
        _uiState.update { current ->
            val parsed = NumberFormatHelper.parseDecimal(value)
            val error = if (value.isNotBlank() && (parsed == null || parsed < 0)) "Número no válido" else null
            val updated = current.copy(sugars100g = value, sugarsError = error)
            revalidateOcr(updated)
        }
    }

    fun onSatFatChanged(value: String) {
        _uiState.update { current ->
            val parsed = NumberFormatHelper.parseDecimal(value)
            val error = if (value.isNotBlank() && (parsed == null || parsed < 0)) "Número no válido" else null
            val updated = current.copy(satFat100g = value, satFatError = error)
            revalidateOcr(updated)
        }
    }

    fun onSaltChanged(value: String) {
        _uiState.update { current ->
            val parsed = NumberFormatHelper.parseDecimal(value)
            val error = if (value.isNotBlank() && (parsed == null || parsed < 0)) "Número no válido" else null
            val updated = current.copy(salt100g = value, saltError = error)
            revalidateOcr(updated)
        }
    }

    fun onIsLiquidChanged(value: Boolean) {
        _uiState.update { it.copy(isLiquid = value) }
    }

    fun onServingChanged(value: String) {
        _uiState.update { current ->
            val parsed = NumberFormatHelper.parseDecimal(value)
            val error = if (value.isNotBlank() && (parsed == null || parsed <= 0)) "Número no válido" else null
            current.copy(servingG = value, servingError = error)
        }
    }

    fun onUnitNameChanged(value: String) {
        _uiState.update { it.copy(unitName = value) }
    }

    fun onUnitGChanged(value: String) {
        _uiState.update { current ->
            val parsed = NumberFormatHelper.parseDecimal(value)
            val error = if (value.isNotBlank() && (parsed == null || parsed <= 0)) "Número no válido" else null
            current.copy(unitG = value, unitGError = error)
        }
    }

    fun onAddToDiaryChanged(value: Boolean) {
        _uiState.update { it.copy(addToDiary = value) }
    }

    fun onMealSelected(meal: String) {
        _uiState.update { it.copy(selectedMeal = meal) }
    }

    fun onDiaryGramsChanged(value: String) {
        _uiState.update { current ->
            val parsed = NumberFormatHelper.parseDecimal(value)
            val error = when {
                value.isBlank() -> "Campo obligatorio"
                parsed == null -> "Número no válido"
                parsed <= 0 -> "Debe ser mayor que 0"
                else -> null
            }
            val updated = current.copy(diaryGrams = value, diaryGramsError = error)
            recalculatePortion(updated)
        }
    }

    private fun revalidateOcr(state: ProductConfirmationUiState): ProductConfirmationUiState {
        if (state.source != ProductSource.OCR) return state
        val parsed = state.toParsedNutrients()
        val validationResult = LabelValidator.validate(parsed)
        val validationMap = mutableMapOf<String, String>()
        val generalIssues = mutableListOf<String>()

        for (issue in validationResult.issues) {
            val field = issue.field
            if (field != null) {
                validationMap[field] = issue.message
            } else {
                generalIssues.add(issue.message)
            }
        }
        return state.copy(
            validationIssues = validationMap,
            generalValidationIssues = generalIssues
        )
    }

    private fun recalculatePortion(state: ProductConfirmationUiState): ProductConfirmationUiState {
        val grams = NumberFormatHelper.parseDecimal(state.diaryGrams) ?: 0.0
        val kcal = NumberFormatHelper.parseDecimal(state.kcal100g) ?: 0.0
        val p = NumberFormatHelper.parseDecimal(state.protein100g) ?: 0.0
        val c = NumberFormatHelper.parseDecimal(state.carbs100g) ?: 0.0
        val f = NumberFormatHelper.parseDecimal(state.fat100g) ?: 0.0

        val tempProduct = Product(
            name = state.name,
            kcal100g = kcal,
            protein100g = p,
            carbs100g = c,
            fat100g = f,
            source = state.source
        )
        val portion = NutritionCalculator.calculatePortionMacros(tempProduct, grams)
        val updated = state.copy(
            portionKcal = portion.kcal,
            portionProtein = portion.protein,
            portionCarbs = portion.carbs,
            portionFat = portion.fat,
        )
        return revalidateOcr(updated)
    }

    fun onDiscard(onFinish: () -> Unit) {
        _uiState.value.photoFile?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        onFinish()
    }

    fun onConfirmAndSave(onSuccess: (com.controlcal.core.nutrition.MealEntrySaveResult?) -> Unit = {}) {
        val state = _uiState.value
        if (state.isSaving) return
        val name = state.name.trim()
        val kcal = NumberFormatHelper.parseDecimal(state.kcal100g)
        val protein = NumberFormatHelper.parseDecimal(state.protein100g)
        val carbs = NumberFormatHelper.parseDecimal(state.carbs100g)
        val fat = NumberFormatHelper.parseDecimal(state.fat100g)

        if (name.isEmpty() || kcal == null || kcal < 0 ||
            protein == null || protein < 0 ||
            carbs == null || carbs < 0 ||
            fat == null || fat < 0
        ) {
            // Highlight validation errors on mandatory fields
            _uiState.update {
                it.copy(
                    nameError = if (name.isEmpty()) "Campo obligatorio" else it.nameError,
                    kcalError = if (kcal == null || kcal < 0) "Campo obligatorio" else it.kcalError,
                    proteinError = if (protein == null || protein < 0) "Campo obligatorio" else it.proteinError,
                    carbsError = if (carbs == null || carbs < 0) "Campo obligatorio" else it.carbsError,
                    fatError = if (fat == null || fat < 0) "Campo obligatorio" else it.fatError,
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val now = System.currentTimeMillis()
                val product = Product(
                    id = 0L,
                    barcode = state.barcode.trim().takeIf { it.isNotEmpty() },
                    name = name,
                    brand = state.brand.trim().takeIf { it.isNotEmpty() },
                    kcal100g = kcal,
                    protein100g = protein,
                    carbs100g = carbs,
                    fat100g = fat,
                    fiber100g = NumberFormatHelper.parseDecimal(state.fiber100g),
                    sugars100g = NumberFormatHelper.parseDecimal(state.sugars100g),
                    satFat100g = NumberFormatHelper.parseDecimal(state.satFat100g),
                    salt100g = NumberFormatHelper.parseDecimal(state.salt100g),
                    isLiquid = state.isLiquid,
                    unitName = state.unitName.trim().takeIf { it.isNotEmpty() },
                    unitG = NumberFormatHelper.parseDecimal(state.unitG),
                    servingG = NumberFormatHelper.parseDecimal(state.servingG),
                    source = state.source,
                    createdAt = now,
                    updatedAt = now,
                )

                val insertedId = productRepository.insert(product)

                val saveResult = if (state.addToDiary) {
                    val grams = NumberFormatHelper.parseDecimal(state.diaryGrams) ?: 100.0
                    val portion = NutritionCalculator.calculatePortionMacros(product, grams)
                    val targetDate = state.targetDate
                    val existingEntries = mealEntryRepository.getByDateAndMeal(targetDate.toString(), state.selectedMeal)
                    val configuredStartTime = mealConfigRepository.getMealConfigs().find { it.id == state.selectedMeal }?.startTime
                    val loggedAt = com.controlcal.core.nutrition.MealTimeCalculator.calculateLoggedAt(
                        date = targetDate,
                        existingEntries = existingEntries,
                        configuredStartTime = configuredStartTime,
                        zoneId = clock.zone,
                        clock = clock,
                    )
                    val entry = MealEntry(
                        id = 0L,
                        date = targetDate.toString(),
                        meal = state.selectedMeal,
                        productId = insertedId,
                        productName = product.name,
                        grams = grams,
                        kcal = portion.kcal,
                        protein = portion.protein,
                        carbs = portion.carbs,
                        fat = portion.fat,
                        loggedAt = loggedAt,
                    )
                    mealEntryRepository.save(entry)
                } else null

                // Delete temporary photo file after successful save
                state.photoFile?.let { file ->
                    if (file.exists()) {
                        file.delete()
                    }
                }

                _uiState.update { it.copy(isSaving = false, isSaved = true) }
                onSuccess(saveResult)
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
