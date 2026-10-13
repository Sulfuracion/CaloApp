package com.controlcal.app.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlcal.app.data.off.OffClient
import com.controlcal.app.data.off.OffSearchResult
import com.controlcal.core.model.Meal
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.Product
import com.controlcal.core.nutrition.NutritionCalculator
import com.controlcal.core.repo.MealEntryRepository
import com.controlcal.core.repo.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val offClient: OffClient,
    private val mealEntryRepository: MealEntryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

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

    fun onAddLocalProductToDiary(product: Product, meal: Meal, grams: Double) {
        viewModelScope.launch {
            val portion = NutritionCalculator.calculatePortionMacros(product, grams)
            val entry = MealEntry(
                id = 0L,
                date = LocalDate.now().toString(),
                meal = meal,
                productId = product.id,
                productName = product.name,
                grams = grams,
                kcal = portion.kcal,
                protein = portion.protein,
                carbs = portion.carbs,
                fat = portion.fat,
                loggedAt = System.currentTimeMillis()
            )
            mealEntryRepository.insert(entry)
            onScanAgain()
        }
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
