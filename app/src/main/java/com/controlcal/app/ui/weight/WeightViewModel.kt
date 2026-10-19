package com.controlcal.app.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlcal.core.date.DateHelper
import com.controlcal.core.model.WeightEntry
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.repo.WeightEntryRepository
import com.controlcal.core.tdee.WeightTrend
import com.controlcal.core.tdee.WeightTrendPoint
import com.controlcal.app.R
import com.controlcal.app.ui.feedback.UiFeedbackEvent
import com.controlcal.app.ui.feedback.UiText
import com.controlcal.core.weight.WeightValidationResult
import com.controlcal.core.weight.WeightValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlin.math.roundToLong

@HiltViewModel
class WeightViewModel @Inject constructor(
    private val weightRepository: WeightEntryRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeightUiState())
    val uiState: StateFlow<WeightUiState> = _uiState.asStateFlow()

    private val _feedbackEvents = Channel<UiFeedbackEvent>(Channel.BUFFERED)
    val feedbackEvents: Flow<UiFeedbackEvent> = _feedbackEvents.receiveAsFlow()

    init {
        loadEntries()
    }

    fun loadEntries(preserveEmptyInput: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val today = DateHelper.getToday(clock).toString()
            val allAsc = weightRepository.getAll()
            val allDesc = allAsc.reversed()
            val trend = WeightTrend.calculateTrend(allAsc)

            val todayEntry = allAsc.find { it.date == today }
            val currentInput = _uiState.value.todayInputText
            val newInputText = if (preserveEmptyInput) {
                ""
            } else if (currentInput.isNotBlank()) {
                currentInput
            } else if (todayEntry != null) {
                NumberFormatHelper.formatDecimal(todayEntry.weightKg)
            } else if (allDesc.isNotEmpty()) {
                NumberFormatHelper.formatDecimal(allDesc.first().weightKg)
            } else {
                ""
            }

            val currentFilter = _uiState.value.selectedFilter
            val filtered = filterChartPoints(trend, currentFilter, today)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    todayDate = today,
                    todayWeightKg = todayEntry?.weightKg,
                    todayInputText = newInputText,
                    todayInputError = null,
                    entries = allDesc,
                    trendPoints = trend,
                    chartPoints = filtered,
                )
            }
        }
    }

    fun onTodayInputChanged(text: String) {
        val error = when {
            text.isBlank() -> null
            !WeightValidator.isValid(text) -> "Introduce un peso válido (ej. 78,5)"
            else -> null
        }
        _uiState.update {
            it.copy(
                todayInputText = text,
                todayInputError = error,
            )
        }
    }

    fun onQuickAddDelta(deltaKg: Double) {
        val current = NumberFormatHelper.parseDecimal(_uiState.value.todayInputText)
            ?: _uiState.value.todayWeightKg
            ?: 70.0
        val updated = ((current + deltaKg) * 10.0).roundToLong() / 10.0
        if (updated > 0.0) {
            val formatted = NumberFormatHelper.formatDecimal(updated)
            _uiState.update {
                it.copy(
                    todayInputText = formatted,
                    todayInputError = null,
                )
            }
        }
    }

    fun onSaveTodayWeight() {
        val validation = WeightValidator.validate(_uiState.value.todayInputText)
        if (validation !is WeightValidationResult.Valid) {
            _uiState.update { it.copy(todayInputError = "Introduce un peso válido (ej. 78,5)") }
            return
        }

        val weight = validation.weightKg
        val today = _uiState.value.todayDate.ifBlank { DateHelper.getToday(clock).toString() }
        val alreadyExisted = _uiState.value.todayWeightKg != null
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                weightRepository.upsert(WeightEntry(date = today, weightKg = weight))
                val formatted = NumberFormatHelper.formatDecimal(weight)
                val event = if (alreadyExisted) {
                    UiFeedbackEvent.Success(UiText.StringResource(R.string.feedback_weight_updated, formatted))
                } else {
                    UiFeedbackEvent.Success(UiText.StringResource(R.string.feedback_weight_saved, formatted))
                }
                _feedbackEvents.send(event)
                _uiState.update {
                    it.copy(
                        todayInputText = "",
                        todayInputError = null,
                        isSaving = false,
                        message = if (alreadyExisted) "Peso de hoy actualizado" else "Peso guardado correctamente",
                    )
                }
                loadEntries(preserveEmptyInput = true)
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _feedbackEvents.send(UiFeedbackEvent.Error(UiText.StringResource(R.string.feedback_save_error)))
            }
        }
    }

    fun onFilterSelected(filter: WeightChartFilter) {
        val trend = _uiState.value.trendPoints
        val today = _uiState.value.todayDate.ifBlank { LocalDate.now().toString() }
        val filtered = filterChartPoints(trend, filter, today)
        _uiState.update {
            it.copy(
                selectedFilter = filter,
                chartPoints = filtered,
            )
        }
    }

    fun onStartEditEntry(entry: WeightEntry) {
        _uiState.update {
            it.copy(
                editingEntry = entry,
                editingWeightText = NumberFormatHelper.formatDecimal(entry.weightKg),
                editingDateText = entry.date,
                editingError = null,
            )
        }
    }

    fun onEditingWeightChanged(text: String) {
        val error = when {
            text.isBlank() -> null
            !WeightValidator.isValid(text) -> "Introduce un peso válido (ej. 78,5)"
            else -> null
        }
        _uiState.update { it.copy(editingWeightText = text, editingError = error) }
    }

    fun onEditingDateChanged(text: String) {
        _uiState.update { it.copy(editingDateText = text, editingError = null) }
    }

    fun onSaveEditEntry() {
        val oldEntry = _uiState.value.editingEntry ?: return
        val newDate = _uiState.value.editingDateText.trim()
        val validation = WeightValidator.validate(_uiState.value.editingWeightText)

        val parsedDate = try {
            LocalDate.parse(newDate)
        } catch (e: DateTimeParseException) {
            _uiState.update { it.copy(editingError = "Fecha inválida") }
            return
        }

        val today = DateHelper.getToday(clock)
        if (parsedDate.isAfter(today)) {
            _uiState.update { it.copy(editingError = "No se pueden registrar pesajes en fechas futuras") }
            return
        }

        if (validation !is WeightValidationResult.Valid) {
            _uiState.update { it.copy(editingError = "Introduce un peso válido (ej. 78,5)") }
            return
        }
        val newWeight = validation.weightKg

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                if (oldEntry.date != newDate) {
                    val existing = weightRepository.getByDate(newDate)
                    if (existing != null) {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                conflictState = WeightConflict(
                                    isFromEdit = true,
                                    oldDate = oldEntry.date,
                                    targetDate = newDate,
                                    newWeightKg = newWeight,
                                    existingWeightKg = existing.weightKg,
                                )
                            )
                        }
                        return@launch
                    }
                    weightRepository.moveEntry(oldDate = oldEntry.date, newEntry = WeightEntry(date = newDate, weightKg = newWeight))
                } else {
                    weightRepository.upsert(WeightEntry(date = newDate, weightKg = newWeight))
                }

                val formatted = NumberFormatHelper.formatDecimal(newWeight)
                _uiState.update {
                    it.copy(
                        editingEntry = null,
                        editingError = null,
                        isSaving = false,
                        message = "Pesaje actualizado",
                    )
                }
                _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(R.string.feedback_weight_past_updated, newDate, formatted)))
                loadEntries()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _feedbackEvents.send(UiFeedbackEvent.Error(UiText.StringResource(R.string.feedback_save_error)))
            }
        }
    }

    fun onDismissEditDialog() {
        _uiState.update { it.copy(editingEntry = null, editingError = null) }
    }

    fun onStartDeleteEntry(entry: WeightEntry) {
        _uiState.update { it.copy(deletingEntry = entry) }
    }

    fun onConfirmDeleteEntry() {
        val entry = _uiState.value.deletingEntry ?: return
        viewModelScope.launch {
            weightRepository.deleteByDate(entry.date)
            _uiState.update {
                it.copy(
                    deletingEntry = null,
                    message = "Pesaje eliminado",
                )
            }
            _feedbackEvents.send(UiFeedbackEvent.Success(UiText.DynamicString("Pesaje eliminado")))
            loadEntries()
        }
    }

    fun onDismissDeleteDialog() {
        _uiState.update { it.copy(deletingEntry = null) }
    }

    fun onStartAddPastEntry() {
        val defaultPastDate = DateHelper.getToday(clock).minusDays(1).toString()
        _uiState.update {
            it.copy(
                isAddingPastEntry = true,
                pastEntryDate = defaultPastDate,
                pastEntryWeightText = "",
                pastEntryError = null,
            )
        }
    }

    fun onPastEntryDateChanged(date: String) {
        _uiState.update { it.copy(pastEntryDate = date, pastEntryError = null) }
    }

    fun onPastEntryWeightChanged(text: String) {
        val error = when {
            text.isBlank() -> null
            !WeightValidator.isValid(text) -> "Introduce un peso válido (ej. 78,5)"
            else -> null
        }
        _uiState.update { it.copy(pastEntryWeightText = text, pastEntryError = error) }
    }

    fun onSavePastEntry() {
        val dateText = _uiState.value.pastEntryDate.trim()
        val validation = WeightValidator.validate(_uiState.value.pastEntryWeightText)

        val parsedDate = try {
            LocalDate.parse(dateText)
        } catch (e: DateTimeParseException) {
            _uiState.update { it.copy(pastEntryError = "Fecha inválida") }
            return
        }

        val today = DateHelper.getToday(clock)
        if (parsedDate.isAfter(today)) {
            _uiState.update { it.copy(pastEntryError = "No se pueden registrar pesajes en fechas futuras") }
            return
        }

        if (validation !is WeightValidationResult.Valid) {
            _uiState.update { it.copy(pastEntryError = "Introduce un peso válido (ej. 78,5)") }
            return
        }
        val weight = validation.weightKg

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val existing = weightRepository.getByDate(dateText)
                if (existing != null) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            conflictState = WeightConflict(
                                isFromEdit = false,
                                oldDate = null,
                                targetDate = dateText,
                                newWeightKg = weight,
                                existingWeightKg = existing.weightKg,
                            )
                        )
                    }
                    return@launch
                }

                weightRepository.upsert(WeightEntry(date = dateText, weightKg = weight))
                val formatted = NumberFormatHelper.formatDecimal(weight)
                _uiState.update {
                    it.copy(
                        isAddingPastEntry = false,
                        pastEntryError = null,
                        isSaving = false,
                        message = "Peso guardado correctamente",
                    )
                }
                _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(R.string.feedback_weight_past_saved, dateText, formatted)))
                loadEntries()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _feedbackEvents.send(UiFeedbackEvent.Error(UiText.StringResource(R.string.feedback_save_error)))
            }
        }
    }

    fun onDismissAddPastEntry() {
        _uiState.update { it.copy(isAddingPastEntry = false, pastEntryError = null) }
    }

    fun onConfirmConflict() {
        val conflict = _uiState.value.conflictState ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val formatted = NumberFormatHelper.formatDecimal(conflict.newWeightKg)
                if (conflict.isFromEdit && conflict.oldDate != null) {
                    weightRepository.moveEntry(
                        oldDate = conflict.oldDate,
                        newEntry = WeightEntry(date = conflict.targetDate, weightKg = conflict.newWeightKg)
                    )
                    _uiState.update {
                        it.copy(
                            conflictState = null,
                            editingEntry = null,
                            isSaving = false,
                            message = "Pesaje actualizado",
                        )
                    }
                } else {
                    weightRepository.upsert(
                        WeightEntry(date = conflict.targetDate, weightKg = conflict.newWeightKg)
                    )
                    _uiState.update {
                        it.copy(
                            conflictState = null,
                            isAddingPastEntry = false,
                            isSaving = false,
                            message = "Peso guardado correctamente",
                        )
                    }
                }
                _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(R.string.feedback_weight_past_updated, conflict.targetDate, formatted)))
                loadEntries()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _feedbackEvents.send(UiFeedbackEvent.Error(UiText.StringResource(R.string.feedback_save_error)))
            }
        }
    }

    fun onDismissConflict() {
        _uiState.update { it.copy(conflictState = null) }
    }

    fun onMessageConsumed() {
        _uiState.update { it.copy(message = null) }
    }

    private fun filterChartPoints(
        points: List<WeightTrendPoint>,
        filter: WeightChartFilter,
        referenceDateString: String,
    ): List<WeightTrendPoint> {
        if (filter == WeightChartFilter.ALL) return points
        val refDate = try {
            LocalDate.parse(referenceDateString)
        } catch (e: Exception) {
            LocalDate.now()
        }

        val cutoffDate = when (filter) {
            WeightChartFilter.DAYS_30 -> refDate.minusDays(30)
            WeightChartFilter.DAYS_90 -> refDate.minusDays(90)
            WeightChartFilter.ALL -> return points
        }.toString()

        return points.filter { it.date >= cutoffDate }
    }
}
