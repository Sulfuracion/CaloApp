package com.controlcal.app.ui.savedmeals

import com.controlcal.core.model.Product
import com.controlcal.core.model.ResolvedSavedMeal
import com.controlcal.core.model.SavedMealItem

data class SavedMealsUiState(
    val meals: List<ResolvedSavedMeal> = emptyList(),
    val isLoading: Boolean = false,
    val expandedMealId: Long? = null,
    // Renaming
    val renamingMeal: ResolvedSavedMeal? = null,
    val renameText: String = "",
    val renameError: String? = null,
    // Deletion confirmation
    val deletingMeal: ResolvedSavedMeal? = null,
    // Editing item grams
    val editingMealItem: Pair<Long, SavedMealItem>? = null,
    val editGramsText: String = "",
    val editGramsError: String? = null,
    // Adding product to meal
    val addingItemToMealId: Long? = null,
    val addProductSearchQuery: String = "",
    val addProductSearchResults: List<Product> = emptyList(),
    val selectedProductForAdd: Product? = null,
    val addGramsText: String = "",
    val addGramsError: String? = null,
    val isSaving: Boolean = false,
)
