package com.controlcal.app.ui.weight

import com.controlcal.core.model.WeightEntry
import com.controlcal.core.tdee.WeightTrendPoint
import com.controlcal.core.weight.WeightPeriodSummary
import com.controlcal.core.weight.WeightPlotPoint
import com.controlcal.core.weight.WeightRange

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
    val selectedRange: WeightRange = WeightRange.LAST_1_MONTH,
    val chartPoints: List<WeightTrendPoint> = emptyList(),
    val plotPoints: List<WeightPlotPoint> = emptyList(),
    val periodSummary: WeightPeriodSummary? = null,
    val isWeeklyAggregated: Boolean = false,
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
