package com.controlcal.app.ui.savedmeals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlcal.app.R
import com.controlcal.app.ui.feedback.UiFeedbackEvent
import com.controlcal.app.ui.feedback.UiText
import com.controlcal.core.model.Product
import com.controlcal.core.model.ResolvedSavedMeal
import com.controlcal.core.model.SavedMealItem
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.nutrition.SavedMealCalculator
import com.controlcal.core.repo.ProductRepository
import com.controlcal.core.repo.SavedMealRepository
import com.controlcal.core.search.ProductSearchEngine
import com.controlcal.core.suggest.SavedMealRanker
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
class SavedMealsViewModel @Inject constructor(
    private val savedMealRepository: SavedMealRepository,
    private val productRepository: ProductRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SavedMealsUiState(isLoading = true))
    val uiState: StateFlow<SavedMealsUiState> = _uiState.asStateFlow()

    private val _feedbackEvents = Channel<UiFeedbackEvent>(Channel.BUFFERED)
    val feedbackEvents: Flow<UiFeedbackEvent> = _feedbackEvents.receiveAsFlow()

    private var cachedActiveProducts: List<Product> = emptyList()

    init {
        loadMeals()
    }

    fun loadMeals() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val rawMeals = savedMealRepository.getAll()
            val allProducts = productRepository.getAllIncludingArchived()
            cachedActiveProducts = allProducts.filter { !it.archived }
            val productsById = allProducts.associateBy { it.id }

            val resolved = rawMeals.map { meal ->
                SavedMealCalculator.resolveMeal(meal, productsById)
            }
            val ranked = SavedMealRanker.rankResolved(resolved)

            _uiState.update {
                it.copy(
                    meals = ranked,
                    isLoading = false,
                )
            }
        }
    }

    fun onToggleExpandMeal(mealId: Long) {
        _uiState.update {
            it.copy(
                expandedMealId = if (it.expandedMealId == mealId) null else mealId
            )
        }
    }

    // --- Renaming ---
    fun onOpenRenameDialog(meal: ResolvedSavedMeal) {
        _uiState.update {
            it.copy(
                renamingMeal = meal,
                renameText = meal.meal.name,
                renameError = null,
            )
        }
    }

    fun onRenameTextChanged(text: String) {
        _uiState.update {
            it.copy(
                renameText = text,
                renameError = null,
            )
        }
    }

    fun onDismissRenameDialog() {
        _uiState.update {
            it.copy(
                renamingMeal = null,
                renameText = "",
                renameError = null,
            )
        }
    }

    fun onConfirmRename() {
        val state = _uiState.value
        val meal = state.renamingMeal ?: return
        val newName = state.renameText.trim()
        if (newName.isEmpty()) {
            _uiState.update { it.copy(renameError = "El nombre no puede estar vacío") }
            return
        }

        viewModelScope.launch {
            if (savedMealRepository.isNameTaken(newName, excludeId = meal.meal.id)) {
                _uiState.update { it.copy(renameError = "Ya existe una comida con este nombre") }
                return@launch
            }

            _uiState.update { it.copy(isSaving = true) }
            try {
                val updated = meal.meal.copy(
                    name = newName,
                    updatedAt = System.currentTimeMillis(),
                )
                savedMealRepository.updateMeal(updated)
                _uiState.update {
                    it.copy(
                        renamingMeal = null,
                        renameText = "",
                        renameError = null,
                        isSaving = false,
                    )
                }
                _feedbackEvents.send(
                    UiFeedbackEvent.Success(
                        UiText.StringResource(R.string.saved_meals_renamed_feedback, newName)
                    )
                )
                loadMeals()
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

    // --- Deletion ---
    fun onOpenDeleteDialog(meal: ResolvedSavedMeal) {
        _uiState.update { it.copy(deletingMeal = meal) }
    }

    fun onDismissDeleteDialog() {
        _uiState.update { it.copy(deletingMeal = null) }
    }

    fun onConfirmDelete() {
        val meal = _uiState.value.deletingMeal ?: return
        viewModelScope.launch {
            try {
                savedMealRepository.deleteMeal(meal.meal.id)
                _uiState.update { it.copy(deletingMeal = null) }
                _feedbackEvents.send(
                    UiFeedbackEvent.Success(
                        UiText.StringResource(R.string.saved_meals_deleted_feedback, meal.meal.name)
                    )
                )
                loadMeals()
            } catch (e: Exception) {
                _feedbackEvents.send(
                    UiFeedbackEvent.Error(
                        UiText.StringResource(R.string.feedback_save_error)
                    )
                )
            }
        }
    }

    // --- Edit Item Grams ---
    fun onOpenEditGramsDialog(mealId: Long, item: SavedMealItem) {
        _uiState.update {
            it.copy(
                editingMealItem = mealId to item,
                editGramsText = NumberFormatHelper.formatDecimal(item.grams),
                editGramsError = null,
            )
        }
    }

    fun onEditGramsChanged(text: String) {
        _uiState.update {
            it.copy(
                editGramsText = text,
                editGramsError = null,
            )
        }
    }

    fun onDismissEditGramsDialog() {
        _uiState.update {
            it.copy(
                editingMealItem = null,
                editGramsText = "",
                editGramsError = null,
            )
        }
    }

    fun onConfirmEditGrams() {
        val (mealId, item) = _uiState.value.editingMealItem ?: return
        val parsed = NumberFormatHelper.parseDecimal(_uiState.value.editGramsText)
        if (parsed == null || parsed <= 0.0) {
            _uiState.update { it.copy(editGramsError = "Introduce una cantidad mayor a 0") }
            return
        }

        viewModelScope.launch {
            val meal = savedMealRepository.getById(mealId) ?: return@launch
            val updatedItems = meal.items.map { currentItem ->
                if (currentItem.id == item.id || (item.id == 0L && currentItem.position == item.position)) {
                    currentItem.copy(grams = parsed)
                } else {
                    currentItem
                }
            }
            val updatedMeal = meal.copy(
                items = updatedItems,
                updatedAt = System.currentTimeMillis(),
            )
            savedMealRepository.updateMeal(updatedMeal)
            _uiState.update {
                it.copy(
                    editingMealItem = null,
                    editGramsText = "",
                    editGramsError = null,
                )
            }
            _feedbackEvents.send(
                UiFeedbackEvent.Success(
                    UiText.StringResource(R.string.saved_meals_updated_feedback)
                )
            )
            loadMeals()
        }
    }

    // --- Remove Item ---
    fun onRemoveItem(mealId: Long, item: SavedMealItem) {
        viewModelScope.launch {
            val meal = savedMealRepository.getById(mealId) ?: return@launch
            if (meal.items.size <= 1) {
                _feedbackEvents.send(
                    UiFeedbackEvent.Error(
                        UiText.StringResource(R.string.saved_meals_error_cannot_remove_last_item)
                    )
                )
                return@launch
            }

            val remainingItems = meal.items
                .filterNot { it.id == item.id || (item.id == 0L && it.position == item.position) }
                .mapIndexed { idx, it -> it.copy(position = idx) }

            val updatedMeal = meal.copy(
                items = remainingItems,
                updatedAt = System.currentTimeMillis(),
            )
            savedMealRepository.updateMeal(updatedMeal)
            _feedbackEvents.send(
                UiFeedbackEvent.Success(
                    UiText.StringResource(R.string.saved_meals_updated_feedback)
                )
            )
            loadMeals()
        }
    }

    // --- Add Product to Meal ---
    fun onOpenAddProductDialog(mealId: Long) {
        _uiState.update {
            it.copy(
                addingItemToMealId = mealId,
                addProductSearchQuery = "",
                addProductSearchResults = cachedActiveProducts.take(15),
                selectedProductForAdd = null,
                addGramsText = "100",
                addGramsError = null,
            )
        }
    }

    fun onAddProductSearchQueryChanged(query: String) {
        val filtered = ProductSearchEngine.search(query, cachedActiveProducts).take(20)
        _uiState.update {
            it.copy(
                addProductSearchQuery = query,
                addProductSearchResults = filtered,
            )
        }
    }

    fun onSelectProductForAdd(product: Product) {
        _uiState.update {
            it.copy(
                selectedProductForAdd = product,
                addGramsText = NumberFormatHelper.formatDecimal(product.servingG ?: 100.0),
                addGramsError = null,
            )
        }
    }

    fun onAddGramsChanged(text: String) {
        _uiState.update {
            it.copy(
                addGramsText = text,
                addGramsError = null,
            )
        }
    }

    fun onDismissAddProductDialog() {
        _uiState.update {
            it.copy(
                addingItemToMealId = null,
                addProductSearchQuery = "",
                addProductSearchResults = emptyList(),
                selectedProductForAdd = null,
                addGramsText = "",
                addGramsError = null,
            )
        }
    }

    fun onConfirmAddProduct() {
        val state = _uiState.value
        val mealId = state.addingItemToMealId ?: return
        val product = state.selectedProductForAdd ?: return
        val parsedGrams = NumberFormatHelper.parseDecimal(state.addGramsText)
        if (parsedGrams == null || parsedGrams <= 0.0) {
            _uiState.update { it.copy(addGramsError = "Introduce una cantidad mayor a 0") }
            return
        }

        viewModelScope.launch {
            val meal = savedMealRepository.getById(mealId) ?: return@launch
            val newItem = SavedMealItem(
                id = 0L,
                savedMealId = mealId,
                position = meal.items.size,
                productId = product.id,
                productName = product.name,
                grams = parsedGrams,
            )
            val updatedMeal = meal.copy(
                items = meal.items + newItem,
                updatedAt = System.currentTimeMillis(),
            )
            savedMealRepository.updateMeal(updatedMeal)
            _uiState.update {
                it.copy(
                    addingItemToMealId = null,
                    addProductSearchQuery = "",
                    addProductSearchResults = emptyList(),
                    selectedProductForAdd = null,
                    addGramsText = "",
                    addGramsError = null,
                )
            }
            _feedbackEvents.send(
                UiFeedbackEvent.Success(
                    UiText.StringResource(R.string.saved_meals_updated_feedback)
                )
            )
            loadMeals()
        }
    }
}
