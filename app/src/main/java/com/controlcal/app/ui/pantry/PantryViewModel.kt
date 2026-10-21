package com.controlcal.app.ui.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlcal.app.R
import com.controlcal.app.ui.feedback.UiFeedbackEvent
import com.controlcal.app.ui.feedback.UiText
import com.controlcal.core.model.Product
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.pantry.PantryError
import com.controlcal.core.pantry.PantryLimitsValidator
import com.controlcal.core.pantry.PantryValidationResult
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
import javax.inject.Inject

@HiltViewModel
class PantryViewModel @Inject constructor(
    private val productRepository: ProductRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PantryUiState())
    val uiState: StateFlow<PantryUiState> = _uiState.asStateFlow()

    private val _feedbackEvents = Channel<UiFeedbackEvent>(Channel.BUFFERED)
    val feedbackEvents: Flow<UiFeedbackEvent> = _feedbackEvents.receiveAsFlow()

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
            _feedbackEvents.send(UiFeedbackEvent.Success(UiText.DynamicString(msg)))
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

    private data class FormErrors(
        val minGError: String?,
        val maxGError: String?,
        val unitNameError: String?,
        val unitGError: String?,
    )

    private fun validateFields(
        minGText: String,
        maxGText: String,
        unitNameText: String,
        unitGText: String,
    ): FormErrors {
        var minGError: String? = null
        var maxGError: String? = null
        var unitGError: String? = null

        val parsedMinG = if (minGText.isNotBlank()) {
            val p = NumberFormatHelper.parseDecimal(minGText)
            if (p == null) minGError = "Introduce un número válido"
            p
        } else null

        val parsedMaxG = if (maxGText.isNotBlank()) {
            val p = NumberFormatHelper.parseDecimal(maxGText)
            if (p == null) maxGError = "Introduce un número válido"
            p
        } else null

        val parsedUnitG = if (unitGText.isNotBlank()) {
            val p = NumberFormatHelper.parseDecimal(unitGText)
            if (p == null) unitGError = "Introduce un número válido"
            p
        } else null

        if (minGError != null || maxGError != null || unitGError != null) {
            return FormErrors(minGError, maxGError, null, unitGError)
        }

        return when (val res = PantryLimitsValidator.validate(parsedMinG, parsedMaxG, unitNameText, parsedUnitG)) {
            is PantryValidationResult.Invalid -> FormErrors(
                mapError(res.minGError),
                mapError(res.maxGError),
                mapError(res.unitNameError),
                mapError(res.unitGError),
            )
            is PantryValidationResult.Valid -> FormErrors(null, null, null, null)
        }
    }

    fun onEditMinGChanged(text: String) {
        val current = _uiState.value
        val errors = validateFields(text, current.editMaxGText, current.editUnitNameText, current.editUnitGText)
        _uiState.update {
            it.copy(
                editMinGText = text,
                editMinGError = errors.minGError,
                editMaxGError = errors.maxGError,
                editUnitNameError = errors.unitNameError,
                editUnitGError = errors.unitGError,
            )
        }
    }

    fun onEditMaxGChanged(text: String) {
        val current = _uiState.value
        val errors = validateFields(current.editMinGText, text, current.editUnitNameText, current.editUnitGText)
        _uiState.update {
            it.copy(
                editMaxGText = text,
                editMinGError = errors.minGError,
                editMaxGError = errors.maxGError,
                editUnitNameError = errors.unitNameError,
                editUnitGError = errors.unitGError,
            )
        }
    }

    fun onEditUnitNameChanged(text: String) {
        val current = _uiState.value
        val errors = validateFields(current.editMinGText, current.editMaxGText, text, current.editUnitGText)
        _uiState.update {
            it.copy(
                editUnitNameText = text,
                editMinGError = errors.minGError,
                editMaxGError = errors.maxGError,
                editUnitNameError = errors.unitNameError,
                editUnitGError = errors.unitGError,
            )
        }
    }

    fun onEditUnitGChanged(text: String) {
        val current = _uiState.value
        val errors = validateFields(current.editMinGText, current.editMaxGText, current.editUnitNameText, text)
        _uiState.update {
            it.copy(
                editUnitGText = text,
                editMinGError = errors.minGError,
                editMaxGError = errors.maxGError,
                editUnitNameError = errors.unitNameError,
                editUnitGError = errors.unitGError,
            )
        }
    }

    fun onSaveProductConfig() {
        val current = _uiState.value
        val product = current.editingProduct ?: return

        val errors = validateFields(
            current.editMinGText,
            current.editMaxGText,
            current.editUnitNameText,
            current.editUnitGText,
        )

        if (errors.minGError != null || errors.maxGError != null ||
            errors.unitNameError != null || errors.unitGError != null
        ) {
            _uiState.update {
                it.copy(
                    editMinGError = errors.minGError,
                    editMaxGError = errors.maxGError,
                    editUnitNameError = errors.unitNameError,
                    editUnitGError = errors.unitGError,
                )
            }
            return
        }

        val parsedMinG = if (current.editMinGText.isNotBlank()) NumberFormatHelper.parseDecimal(current.editMinGText) else null
        val parsedMaxG = if (current.editMaxGText.isNotBlank()) NumberFormatHelper.parseDecimal(current.editMaxGText) else null
        val parsedUnitG = if (current.editUnitGText.isNotBlank()) NumberFormatHelper.parseDecimal(current.editUnitGText) else null

        val validationResult = PantryLimitsValidator.validate(
            minG = parsedMinG,
            maxG = parsedMaxG,
            unitName = current.editUnitNameText,
            unitG = parsedUnitG,
        )

        if (validationResult is PantryValidationResult.Valid) {
            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true) }
                try {
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
                            isSaving = false,
                            editingProduct = null,
                            allProducts = all,
                            pantryProducts = pantry,
                            displayedProducts = displayed,
                            snackbarMessage = "Configuración guardada para «${product.name}»",
                        )
                    }
                    _feedbackEvents.send(
                        UiFeedbackEvent.Success(
                            UiText.StringResource(R.string.feedback_pantry_saved, product.name)
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
    }

    fun onSnackbarMessageConsumed() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun sendFeedback(event: UiFeedbackEvent) {
        viewModelScope.launch {
            _feedbackEvents.send(event)
        }
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
