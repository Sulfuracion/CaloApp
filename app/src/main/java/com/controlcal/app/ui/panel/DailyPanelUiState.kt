package com.controlcal.app.ui.panel

import com.controlcal.core.model.DayRemaining
import com.controlcal.core.model.DayTotals
import com.controlcal.core.model.DayType
import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.GoalProfile
import com.controlcal.core.model.MealEntry
import com.controlcal.core.nutrition.MealClassifier
import com.controlcal.core.nutrition.NutritionCalculator
import com.controlcal.core.suggest.QuickLogSuggestion
import com.controlcal.core.tdee.TdeeCalculationResult
import com.controlcal.core.tdee.TdeeGoalProposal
import com.controlcal.core.model.ConfiguredMeal
import java.time.LocalDate
import java.time.LocalTime

data class MealSectionUiModel(
    val mealId: String,
    val name: String,
    val startTime: LocalTime?,
    val isInactive: Boolean = false,
    val isUnassigned: Boolean = false,
    val entries: List<MealEntry> = emptyList(),
    val totalKcal: Double = 0.0,
    val summary: String = "",
)

data class DailyPanelUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val dayTotals: DayTotals = NutritionCalculator.calculateDayTotals(LocalDate.now().toString(), emptyList()),
    val entriesByMeal: Map<String, List<MealEntry>> = DefaultMealIds.ALL.associateWith { emptyList() },
    val mealSections: List<MealSectionUiModel> = emptyList(),
    val suggestions: List<QuickLogSuggestion> = emptyList(),
    val currentSlotMeal: String = DefaultMealIds.BREAKFAST,
    val currentSlotMealName: String = "",
    val editingSuggestion: QuickLogSuggestion? = null,
    val editingGramsText: String = "",
    val editingGramsError: String? = null,
    val portionPreviewKcal: Double = 0.0,
    val portionPreviewProtein: Double = 0.0,
    val portionPreviewCarbs: Double = 0.0,
    val portionPreviewFat: Double = 0.0,
    val dayType: DayType = DayType.REST,
    val activeGoalProfile: GoalProfile? = null,
    val dayRemaining: DayRemaining? = null,
    val tdeeProposal: TdeeGoalProposal? = null,
    val isWeeklyProposalVisible: Boolean = false,
    val tdeeCalculationResult: TdeeCalculationResult? = null,
    val isLoading: Boolean = false,
    val recentlyMergedPreviousEntry: MealEntry? = null,
    val isSaving: Boolean = false,
    val message: String? = null,
) {
    val isToday: Boolean
        get() = selectedDate == today
}
