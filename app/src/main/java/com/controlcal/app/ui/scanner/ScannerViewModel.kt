package com.controlcal.app.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlcal.app.data.off.OffClient
import com.controlcal.app.data.off.OffSearchResult
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.Product
import com.controlcal.core.nutrition.NutritionCalculator
import com.controlcal.core.repo.MealEntryRepository
import com.controlcal.core.repo.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.nutrition.MealClassifier
import com.controlcal.core.repo.MealConfigRepository
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.controlcal.app.R
import com.controlcal.app.ui.feedback.UiFeedbackEvent
import com.controlcal.app.ui.feedback.UiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val offClient: OffClient,
    private val mealEntryRepository: MealEntryRepository,
    private val mealConfigRepository: MealConfigRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val _feedbackEvents = Channel<UiFeedbackEvent>(Channel.BUFFERED)
    val feedbackEvents: Flow<UiFeedbackEvent> = _feedbackEvents.receiveAsFlow()

    fun sendFeedback(event: UiFeedbackEvent) {
        viewModelScope.launch {
            _feedbackEvents.send(event)
        }
    }

    private var targetDate: LocalDate? = null

    init {
        loadMealConfig()
    }

    fun setContext(date: LocalDate, mealId: String?) {
        targetDate = date
        if (mealId != null) {
            _uiState.update { it.copy(preselectedMeal = mealId) }
        }
    }

    private fun loadMealConfig() {
        viewModelScope.launch {
            val meals = mealConfigRepository.getMealConfigs()
            val active = meals.filter { it.isActive }.sortedBy { it.startTime }
            val suggested = MealClassifier.suggestMeal(active, LocalTime.now(clock))?.id
                ?: active.firstOrNull()?.id
                ?: DefaultMealIds.BREAKFAST
            _uiState.update { current ->
                current.copy(
                    availableMeals = active,
                    preselectedMeal = suggested,
                )
            }
        }
    }

    fun onPermissionChecked(isGranted: Boolean) {
        _uiState.update { current ->
            if (isGranted) {
                current.copy(
                    permissionState = CameraPermissionState.GRANTED,
                    showRationaleDialog = false
                )
            } else {
                current.copy(
                    permissionState = CameraPermissionState.RATIONALE,
                    showRationaleDialog = true
                )
            }
        }
    }

    fun onRationaleDismissed() {
        _uiState.update { current ->
            current.copy(
                permissionState = CameraPermissionState.DENIED,
                showRationaleDialog = false
            )
        }
    }

    fun onRationaleAccepted() {
        _uiState.update { current ->
            current.copy(showRationaleDialog = false)
        }
    }

    fun onPermissionResult(isGranted: Boolean) {
        _uiState.update { current ->
            if (isGranted) {
                current.copy(
                    permissionState = CameraPermissionState.GRANTED,
                    showRationaleDialog = false
                )
            } else {
                current.copy(
                    permissionState = CameraPermissionState.DENIED,
                    showRationaleDialog = false,
                    isTorchOn = false
                )
            }
        }
    }

    fun onTorchToggled() {
        _uiState.update { current ->
            if (current.isTorchAvailable) {
                current.copy(isTorchOn = !current.isTorchOn)
            } else {
                current
            }
        }
    }

    fun onTorchAvailabilityChanged(isAvailable: Boolean) {
        _uiState.update { current ->
            current.copy(
                isTorchAvailable = isAvailable,
                isTorchOn = if (isAvailable) current.isTorchOn else false
            )
        }
    }

    fun onRetryPermission() {
        _uiState.update { current ->
            current.copy(
                permissionState = CameraPermissionState.RATIONALE,
                showRationaleDialog = true
            )
        }
    }

    fun onBarcodeScanned(barcode: String) {
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) return

        if (_uiState.value.scannedBarcode != null) return

        _uiState.update { current ->
            current.copy(
                scannedBarcode = trimmed,
                lookupState = BarcodeLookupState.Searching(trimmed)
            )
        }

        performLookup(trimmed)
    }

    fun onRetryLookup() {
        val barcode = _uiState.value.scannedBarcode ?: return
        _uiState.update { it.copy(lookupState = BarcodeLookupState.Searching(barcode)) }
        performLookup(barcode)
    }

    private fun performLookup(barcode: String) {
        viewModelScope.launch {
            // Step 1: Check local database first (Rule from PLAN.md section 5.2)
            val localProduct = productRepository.getByBarcode(barcode)
            if (localProduct != null) {
                _uiState.update { it.copy(lookupState = BarcodeLookupState.LocalProductFound(localProduct)) }
                return@launch
            }

            // Step 2: Query Open Food Facts
            when (val result = offClient.getProductByBarcode(barcode)) {
                is OffSearchResult.Success -> {
                    _uiState.update { it.copy(lookupState = BarcodeLookupState.OffProductFound(result.product)) }
                }

                is OffSearchResult.Offline -> {
                    _uiState.update { it.copy(lookupState = BarcodeLookupState.Offline(barcode)) }
                }

                is OffSearchResult.NotFound -> {
                    _uiState.update { it.copy(lookupState = BarcodeLookupState.NotFound(barcode)) }
                }

                is OffSearchResult.Error -> {
                    _uiState.update { it.copy(lookupState = BarcodeLookupState.Error(barcode, result.message)) }
                }
            }
        }
    }

    fun onAddLocalProductToDiary(product: Product, meal: String, grams: Double) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val portion = NutritionCalculator.calculatePortionMacros(product, grams)
                val effectiveDate = targetDate ?: LocalDate.now(clock)
                val entryDate = effectiveDate.toString()
                val existingEntries = mealEntryRepository.getByDateAndMeal(entryDate, meal)
                val configuredStartTime = mealConfigRepository.getMealConfigs().find { it.id == meal }?.startTime
                val loggedAt = com.controlcal.core.nutrition.MealTimeCalculator.calculateLoggedAt(
                    date = effectiveDate,
                    existingEntries = existingEntries,
                    configuredStartTime = configuredStartTime,
                    zoneId = clock.zone,
                    clock = clock,
                )
                val entry = MealEntry(
                    id = 0L,
                    date = entryDate,
                    meal = meal,
                    productId = product.id,
                    productName = product.name,
                    grams = grams,
                    kcal = portion.kcal,
                    protein = portion.protein,
                    carbs = portion.carbs,
                    fat = portion.fat,
                    loggedAt = loggedAt,
                )
                val result = mealEntryRepository.save(entry)
                _uiState.update { it.copy(isSaving = false) }
                if (result is com.controlcal.core.nutrition.MealEntrySaveResult.Merged) {
                    val formattedQuantity = com.controlcal.core.nutrition.FoodPortionFormatter.formatQuantity(
                        grams = result.mergedEntry.grams,
                        unitG = product.unitG,
                        unitName = product.unitName,
                    )
                    _uiState.update { current ->
                        current.copy(
                            recentlyMergedPreviousEntry = result.previousEntry,
                            feedbackMessage = null,
                        )
                    }
                    _feedbackEvents.send(
                        UiFeedbackEvent.Success(
                            message = UiText.StringResource(
                                R.string.meal_entry_merged_snackbar,
                                product.name,
                                formattedQuantity,
                            ),
                            actionLabel = UiText.StringResource(R.string.feedback_action_undo),
                            onAction = { onUndoMerge() },
                        )
                    )
                } else if (result is com.controlcal.core.nutrition.MealEntrySaveResult.Created) {
                    val formattedQuantity = com.controlcal.core.nutrition.FoodPortionFormatter.formatQuantity(
                        grams = result.entry.grams,
                        unitG = product.unitG,
                        unitName = product.unitName,
                    )
                    _uiState.update { current ->
                        current.copy(
                            recentlyMergedPreviousEntry = null,
                            feedbackMessage = null,
                        )
                    }
                    _feedbackEvents.send(
                        UiFeedbackEvent.Success(
                            message = UiText.StringResource(
                                R.string.feedback_food_added,
                                product.name,
                                formattedQuantity,
                            )
                        )
                    )
                }
                onScanAgain()
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

    fun onUndoMerge() {
        val previous = _uiState.value.recentlyMergedPreviousEntry ?: return
        viewModelScope.launch {
            mealEntryRepository.update(previous)
            _uiState.update {
                it.copy(
                    recentlyMergedPreviousEntry = null,
                    feedbackMessage = null,
                )
            }
            _feedbackEvents.send(
                UiFeedbackEvent.Success(
                    message = UiText.DynamicString("Acción deshecha")
                )
            )
        }
    }

    fun onFeedbackMessageConsumed() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    fun onScanAgain() {
        _uiState.update { current ->
            current.copy(
                scannedBarcode = null,
                lookupState = BarcodeLookupState.Idle
            )
        }
    }
}
