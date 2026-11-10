package com.controlcal.app.ui.recipes.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlcal.app.R
import com.controlcal.app.ui.feedback.UiFeedbackEvent
import com.controlcal.app.ui.feedback.UiText
import com.controlcal.core.model.Product
import com.controlcal.core.model.Recipe
import com.controlcal.core.model.RecipeIngredient
import com.controlcal.core.model.ResolvedRecipe
import com.controlcal.core.model.ResolvedRecipeIngredient
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.recipe.RecipeCalculator
import com.controlcal.core.recipe.RecipeCycleDetector
import com.controlcal.core.repo.ProductRepository
import com.controlcal.core.repo.RecipeRepository
import com.controlcal.core.search.ProductSearchEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RecipeEditorViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val productRepository: ProductRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeEditorUiState())
    val uiState: StateFlow<RecipeEditorUiState> = _uiState.asStateFlow()

    private val _feedbackEvents = Channel<UiFeedbackEvent>(Channel.BUFFERED)
    val feedbackEvents: Flow<UiFeedbackEvent> = _feedbackEvents.receiveAsFlow()

    private var allActiveProducts: List<Product> = emptyList()

    fun initializeByProductId(productId: Long) {
        viewModelScope.launch {
            val resolved = recipeRepository.getByProductId(productId)
            initialize(resolved?.id)
        }
    }

    fun initialize(recipeId: Long?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val activeProds = productRepository.getActiveProducts()
            allActiveProducts = activeProds

            if (recipeId != null && recipeId > 0L) {
                val resolvedRecipe = recipeRepository.getById(recipeId)
                if (resolvedRecipe != null) {
                    val items = resolvedRecipe.ingredients.map { ing ->
                        RecipeEditorIngredientItem(
                            tempId = UUID.randomUUID().toString(),
                            product = ing.product,
                            grams = ing.grams,
                            gramsText = NumberFormatHelper.formatDecimal(ing.grams),
                            gramsError = null,
                        )
                    }
                    val cookedWeightText = resolvedRecipe.cookedWeightG?.let {
                        NumberFormatHelper.formatDecimal(it)
                    } ?: ""
                    val servingsText = resolvedRecipe.servings?.let {
                        if (it % 1.0 == 0.0) it.toInt().toString() else NumberFormatHelper.formatDecimal(it)
                    } ?: ""

                    _uiState.value = RecipeEditorUiState(
                        recipeId = resolvedRecipe.id,
                        productId = resolvedRecipe.productId,
                        name = resolvedRecipe.name,
                        cookedWeightGText = cookedWeightText,
                        servingsText = servingsText,
                        notes = resolvedRecipe.notes ?: "",
                        ingredients = items,
                        isLoading = false,
                    )
                    recalculate()
                    return@launch
                }
            }

            // New recipe
            _uiState.value = RecipeEditorUiState(
                recipeId = 0L,
                productId = 0L,
                name = "",
                isLoading = false,
            )
            recalculate()
        }
    }

    fun onNameChanged(name: String) {
        val error = if (name.isBlank()) "El nombre no puede estar vacío" else null
        _uiState.update { it.copy(name = name, nameError = error) }
    }

    fun onCookedWeightChanged(text: String) {
        val parsed = NumberFormatHelper.parseDecimal(text)
        val error = if (text.isNotBlank() && (parsed == null || parsed <= 0.0)) {
            "Introduce un peso válido mayor que 0"
        } else {
            null
        }
        _uiState.update { it.copy(cookedWeightGText = text, cookedWeightGError = error) }
        recalculate()
    }

    fun onServingsChanged(text: String) {
        val parsed = NumberFormatHelper.parseDecimal(text)
        val error = if (text.isNotBlank() && (parsed == null || parsed <= 0.0)) {
            "Introduce un número de raciones mayor que 0"
        } else {
            null
        }
        _uiState.update { it.copy(servingsText = text, servingsError = error) }
        recalculate()
    }

    fun onNotesChanged(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun onIngredientGramsChanged(tempId: String, text: String) {
        val parsed = NumberFormatHelper.parseDecimal(text)
        val error = if (parsed == null || parsed <= 0.0) {
            "Mayor que 0"
        } else {
            null
        }

        _uiState.update { state ->
            val updated = state.ingredients.map { item ->
                if (item.tempId == tempId) {
                    item.copy(
                        grams = parsed ?: 0.0,
                        gramsText = text,
                        gramsError = error,
                    )
                } else {
                    item
                }
            }
            state.copy(ingredients = updated)
        }
        recalculate()
    }

    fun onMoveIngredientUp(tempId: String) {
        _uiState.update { state ->
            val index = state.ingredients.indexOfFirst { it.tempId == tempId }
            if (index > 0) {
                val list = state.ingredients.toMutableList()
                val item = list.removeAt(index)
                list.add(index - 1, item)
                state.copy(ingredients = list)
            } else {
                state
            }
        }
        recalculate()
    }

    fun onMoveIngredientDown(tempId: String) {
        _uiState.update { state ->
            val index = state.ingredients.indexOfFirst { it.tempId == tempId }
            if (index in 0 until state.ingredients.size - 1) {
                val list = state.ingredients.toMutableList()
                val item = list.removeAt(index)
                list.add(index + 1, item)
                state.copy(ingredients = list)
            } else {
                state
            }
        }
        recalculate()
    }

    fun onRemoveIngredient(tempId: String) {
        _uiState.update { state ->
            state.copy(ingredients = state.ingredients.filter { it.tempId != tempId })
        }
        recalculate()
    }

    fun onOpenAddIngredientDialog() {
        val filtered = ProductSearchEngine.search("", allActiveProducts)
        _uiState.update {
            it.copy(
                showAddIngredientDialog = true,
                ingredientSearchQuery = "",
                candidateProducts = filtered,
                selectedCandidateProduct = null,
                candidateGramsText = "100",
                candidateGramsError = null,
                cycleError = null,
            )
        }
    }

    fun onDismissAddIngredientDialog() {
        _uiState.update { it.copy(showAddIngredientDialog = false) }
    }

    fun onIngredientSearchQueryChanged(query: String) {
        val filtered = ProductSearchEngine.search(query, allActiveProducts)
        _uiState.update {
            it.copy(
                ingredientSearchQuery = query,
                candidateProducts = filtered,
            )
        }
    }

    fun onSelectCandidateProduct(product: Product) {
        viewModelScope.launch {
            val targetProductId = _uiState.value.productId
            val allRecipes = recipeRepository.getAll(includeArchived = false)
            val graph = allRecipes.associate { r ->
                r.productId to r.ingredients.map { it.productId }
            }
            val isCycle = RecipeCycleDetector.wouldCreateCycle(
                targetRecipeProductId = if (targetProductId > 0) targetProductId else null,
                candidateIngredientProductId = product.id,
                ingredientProductIdsProvider = { rProdId -> graph[rProdId] ?: emptyList() },
            )

            if (isCycle) {
                _uiState.update {
                    it.copy(
                        selectedCandidateProduct = null,
                        cycleError = "No se puede añadir «${product.name}»: crearía una dependencia circular entre recetas.",
                    )
                }
            } else {
                val defaultGrams = product.unitG ?: product.servingG ?: 100.0
                _uiState.update {
                    it.copy(
                        selectedCandidateProduct = product,
                        candidateGramsText = if (defaultGrams % 1.0 == 0.0) defaultGrams.toInt().toString() else NumberFormatHelper.formatDecimal(defaultGrams),
                        candidateGramsError = null,
                        cycleError = null,
                    )
                }
            }
        }
    }

    fun onCandidateGramsChanged(text: String) {
        val parsed = NumberFormatHelper.parseDecimal(text)
        val error = if (parsed == null || parsed <= 0.0) "Gramos inválidos" else null
        _uiState.update { it.copy(candidateGramsText = text, candidateGramsError = error) }
    }

    fun onConfirmAddIngredient() {
        val selectedProduct = _uiState.value.selectedCandidateProduct ?: return
        val grams = NumberFormatHelper.parseDecimal(_uiState.value.candidateGramsText) ?: return
        if (grams <= 0.0) return

        val newItem = RecipeEditorIngredientItem(
            tempId = UUID.randomUUID().toString(),
            product = selectedProduct,
            grams = grams,
            gramsText = NumberFormatHelper.formatDecimal(grams),
            gramsError = null,
        )

        _uiState.update { state ->
            state.copy(
                ingredients = state.ingredients + newItem,
                showAddIngredientDialog = false,
                selectedCandidateProduct = null,
            )
        }
        recalculate()
    }

    fun saveRecipe(onSaved: (ResolvedRecipe) -> Unit) {
        val state = _uiState.value
        if (!state.isValid || state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val cookedWeight = NumberFormatHelper.parseDecimal(state.cookedWeightGText)
            val servings = NumberFormatHelper.parseDecimal(state.servingsText)

            val recipeDomain = Recipe(
                id = state.recipeId,
                productId = state.productId,
                name = state.name.trim(),
                cookedWeightG = cookedWeight,
                servings = servings,
                notes = state.notes.trim().ifEmpty { null },
            )

            val ingredientsDomain = state.ingredients.mapIndexed { index, item ->
                RecipeIngredient(
                    id = 0L,
                    recipeId = state.recipeId,
                    position = index,
                    productId = item.product.id,
                    grams = item.grams,
                )
            }

            try {
                val saved = recipeRepository.saveRecipe(recipeDomain, ingredientsDomain)
                val msg = if (state.recipeId > 0L) {
                    R.string.recipes_updated_feedback
                } else {
                    R.string.recipes_saved_feedback
                }
                _feedbackEvents.send(UiFeedbackEvent.Success(UiText.StringResource(msg, saved.name)))
                _uiState.update { it.copy(isSaving = false) }
                onSaved(saved)
            } catch (e: Exception) {
                _feedbackEvents.send(UiFeedbackEvent.Error(UiText.StringResource(R.string.feedback_save_error)))
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun recalculate() {
        val state = _uiState.value
        val resolved = state.ingredients.mapIndexed { index, item ->
            ResolvedRecipeIngredient(
                ingredient = RecipeIngredient(
                    id = 0L,
                    recipeId = state.recipeId,
                    position = index,
                    productId = item.product.id,
                    grams = item.grams,
                ),
                product = item.product,
            )
        }
        val cookedWeight = NumberFormatHelper.parseDecimal(state.cookedWeightGText)
        val servings = NumberFormatHelper.parseDecimal(state.servingsText)

        val calculation = RecipeCalculator.calculate(
            ingredients = resolved,
            cookedWeightG = cookedWeight,
            servings = servings,
        )
        _uiState.update { it.copy(calculation = calculation) }
    }
}
