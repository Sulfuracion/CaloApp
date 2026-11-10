package com.controlcal.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.controlcal.app.ui.about.AboutScreen
import com.controlcal.app.ui.confirm.ProductConfirmationRoute
import com.controlcal.app.ui.confirm.ProductConfirmationViewModel
import com.controlcal.app.ui.debug.DebugFeatures
import com.controlcal.app.ui.detail.MealDetailRoute
import com.controlcal.app.ui.detail.MealDetailViewModel
import com.controlcal.app.ui.entry.AddEntryRoute
import com.controlcal.app.ui.entry.AddEntryViewModel
import com.controlcal.app.ui.feedback.UiFeedbackEvent
import com.controlcal.app.ui.feedback.UiText
import com.controlcal.app.ui.ocr.OcrCaptureRoute
import com.controlcal.app.ui.ocr.OcrCaptureViewModel
import com.controlcal.app.ui.panel.DailyPanelRoute
import com.controlcal.app.ui.panel.DailyPanelViewModel
import com.controlcal.app.ui.pantry.PantryRoute
import com.controlcal.app.ui.pantry.PantryViewModel
import com.controlcal.app.ui.scanner.ScannerRoute
import com.controlcal.app.ui.scanner.ScannerViewModel
import com.controlcal.app.ui.settings.SettingsRoute
import com.controlcal.app.ui.settings.SettingsViewModel
import com.controlcal.app.ui.tetris.MacroTetrisRoute
import com.controlcal.app.ui.tetris.MacroTetrisViewModel
import com.controlcal.app.ui.theme.ControlCalTheme
import com.controlcal.app.ui.weight.WeightRoute
import com.controlcal.app.ui.weight.WeightViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

data class MealEntryContext(
    val date: LocalDate,
    val mealId: String,
)

enum class MainDestination {
    DAILY_PANEL,
    MEAL_DETAIL,
    ADD_ENTRY,
    SCANNER,
    PRODUCT_CONFIRMATION,
    OCR_CAPTURE,
    ABOUT,
    DEBUG_OCR_COLLECTOR,
    WEIGHT,
    SETTINGS,
    PANTRY,
    MACRO_TETRIS,
    SAVED_MEALS,
    SUPPLEMENTS,
    RECIPES,
    RECIPE_EDITOR,
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val dailyPanelViewModel: DailyPanelViewModel by viewModels()
    private val mealDetailViewModel: MealDetailViewModel by viewModels()
    private val addEntryViewModel: AddEntryViewModel by viewModels()
    private val scannerViewModel: ScannerViewModel by viewModels()
    private val confirmationViewModel: ProductConfirmationViewModel by viewModels()
    private val ocrCaptureViewModel: OcrCaptureViewModel by viewModels()
    private val weightViewModel: WeightViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val pantryViewModel: PantryViewModel by viewModels()
    private val macroTetrisViewModel: MacroTetrisViewModel by viewModels()
    private val savedMealsViewModel: com.controlcal.app.ui.savedmeals.SavedMealsViewModel by viewModels()
    private val supplementsViewModel: com.controlcal.app.ui.supplement.SupplementsViewModel by viewModels()
    private val recipesViewModel: com.controlcal.app.ui.recipes.RecipesViewModel by viewModels()
    private val recipeEditorViewModel: com.controlcal.app.ui.recipes.editor.RecipeEditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ControlCalTheme {
                val navigationState = rememberMainNavigationState()
                var currentDestination by navigationState::currentDestination
                var manualEntryBarcode by navigationState::manualEntryBarcode
                var activeMealContext by navigationState::activeMealContext
                var returnToPantryAfterConfirmation by navigationState::returnToPantryAfterConfirmation

                val navigateBack: () -> Unit = {
                    when (currentDestination) {
                        MainDestination.DAILY_PANEL -> {
                            // En el panel diario el BackHandler está desactivado, el sistema gestiona la salida
                        }
                        MainDestination.MEAL_DETAIL -> {
                            dailyPanelViewModel.loadDay(mealDetailViewModel.uiState.value.date)
                            currentDestination = MainDestination.DAILY_PANEL
                        }
                        MainDestination.ADD_ENTRY -> {
                            val ctx = activeMealContext
                            if (ctx != null) {
                                mealDetailViewModel.initialize(ctx.date, ctx.mealId)
                                currentDestination = MainDestination.MEAL_DETAIL
                            } else {
                                dailyPanelViewModel.loadDay(dailyPanelViewModel.uiState.value.selectedDate)
                                currentDestination = MainDestination.DAILY_PANEL
                            }
                        }
                        MainDestination.SCANNER -> {
                            if (activeMealContext != null) {
                                currentDestination = MainDestination.ADD_ENTRY
                            } else {
                                currentDestination = MainDestination.DAILY_PANEL
                            }
                        }
                        MainDestination.OCR_CAPTURE -> {
                            ocrCaptureViewModel.onCancelCapture()
                            currentDestination = MainDestination.SCANNER
                        }
                        MainDestination.PRODUCT_CONFIRMATION -> {
                            confirmationViewModel.cleanupTempPhoto()
                            if (returnToPantryAfterConfirmation) {
                                returnToPantryAfterConfirmation = false
                                pantryViewModel.loadProducts()
                                currentDestination = MainDestination.PANTRY
                            } else {
                                val targetDate = activeMealContext?.date ?: dailyPanelViewModel.uiState.value.selectedDate
                                val targetMeal = activeMealContext?.mealId
                                dailyPanelViewModel.loadDay(targetDate)
                                if (targetMeal != null) {
                                    mealDetailViewModel.initialize(targetDate, targetMeal)
                                    currentDestination = MainDestination.MEAL_DETAIL
                                } else {
                                    currentDestination = MainDestination.DAILY_PANEL
                                }
                            }
                        }
                        MainDestination.ABOUT -> {
                            currentDestination = MainDestination.DAILY_PANEL
                        }
                        MainDestination.DEBUG_OCR_COLLECTOR -> {
                            currentDestination = MainDestination.SCANNER
                        }
                        MainDestination.WEIGHT -> {
                            currentDestination = MainDestination.DAILY_PANEL
                        }
                        MainDestination.SETTINGS -> {
                            dailyPanelViewModel.loadDay(dailyPanelViewModel.uiState.value.selectedDate)
                            currentDestination = MainDestination.DAILY_PANEL
                        }
                        MainDestination.PANTRY -> {
                            dailyPanelViewModel.loadDay(dailyPanelViewModel.uiState.value.selectedDate)
                            currentDestination = MainDestination.DAILY_PANEL
                        }
                        MainDestination.MACRO_TETRIS -> {
                            dailyPanelViewModel.loadDay(dailyPanelViewModel.uiState.value.selectedDate)
                            currentDestination = MainDestination.DAILY_PANEL
                        }
                        MainDestination.SAVED_MEALS -> {
                            dailyPanelViewModel.loadDay(dailyPanelViewModel.uiState.value.selectedDate)
                            currentDestination = MainDestination.DAILY_PANEL
                        }
                        MainDestination.SUPPLEMENTS -> {
                            dailyPanelViewModel.loadDay(dailyPanelViewModel.uiState.value.selectedDate)
                            currentDestination = MainDestination.DAILY_PANEL
                        }
                        MainDestination.RECIPES -> {
                            if (navigationState.openedRecipesFromAddEntry) {
                                navigationState.openedRecipesFromAddEntry = false
                                val ctx = activeMealContext
                                if (ctx != null) {
                                    addEntryViewModel.initialize(ctx.date, ctx.mealId)
                                }
                                currentDestination = MainDestination.ADD_ENTRY
                            } else {
                                dailyPanelViewModel.loadDay(dailyPanelViewModel.uiState.value.selectedDate)
                                currentDestination = MainDestination.DAILY_PANEL
                            }
                        }
                        MainDestination.RECIPE_EDITOR -> {
                            if (navigationState.returnToPantryAfterRecipeEditor) {
                                navigationState.returnToPantryAfterRecipeEditor = false
                                pantryViewModel.loadProducts()
                                currentDestination = MainDestination.PANTRY
                            } else {
                                recipesViewModel.loadRecipes()
                                currentDestination = MainDestination.RECIPES
                            }
                        }
                    }
                }

                BackHandler(enabled = currentDestination != MainDestination.DAILY_PANEL) {
                    navigateBack()
                }

                when (currentDestination) {
                    MainDestination.DAILY_PANEL -> {
                        DailyPanelRoute(
                            viewModel = dailyPanelViewModel,
                            onNavigateToScanner = {
                                activeMealContext = null
                                scannerViewModel.onScanAgain()
                                currentDestination = MainDestination.SCANNER
                            },
                            onNavigateToWeight = {
                                weightViewModel.loadEntries()
                                currentDestination = MainDestination.WEIGHT
                            },
                            onNavigateToPantry = {
                                pantryViewModel.loadProducts()
                                currentDestination = MainDestination.PANTRY
                            },
                            onNavigateToRecipes = {
                                navigationState.openedRecipesFromAddEntry = false
                                recipesViewModel.loadRecipes()
                                currentDestination = MainDestination.RECIPES
                            },
                            onNavigateToSavedMeals = {
                                savedMealsViewModel.loadMeals()
                                currentDestination = MainDestination.SAVED_MEALS
                            },
                            onNavigateToSupplements = {
                                supplementsViewModel.loadSupplements()
                                currentDestination = MainDestination.SUPPLEMENTS
                            },
                            onNavigateToSettings = {
                                settingsViewModel.loadSettings()
                                currentDestination = MainDestination.SETTINGS
                            },
                            onNavigateToMacroTetris = {
                                macroTetrisViewModel.loadData(dailyPanelViewModel.uiState.value.selectedDate)
                                currentDestination = MainDestination.MACRO_TETRIS
                            },
                            onNavigateToMealDetail = { mealId ->
                                val date = dailyPanelViewModel.uiState.value.selectedDate
                                activeMealContext = MealEntryContext(date, mealId)
                                mealDetailViewModel.initialize(date, mealId)
                                currentDestination = MainDestination.MEAL_DETAIL
                            },
                            onNavigateToAddFood = { mealId ->
                                val date = dailyPanelViewModel.uiState.value.selectedDate
                                activeMealContext = MealEntryContext(date, mealId)
                                addEntryViewModel.initialize(date, mealId)
                                currentDestination = MainDestination.ADD_ENTRY
                            },
                        )
                    }

                    MainDestination.MEAL_DETAIL -> {
                        MealDetailRoute(
                            viewModel = mealDetailViewModel,
                            onBack = navigateBack,
                            onNavigateToAddFood = { mealId ->
                                val date = mealDetailViewModel.uiState.value.date
                                activeMealContext = MealEntryContext(date, mealId)
                                addEntryViewModel.initialize(date, mealId)
                                currentDestination = MainDestination.ADD_ENTRY
                            },
                        )
                    }

                    MainDestination.ADD_ENTRY -> {
                        AddEntryRoute(
                            viewModel = addEntryViewModel,
                            onBack = navigateBack,
                            onNavigateToScanner = {
                                val ctx = activeMealContext
                                if (ctx != null) {
                                    scannerViewModel.setContext(ctx.date, ctx.mealId)
                                }
                                scannerViewModel.onScanAgain()
                                currentDestination = MainDestination.SCANNER
                            },
                            onNavigateToOcrCapture = {
                                currentDestination = MainDestination.OCR_CAPTURE
                            },
                            onNavigateToManualEntry = {
                                manualEntryBarcode = null
                                returnToPantryAfterConfirmation = false
                                val ctx = activeMealContext
                                val selectedMeal = addEntryViewModel.uiState.value.targetMealId.ifEmpty { ctx?.mealId }
                                confirmationViewModel.initializeForManualEntry(
                                    barcode = null,
                                    targetDate = ctx?.date ?: dailyPanelViewModel.uiState.value.selectedDate,
                                    targetMeal = selectedMeal,
                                )
                                currentDestination = MainDestination.PRODUCT_CONFIRMATION
                            },
                            onNavigateToConfirmation = { offProduct ->
                                returnToPantryAfterConfirmation = false
                                val ctx = activeMealContext
                                val selectedMeal = addEntryViewModel.uiState.value.targetMealId.ifEmpty { ctx?.mealId }
                                confirmationViewModel.initialize(
                                    offProduct = offProduct,
                                    targetDate = ctx?.date ?: dailyPanelViewModel.uiState.value.selectedDate,
                                    targetMeal = selectedMeal,
                                )
                                currentDestination = MainDestination.PRODUCT_CONFIRMATION
                            },
                            onNavigateToSavedMeals = {
                                savedMealsViewModel.loadMeals()
                                currentDestination = MainDestination.SAVED_MEALS
                            },
                            onNavigateToRecipes = {
                                navigationState.openedRecipesFromAddEntry = true
                                recipesViewModel.loadRecipes()
                                currentDestination = MainDestination.RECIPES
                            },
                            onSavedMealAdded = { resolvedMeal, results ->
                                val ctx = activeMealContext
                                val selectedMealId = addEntryViewModel.uiState.value.targetMealId.ifEmpty { ctx?.mealId ?: "" }
                                if (ctx != null) {
                                    val updatedCtx = ctx.copy(mealId = selectedMealId.ifEmpty { ctx.mealId })
                                    activeMealContext = updatedCtx
                                    mealDetailViewModel.initialize(updatedCtx.date, updatedCtx.mealId)
                                    dailyPanelViewModel.loadDay(updatedCtx.date)
                                    mealDetailViewModel.notifySavedMealAdded(resolvedMeal, results)
                                    currentDestination = MainDestination.MEAL_DETAIL
                                } else {
                                    dailyPanelViewModel.loadDay(dailyPanelViewModel.uiState.value.selectedDate)
                                    dailyPanelViewModel.notifySavedMealAdded(resolvedMeal, results)
                                    currentDestination = MainDestination.DAILY_PANEL
                                }
                            },
                            onEntryAdded = { saveResult ->
                                val ctx = activeMealContext
                                val selectedMealId = addEntryViewModel.uiState.value.targetMealId.ifEmpty { ctx?.mealId ?: "" }
                                if (ctx != null) {
                                    val updatedCtx = ctx.copy(mealId = selectedMealId.ifEmpty { ctx.mealId })
                                    activeMealContext = updatedCtx
                                    mealDetailViewModel.initialize(updatedCtx.date, updatedCtx.mealId)
                                    dailyPanelViewModel.loadDay(updatedCtx.date)
                                    when (saveResult) {
                                        is com.controlcal.core.nutrition.MealEntrySaveResult.Merged -> {
                                            mealDetailViewModel.notifyMerged(saveResult.mergedEntry, saveResult.previousEntry)
                                        }
                                        is com.controlcal.core.nutrition.MealEntrySaveResult.Created -> {
                                            mealDetailViewModel.notifyAdded(saveResult.entry)
                                        }
                                        null -> Unit
                                    }
                                    currentDestination = MainDestination.MEAL_DETAIL
                                } else {
                                    dailyPanelViewModel.loadDay(dailyPanelViewModel.uiState.value.selectedDate)
                                    when (saveResult) {
                                        is com.controlcal.core.nutrition.MealEntrySaveResult.Merged -> {
                                            dailyPanelViewModel.notifyMerged(saveResult.mergedEntry, saveResult.previousEntry)
                                        }
                                        is com.controlcal.core.nutrition.MealEntrySaveResult.Created -> {
                                            dailyPanelViewModel.notifyAdded(saveResult.entry)
                                        }
                                        null -> Unit
                                    }
                                    currentDestination = MainDestination.DAILY_PANEL
                                }
                            },
                        )
                    }

                    MainDestination.SCANNER -> {
                        ScannerRoute(
                            viewModel = scannerViewModel,
                            onBack = navigateBack,
                            onNavigateToConfirmation = { offProduct ->
                                val ctx = activeMealContext
                                confirmationViewModel.initialize(offProduct, ctx?.date, ctx?.mealId)
                                currentDestination = MainDestination.PRODUCT_CONFIRMATION
                            },
                            onNavigateToAbout = {
                                currentDestination = MainDestination.ABOUT
                            },
                            onNavigateToManualEntry = { barcode ->
                                manualEntryBarcode = barcode
                                returnToPantryAfterConfirmation = false
                                val ctx = activeMealContext
                                confirmationViewModel.initializeForManualEntry(
                                    barcode = barcode,
                                    targetDate = ctx?.date ?: dailyPanelViewModel.uiState.value.selectedDate,
                                    targetMeal = ctx?.mealId,
                                )
                                currentDestination = MainDestination.PRODUCT_CONFIRMATION
                            },
                            onNavigateToOcrCapture = { barcode ->
                                manualEntryBarcode = barcode
                                currentDestination = MainDestination.OCR_CAPTURE
                            },
                            onNavigateToDebugOcr = {
                                currentDestination = MainDestination.DEBUG_OCR_COLLECTOR
                            }
                        )
                    }

                    MainDestination.OCR_CAPTURE -> {
                        OcrCaptureRoute(
                            viewModel = ocrCaptureViewModel,
                            targetBarcode = manualEntryBarcode,
                            onBack = navigateBack,
                            onNavigateToConfirmation = { photoFile, parsedNutrients, barcode ->
                                val ctx = activeMealContext
                                confirmationViewModel.initializeFromOcr(
                                    photoFile = photoFile,
                                    parsedNutrients = parsedNutrients,
                                    barcode = barcode,
                                    targetDate = ctx?.date,
                                    targetMeal = ctx?.mealId,
                                )
                                currentDestination = MainDestination.PRODUCT_CONFIRMATION
                            }
                        )
                    }

                    MainDestination.PRODUCT_CONFIRMATION -> {
                        ProductConfirmationRoute(
                            viewModel = confirmationViewModel,
                            onNavigateToOcrCapture = { barcode ->
                                manualEntryBarcode = barcode
                                currentDestination = MainDestination.OCR_CAPTURE
                            },
                            onProductDeletedOrArchived = { archived, productName ->
                                val feedbackRes = if (archived) {
                                    R.string.feedback_product_archived
                                } else {
                                    R.string.feedback_product_deleted
                                }
                                if (returnToPantryAfterConfirmation) {
                                    returnToPantryAfterConfirmation = false
                                    pantryViewModel.loadProducts()
                                    pantryViewModel.sendFeedback(
                                        UiFeedbackEvent.Success(
                                            UiText.StringResource(feedbackRes, productName)
                                        )
                                    )
                                    currentDestination = MainDestination.PANTRY
                                } else {
                                    val targetDate = activeMealContext?.date ?: dailyPanelViewModel.uiState.value.selectedDate
                                    dailyPanelViewModel.loadDay(targetDate)
                                    dailyPanelViewModel.sendFeedback(
                                        UiFeedbackEvent.Success(
                                            UiText.StringResource(feedbackRes, productName)
                                        )
                                    )
                                    currentDestination = MainDestination.DAILY_PANEL
                                }
                            },
                            onConfirmed = { saveResult ->
                                val savedProductName = confirmationViewModel.uiState.value.name.trim()
                                val wasEditing = confirmationViewModel.uiState.value.editingProductId != null
                                if (returnToPantryAfterConfirmation) {
                                    returnToPantryAfterConfirmation = false
                                    pantryViewModel.loadProducts()
                                    pantryViewModel.sendFeedback(
                                        UiFeedbackEvent.Success(
                                            UiText.StringResource(
                                                if (wasEditing) R.string.feedback_product_updated else R.string.feedback_product_saved,
                                                savedProductName,
                                            )
                                        )
                                    )
                                    currentDestination = MainDestination.PANTRY
                                } else {
                                    val targetDate = activeMealContext?.date ?: dailyPanelViewModel.uiState.value.selectedDate
                                    val targetMeal = activeMealContext?.mealId
                                    dailyPanelViewModel.loadDay(targetDate)
                                    if (targetMeal != null) {
                                        val finalMeal = confirmationViewModel.uiState.value.selectedMeal.ifEmpty { targetMeal }
                                        activeMealContext = MealEntryContext(targetDate, finalMeal)
                                        mealDetailViewModel.initialize(targetDate, finalMeal)
                                        when (saveResult) {
                                            is com.controlcal.core.nutrition.MealEntrySaveResult.Merged -> {
                                                mealDetailViewModel.notifyMerged(saveResult.mergedEntry, saveResult.previousEntry)
                                            }
                                            is com.controlcal.core.nutrition.MealEntrySaveResult.Created -> {
                                                mealDetailViewModel.notifyAdded(saveResult.entry)
                                            }
                                            null -> {
                                                mealDetailViewModel.sendFeedback(
                                                    UiFeedbackEvent.Success(
                                                        UiText.StringResource(
                                                            if (wasEditing) R.string.feedback_product_updated else R.string.feedback_product_saved,
                                                            savedProductName,
                                                        )
                                                    )
                                                )
                                            }
                                        }
                                        currentDestination = MainDestination.MEAL_DETAIL
                                    } else {
                                        when (saveResult) {
                                            is com.controlcal.core.nutrition.MealEntrySaveResult.Merged -> {
                                                dailyPanelViewModel.notifyMerged(saveResult.mergedEntry, saveResult.previousEntry)
                                            }
                                            is com.controlcal.core.nutrition.MealEntrySaveResult.Created -> {
                                                dailyPanelViewModel.notifyAdded(saveResult.entry)
                                            }
                                            null -> {
                                                dailyPanelViewModel.sendFeedback(
                                                    UiFeedbackEvent.Success(
                                                        UiText.StringResource(
                                                            if (wasEditing) R.string.feedback_product_updated else R.string.feedback_product_saved,
                                                            savedProductName,
                                                        )
                                                    )
                                                )
                                            }
                                        }
                                        currentDestination = MainDestination.DAILY_PANEL
                                    }
                                }
                            },
                            onDiscard = navigateBack,
                        )
                    }

                    MainDestination.ABOUT -> {
                        AboutScreen(
                            onBack = navigateBack
                        )
                    }

                    MainDestination.DEBUG_OCR_COLLECTOR -> {
                        DebugFeatures.DebugOcrCollectorRoute(
                            onBack = navigateBack
                        )
                    }

                    MainDestination.WEIGHT -> {
                        WeightRoute(
                            viewModel = weightViewModel,
                            onBack = navigateBack
                        )
                    }

                    MainDestination.SETTINGS -> {
                        SettingsRoute(
                            viewModel = settingsViewModel,
                            onBack = navigateBack
                        )
                    }

                    MainDestination.PANTRY -> {
                        PantryRoute(
                            viewModel = pantryViewModel,
                            onBack = navigateBack,
                            onEditNutrition = { product ->
                                if (product.source == com.controlcal.core.model.ProductSource.RECIPE) {
                                    navigationState.returnToPantryAfterRecipeEditor = true
                                    recipeEditorViewModel.initializeByProductId(product.id)
                                    currentDestination = MainDestination.RECIPE_EDITOR
                                } else {
                                    returnToPantryAfterConfirmation = true
                                    confirmationViewModel.initializeForEdit(product)
                                    currentDestination = MainDestination.PRODUCT_CONFIRMATION
                                }
                            },
                            onCreateProduct = {
                                manualEntryBarcode = null
                                returnToPantryAfterConfirmation = true
                                confirmationViewModel.initializeForManualEntry(
                                    barcode = null,
                                    targetDate = dailyPanelViewModel.uiState.value.selectedDate,
                                    targetMeal = null,
                                )
                                currentDestination = MainDestination.PRODUCT_CONFIRMATION
                            },
                        )
                    }

                    MainDestination.MACRO_TETRIS -> {
                        MacroTetrisRoute(
                            viewModel = macroTetrisViewModel,
                            onBack = navigateBack,
                            onNavigateToPantry = {
                                pantryViewModel.loadProducts()
                                currentDestination = MainDestination.PANTRY
                            },
                            onTetrisAdded = { mealName ->
                                dailyPanelViewModel.loadDay(dailyPanelViewModel.uiState.value.selectedDate)
                                dailyPanelViewModel.sendFeedback(
                                    UiFeedbackEvent.Success(
                                        UiText.StringResource(
                                            R.string.feedback_macro_tetris_added,
                                            mealName,
                                        )
                                    )
                                )
                                currentDestination = MainDestination.DAILY_PANEL
                            },
                        )
                    }

                    MainDestination.SAVED_MEALS -> {
                        com.controlcal.app.ui.savedmeals.SavedMealsRoute(
                            viewModel = savedMealsViewModel,
                            onBack = navigateBack,
                        )
                    }

                    MainDestination.SUPPLEMENTS -> {
                        com.controlcal.app.ui.supplement.SupplementsRoute(
                            viewModel = supplementsViewModel,
                            onBack = navigateBack,
                        )
                    }

                    MainDestination.RECIPES -> {
                        com.controlcal.app.ui.recipes.RecipesRoute(
                            viewModel = recipesViewModel,
                            onBack = navigateBack,
                            onCreateRecipe = {
                                navigationState.returnToPantryAfterRecipeEditor = false
                                recipeEditorViewModel.initialize(null)
                                currentDestination = MainDestination.RECIPE_EDITOR
                            },
                            onEditRecipe = { recipeId ->
                                navigationState.returnToPantryAfterRecipeEditor = false
                                recipeEditorViewModel.initialize(recipeId)
                                currentDestination = MainDestination.RECIPE_EDITOR
                            },
                        )
                    }

                    MainDestination.RECIPE_EDITOR -> {
                        com.controlcal.app.ui.recipes.editor.RecipeEditorRoute(
                            viewModel = recipeEditorViewModel,
                            onBack = navigateBack,
                            onRecipeSaved = { _ ->
                                if (navigationState.returnToPantryAfterRecipeEditor) {
                                    navigationState.returnToPantryAfterRecipeEditor = false
                                    pantryViewModel.loadProducts()
                                    currentDestination = MainDestination.PANTRY
                                } else {
                                    recipesViewModel.loadRecipes()
                                    currentDestination = MainDestination.RECIPES
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
