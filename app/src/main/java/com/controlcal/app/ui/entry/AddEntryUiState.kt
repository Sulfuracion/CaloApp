package com.controlcal.app.ui.entry

import com.controlcal.core.model.Product
import java.time.LocalDate

data class HabitualFoodUiModel(
    val product: Product,
    val defaultGrams: Double,
    val formattedQuantity: String,
    val formattedKcal: String,
)

data class AddEntryUiState(
    val targetDate: LocalDate = LocalDate.now(),
    val targetMealId: String = "",
    val targetMealName: String = "",
    val searchQuery: String = "",
    val searchResults: List<Product> = emptyList(),
    val habitualFoods: List<HabitualFoodUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val selectedProductForPortion: Product? = null,
    val isMeasuringInUnits: Boolean = false,
    val inputQuantityText: String = "",
    val inputQuantityError: String? = null,
    val portionPreviewKcal: Double = 0.0,
    val portionPreviewProtein: Double = 0.0,
    val portionPreviewCarbs: Double = 0.0,
    val portionPreviewFat: Double = 0.0,
    val isAddedSuccessfully: Boolean = false,
    val userMessage: String? = null,
)
