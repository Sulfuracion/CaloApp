package com.controlcal.app.ui.settings

import com.controlcal.core.model.ConfiguredMeal
import com.controlcal.core.model.WeeklyDayPattern

data class SettingsUiState(
    val weeklyPattern: WeeklyDayPattern = WeeklyDayPattern(),
    val trainingKcalText: String = "",
    val trainingProteinText: String = "",
    val trainingCarbsText: String = "",
    val trainingFatText: String = "",
    val restKcalText: String = "",
    val restProteinText: String = "",
    val restCarbsText: String = "",
    val restFatText: String = "",
    val minKcalText: String = "1200",
    val minKcalError: String? = null,
    val trainingKcalError: String? = null,
    val trainingProteinError: String? = null,
    val trainingCarbsError: String? = null,
    val trainingFatError: String? = null,
    val restKcalError: String? = null,
    val restProteinError: String? = null,
    val restCarbsError: String? = null,
    val restFatError: String? = null,
    val isSavingGoals: Boolean = false,
    val isSavingMeal: Boolean = false,
    val configuredMeals: List<ConfiguredMeal> = emptyList(),
    val showAddMealDialog: Boolean = false,
    val editingMeal: ConfiguredMeal? = null,
    val changingTimeMeal: ConfiguredMeal? = null,
    val deactivateOfferMeal: ConfiguredMeal? = null,
    val deleteConfirmMeal: ConfiguredMeal? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isLoading: Boolean = false,
)
