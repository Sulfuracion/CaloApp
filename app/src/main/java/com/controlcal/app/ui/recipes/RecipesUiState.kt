package com.controlcal.app.ui.recipes

import com.controlcal.core.model.ResolvedRecipe

data class RecipesUiState(
    val recipes: List<ResolvedRecipe> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val deleteDialogRecipe: ResolvedRecipe? = null,
    val isDeleting: Boolean = false,
) {
    val filteredRecipes: List<ResolvedRecipe>
        get() = if (searchQuery.isBlank()) {
            recipes
        } else {
            val query = searchQuery.trim().lowercase()
            recipes.filter { it.name.lowercase().contains(query) }
        }
}
