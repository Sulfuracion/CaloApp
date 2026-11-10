package com.controlcal.app.ui.recipes.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlcal.app.R
import com.controlcal.app.ui.feedback.HandleFeedbackEvents
import com.controlcal.core.model.Product
import com.controlcal.core.model.ResolvedRecipe
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.recipe.RecipeCalculationResult

@Composable
fun RecipeEditorRoute(
    viewModel: RecipeEditorViewModel,
    onBack: () -> Unit,
    onRecipeSaved: (ResolvedRecipe) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    HandleFeedbackEvents(
        events = viewModel.feedbackEvents,
        snackbarHostState = snackbarHostState,
    )

    RecipeEditorScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onNameChanged = viewModel::onNameChanged,
        onCookedWeightChanged = viewModel::onCookedWeightChanged,
        onServingsChanged = viewModel::onServingsChanged,
        onNotesChanged = viewModel::onNotesChanged,
        onIngredientGramsChanged = viewModel::onIngredientGramsChanged,
        onMoveIngredientUp = viewModel::onMoveIngredientUp,
        onMoveIngredientDown = viewModel::onMoveIngredientDown,
        onRemoveIngredient = viewModel::onRemoveIngredient,
        onOpenAddIngredientDialog = viewModel::onOpenAddIngredientDialog,
        onDismissAddIngredientDialog = viewModel::onDismissAddIngredientDialog,
        onIngredientSearchQueryChanged = viewModel::onIngredientSearchQueryChanged,
        onSelectCandidateProduct = viewModel::onSelectCandidateProduct,
        onCandidateGramsChanged = viewModel::onCandidateGramsChanged,
        onConfirmAddIngredient = viewModel::onConfirmAddIngredient,
        onSaveRecipe = { viewModel.saveRecipe(onRecipeSaved) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(
    uiState: RecipeEditorUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onCookedWeightChanged: (String) -> Unit,
    onServingsChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onIngredientGramsChanged: (tempId: String, text: String) -> Unit,
    onMoveIngredientUp: (tempId: String) -> Unit,
    onMoveIngredientDown: (tempId: String) -> Unit,
    onRemoveIngredient: (tempId: String) -> Unit,
    onOpenAddIngredientDialog: () -> Unit,
    onDismissAddIngredientDialog: () -> Unit,
    onIngredientSearchQueryChanged: (String) -> Unit,
    onSelectCandidateProduct: (Product) -> Unit,
    onCandidateGramsChanged: (String) -> Unit,
    onConfirmAddIngredient: () -> Unit,
    onSaveRecipe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.recipeId > 0L) {
                            stringResource(R.string.recipes_edit_title)
                        } else {
                            stringResource(R.string.recipes_new_title)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.recipe_editor_back),
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = onSaveRecipe,
                        enabled = uiState.isValid && !uiState.isSaving,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.recipe_editor_save_action))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    // Recipe Name
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = onNameChanged,
                        label = { Text(stringResource(R.string.recipe_editor_name_label)) },
                        isError = uiState.nameError != null,
                        supportingText = uiState.nameError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                // Ingredients Header & Button
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${stringResource(R.string.recipe_editor_ingredients_title)} (${uiState.ingredients.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )

                        OutlinedButton(
                            onClick = onOpenAddIngredientDialog,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.recipe_editor_add_ingredient_action))
                        }
                    }
                }

                if (uiState.ingredients.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.recipe_editor_no_ingredients),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = uiState.ingredients,
                        key = { it.tempId },
                    ) { item ->
                        val index = uiState.ingredients.indexOf(item)
                        RecipeEditorIngredientCard(
                            item = item,
                            isFirst = index == 0,
                            isLast = index == uiState.ingredients.size - 1,
                            onGramsChanged = { onIngredientGramsChanged(item.tempId, it) },
                            onMoveUp = { onMoveIngredientUp(item.tempId) },
                            onMoveDown = { onMoveIngredientDown(item.tempId) },
                            onRemove = { onRemoveIngredient(item.tempId) },
                        )
                    }
                }

                // Optional Cooking & Servings Section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.recipe_editor_optional_section_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )

                            // Cooked weight
                            OutlinedTextField(
                                value = uiState.cookedWeightGText,
                                onValueChange = onCookedWeightChanged,
                                label = { Text(stringResource(R.string.recipe_editor_cooked_weight_label)) },
                                isError = uiState.cookedWeightGError != null,
                                supportingText = {
                                    Text(
                                        uiState.cookedWeightGError
                                            ?: stringResource(R.string.recipe_editor_cooked_weight_helper)
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )

                            // Servings
                            OutlinedTextField(
                                value = uiState.servingsText,
                                onValueChange = onServingsChanged,
                                label = { Text(stringResource(R.string.recipe_editor_servings_label)) },
                                isError = uiState.servingsError != null,
                                supportingText = {
                                    Text(
                                        uiState.servingsError
                                            ?: stringResource(R.string.recipe_editor_servings_helper)
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )

                            // Notes
                            OutlinedTextField(
                                value = uiState.notes,
                                onValueChange = onNotesChanged,
                                label = { Text(stringResource(R.string.recipe_editor_notes_label)) },
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                }

                // Live Calculation Card
                val calculation = uiState.calculation
                if (calculation != null && uiState.ingredients.isNotEmpty()) {
                    item {
                        RecipeCalculationPreviewCard(
                            calculation = calculation,
                            cookedWeightEntered = uiState.cookedWeightGText.isNotBlank(),
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Add ingredient dialog
    if (uiState.showAddIngredientDialog) {
        AddIngredientSelectionDialog(
            searchQuery = uiState.ingredientSearchQuery,
            candidateProducts = uiState.candidateProducts,
            selectedProduct = uiState.selectedCandidateProduct,
            gramsText = uiState.candidateGramsText,
            gramsError = uiState.candidateGramsError,
            cycleError = uiState.cycleError,
            onSearchQueryChanged = onIngredientSearchQueryChanged,
            onSelectProduct = onSelectCandidateProduct,
            onGramsChanged = onCandidateGramsChanged,
            onConfirm = onConfirmAddIngredient,
            onDismiss = onDismissAddIngredientDialog,
        )
    }
}

@Composable
fun RecipeEditorIngredientCard(
    item: RecipeEditorIngredientItem,
    isFirst: Boolean,
    isLast: Boolean,
    onGramsChanged: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.product.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val brand = item.product.brand
                    if (!brand.isNullOrBlank()) {
                        Text(
                            text = brand,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Up / Down / Remove buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = !isFirst,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_up),
                            contentDescription = stringResource(R.string.recipe_editor_move_up),
                        )
                    }

                    IconButton(
                        onClick = onMoveDown,
                        enabled = !isLast,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_down),
                            contentDescription = stringResource(R.string.recipe_editor_move_down),
                        )
                    }

                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.recipe_editor_remove_ingredient),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Grams editable field
                OutlinedTextField(
                    value = item.gramsText,
                    onValueChange = onGramsChanged,
                    label = { Text(stringResource(R.string.recipe_editor_ingredient_grams_label)) },
                    suffix = { Text("g") },
                    isError = item.gramsError != null,
                    supportingText = item.gramsError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.width(130.dp),
                    shape = RoundedCornerShape(10.dp),
                )

                // Contribution preview for this ingredient
                val factor = item.grams / 100.0
                val cKcal = item.product.kcal100g * factor
                val cProt = item.product.protein100g * factor
                val cCarb = item.product.carbs100g * factor
                val cFat = item.product.fat100g * factor

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${NumberFormatHelper.formatDecimal(cKcal)} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "P: ${NumberFormatHelper.formatDecimal(cProt)} · " +
                            "C: ${NumberFormatHelper.formatDecimal(cCarb)} · " +
                            "G: ${NumberFormatHelper.formatDecimal(cFat)} g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun RecipeCalculationPreviewCard(
    calculation: RecipeCalculationResult,
    cookedWeightEntered: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.recipe_editor_preview_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = stringResource(R.string.recipe_editor_preview_per_100g),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // Base weight info
            val baseWeightStr = NumberFormatHelper.formatDecimal(calculation.baseWeightG)
            val baseWeightMsg = if (cookedWeightEntered) {
                stringResource(R.string.recipe_editor_base_weight_cooked, baseWeightStr)
            } else {
                stringResource(R.string.recipe_editor_base_weight_raw, baseWeightStr)
            }
            Text(
                text = baseWeightMsg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Main per 100g macros
            Text(
                text = "${NumberFormatHelper.formatDecimal(calculation.kcal100g)} kcal / 100 g",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "Proteínas: ${NumberFormatHelper.formatDecimal(calculation.protein100g)} g  ·  " +
                    "Hidratos: ${NumberFormatHelper.formatDecimal(calculation.carbs100g)} g  ·  " +
                    "Grasas: ${NumberFormatHelper.formatDecimal(calculation.fat100g)} g",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )

            // Secondary nutrients
            if (calculation.fiber100g != null || calculation.sugars100g != null || calculation.satFat100g != null || calculation.salt100g != null) {
                val secList = mutableListOf<String>()
                calculation.fiber100g?.let { secList.add("Fibra: ${NumberFormatHelper.formatDecimal(it)} g") }
                calculation.sugars100g?.let { secList.add("Azúcares: ${NumberFormatHelper.formatDecimal(it)} g") }
                calculation.satFat100g?.let { secList.add("Sat: ${NumberFormatHelper.formatDecimal(it)} g") }
                calculation.salt100g?.let { secList.add("Sal: ${NumberFormatHelper.formatDecimal(it)} g") }

                Text(
                    text = secList.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.recipe_editor_secondary_nutrients_incomplete),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            // Per serving display if available
            val servingG = calculation.servingG
            val servingKcal = calculation.servingKcal
            val servingProtein = calculation.servingProtein
            val servingCarbs = calculation.servingCarbs
            val servingFat = calculation.servingFat
            if (servingG != null && servingKcal != null && servingProtein != null && servingCarbs != null && servingFat != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = stringResource(
                                R.string.recipe_editor_preview_per_serving,
                                NumberFormatHelper.formatDecimal(servingG),
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${NumberFormatHelper.formatDecimal(servingKcal)} kcal  ·  " +
                                "P: ${NumberFormatHelper.formatDecimal(servingProtein)} g  ·  " +
                                "C: ${NumberFormatHelper.formatDecimal(servingCarbs)} g  ·  " +
                                "G: ${NumberFormatHelper.formatDecimal(servingFat)} g",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            // Totals of the recipe
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${stringResource(R.string.recipe_editor_preview_totals)}: " +
                    "${NumberFormatHelper.formatDecimal(calculation.totalKcal)} kcal " +
                    "(P: ${NumberFormatHelper.formatDecimal(calculation.totalProtein)} g · " +
                    "C: ${NumberFormatHelper.formatDecimal(calculation.totalCarbs)} g · " +
                    "G: ${NumberFormatHelper.formatDecimal(calculation.totalFat)} g)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Advisory warning banner if validation warnings exist
            if (!calculation.validationResult.isValid) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = stringResource(R.string.recipe_editor_advisory_warning),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        calculation.validationResult.issues.forEach { issue ->
                            val desc = when (issue.rule) {
                                com.controlcal.core.ocr.ValidationRule.CALORIC_BALANCE -> "Balance calórico: 4×P + 4×C + 9×G difiere de las kcal."
                                com.controlcal.core.ocr.ValidationRule.SUGARS_LEQ_CARBS -> "Los azúcares superan a los hidratos."
                                com.controlcal.core.ocr.ValidationRule.SAT_FAT_LEQ_FAT -> "Grasas saturadas superan grasas totales."
                                com.controlcal.core.ocr.ValidationRule.VALUE_LEQ_100 -> "Nutriente por 100g supera 100g."
                                com.controlcal.core.ocr.ValidationRule.KCAL_LEQ_900 -> "Calorías por 100g superan 900 kcal."
                                com.controlcal.core.ocr.ValidationRule.NON_NEGATIVE -> "Valores negativos detectados."
                            }
                            Text(
                                text = "• $desc",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddIngredientSelectionDialog(
    searchQuery: String,
    candidateProducts: List<Product>,
    selectedProduct: Product?,
    gramsText: String,
    gramsError: String?,
    cycleError: String?,
    onSearchQueryChanged: (String) -> Unit,
    onSelectProduct: (Product) -> Unit,
    onGramsChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.recipe_editor_select_ingredient_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text(stringResource(R.string.recipe_editor_select_ingredient_search_hint)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = null,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                // Cycle error banner if detected
                if (cycleError != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = cycleError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }

                // If a product is selected, display grams input
                if (selectedProduct != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        ) {
                            Text(
                                text = selectedProduct.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = gramsText,
                                onValueChange = onGramsChanged,
                                label = { Text(stringResource(R.string.recipe_editor_ingredient_grams_label)) },
                                suffix = { Text("g") },
                                isError = gramsError != null,
                                supportingText = gramsError?.let { { Text(it) } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                            )
                        }
                    }
                }

                // Candidate products list
                Text(
                    text = "Alimentos disponibles (${candidateProducts.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = candidateProducts,
                        key = { it.id },
                    ) { product ->
                        val isSelected = selectedProduct?.id == product.id
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProduct(product) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = product.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    val brand = product.brand
                                    if (!brand.isNullOrBlank()) {
                                        Text(
                                            text = brand,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Text(
                                    text = "${NumberFormatHelper.formatDecimal(product.kcal100g)} kcal",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = selectedProduct != null && gramsError == null && cycleError == null,
            ) {
                Text(stringResource(R.string.add_entry_confirm_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.recipes_delete_dialog_cancel))
            }
        },
    )
}
