package com.controlcal.app.ui.recipes.editor

import com.controlcal.core.model.Product
import com.controlcal.core.recipe.RecipeCalculationResult

data class RecipeEditorIngredientItem(
    val tempId: String,
    val product: Product,
    val grams: Double,
    val gramsText: String,
    val gramsError: String? = null,
)

data class RecipeEditorUiState(
    val recipeId: Long = 0L,
    val productId: Long = 0L,
    val name: String = "",
    val nameError: String? = null,
    val cookedWeightGText: String = "",
    val cookedWeightGError: String? = null,
    val servingsText: String = "",
    val servingsError: String? = null,
    val notes: String = "",
    val ingredients: List<RecipeEditorIngredientItem> = emptyList(),
    val calculation: RecipeCalculationResult? = null,
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val showAddIngredientDialog: Boolean = false,
    val ingredientSearchQuery: String = "",
    val candidateProducts: List<Product> = emptyList(),
    val selectedCandidateProduct: Product? = null,
    val candidateGramsText: String = "100",
    val candidateGramsError: String? = null,
    val cycleError: String? = null,
) {
    val isValid: Boolean
        get() = name.isNotBlank() &&
            nameError == null &&
            cookedWeightGError == null &&
            servingsError == null &&
            ingredients.isNotEmpty() &&
            ingredients.all { it.gramsError == null && it.grams > 0.0 }
}
