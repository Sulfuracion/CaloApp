package com.controlcal.app.ui.supplement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlcal.app.R
import com.controlcal.app.reminder.SupplementNotificationHelper
import com.controlcal.app.reminder.SupplementReminderScheduler
import com.controlcal.app.ui.feedback.UiFeedbackEvent
import com.controlcal.app.ui.feedback.UiText
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.repo.SupplementRepository
import com.controlcal.core.supplement.ScheduleType
import com.controlcal.core.supplement.Supplement
import com.controlcal.core.supplement.SupplementAdherenceCalculator
import com.controlcal.core.supplement.SupplementScheduleCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class SupplementsViewModel @Inject constructor(
    private val supplementRepository: SupplementRepository,
    private val reminderScheduler: SupplementReminderScheduler,
    private val notificationHelper: SupplementNotificationHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SupplementsUiState(isLoading = true))
    val uiState: StateFlow<SupplementsUiState> = _uiState.asStateFlow()

    private val _feedbackEvents = Channel<UiFeedbackEvent>(Channel.BUFFERED)
    val feedbackEvents: Flow<UiFeedbackEvent> = _feedbackEvents.receiveAsFlow()

    init {
        loadSupplements()
    }

    fun loadSupplements() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val supplements = supplementRepository.getAllSupplements()
            val active = supplements.filter { it.active }
            val inactive = supplements.filter { !it.active }

            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(SupplementAdherenceCalculator.DEFAULT_DAYS_WINDOW.toLong() - 1L)
            val intakes = supplementRepository.getIntakesBetween(startDate, endDate)
            val adherence = SupplementAdherenceCalculator.calculateAdherence(
                supplements = supplements,
                intakes = intakes,
                startDate = startDate,
                endDate = endDate,
            )

            _uiState.update {
                it.copy(
                    activeSupplements = active,
                    inactiveSupplements = inactive,
                    adherence = adherence,
                    isLoading = false,
                )
            }
        }
    }

    fun onAddSupplementClick() {
        _uiState.update {
            it.copy(
                isEditing = true,
                editingSupplementId = null,
                name = "",
                nameError = null,
                doseAmountText = "",
                doseAmountError = null,
                doseUnit = "mg",
                scheduleType = ScheduleType.DAILY,
                selectedWeekdays = emptySet(),
                weekdaysError = null,
                intervalDaysText = "2",
                intervalDaysError = null,
                startDate = LocalDate.now(),
                times = listOf(LocalTime.of(8, 0)),
                timesError = null,
                remindersEnabled = true,
                notes = "",
                showNotificationRationale = false,
                isSaving = false,
            )
        }
    }

    fun onEditSupplementClick(supplement: Supplement) {
        val weekdaysSet = mutableSetOf<DayOfWeek>()
        supplement.weekdays?.let { mask ->
            for (day in DayOfWeek.entries) {
                if (SupplementScheduleCalculator.isDaySelectedInMask(mask, day)) {
                    weekdaysSet.add(day)
                }
            }
        }

        _uiState.update {
            it.copy(
                isEditing = true,
                editingSupplementId = supplement.id,
                name = supplement.name,
                nameError = null,
                doseAmountText = NumberFormatHelper.formatDecimal(supplement.doseAmount),
                doseAmountError = null,
                doseUnit = supplement.doseUnit,
                scheduleType = supplement.scheduleType,
                selectedWeekdays = weekdaysSet,
                weekdaysError = null,
                intervalDaysText = supplement.intervalDays?.toString() ?: "2",
                intervalDaysError = null,
                startDate = supplement.startDate,
                times = supplement.times.ifEmpty { listOf(LocalTime.of(8, 0)) },
                timesError = null,
                remindersEnabled = supplement.remindersEnabled,
                notes = supplement.notes ?: "",
                showNotificationRationale = false,
                isSaving = false,
            )
        }
    }

    fun onDismissDialog() {
        _uiState.update { it.copy(isEditing = false, editingSupplementId = null) }
    }

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, nameError = null) }
    }

    fun onDoseAmountChanged(doseText: String) {
        _uiState.update { it.copy(doseAmountText = doseText, doseAmountError = null) }
    }

    fun onDoseUnitChanged(unit: String) {
        _uiState.update { it.copy(doseUnit = unit) }
    }

    fun onScheduleTypeChanged(type: ScheduleType) {
        _uiState.update {
            it.copy(
                scheduleType = type,
                weekdaysError = null,
                intervalDaysError = null,
            )
        }
    }

    fun onToggleWeekday(day: DayOfWeek) {
        _uiState.update { state ->
            val updated = if (state.selectedWeekdays.contains(day)) {
                state.selectedWeekdays - day
            } else {
                state.selectedWeekdays + day
            }
            state.copy(selectedWeekdays = updated, weekdaysError = null)
        }
    }

    fun onIntervalDaysChanged(intervalText: String) {
        _uiState.update { it.copy(intervalDaysText = intervalText, intervalDaysError = null) }
    }

    fun onStartDateChanged(date: LocalDate) {
        _uiState.update { it.copy(startDate = date) }
    }

    fun onAddTime(time: LocalTime) {
        _uiState.update { state ->
            if (!state.times.contains(time)) {
                state.copy(times = (state.times + time).sorted(), timesError = null)
            } else {
                state
            }
        }
    }

    fun onRemoveTime(time: LocalTime) {
        _uiState.update { state ->
            val updated = state.times.filter { it != time }
            state.copy(times = updated)
        }
    }

    fun onToggleReminders(enabled: Boolean) {
        if (enabled && !notificationHelper.hasNotificationPermission()) {
            _uiState.update { it.copy(remindersEnabled = true, showNotificationRationale = true) }
        } else {
            _uiState.update { it.copy(remindersEnabled = enabled, showNotificationRationale = false) }
        }
    }

    fun onDismissRationale() {
        _uiState.update { it.copy(showNotificationRationale = false) }
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun onSaveSupplement() {
        val state = _uiState.value
        val nameTrimmed = state.name.trim()
        val doseParsed = NumberFormatHelper.parseDecimal(state.doseAmountText)
        val intervalParsed = state.intervalDaysText.trim().toIntOrNull()

        var hasError = false
        var nameErr: String? = null
        var doseErr: String? = null
        var weekdaysErr: String? = null
        var intervalErr: String? = null
        var timesErr: String? = null

        if (nameTrimmed.isEmpty()) {
            nameErr = "Introduce el nombre del suplemento"
            hasError = true
        }

        if (doseParsed == null || doseParsed <= 0.0) {
            doseErr = "Introduce una dosis mayor que 0"
            hasError = true
        }

        if (state.times.isEmpty()) {
            timesErr = "Añade al menos una hora de toma"
            hasError = true
        }

        if (state.scheduleType == ScheduleType.WEEKDAYS && state.selectedWeekdays.isEmpty()) {
            weekdaysErr = "Selecciona al menos un día"
            hasError = true
        }

        if (state.scheduleType == ScheduleType.EVERY_N_DAYS && (intervalParsed == null || intervalParsed <= 0)) {
            intervalErr = "Introduce un intervalo de días válido (mínimo 1)"
            hasError = true
        }

        if (hasError) {
            _uiState.update {
                it.copy(
                    nameError = nameErr,
                    doseAmountError = doseErr,
                    weekdaysError = weekdaysErr,
                    intervalDaysError = intervalErr,
                    timesError = timesErr,
                )
            }
            return
        }

        val weekdaysMask = if (state.scheduleType == ScheduleType.WEEKDAYS) {
            SupplementScheduleCalculator.createWeekdaysMask(state.selectedWeekdays)
        } else {
            null
        }

        val supplement = Supplement(
            id = state.editingSupplementId ?: 0L,
            name = nameTrimmed,
            doseAmount = doseParsed!!,
            doseUnit = state.doseUnit,
            scheduleType = state.scheduleType,
            weekdays = weekdaysMask,
            intervalDays = if (state.scheduleType == ScheduleType.EVERY_N_DAYS) intervalParsed else null,
            startDate = state.startDate,
            times = state.times.sorted(),
            active = true,
            remindersEnabled = state.remindersEnabled,
            notes = state.notes.trim().ifEmpty { null },
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val id = if (state.editingSupplementId == null) {
                    val newId = supplementRepository.saveSupplement(supplement)
                    reminderScheduler.scheduleNextForSupplement(newId)
                    newId
                } else {
                    supplementRepository.updateSupplement(supplement)
                    reminderScheduler.scheduleNextForSupplement(supplement.id)
                    supplement.id
                }

                _uiState.update { it.copy(isEditing = false, editingSupplementId = null, isSaving = false) }
                loadSupplements()

                val feedback = if (state.editingSupplementId == null) {
                    UiFeedbackEvent.Success(UiText.StringResource(R.string.supplement_saved_feedback, supplement.name))
                } else {
                    UiFeedbackEvent.Success(UiText.StringResource(R.string.supplement_updated_feedback, supplement.name))
                }
                _feedbackEvents.send(feedback)
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _feedbackEvents.send(UiFeedbackEvent.Error(UiText.DynamicString("Error al guardar el suplemento")))
            }
        }
    }

    fun onToggleActive(supplement: Supplement) {
        viewModelScope.launch {
            val newActive = !supplement.active
            supplementRepository.setSupplementActive(supplement.id, newActive)
            if (newActive) {
                reminderScheduler.scheduleNextForSupplement(supplement.id)
                _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(R.string.supplement_reactivated_feedback)))
            } else {
                reminderScheduler.cancelSupplementAlarm(supplement.id)
                reminderScheduler.cancelSecondWarning(supplement.id)
                _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(R.string.supplement_deactivated_feedback)))
            }
            loadSupplements()
        }
    }
}
