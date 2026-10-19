package com.controlcal.app.ui.weight

import com.controlcal.core.model.WeightEntry
import com.controlcal.core.tdee.WeightTrendPoint

enum class WeightChartFilter {
    DAYS_30,
    DAYS_90,
    ALL
}

data class WeightConflict(
    val isFromEdit: Boolean,
    val oldDate: String? = null,
    val targetDate: String,
    val newWeightKg: Double,
    val existingWeightKg: Double,
)

data class WeightUiState(
    val isLoading: Boolean = false,
    val todayDate: String = "",
    val todayWeightKg: Double? = null,
    val todayInputText: String = "",
    val todayInputError: String? = null,
    val entries: List<WeightEntry> = emptyList(),
    val trendPoints: List<WeightTrendPoint> = emptyList(),
    val selectedFilter: WeightChartFilter = WeightChartFilter.DAYS_30,
    val chartPoints: List<WeightTrendPoint> = emptyList(),
    val editingEntry: WeightEntry? = null,
    val editingWeightText: String = "",
    val editingDateText: String = "",
    val editingError: String? = null,
    val deletingEntry: WeightEntry? = null,
    val isAddingPastEntry: Boolean = false,
    val pastEntryDate: String = "",
    val pastEntryWeightText: String = "",
    val pastEntryError: String? = null,
    val conflictState: WeightConflict? = null,
    val isSaving: Boolean = false,
    val message: String? = null,
)
