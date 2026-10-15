package com.controlcal.app.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.Product
import com.controlcal.core.nutrition.FoodPortionFormatter
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.nutrition.NutritionCalculator
import com.controlcal.core.repo.MealConfigRepository
import com.controlcal.core.repo.MealEntryRepository
import com.controlcal.core.repo.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AddEntryViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val mealEntryRepository: MealEntryRepository,
    private val mealConfigRepository: MealConfigRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEntryUiState())
    val uiState: StateFlow<AddEntryUiState> = _uiState.asStateFlow()

    fun initialize(date: LocalDate, mealId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    targetDate = date,
                    targetMealId = mealId,
                    searchQuery = "",
                    selectedProductForPortion = null,
                    isAddedSuccessfully = false,
                    userMessage = null,
                    isLoading = true,
                )
            }

            val configs = mealConfigRepository.getMealConfigs()
            val mealName = configs.find { it.id == mealId }?.name ?: mealId

            loadHabitualFoods(mealId)
            loadSearchResults("")

            _uiState.update {
                it.copy(
                    targetMealName = mealName,
                    isLoading = false,
                )
            }
        }
    }

    private suspend fun loadHabitualFoods(mealId: String) {
        val sixtyDaysAgo = clock.millis() - (60L * 24L * 60L * 60L * 1000L)
        val history = mealEntryRepository.getSince(sixtyDaysAgo)
        val activeProducts = productRepository.getActiveProducts().associateBy { it.id }

        // Filter by meal first, falling back to any meal if empty (excluding quick calories with null productId)
        val mealEntries = history.filter { it.meal == mealId && it.productId != null && it.grams > 0.0 }
        val sourceEntries = mealEntries.ifEmpty {
            history.filter { it.productId != null && it.grams > 0.0 }
        }

        val grouped = sourceEntries.groupBy { it.productId }
        val rankedHabituals = grouped.mapNotNull { (productId, entries) ->
            if (productId == null) return@mapNotNull null
            val product = activeProducts[productId] ?: return@mapNotNull null
            // Mode of grams
            val mostFrequentGrams = entries
                .groupBy { it.grams }
                .maxByOrNull { it.value.size }
                ?.key ?: product.servingG ?: 100.0

            val macros = NutritionCalculator.calculatePortionMacros(product, mostFrequentGrams)
            HabitualFoodUiModel(
                product = product,
                defaultGrams = mostFrequentGrams,
                formattedQuantity = FoodPortionFormatter.formatQuantity(
                    grams = mostFrequentGrams,
                    unitG = product.unitG,
                    unitName = product.unitName,
                ),
                formattedKcal = "${NumberFormatHelper.formatDecimal(macros.kcal)} kcal",
            )
        }.take(6)

        _uiState.update { it.copy(habitualFoods = rankedHabituals) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        viewModelScope.launch {
            loadSearchResults(query)
        }
    }

    private suspend fun loadSearchResults(query: String) {
        val trimmed = query.trim()
        val results = if (trimmed.isEmpty()) {
            productRepository.getActiveProducts()
        } else {
            productRepository.searchByName(trimmed)
        }
        _uiState.update { it.copy(searchResults = results) }
    }

    fun onSelectProductForPortion(product: Product, initialGrams: Double? = null) {
        val unitG = product.unitG
        val defaultG = initialGrams ?: product.servingG ?: unitG ?: 100.0
        val hasUnit = unitG != null && unitG > 0.0
        val isUnitsMode = hasUnit

        val initialValueText = if (isUnitsMode && hasUnit) {
            val units = NutritionCalculator.gramsToUnits(defaultG, unitG)
            NumberFormatHelper.formatDecimal(units)
        } else {
            NumberFormatHelper.formatDecimal(defaultG)
        }

        val portion = NutritionCalculator.calculatePortionMacros(product, defaultG)

        _uiState.update {
            it.copy(
                selectedProductForPortion = product,
                isMeasuringInUnits = isUnitsMode,
                inputQuantityText = initialValueText,
                inputQuantityError = null,
                portionPreviewKcal = portion.kcal,
                portionPreviewProtein = portion.protein,
                portionPreviewCarbs = portion.carbs,
                portionPreviewFat = portion.fat,
            )
        }
    }

    fun onToggleMeasuringUnit() {
        val state = _uiState.value
        val product = state.selectedProductForPortion ?: return
        val unitG = product.unitG ?: return
        if (unitG <= 0.0) return

        val newMeasuringInUnits = !state.isMeasuringInUnits
        val currentGrams = resolveGrams(state.inputQuantityText, state.isMeasuringInUnits, unitG) ?: 100.0

        val newText = if (newMeasuringInUnits) {
            val units = NutritionCalculator.gramsToUnits(currentGrams, unitG)
            NumberFormatHelper.formatDecimal(units)
        } else {
            NumberFormatHelper.formatDecimal(currentGrams)
        }

        val portion = NutritionCalculator.calculatePortionMacros(product, currentGrams)

        _uiState.update {
            it.copy(
                isMeasuringInUnits = newMeasuringInUnits,
                inputQuantityText = newText,
                inputQuantityError = null,
                portionPreviewKcal = portion.kcal,
                portionPreviewProtein = portion.protein,
                portionPreviewCarbs = portion.carbs,
                portionPreviewFat = portion.fat,
            )
        }
    }

    fun onQuantityInputChanged(text: String) {
        val parsed = NumberFormatHelper.parseDecimal(text)
        val error = when {
            text.isBlank() -> "Campo obligatorio"
            parsed == null -> "Número no válido"
            parsed <= 0.0 -> "Debe ser mayor que 0"
            else -> null
        }

        val state = _uiState.value
        val product = state.selectedProductForPortion
        val grams = if (product != null && parsed != null && parsed > 0.0) {
            resolveGrams(text, state.isMeasuringInUnits, product.unitG) ?: 0.0
        } else 0.0

        val portion = if (product != null && grams > 0.0) {
            NutritionCalculator.calculatePortionMacros(product, grams)
        } else null

        _uiState.update {
            it.copy(
                inputQuantityText = text,
                inputQuantityError = error,
                portionPreviewKcal = portion?.kcal ?: 0.0,
                portionPreviewProtein = portion?.protein ?: 0.0,
                portionPreviewCarbs = portion?.carbs ?: 0.0,
                portionPreviewFat = portion?.fat ?: 0.0,
            )
        }
    }

    private fun resolveGrams(text: String, inUnits: Boolean, unitG: Double?): Double? {
        val parsed = NumberFormatHelper.parseDecimal(text) ?: return null
        if (inUnits && unitG != null && unitG > 0.0) {
            return NutritionCalculator.unitsToGrams(parsed, unitG)
        }
        return parsed
    }

    fun onConfirmAddProduct(onSuccess: () -> Unit = {}) {
        val state = _uiState.value
        val product = state.selectedProductForPortion ?: return
        val grams = resolveGrams(state.inputQuantityText, state.isMeasuringInUnits, product.unitG)

        if (grams == null || grams <= 0.0) {
            _uiState.update { it.copy(inputQuantityError = "Introduce una cantidad válida") }
            return
        }

        viewModelScope.launch {
            saveEntry(product, grams)
            _uiState.update {
                it.copy(
                    selectedProductForPortion = null,
                    isAddedSuccessfully = true,
                )
            }
            onSuccess()
        }
    }

    fun onQuickAddHabitual(habitual: HabitualFoodUiModel, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            saveEntry(habitual.product, habitual.defaultGrams)
            _uiState.update {
                it.copy(
                    isAddedSuccessfully = true,
                )
            }
            onSuccess()
        }
    }

    private suspend fun saveEntry(product: Product, grams: Double) {
        val state = _uiState.value
        val portion = NutritionCalculator.calculatePortionMacros(product, grams)
        val existingEntries = mealEntryRepository.getByDateAndMeal(state.targetDate.toString(), state.targetMealId)
        val configuredStartTime = mealConfigRepository.getMealConfigs().find { it.id == state.targetMealId }?.startTime
        val loggedAt = com.controlcal.core.nutrition.MealTimeCalculator.calculateLoggedAt(
            date = state.targetDate,
            existingEntries = existingEntries,
            configuredStartTime = configuredStartTime,
            zoneId = clock.zone,
            clock = clock,
        )

        val entry = MealEntry(
            id = 0L,
            date = state.targetDate.toString(),
            meal = state.targetMealId,
            productId = product.id,
            productName = product.name,
            grams = grams,
            kcal = portion.kcal,
            protein = portion.protein,
            carbs = portion.carbs,
            fat = portion.fat,
            loggedAt = loggedAt,
        )

        mealEntryRepository.insert(entry)
    }

    fun onDismissPortionDialog() {
        _uiState.update { it.copy(selectedProductForPortion = null) }
    }

    fun onMessageConsumed() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
