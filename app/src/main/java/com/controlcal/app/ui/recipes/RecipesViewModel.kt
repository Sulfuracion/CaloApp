package com.controlcal.app.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlcal.app.R
import com.controlcal.app.ui.feedback.UiFeedbackEvent
import com.controlcal.app.ui.feedback.UiText
import com.controlcal.core.model.ResolvedRecipe
import com.controlcal.core.repo.RecipeDeleteResult
import com.controlcal.core.repo.RecipeRepository
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
class RecipesViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipesUiState(isLoading = true))
    val uiState: StateFlow<RecipesUiState> = _uiState.asStateFlow()

    private val _feedbackEvents = Channel<UiFeedbackEvent>(Channel.BUFFERED)
    val feedbackEvents: Flow<UiFeedbackEvent> = _feedbackEvents.receiveAsFlow()

    init {
        loadRecipes()
    }

    fun loadRecipes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val list = recipeRepository.getAll(includeArchived = false)
            _uiState.update {
                it.copy(
                    recipes = list,
                    isLoading = false,
                )
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onShowDeleteDialog(recipe: ResolvedRecipe) {
        _uiState.update { it.copy(deleteDialogRecipe = recipe) }
    }

    fun onDismissDeleteDialog() {
        _uiState.update { it.copy(deleteDialogRecipe = null) }
    }

    fun onConfirmDelete() {
        val target = _uiState.value.deleteDialogRecipe ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            val result = recipeRepository.deleteRecipe(target.id)
            val event = when (result) {
                is RecipeDeleteResult.Deleted -> UiFeedbackEvent.Success(
                    UiText.StringResource(R.string.recipes_deleted_feedback, target.name)
                )
                is RecipeDeleteResult.Archived -> UiFeedbackEvent.Success(
                    UiText.StringResource(R.string.recipes_archived_feedback, target.name)
                )
            }
            _feedbackEvents.send(event)
            _uiState.update { it.copy(deleteDialogRecipe = null, isDeleting = false) }
            loadRecipes()
        }
    }
}
