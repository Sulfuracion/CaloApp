package com.controlcal.app.ui.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlcal.app.R
import com.controlcal.app.data.off.OffProduct
import com.controlcal.app.ui.feedback.UiText
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class ProductConfirmationViewModel(
    private val productRepository: ProductRepository,
    private val mealEntryRepository: MealEntryRepository,
    private val mealConfigRepository: MealConfigRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
) : ViewModel() {

    @Inject
    constructor(
        productRepository: ProductRepository,
        mealEntryRepository: MealEntryRepository,
        mealConfigRepository: MealConfigRepository,
        clock: Clock,
    ) : this(
        productRepository = productRepository,
        mealEntryRepository = mealEntryRepository,
        mealConfigRepository = mealConfigRepository,
        clock = clock,
        ioDispatcher = Dispatchers.IO,
    )

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
                addToDiary = targetMeal != null,
            )
            recalculatePortion(state)
        }
    }

    fun initializeForManualEntry(
        barcode: String? = null,
        targetDate: LocalDate? = null,
        targetMeal: String? = null,
    ) {
        val activeMeals = _uiState.value.availableMeals
        val selected = targetMeal ?: _uiState.value.selectedMeal
        val date = targetDate ?: LocalDate.now(clock)
        _uiState.update {
            val state = toManualConfirmationUiState(barcode, LocalTime.now(clock).hour).copy(
                availableMeals = activeMeals,
                selectedMeal = selected,
                targetDate = date,
                addToDiary = targetMeal != null,
            )
            recalculatePortion(state)
        }
    }

    fun initializeForEdit(product: Product) {
        val activeMeals = _uiState.value.availableMeals
        _uiState.update {
            val state = product.toEditConfirmationUiState().copy(
                availableMeals = activeMeals,
                selectedMeal = _uiState.value.selectedMeal,
                targetDate = LocalDate.now(clock),
            )
            recalculatePortion(state)
        }
    }

    fun onBarcodeChanged(value: String) {
        _uiState.update { it.copy(barcode = value, barcodeError = null, saveError = null) }
    }

    fun onNameChanged(value: String) {
        _uiState.update { current ->
            val error = if (value.isBlank()) UiText.StringResource(R.string.confirm_product_field_required) else null
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
                value.isBlank() -> UiText.StringResource(R.string.confirm_product_field_required)
                parsed == null -> UiText.StringResource(R.string.confirm_product_field_invalid_number)
                parsed < 0 -> UiText.StringResource(R.string.confirm_product_field_non_negative)
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
                value.isBlank() -> UiText.StringResource(R.string.confirm_product_field_required)
                parsed == null -> UiText.StringResource(R.string.confirm_product_field_invalid_number)
                parsed < 0 -> UiText.StringResource(R.string.confirm_product_field_non_negative)
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
                value.isBlank() -> UiText.StringResource(R.string.confirm_product_field_required)
                parsed == null -> UiText.StringResource(R.string.confirm_product_field_invalid_number)
                parsed < 0 -> UiText.StringResource(R.string.confirm_product_field_non_negative)
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
                value.isBlank() -> UiText.StringResource(R.string.confirm_product_field_required)
                parsed == null -> UiText.StringResource(R.string.confirm_product_field_invalid_number)
                parsed < 0 -> UiText.StringResource(R.string.confirm_product_field_non_negative)
                else -> null
            }
            val updated = current.copy(fat100g = value, fatError = error)
            recalculatePortion(updated)
        }
    }

    fun onFiberChanged(value: String) {
        _uiState.update { current ->
            val parsed = NumberFormatHelper.parseDecimal(value)
            val error = if (value.isNotBlank() && (parsed == null || parsed < 0)) {
                UiText.StringResource(R.string.confirm_product_field_invalid_number)
            } else null
            val updated = current.copy(fiber100g = value, fiberError = error)
            revalidateOcr(updated)
        }
    }

    fun onSugarsChanged(value: String) {
        _uiState.update { current ->
            val parsed = NumberFormatHelper.parseDecimal(value)
            val error = if (value.isNotBlank() && (parsed == null || parsed < 0)) {
                UiText.StringResource(R.string.confirm_product_field_invalid_number)
            } else null
            val updated = current.copy(sugars100g = value, sugarsError = error)
            revalidateOcr(updated)
        }
    }

    fun onSatFatChanged(value: String) {
        _uiState.update { current ->
            val parsed = NumberFormatHelper.parseDecimal(value)
            val error = if (value.isNotBlank() && (parsed == null || parsed < 0)) {
                UiText.StringResource(R.string.confirm_product_field_invalid_number)
            } else null
            val updated = current.copy(satFat100g = value, satFatError = error)
            revalidateOcr(updated)
        }
    }

    fun onSaltChanged(value: String) {
        _uiState.update { current ->
            val parsed = NumberFormatHelper.parseDecimal(value)
            val error = if (value.isNotBlank() && (parsed == null || parsed < 0)) {
                UiText.StringResource(R.string.confirm_product_field_invalid_number)
            } else null
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
            val error = if (value.isNotBlank() && (parsed == null || parsed <= 0)) {
                UiText.StringResource(R.string.confirm_product_field_invalid_number)
            } else null
            current.copy(servingG = value, servingError = error)
        }
    }

    fun onUnitNameChanged(value: String) {
        _uiState.update { it.copy(unitName = value) }
    }

    fun onUnitGChanged(value: String) {
        _uiState.update { current ->
            val parsed = NumberFormatHelper.parseDecimal(value)
            val error = if (value.isNotBlank() && (parsed == null || parsed <= 0)) {
                UiText.StringResource(R.string.confirm_product_field_invalid_number)
            } else null
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
                value.isBlank() -> UiText.StringResource(R.string.confirm_product_field_required)
                parsed == null -> UiText.StringResource(R.string.confirm_product_field_invalid_number)
                parsed <= 0 -> UiText.StringResource(R.string.confirm_product_field_positive)
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

    fun cleanupTempPhoto() {
        val file = _uiState.value.photoFile
        _uiState.update { it.copy(photoFile = null) }
        if (file != null) {
            viewModelScope.launch(ioDispatcher) {
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    fun onDiscard(onFinish: () -> Unit) {
        cleanupTempPhoto()
        onFinish()
    }

    fun onDeleteOrArchiveProduct(onCompleted: (archived: Boolean, productName: String) -> Unit = { _, _ -> }) {
        val state = _uiState.value
        val productId = state.editingProductId ?: return
        if (state.isSaving) return
        val productName = state.name.trim().ifEmpty { "Producto" }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null) }
            try {
                val hasEntries = productRepository.hasEntries(productId)
                if (hasEntries) {
                    productRepository.archive(productId)
                } else {
                    productRepository.delete(productId)
                }
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
                onCompleted(hasEntries, productName)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = UiText.StringResource(R.string.feedback_save_error),
                    )
                }
            }
        }
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
                    nameError = if (name.isEmpty()) UiText.StringResource(R.string.confirm_product_field_required) else it.nameError,
                    kcalError = if (kcal == null || kcal < 0) UiText.StringResource(R.string.confirm_product_field_required) else it.kcalError,
                    proteinError = if (protein == null || protein < 0) UiText.StringResource(R.string.confirm_product_field_required) else it.proteinError,
                    carbsError = if (carbs == null || carbs < 0) UiText.StringResource(R.string.confirm_product_field_required) else it.carbsError,
                    fatError = if (fat == null || fat < 0) UiText.StringResource(R.string.confirm_product_field_required) else it.fatError,
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, barcodeError = null, saveError = null) }
            try {
                val normalizedBarcode = state.barcode.trim().takeIf { it.isNotEmpty() }
                val existingByBarcode = if (normalizedBarcode != null) {
                    productRepository.getByBarcode(normalizedBarcode)
                } else null
                if (existingByBarcode != null && existingByBarcode.id != state.editingProductId) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            barcodeError = UiText.StringResource(R.string.confirm_product_barcode_duplicate_error),
                        )
                    }
                    return@launch
                }

                val now = System.currentTimeMillis()
                val product = Product(
                    id = state.editingProductId ?: 0L,
                    barcode = normalizedBarcode,
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
                    inPantry = state.inPantry,
                    minG = state.minG,
                    maxG = state.maxG,
                    unitName = state.unitName.trim().takeIf { it.isNotEmpty() },
                    unitG = NumberFormatHelper.parseDecimal(state.unitG),
                    servingG = NumberFormatHelper.parseDecimal(state.servingG),
                    source = state.source,
                    createdAt = state.createdAt ?: now,
                    updatedAt = now,
                )

                val savedProductId = if (state.editingProductId != null) {
                    productRepository.update(product)
                    state.editingProductId
                } else {
                    productRepository.insert(product)
                }

                val saveResult = if (state.addToDiary && state.editingProductId == null) {
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
                        productId = savedProductId,
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
                cleanupTempPhoto()

                _uiState.update { it.copy(isSaving = false, isSaved = true) }
                onSuccess(saveResult)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = UiText.StringResource(R.string.feedback_save_error),
                    )
                }
            }
        }
    }
}
