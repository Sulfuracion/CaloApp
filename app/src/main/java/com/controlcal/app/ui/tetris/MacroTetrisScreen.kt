package com.controlcal.app.ui.tetris

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import com.controlcal.core.model.ConfiguredMeal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlcal.app.R
import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.Product
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.nutrition.NutritionCalculator
import com.controlcal.core.solver.MacroError
import com.controlcal.core.solver.MacroSolution
import com.controlcal.core.solver.MacroSolutionItem
import com.controlcal.core.solver.MacroSuggestion

@Composable
fun MacroTetrisRoute(
    viewModel: MacroTetrisViewModel,
    onBack: () -> Unit,
    onNavigateToPantry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onMessageConsumed()
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onBack()
        }
    }

    MacroTetrisScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onNavigateToPantry = onNavigateToPantry,
        onCalculate = viewModel::calculate,
        onExcludeProduct = viewModel::excludeProduct,
        onIncludeProduct = viewModel::includeProduct,
        onSelectMeal = viewModel::selectMeal,
        onAddToDiary = { viewModel.addToDiary() },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroTetrisScreen(
    uiState: MacroTetrisUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onNavigateToPantry: () -> Unit,
    onCalculate: () -> Unit,
    onExcludeProduct: (Long) -> Unit,
    onIncludeProduct: (Long) -> Unit,
    onSelectMeal: (String) -> Unit,
    onAddToDiary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.tetris_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.tetris_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToPantry) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pantry),
                            contentDescription = stringResource(R.string.pantry_action),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
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
                    RemainingMacrosCard(uiState = uiState)
                }

                if (uiState.isPantryEmpty) {
                    item {
                        EmptyPantryCard(onNavigateToPantry = onNavigateToPantry)
                    }
                } else if (!uiState.areTargetsMet) {
                    item {
                        CalculateActionBar(
                            hasCalculated = uiState.hasCalculated,
                            onCalculate = onCalculate,
                        )
                    }
                }

                val solution = uiState.solution
                if (solution != null && solution.items.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.tetris_solution_title, solution.items.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    items(solution.items, key = { it.product.id }) { item ->
                        SolutionItemCard(
                            item = item,
                            onExclude = { onExcludeProduct(item.product.id) },
                        )
                    }

                    if (uiState.excludedProducts.isNotEmpty()) {
                        item {
                            ExcludedProductsSection(
                                excludedProducts = uiState.excludedProducts,
                                onRestore = onIncludeProduct,
                            )
                        }
                    }

                    item {
                        MacroErrorCard(
                            target = solution.targetMacros,
                            achieved = solution.achievedMacros,
                            error = solution.error,
                        )
                    }

                    if (solution.suggestions.isNotEmpty()) {
                        item {
                            MacroSuggestionsCard(suggestions = solution.suggestions)
                        }
                    }

                    item {
                        AddToDiaryCard(
                            selectedMeal = uiState.selectedMeal,
                            availableMeals = uiState.availableMeals,
                            isAdding = uiState.isAddingToDiary,
                            onSelectMeal = onSelectMeal,
                            onAddToDiary = onAddToDiary,
                        )
                    }
                } else if (uiState.hasCalculated && !uiState.areTargetsMet && !uiState.isPantryEmpty) {
                    item {
                        NoSolutionCard()
                    }
                    if (uiState.excludedProducts.isNotEmpty()) {
                        item {
                            ExcludedProductsSection(
                                excludedProducts = uiState.excludedProducts,
                                onRestore = onIncludeProduct,
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun RemainingMacrosCard(
    uiState: MacroTetrisUiState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.tetris_remaining_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.tetris_remaining_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.areTargetsMet) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.tetris_targets_met_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = stringResource(R.string.tetris_targets_met_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            } else {
                val targets = uiState.targetMacros
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    RemainingBadge(
                        label = stringResource(R.string.settings_kcal_label),
                        value = "${NumberFormatHelper.formatDecimal(targets.kcal)} kcal",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    RemainingBadge(
                        label = stringResource(R.string.daily_panel_protein_label),
                        value = "${NumberFormatHelper.formatDecimal(targets.protein)} g",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    RemainingBadge(
                        label = stringResource(R.string.daily_panel_carbs_label),
                        value = "${NumberFormatHelper.formatDecimal(targets.carbs)} g",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    RemainingBadge(
                        label = stringResource(R.string.daily_panel_fat_label),
                        value = "${NumberFormatHelper.formatDecimal(targets.fat)} g",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RemainingBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CalculateActionBar(
    hasCalculated: Boolean,
    onCalculate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onCalculate,
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_macro_tetris),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (hasCalculated) {
                stringResource(R.string.tetris_recalculate_action)
            } else {
                stringResource(R.string.tetris_calculate_action)
            }
        )
    }
}

@Composable
private fun EmptyPantryCard(
    onNavigateToPantry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.tetris_empty_pantry_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.tetris_empty_pantry_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onNavigateToPantry,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_pantry),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.tetris_go_to_pantry_action))
            }
        }
    }
}

@Composable
private fun NoSolutionCard(
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.tetris_no_solution_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.tetris_no_solution_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SolutionItemCard(
    item: MacroSolutionItem,
    onExclude: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                val brand = item.product.brand
                if (!brand.isNullOrBlank()) {
                    Text(
                        text = brand,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Quantity display: in units if available, and in grams
                val unitCount = item.unitCount
                val unitName = item.unitName
                val quantityText = if (item.isUnitBased && unitCount != null && unitName != null) {
                    val countStr = if (unitCount % 1.0 == 0.0) {
                        unitCount.toLong().toString()
                    } else {
                        NumberFormatHelper.formatDecimal(unitCount)
                    }
                    stringResource(
                        R.string.tetris_unit_quantity_format,
                        countStr,
                        unitName,
                        NumberFormatHelper.formatDecimal(item.roundedGrams),
                    )
                } else {
                    stringResource(
                        R.string.tetris_grams_quantity_format,
                        NumberFormatHelper.formatDecimal(item.roundedGrams),
                    )
                }

                Text(
                    text = quantityText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.height(4.dp))

                val portion = NutritionCalculator.calculatePortionMacros(item.product, item.roundedGrams)
                Text(
                    text = stringResource(
                        R.string.tetris_portion_macros,
                        NumberFormatHelper.formatDecimal(portion.kcal),
                        NumberFormatHelper.formatDecimal(portion.protein),
                        NumberFormatHelper.formatDecimal(portion.carbs),
                        NumberFormatHelper.formatDecimal(portion.fat),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = onExclude,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.tetris_exclude_action))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExcludedProductsSection(
    excludedProducts: List<Product>,
    onRestore: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.tetris_excluded_section_title, excludedProducts.size),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            excludedProducts.forEach { product ->
                InputChip(
                    selected = false,
                    onClick = { onRestore(product.id) },
                    label = { Text(product.name) },
                    trailingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = stringResource(R.string.tetris_restore_content_description, product.name),
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun MacroErrorCard(
    target: com.controlcal.core.model.MacroTargets,
    achieved: com.controlcal.core.model.NutrientTotals,
    error: MacroError,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.tetris_error_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.tetris_error_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.tetris_error_col_macro),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1.2f),
                )
                Text(
                    text = stringResource(R.string.tetris_error_col_target),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.tetris_error_col_achieved),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.tetris_error_col_diff),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1.2f),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            MacroRow(
                macroName = stringResource(R.string.settings_kcal_label),
                target = "${NumberFormatHelper.formatDecimal(target.kcal)}",
                achieved = "${NumberFormatHelper.formatDecimal(achieved.kcal)}",
                diff = formatDiff(error.diffKcal, "kcal"),
                percent = error.percentKcal,
            )

            MacroRow(
                macroName = stringResource(R.string.daily_panel_protein_label),
                target = "${NumberFormatHelper.formatDecimal(target.protein)} g",
                achieved = "${NumberFormatHelper.formatDecimal(achieved.protein)} g",
                diff = formatDiff(error.diffProtein, "g"),
                percent = error.percentProtein,
            )

            MacroRow(
                macroName = stringResource(R.string.daily_panel_carbs_label),
                target = "${NumberFormatHelper.formatDecimal(target.carbs)} g",
                achieved = "${NumberFormatHelper.formatDecimal(achieved.carbs)} g",
                diff = formatDiff(error.diffCarbs, "g"),
                percent = error.percentCarbs,
            )

            MacroRow(
                macroName = stringResource(R.string.daily_panel_fat_label),
                target = "${NumberFormatHelper.formatDecimal(target.fat)} g",
                achieved = "${NumberFormatHelper.formatDecimal(achieved.fat)} g",
                diff = formatDiff(error.diffFat, "g"),
                percent = error.percentFat,
            )
        }
    }
}

@Composable
private fun MacroRow(
    macroName: String,
    target: String,
    achieved: String,
    diff: String,
    percent: Double,
    modifier: Modifier = Modifier,
) {
    val isHighError = percent > 10.0
    val errorColor = if (isHighError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = macroName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.2f),
        )
        Text(
            text = target,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = achieved,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$diff (${NumberFormatHelper.formatDecimal(percent)}%)",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isHighError) FontWeight.Bold else FontWeight.Normal,
            color = errorColor,
            modifier = Modifier.weight(1.2f),
        )
    }
}

private fun formatDiff(diff: Double, unit: String): String {
    val sign = if (diff > 0.0) "+" else ""
    return "$sign${NumberFormatHelper.formatDecimal(diff)} $unit"
}

@Composable
private fun MacroSuggestionsCard(
    suggestions: List<MacroSuggestion>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.tetris_suggestions_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.tetris_suggestions_subtitle),
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(modifier = Modifier.height(8.dp))

            suggestions.forEach { suggestion ->
                Text(
                    text = "• ${suggestion.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun AddToDiaryCard(
    selectedMeal: String,
    availableMeals: List<ConfiguredMeal>,
    isAdding: Boolean,
    onSelectMeal: (String) -> Unit,
    onAddToDiary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.tetris_meal_destination_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.tetris_meal_destination_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableMeals.forEach { meal ->
                    val isSelected = selectedMeal == meal.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectMeal(meal.id) },
                        label = { Text(meal.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onAddToDiary,
                enabled = !isAdding,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (isAdding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.tetris_adding_to_diary))
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.tetris_add_to_diary_action))
                }
            }
        }
    }
}
