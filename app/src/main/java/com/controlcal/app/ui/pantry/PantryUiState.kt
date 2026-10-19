package com.controlcal.app.ui.pantry

import com.controlcal.core.model.Product

data class PantryUiState(
    val isLoading: Boolean = false,
    val allProducts: List<Product> = emptyList(),
    val pantryProducts: List<Product> = emptyList(),
    val displayedProducts: List<Product> = emptyList(),
    val searchQuery: String = "",
    val showOnlyPantry: Boolean = true,
    val editingProduct: Product? = null,
    val editInPantry: Boolean = false,
    val editMinGText: String = "",
    val editMaxGText: String = "",
    val editUnitNameText: String = "",
    val editUnitGText: String = "",
    val editMinGError: String? = null,
    val editMaxGError: String? = null,
    val editUnitNameError: String? = null,
    val editUnitGError: String? = null,
    val isSaving: Boolean = false,
    val snackbarMessage: String? = null,
)
