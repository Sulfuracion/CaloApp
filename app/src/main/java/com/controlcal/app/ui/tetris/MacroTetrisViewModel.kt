package com.controlcal.app.ui.tetris

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.MacroTargets
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.toMacroTargets
import com.controlcal.core.nutrition.CalculateDayRemainingUseCase
import com.controlcal.core.nutrition.GetActiveGoalProfileUseCase
import com.controlcal.core.nutrition.MealClassifier
import com.controlcal.core.nutrition.NutritionCalculator
import com.controlcal.core.nutrition.ResolveDayTypeUseCase
import com.controlcal.core.repo.MealConfigRepository
import com.controlcal.core.repo.MealEntryRepository
import com.controlcal.core.repo.ProductRepository
import com.controlcal.core.solver.FoodSelector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class MacroTetrisViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val mealEntryRepository: MealEntryRepository,
    private val resolveDayTypeUseCase: ResolveDayTypeUseCase,
    private val getActiveGoalProfileUseCase: GetActiveGoalProfileUseCase,
    private val mealConfigRepository: MealConfigRepository,
    private val calculateDayRemainingUseCase: CalculateDayRemainingUseCase = CalculateDayRemainingUseCase(),
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(MacroTetrisUiState())
    val uiState: StateFlow<MacroTetrisUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData(date: LocalDate = LocalDate.now(clock)) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isSaved = false) }

            val dateStr = date.toString()
            val entries = mealEntryRepository.getByDate(dateStr)
            val totals = NutritionCalculator.calculateDayTotals(dateStr, entries)
            val dayType = resolveDayTypeUseCase(date)
            val activeProfile = getActiveGoalProfileUseCase(dayType, dateStr)
            val targets = activeProfile.toMacroTargets()
            val remaining = calculateDayRemainingUseCase(totals.totals, targets)

            val pantryProducts = productRepository.getPantryProducts()

            val targetMacros = MacroTargets(
                kcal = remaining.remainingKcal,
                protein = remaining.remainingProtein,
                carbs = remaining.remainingCarbs,
                fat = remaining.remainingFat,
            )

            val configuredMeals = mealConfigRepository.getMealConfigs()
            val activeMeals = configuredMeals.filter { it.isActive }.sortedBy { it.startTime }
            val defaultMeal = MealClassifier.suggestMeal(activeMeals, LocalTime.now(clock))?.id
                ?: activeMeals.firstOrNull()?.id
                ?: DefaultMealIds.LUNCH

            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    todayRemaining = remaining,
                    targetMacros = targetMacros,
                    pantryProducts = pantryProducts,
                    availableMeals = activeMeals,
                    selectedMeal = defaultMeal,
                )
            }

            calculate()
        }
    }

    fun calculate() {
        val state = _uiState.value
        val availableProducts = state.pantryProducts.filter { it.id !in state.excludedProductIds }
        val solution = FoodSelector.selectAndSolve(
            target = state.targetMacros,
            pantryProducts = availableProducts,
        )
        _uiState.update {
            it.copy(
                solution = solution,
                hasCalculated = true,
            )
        }
    }

    fun excludeProduct(productId: Long) {
        _uiState.update { current ->
            val updated = current.excludedProductIds + productId
            current.copy(excludedProductIds = updated)
        }
        calculate()
    }

    fun includeProduct(productId: Long) {
        _uiState.update { current ->
            val updated = current.excludedProductIds - productId
            current.copy(excludedProductIds = updated)
        }
        calculate()
    }

    fun selectMeal(meal: String) {
        _uiState.update { it.copy(selectedMeal = meal) }
    }

    fun addToDiary(date: LocalDate = LocalDate.now(), onSuccess: (String) -> Unit = {}) {
        val state = _uiState.value
        if (state.isAddingToDiary) return
        val items = state.solution?.items ?: return
        if (items.isEmpty()) return

        val mealName = state.availableMeals.find { it.id == state.selectedMeal }?.name ?: state.selectedMeal

        viewModelScope.launch {
            _uiState.update { it.copy(isAddingToDiary = true) }
            try {
                val dateStr = date.toString()
                val existingEntries = mealEntryRepository.getByDateAndMeal(dateStr, state.selectedMeal)
                val configuredStartTime = mealConfigRepository.getMealConfigs().find { it.id == state.selectedMeal }?.startTime
                val loggedAt = com.controlcal.core.nutrition.MealTimeCalculator.calculateLoggedAt(
                    date = date,
                    existingEntries = existingEntries,
                    configuredStartTime = configuredStartTime,
                    zoneId = clock.zone,
                    clock = clock,
                )

                val mealEntries = items.map { item ->
                    val portion = NutritionCalculator.calculatePortionMacros(item.product, item.roundedGrams)
                    MealEntry(
                        id = 0L,
                        date = dateStr,
                        meal = state.selectedMeal,
                        productId = item.product.id,
                        productName = item.product.name,
                        grams = item.roundedGrams,
                        kcal = portion.kcal,
                        protein = portion.protein,
                        carbs = portion.carbs,
                        fat = portion.fat,
                        loggedAt = loggedAt,
                    )
                }

                mealEntryRepository.saveAll(mealEntries)

                _uiState.update {
                    it.copy(
                        isAddingToDiary = false,
                        isSaved = true,
                    )
                }
                onSuccess(mealName)
            } catch (e: Exception) {
                _uiState.update { it.copy(isAddingToDiary = false) }
            }
        }
    }

    fun onMessageConsumed() {
        _uiState.update { it.copy(message = null) }
    }
}
