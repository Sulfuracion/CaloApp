package com.controlcal.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlcal.core.model.ConfiguredMeal
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.Product
import com.controlcal.core.nutrition.CopyDayUseCase
import com.controlcal.core.nutrition.CreateTareEntryUseCase
import com.controlcal.core.nutrition.FoodPortionFormatter
import com.controlcal.core.nutrition.MealPortionCalculator
import com.controlcal.core.nutrition.MealTimeCalculator
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.nutrition.NutritionCalculator
import com.controlcal.core.nutrition.QuickCaloriesValidator
import com.controlcal.core.nutrition.RepeatMealUseCase
import com.controlcal.app.R
import com.controlcal.app.ui.feedback.UiFeedbackEvent
import com.controlcal.app.ui.feedback.UiText
import com.controlcal.core.repo.MealConfigRepository
import com.controlcal.core.repo.MealEntryRepository
import com.controlcal.core.repo.ProductRepository
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class MealDetailViewModel @Inject constructor(
    private val mealEntryRepository: MealEntryRepository,
    private val mealConfigRepository: MealConfigRepository,
    private val productRepository: ProductRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val repeatMealUseCase = RepeatMealUseCase()
    private val copyDayUseCase = CopyDayUseCase()
    private val createTareEntryUseCase = CreateTareEntryUseCase()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private val _uiState = MutableStateFlow(MealDetailUiState())
    val uiState: StateFlow<MealDetailUiState> = _uiState.asStateFlow()

    private val _feedbackEvents = Channel<UiFeedbackEvent>(Channel.BUFFERED)
    val feedbackEvents: Flow<UiFeedbackEvent> = _feedbackEvents.receiveAsFlow()

    fun sendFeedback(event: UiFeedbackEvent) {
        viewModelScope.launch {
            _feedbackEvents.send(event)
        }
    }

    fun initialize(date: LocalDate, initialMealId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    date = date,
                    isLoading = true,
                    recentlyDeletedEntry = null,
                    recentlyMergedPreviousEntry = null,
                    userMessage = null,
                    customMealTime = null,
                )
            }

            val dateStr = date.toString()
            val dayEntries = mealEntryRepository.getByDate(dateStr)
            val configuredMeals = mealConfigRepository.getMealConfigs()
            val activeConfigs = configuredMeals.filter { it.isActive }.sortedBy { it.startTime }
            val inactiveConfigs = configuredMeals.filter { !it.isActive }
            val configById = configuredMeals.associateBy { it.id }

            // Inactive meals that have entries on this day
            val inactiveWithEntries = inactiveConfigs.filter { inactive ->
                dayEntries.any { it.meal == inactive.id }
            }

            val available = (activeConfigs + inactiveWithEntries).toMutableList()
            // Check if there are unknown meal entries
            val hasUnassigned = dayEntries.any { it.meal !in configById }
            if (hasUnassigned || initialMealId == UNASSIGNED_MEAL_ID) {
                available.add(
                    ConfiguredMeal(
                        id = UNASSIGNED_MEAL_ID,
                        name = "Sin asignar",
                        startTime = LocalTime.MIDNIGHT,
                        isActive = false,
                    )
                )
            }

            _uiState.update { it.copy(availableMeals = available) }

            loadMealInternal(date, initialMealId)
        }
    }

    fun onSwitchMeal(mealId: String) {
        val date = _uiState.value.date
        viewModelScope.launch {
            _uiState.update { it.copy(customMealTime = null, isDropdownExpanded = false) }
            loadMealInternal(date, mealId)
        }
    }

    private suspend fun loadMealInternal(date: LocalDate, mealId: String) {
        val dateStr = date.toString()
        val entries = mealEntryRepository.getByDateAndMeal(dateStr, mealId)
        val totals = NutritionCalculator.calculateTotals(entries)
        val mealConfig = _uiState.value.availableMeals.find { it.id == mealId }
        val mealName = mealConfig?.name ?: mealId

        val effectiveMealTime = if (entries.isNotEmpty()) {
            MealTimeCalculator.resolveMealTime(
                date = date,
                existingEntries = entries,
                configuredStartTime = mealConfig?.startTime,
                zoneId = clock.zone,
                clock = clock,
            )
        } else {
            _uiState.value.customMealTime ?: MealTimeCalculator.resolveMealTime(
                date = date,
                existingEntries = entries,
                configuredStartTime = mealConfig?.startTime,
                zoneId = clock.zone,
                clock = clock,
            )
        }
        val formattedMealTime = effectiveMealTime.format(timeFormatter)

        val uiEntries = entries.map { entry ->
            val product = entry.productId?.let { productRepository.getById(it) }
            val isTare = entry.isTare
            val isQuick = entry.isQuickCalories
            val formattedQuantity = if (isQuick) {
                ""
            } else {
                FoodPortionFormatter.formatQuantity(
                    grams = entry.grams,
                    unitG = product?.unitG,
                    unitName = product?.unitName,
                )
            }
            val kcalFormatted = if (isTare) {
                "-${NumberFormatHelper.formatDecimal(-entry.kcal)} kcal"
            } else {
                "${NumberFormatHelper.formatDecimal(entry.kcal)} kcal"
            }

            val stepGrams = if (isQuick) 0.0 else MealPortionCalculator.calculateStep(product?.unitG, product?.servingG)
            val canDecrease = !isTare && !isQuick && MealPortionCalculator.canDecrease(entry.grams, stepGrams)

            MealDetailEntryUiModel(
                entry = entry,
                formattedQuantity = formattedQuantity,
                formattedKcal = kcalFormatted,
                isTare = isTare,
                isQuickCalories = isQuick,
                stepGrams = stepGrams,
                canDecrease = canDecrease,
            )
        }

        _uiState.update {
            it.copy(
                date = date,
                currentMealId = mealId,
                currentMealName = mealName,
                entries = uiEntries,
                totals = totals,
                mealTime = effectiveMealTime,
                formattedMealTime = formattedMealTime,
                isLoading = false,
            )
        }
    }

    fun onIncreaseEntryQuantity(entry: MealEntry) {
        if (entry.isQuickCalories || entry.isTare) return
        val productId = entry.productId ?: return
        viewModelScope.launch {
            val product = productRepository.getById(productId)
            val step = MealPortionCalculator.calculateStep(product?.unitG, product?.servingG)
            val updated = MealPortionCalculator.increaseQuantity(entry, step)
            mealEntryRepository.update(updated)
            loadMealInternal(_uiState.value.date, _uiState.value.currentMealId)
            val formattedQuantity = FoodPortionFormatter.formatQuantity(
                grams = updated.grams,
                unitG = product?.unitG,
                unitName = product?.unitName,
            )
            _feedbackEvents.send(
                UiFeedbackEvent.Success(
                    UiText.StringResource(
                        R.string.feedback_portion_adjusted,
                        updated.productName,
                        formattedQuantity,
                    )
                )
            )
        }
    }

    fun onDecreaseEntryQuantity(entry: MealEntry) {
        if (entry.isQuickCalories || entry.isTare) return
        val productId = entry.productId ?: return
        viewModelScope.launch {
            val product = productRepository.getById(productId)
            val step = MealPortionCalculator.calculateStep(product?.unitG, product?.servingG)
            val updated = MealPortionCalculator.decreaseQuantity(entry, step)
            mealEntryRepository.update(updated)
            loadMealInternal(_uiState.value.date, _uiState.value.currentMealId)
            val formattedQuantity = FoodPortionFormatter.formatQuantity(
                grams = updated.grams,
                unitG = product?.unitG,
                unitName = product?.unitName,
            )
            _feedbackEvents.send(
                UiFeedbackEvent.Success(
                    UiText.StringResource(
                        R.string.feedback_portion_adjusted,
                        updated.productName,
                        formattedQuantity,
                    )
                )
            )
        }
    }

    fun onOpenTimePicker() {
        _uiState.update { it.copy(showTimePickerDialog = true) }
    }

    fun onDismissTimePicker() {
        _uiState.update { it.copy(showTimePickerDialog = false) }
    }

    fun onMealTimeChanged(newTime: LocalTime) {
        val state = _uiState.value
        val date = state.date
        val mealId = state.currentMealId
        val newLoggedAt = MealTimeCalculator.calculateLoggedAt(date, newTime, clock.zone)
        val formatted = newTime.format(timeFormatter)

        viewModelScope.launch {
            mealEntryRepository.updateLoggedAtForMeal(date.toString(), mealId, newLoggedAt)
            _uiState.update {
                it.copy(
                    mealTime = newTime,
                    formattedMealTime = formatted,
                    customMealTime = newTime,
                    showTimePickerDialog = false,
                    userMessage = null,
                )
            }
            loadMealInternal(date, mealId)
            _feedbackEvents.send(
                UiFeedbackEvent.Success(
                    UiText.StringResource(R.string.feedback_meal_time_updated, formatted)
                )
            )
        }
    }

    fun onDeleteEntry(entry: MealEntry) {
        viewModelScope.launch {
            mealEntryRepository.delete(entry)
            _uiState.update {
                it.copy(
                    recentlyDeletedEntry = entry,
                    userMessage = null,
                )
            }
            loadMealInternal(_uiState.value.date, _uiState.value.currentMealId)
            _feedbackEvents.send(
                UiFeedbackEvent.Success(
                    message = UiText.DynamicString("«${entry.productName}» eliminado"),
                    actionLabel = UiText.StringResource(R.string.feedback_action_undo),
                    onAction = { onUndoDelete() },
                )
            )
        }
    }

    fun onUndoDelete() {
        val entryToRestore = _uiState.value.recentlyDeletedEntry ?: return
        viewModelScope.launch {
            mealEntryRepository.save(entryToRestore)
            _uiState.update {
                it.copy(
                    recentlyDeletedEntry = null,
                    userMessage = null,
                )
            }
            loadMealInternal(_uiState.value.date, _uiState.value.currentMealId)
            _feedbackEvents.send(
                UiFeedbackEvent.Success(
                    message = UiText.DynamicString("Alimento restaurado")
                )
            )
        }
    }

    fun notifyAdded(entry: MealEntry) {
        viewModelScope.launch {
            val product = entry.productId?.let { productRepository.getById(it) }
            val formattedQuantity = com.controlcal.core.nutrition.FoodPortionFormatter.formatQuantity(
                grams = entry.grams,
                unitG = product?.unitG,
                unitName = product?.unitName,
            )
            loadMealInternal(_uiState.value.date, _uiState.value.currentMealId)
            _feedbackEvents.send(
                UiFeedbackEvent.Success(
                    message = UiText.StringResource(
                        R.string.feedback_food_added,
                        entry.productName,
                        formattedQuantity,
                    )
                )
            )
        }
    }

    fun notifyMerged(mergedEntry: MealEntry, previousEntry: MealEntry) {
        viewModelScope.launch {
            val product = mergedEntry.productId?.let { productRepository.getById(it) }
            val formattedQuantity = com.controlcal.core.nutrition.FoodPortionFormatter.formatQuantity(
                grams = mergedEntry.grams,
                unitG = product?.unitG,
                unitName = product?.unitName,
            )
            _uiState.update {
                it.copy(
                    recentlyMergedPreviousEntry = previousEntry,
                    userMessage = null,
                )
            }
            loadMealInternal(_uiState.value.date, _uiState.value.currentMealId)
            _feedbackEvents.send(
                UiFeedbackEvent.Success(
                    message = UiText.StringResource(
                        R.string.meal_entry_merged_snackbar,
                        mergedEntry.productName,
                        formattedQuantity,
                    ),
                    actionLabel = UiText.StringResource(R.string.feedback_action_undo),
                    onAction = { onUndoMerge() },
                )
            )
        }
    }

    fun onUndoMerge() {
        val previous = _uiState.value.recentlyMergedPreviousEntry ?: return
        viewModelScope.launch {
            mealEntryRepository.update(previous)
            _uiState.update {
                it.copy(
                    recentlyMergedPreviousEntry = null,
                    userMessage = null,
                )
            }
            loadMealInternal(_uiState.value.date, _uiState.value.currentMealId)
            _feedbackEvents.send(
                UiFeedbackEvent.Success(
                    message = UiText.DynamicString("Acción deshecha")
                )
            )
        }
    }

    fun onOpenTareDialog() {
        viewModelScope.launch {
            val dateStr = _uiState.value.date.toString()
            val currentMeal = _uiState.value.currentMealId
            val entries = mealEntryRepository.getByDateAndMeal(dateStr, currentMeal)

            // Positive entries with a valid productId (excluding quick calories)
            val eligibleProductIds = entries
                .filter { it.grams > 0.0 && it.productId != null }
                .mapNotNull { it.productId }
                .distinct()

            if (eligibleProductIds.isEmpty()) {
                _uiState.update {
                    it.copy(userMessage = "No hay alimentos en esta comida para restar sobras")
                }
                return@launch
            }

            val products = eligibleProductIds.mapNotNull { productRepository.getById(it) }

            _uiState.update {
                it.copy(
                    showTareDialog = true,
                    tareEligibleProducts = products,
                    selectedTareProduct = products.firstOrNull(),
                    tareGramsText = "",
                    tareGramsError = null,
                    isOptionsMenuExpanded = false,
                )
            }
        }
    }

    fun onTareProductSelected(product: Product) {
        _uiState.update { it.copy(selectedTareProduct = product) }
    }

    fun onTareGramsChanged(text: String) {
        val parsed = NumberFormatHelper.parseDecimal(text)
        val error = when {
            text.isBlank() -> "Campo obligatorio"
            parsed == null -> "Número no válido"
            parsed <= 0.0 -> "Debe ser mayor que 0"
            else -> null
        }
        _uiState.update {
            it.copy(
                tareGramsText = text,
                tareGramsError = error,
            )
        }
    }

    fun onConfirmTare() {
        val state = _uiState.value
        val product = state.selectedTareProduct ?: return
        val parsedGrams = NumberFormatHelper.parseDecimal(state.tareGramsText)

        if (parsedGrams == null || parsedGrams <= 0.0) {
            _uiState.update { it.copy(tareGramsError = "Introduce una cantidad válida mayor que 0") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val newLoggedAt = MealTimeCalculator.calculateLoggedAt(
                    date = state.date,
                    mealTime = state.mealTime,
                    zoneId = clock.zone,
                )
                val tareEntry = createTareEntryUseCase(
                    product = product,
                    leftoverGrams = parsedGrams,
                    date = state.date.toString(),
                    meal = state.currentMealId,
                    loggedAt = newLoggedAt,
                )

                mealEntryRepository.save(tareEntry)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        showTareDialog = false,
                        selectedTareProduct = null,
                        tareGramsText = "",
                        tareGramsError = null,
                        userMessage = null,
                    )
                }
                loadMealInternal(state.date, state.currentMealId)
                _feedbackEvents.send(
                    UiFeedbackEvent.Success(
                        UiText.StringResource(
                            R.string.feedback_leftovers_subtracted,
                            NumberFormatHelper.formatDecimal(parsedGrams) + " g",
                            product.name,
                        )
                    )
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _feedbackEvents.send(
                    UiFeedbackEvent.Error(
                        UiText.StringResource(R.string.feedback_save_error)
                    )
                )
            }
        }
    }

    fun onDismissTareDialog() {
        _uiState.update {
            it.copy(
                showTareDialog = false,
                selectedTareProduct = null,
                tareGramsText = "",
                tareGramsError = null,
            )
        }
    }

    fun onRepeatMeal() {
        viewModelScope.launch {
            val dateStr = _uiState.value.date.toString()
            val meal = _uiState.value.currentMealId
            val latestDate = mealEntryRepository.getLatestDateWithMealBefore(dateStr, meal)

            if (latestDate == null) {
                _uiState.update {
                    it.copy(
                        userMessage = null,
                        isOptionsMenuExpanded = false,
                    )
                }
                _feedbackEvents.send(
                    UiFeedbackEvent.Error(
                        UiText.StringResource(R.string.daily_panel_repeat_meal_empty)
                    )
                )
                return@launch
            }

            val sourceEntries = mealEntryRepository.getByDateAndMeal(latestDate, meal)
            if (sourceEntries.isEmpty()) {
                _uiState.update {
                    it.copy(
                        userMessage = null,
                        isOptionsMenuExpanded = false,
                    )
                }
                _feedbackEvents.send(
                    UiFeedbackEvent.Error(
                        UiText.StringResource(R.string.daily_panel_repeat_meal_empty)
                    )
                )
                return@launch
            }

            val cloned = repeatMealUseCase(sourceEntries, dateStr, meal, clock.zone)
            mealEntryRepository.saveAll(cloned)
            loadMealInternal(_uiState.value.date, meal)
            _uiState.update {
                it.copy(
                    isOptionsMenuExpanded = false,
                    userMessage = null,
                )
            }
            _feedbackEvents.send(
                UiFeedbackEvent.Success(
                    UiText.StringResource(R.string.daily_panel_repeat_meal_success)
                )
            )
        }
    }

    fun onCopyYesterday() {
        viewModelScope.launch {
            val yesterdayStr = _uiState.value.date.minusDays(1).toString()
            val sourceEntries = mealEntryRepository.getByDate(yesterdayStr)

            if (sourceEntries.isEmpty()) {
                _uiState.update {
                    it.copy(
                        userMessage = null,
                        isOptionsMenuExpanded = false,
                    )
                }
                _feedbackEvents.send(
                    UiFeedbackEvent.Error(
                        UiText.StringResource(R.string.daily_panel_copy_yesterday_empty)
                    )
                )
                return@launch
            }

            val targetDateStr = _uiState.value.date.toString()
            val cloned = copyDayUseCase(sourceEntries, targetDateStr, clock.zone)
            mealEntryRepository.saveAll(cloned)
            loadMealInternal(_uiState.value.date, _uiState.value.currentMealId)
            _uiState.update {
                it.copy(
                    isOptionsMenuExpanded = false,
                    userMessage = null,
                )
            }
            _feedbackEvents.send(
                UiFeedbackEvent.Success(
                    UiText.StringResource(R.string.daily_panel_copy_yesterday_success)
                )
            )
        }
    }

    fun onToggleDropdown(expanded: Boolean) {
        _uiState.update { it.copy(isDropdownExpanded = expanded) }
    }

    fun onToggleOptionsMenu(expanded: Boolean) {
        _uiState.update { it.copy(isOptionsMenuExpanded = expanded) }
    }

    fun onOpenAddQuickCaloriesDialog() {
        _uiState.update {
            it.copy(
                showQuickCaloriesDialog = true,
                editingQuickCaloriesEntry = null,
                quickKcalText = "",
                quickProteinText = "",
                quickCarbsText = "",
                quickFatText = "",
                quickDescriptionText = "",
                quickKcalError = null,
                quickAtwaterWarning = null,
                isQuickCaloriesSaveEnabled = false,
            )
        }
    }

    fun onOpenEditQuickCaloriesDialog(entry: MealEntry) {
        val kcalStr = NumberFormatHelper.formatDecimal(entry.kcal)
        val proteinStr = if (entry.protein > 0.0) NumberFormatHelper.formatDecimal(entry.protein) else ""
        val carbsStr = if (entry.carbs > 0.0) NumberFormatHelper.formatDecimal(entry.carbs) else ""
        val fatStr = if (entry.fat > 0.0) NumberFormatHelper.formatDecimal(entry.fat) else ""
        val descStr = entry.productName

        _uiState.update {
            it.copy(
                showQuickCaloriesDialog = true,
                editingQuickCaloriesEntry = entry,
                quickKcalText = kcalStr,
                quickProteinText = proteinStr,
                quickCarbsText = carbsStr,
                quickFatText = fatStr,
                quickDescriptionText = descStr,
                quickKcalError = null,
            )
        }
        revalidateQuickCalories(kcalStr, proteinStr, carbsStr, fatStr)
    }

    fun onQuickKcalChanged(text: String) {
        val s = _uiState.value
        revalidateQuickCalories(text, s.quickProteinText, s.quickCarbsText, s.quickFatText)
    }

    fun onQuickProteinChanged(text: String) {
        val s = _uiState.value
        revalidateQuickCalories(s.quickKcalText, text, s.quickCarbsText, s.quickFatText)
    }

    fun onQuickCarbsChanged(text: String) {
        val s = _uiState.value
        revalidateQuickCalories(s.quickKcalText, s.quickProteinText, text, s.quickFatText)
    }

    fun onQuickFatChanged(text: String) {
        val s = _uiState.value
        revalidateQuickCalories(s.quickKcalText, s.quickProteinText, s.quickCarbsText, text)
    }

    fun onQuickDescriptionChanged(text: String) {
        _uiState.update { it.copy(quickDescriptionText = text) }
    }

    private fun revalidateQuickCalories(
        kcalText: String,
        proteinText: String,
        carbsText: String,
        fatText: String,
    ) {
        val parsedKcal = NumberFormatHelper.parseDecimal(kcalText)
        val parsedProtein = NumberFormatHelper.parseDecimal(proteinText)
        val parsedCarbs = NumberFormatHelper.parseDecimal(carbsText)
        val parsedFat = NumberFormatHelper.parseDecimal(fatText)

        val hasP = proteinText.isNotBlank() && parsedProtein != null
        val hasC = carbsText.isNotBlank() && parsedCarbs != null
        val hasF = fatText.isNotBlank() && parsedFat != null

        val validation = QuickCaloriesValidator.validate(
            kcal = parsedKcal,
            protein = parsedProtein,
            carbs = parsedCarbs,
            fat = parsedFat,
            hasProteinInput = hasP,
            hasCarbsInput = hasC,
            hasFatInput = hasF,
        )

        val warningText = if (validation.hasAtwaterWarning) {
            val calculated = NumberFormatHelper.formatDecimal(validation.calculatedAtwaterKcal)
            val total = NumberFormatHelper.formatDecimal(parsedKcal ?: 0.0)
            if (hasP && hasC && hasF) {
                "Los macros ($calculated kcal) se alejan más de un 15 % de las $total kcal"
            } else {
                "Los macros parciales ($calculated kcal) superan las $total kcal en más de un 15 %"
            }
        } else {
            null
        }

        val kcalError = if (kcalText.isNotBlank() && (parsedKcal == null || parsedKcal <= 0.0)) {
            "Debe ser mayor que 0"
        } else {
            null
        }

        _uiState.update {
            it.copy(
                quickKcalText = kcalText,
                quickProteinText = proteinText,
                quickCarbsText = carbsText,
                quickFatText = fatText,
                quickKcalError = kcalError,
                quickAtwaterWarning = warningText,
                isQuickCaloriesSaveEnabled = parsedKcal != null && parsedKcal > 0.0,
            )
        }
    }

    fun onConfirmQuickCalories() {
        val state = _uiState.value
        val parsedKcal = NumberFormatHelper.parseDecimal(state.quickKcalText) ?: return
        if (parsedKcal <= 0.0) return

        val parsedProtein = NumberFormatHelper.parseDecimal(state.quickProteinText) ?: 0.0
        val parsedCarbs = NumberFormatHelper.parseDecimal(state.quickCarbsText) ?: 0.0
        val parsedFat = NumberFormatHelper.parseDecimal(state.quickFatText) ?: 0.0
        val description = QuickCaloriesValidator.sanitizeDescription(state.quickDescriptionText)

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val editing = state.editingQuickCaloriesEntry
                if (editing != null) {
                    val updated = editing.copy(
                        productName = description,
                        kcal = parsedKcal,
                        protein = parsedProtein,
                        carbs = parsedCarbs,
                        fat = parsedFat,
                    )
                    mealEntryRepository.update(updated)
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            showQuickCaloriesDialog = false,
                            editingQuickCaloriesEntry = null,
                            userMessage = null,
                        )
                    }
                    _feedbackEvents.send(
                        UiFeedbackEvent.Success(
                            UiText.StringResource(
                                R.string.feedback_quick_calories_updated,
                                NumberFormatHelper.formatDecimal(parsedKcal),
                            )
                        )
                    )
                } else {
                    val newLoggedAt = MealTimeCalculator.calculateLoggedAt(
                        date = state.date,
                        mealTime = state.mealTime,
                        zoneId = clock.zone,
                    )
                    val newEntry = MealEntry(
                        id = 0L,
                        date = state.date.toString(),
                        meal = state.currentMealId,
                        productId = null,
                        productName = description,
                        grams = 0.0,
                        kcal = parsedKcal,
                        protein = parsedProtein,
                        carbs = parsedCarbs,
                        fat = parsedFat,
                        loggedAt = newLoggedAt,
                    )
                    mealEntryRepository.save(newEntry)
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            showQuickCaloriesDialog = false,
                            editingQuickCaloriesEntry = null,
                            userMessage = null,
                        )
                    }
                    _feedbackEvents.send(
                        UiFeedbackEvent.Success(
                            UiText.StringResource(
                                R.string.feedback_quick_calories_added,
                                NumberFormatHelper.formatDecimal(parsedKcal),
                                state.currentMealName,
                            )
                        )
                    )
                }
                loadMealInternal(state.date, state.currentMealId)
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _feedbackEvents.send(
                    UiFeedbackEvent.Error(
                        UiText.StringResource(R.string.feedback_save_error)
                    )
                )
            }
        }
    }

    fun onDismissQuickCaloriesDialog() {
        _uiState.update {
            it.copy(
                showQuickCaloriesDialog = false,
                editingQuickCaloriesEntry = null,
            )
        }
    }

    fun onMessageConsumed() {
        _uiState.update { it.copy(userMessage = null) }
    }

    companion object {
        const val UNASSIGNED_MEAL_ID = "UNASSIGNED"
    }
}
