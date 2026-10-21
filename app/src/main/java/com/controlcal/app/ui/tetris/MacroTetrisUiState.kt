package com.controlcal.app.ui.tetris

import com.controlcal.core.model.ConfiguredMeal
import com.controlcal.core.model.DayRemaining
import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.MacroTargets
import com.controlcal.core.model.Product
import com.controlcal.core.solver.MacroSolution
import java.time.LocalDate

data class MacroTetrisUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val isLoading: Boolean = false,
    val todayRemaining: DayRemaining? = null,
    val targetMacros: MacroTargets = MacroTargets(0.0, 0.0, 0.0, 0.0),
    val pantryProducts: List<Product> = emptyList(),
    val excludedProductIds: Set<Long> = emptySet(),
    val solution: MacroSolution? = null,
    val hasCalculated: Boolean = false,
    val availableMeals: List<ConfiguredMeal> = emptyList(),
    val selectedMeal: String = DefaultMealIds.LUNCH,
    val isAddingToDiary: Boolean = false,
    val message: String? = null,
    val isSaved: Boolean = false,
) {
    val excludedProducts: List<Product>
        get() = pantryProducts.filter { it.id in excludedProductIds }

    val areTargetsMet: Boolean
        get() = targetMacros.kcal <= 0.0 &&
            targetMacros.protein <= 0.0 &&
            targetMacros.carbs <= 0.0 &&
            targetMacros.fat <= 0.0

    val isPantryEmpty: Boolean
        get() = pantryProducts.none { it.inPantry && !it.archived }
}
