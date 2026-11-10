package com.controlcal.app.ui.supplement

import com.controlcal.core.supplement.OverallAdherence
import com.controlcal.core.supplement.ScheduleType
import com.controlcal.core.supplement.Supplement
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class SupplementsUiState(
    val activeSupplements: List<Supplement> = emptyList(),
    val inactiveSupplements: List<Supplement> = emptyList(),
    val adherence: OverallAdherence? = null,
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val editingSupplementId: Long? = null,
    // Form fields
    val name: String = "",
    val nameError: String? = null,
    val doseAmountText: String = "",
    val doseAmountError: String? = null,
    val doseUnit: String = "mg",
    val scheduleType: ScheduleType = ScheduleType.DAILY,
    val selectedWeekdays: Set<DayOfWeek> = emptySet(),
    val weekdaysError: String? = null,
    val intervalDaysText: String = "2",
    val intervalDaysError: String? = null,
    val startDate: LocalDate = LocalDate.now(),
    val times: List<LocalTime> = listOf(LocalTime.of(8, 0)),
    val timesError: String? = null,
    val remindersEnabled: Boolean = true,
    val notes: String = "",
    val showNotificationRationale: Boolean = false,
    val isSaving: Boolean = false,
) {
    companion object {
        val DOSE_UNITS = listOf("mg", "µg", "g", "UI", "cápsulas", "gotas", "ml", "comprimidos")
    }
}
