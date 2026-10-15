package com.controlcal.app.ui.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlcal.core.model.Product
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.pantry.PantryError
import com.controlcal.core.pantry.PantryLimitsValidator
import com.controlcal.core.pantry.PantryValidationResult
import com.controlcal.core.repo.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PantryViewModel @Inject constructor(
    private val productRepository: ProductRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PantryUiState())
    val uiState: StateFlow<PantryUiState> = _uiState.asStateFlow()

    init {
        loadProducts()
    }

    fun loadProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val all = productRepository.getActiveProducts()
            val pantry = productRepository.getPantryProducts()

            _uiState.update { current ->
                val displayed = computeDisplayed(
                    all = all,
                    pantry = pantry,
                    onlyPantry = current.showOnlyPantry,
                    query = current.searchQuery,
                )
                current.copy(
                    isLoading = false,
                    allProducts = all,
                    pantryProducts = pantry,
                    displayedProducts = displayed,
                )
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            val displayed = computeDisplayed(
                all = current.allProducts,
                pantry = current.pantryProducts,
                onlyPantry = current.showOnlyPantry,
                query = query,
            )
            current.copy(
                searchQuery = query,
                displayedProducts = displayed,
            )
        }
    }

    fun onFilterToggled(showOnlyPantry: Boolean) {
        _uiState.update { current ->
            val displayed = computeDisplayed(
                all = current.allProducts,
                pantry = current.pantryProducts,
                onlyPantry = showOnlyPantry,
                query = current.searchQuery,
            )
            current.copy(
                showOnlyPantry = showOnlyPantry,
                displayedProducts = displayed,
            )
        }
    }

    fun onTogglePantry(product: Product) {
        viewModelScope.launch {
            val newInPantry = !product.inPantry
            productRepository.setInPantry(product.id, newInPantry)

            val all = productRepository.getActiveProducts()
            val pantry = productRepository.getPantryProducts()

            val msg = if (newInPantry) {
                "«${product.name}» añadido a la despensa"
            } else {
                "«${product.name}» retirado de la despensa"
            }

            _uiState.update { current ->
                val displayed = computeDisplayed(
                    all = all,
                    pantry = pantry,
                    onlyPantry = current.showOnlyPantry,
                    query = current.searchQuery,
                )
                current.copy(
                    allProducts = all,
                    pantryProducts = pantry,
                    displayedProducts = displayed,
                    snackbarMessage = msg,
                )
            }
        }
    }

    fun onStartEditingProduct(product: Product) {
        _uiState.update {
            it.copy(
                editingProduct = product,
                editInPantry = product.inPantry,
                editMinGText = product.minG?.let { min -> NumberFormatHelper.formatDecimal(min) } ?: "",
                editMaxGText = product.maxG?.let { max -> NumberFormatHelper.formatDecimal(max) } ?: "",
                editUnitNameText = product.unitName ?: "",
                editUnitGText = product.unitG?.let { unitG -> NumberFormatHelper.formatDecimal(unitG) } ?: "",
                editMinGError = null,
                editMaxGError = null,
                editUnitNameError = null,
                editUnitGError = null,
            )
        }
    }

    fun onDismissEditDialog() {
        _uiState.update {
            it.copy(
                editingProduct = null,
                editMinGError = null,
                editMaxGError = null,
                editUnitNameError = null,
                editUnitGError = null,
            )
        }
    }

    fun onEditInPantryChanged(inPantry: Boolean) {
        _uiState.update { it.copy(editInPantry = inPantry) }
    }

    fun onEditMinGChanged(text: String) {
        _uiState.update { it.copy(editMinGText = text, editMinGError = null) }
    }

    fun onEditMaxGChanged(text: String) {
        _uiState.update { it.copy(editMaxGText = text, editMaxGError = null) }
    }

    fun onEditUnitNameChanged(text: String) {
        _uiState.update { it.copy(editUnitNameText = text, editUnitNameError = null) }
    }

    fun onEditUnitGChanged(text: String) {
        _uiState.update { it.copy(editUnitGText = text, editUnitGError = null) }
    }

    fun onSaveProductConfig() {
        val current = _uiState.value
        val product = current.editingProduct ?: return

        var hasFormatError = false
        var minGError: String? = null
        var maxGError: String? = null
        var unitGError: String? = null

        val parsedMinG: Double? = if (current.editMinGText.isNotBlank()) {
            val parsed = NumberFormatHelper.parseDecimal(current.editMinGText)
            if (parsed == null) {
                minGError = "Introduce un número válido"
                hasFormatError = true
            }
            parsed
        } else {
            null
        }

        val parsedMaxG: Double? = if (current.editMaxGText.isNotBlank()) {
            val parsed = NumberFormatHelper.parseDecimal(current.editMaxGText)
            if (parsed == null) {
                maxGError = "Introduce un número válido"
                hasFormatError = true
            }
            parsed
        } else {
            null
        }

        val parsedUnitG: Double? = if (current.editUnitGText.isNotBlank()) {
            val parsed = NumberFormatHelper.parseDecimal(current.editUnitGText)
            if (parsed == null) {
                unitGError = "Introduce un número válido"
                hasFormatError = true
            }
            parsed
        } else {
            null
        }

        if (hasFormatError) {
            _uiState.update {
                it.copy(
                    editMinGError = minGError,
                    editMaxGError = maxGError,
                    editUnitGError = unitGError,
                )
            }
            return
        }

        val validationResult = PantryLimitsValidator.validate(
            minG = parsedMinG,
            maxG = parsedMaxG,
            unitName = current.editUnitNameText,
            unitG = parsedUnitG,
        )

        when (validationResult) {
            is PantryValidationResult.Invalid -> {
                _uiState.update {
                    it.copy(
                        editMinGError = mapError(validationResult.minGError),
                        editMaxGError = mapError(validationResult.maxGError),
                        editUnitNameError = mapError(validationResult.unitNameError),
                        editUnitGError = mapError(validationResult.unitGError),
                    )
                }
            }

            is PantryValidationResult.Valid -> {
                viewModelScope.launch {
                    productRepository.updatePantryConfig(
                        id = product.id,
                        inPantry = current.editInPantry,
                        minG = validationResult.minG,
                        maxG = validationResult.maxG,
                        unitName = validationResult.unitName,
                        unitG = validationResult.unitG,
                    )

                    val all = productRepository.getActiveProducts()
                    val pantry = productRepository.getPantryProducts()

                    _uiState.update { state ->
                        val displayed = computeDisplayed(
                            all = all,
                            pantry = pantry,
                            onlyPantry = state.showOnlyPantry,
                            query = state.searchQuery,
                        )
                        state.copy(
                            editingProduct = null,
                            allProducts = all,
                            pantryProducts = pantry,
                            displayedProducts = displayed,
                            snackbarMessage = "Configuración guardada para «${product.name}»",
                        )
                    }
                }
            }
        }
    }

    fun onSnackbarMessageConsumed() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun mapError(error: PantryError?): String? = when (error) {
        null -> null
        PantryError.NEGATIVE_VALUE -> "El valor no puede ser negativo"
        PantryError.MIN_EXCEEDS_MAX -> "El mínimo no puede superar al máximo"
        PantryError.UNIT_NAME_REQUIRED -> "Indica el nombre de la unidad"
        PantryError.UNIT_GRAMS_REQUIRED_OR_INVALID -> "Introduce un peso mayor que 0"
    }

    private fun computeDisplayed(
        all: List<Product>,
        pantry: List<Product>,
        onlyPantry: Boolean,
        query: String,
    ): List<Product> {
        val base = if (onlyPantry) pantry else all
        return if (query.isBlank()) {
            base
        } else {
            val q = query.trim().lowercase()
            base.filter { product ->
                val brand = product.brand
                product.name.lowercase().contains(q) ||
                    (brand != null && brand.lowercase().contains(q))
            }
        }
    }
}
