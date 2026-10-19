package com.controlcal.app.ui.detail

import com.controlcal.core.model.ConfiguredMeal
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.NutrientTotals
import com.controlcal.core.model.Product
import java.time.LocalDate
import java.time.LocalTime

data class MealDetailEntryUiModel(
    val entry: MealEntry,
    val formattedQuantity: String,
    val formattedKcal: String,
    val isTare: Boolean,
    val isQuickCalories: Boolean = entry.productId == null,
    val stepGrams: Double = 10.0,
    val canDecrease: Boolean = false,
)

data class MealDetailUiState(
    val date: LocalDate = LocalDate.now(),
    val currentMealId: String = "",
    val currentMealName: String = "",
    val availableMeals: List<ConfiguredMeal> = emptyList(),
    val entries: List<MealDetailEntryUiModel> = emptyList(),
    val totals: NutrientTotals = NutrientTotals(0.0, 0.0, 0.0, 0.0),
    val mealTime: LocalTime = LocalTime.NOON,
    val formattedMealTime: String = "12:00",
    val showTimePickerDialog: Boolean = false,
    val customMealTime: LocalTime? = null,
    val isLoading: Boolean = false,
    val isDropdownExpanded: Boolean = false,
    val isOptionsMenuExpanded: Boolean = false,
    val showTareDialog: Boolean = false,
    val tareEligibleProducts: List<Product> = emptyList(),
    val selectedTareProduct: Product? = null,
    val tareGramsText: String = "",
    val tareGramsError: String? = null,
    val showQuickCaloriesDialog: Boolean = false,
    val editingQuickCaloriesEntry: MealEntry? = null,
    val quickKcalText: String = "",
    val quickProteinText: String = "",
    val quickCarbsText: String = "",
    val quickFatText: String = "",
    val quickDescriptionText: String = "",
    val quickKcalError: String? = null,
    val quickAtwaterWarning: String? = null,
    val isQuickCaloriesSaveEnabled: Boolean = false,
    val recentlyDeletedEntry: MealEntry? = null,
    val recentlyMergedPreviousEntry: MealEntry? = null,
    val isSaving: Boolean = false,
    val userMessage: String? = null,
)
