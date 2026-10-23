package com.controlcal.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlcal.app.R
import com.controlcal.app.ui.feedback.HandleFeedbackEvents
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.NutrientTotals
import com.controlcal.core.model.Product
import com.controlcal.core.nutrition.NumberFormatHelper
import java.time.LocalTime

@Composable
fun MealDetailRoute(
    viewModel: MealDetailViewModel,
    onBack: () -> Unit,
    onNavigateToAddFood: (mealId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    HandleFeedbackEvents(
        events = viewModel.feedbackEvents,
        snackbarHostState = snackbarHostState,
    )

    MealDetailScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onNavigateToAddFood = { onNavigateToAddFood(uiState.currentMealId) },
        onSwitchMeal = viewModel::onSwitchMeal,
        onIncreaseEntryQuantity = viewModel::onIncreaseEntryQuantity,
        onDecreaseEntryQuantity = viewModel::onDecreaseEntryQuantity,
        onOpenTimePicker = viewModel::onOpenTimePicker,
        onDismissTimePicker = viewModel::onDismissTimePicker,
        onMealTimeChanged = viewModel::onMealTimeChanged,
        onDeleteEntry = viewModel::onDeleteEntry,
        onOpenTareDialog = viewModel::onOpenTareDialog,
        onTareProductSelected = viewModel::onTareProductSelected,
        onTareGramsChanged = viewModel::onTareGramsChanged,
        onConfirmTare = viewModel::onConfirmTare,
        onDismissTareDialog = viewModel::onDismissTareDialog,
        onOpenAddQuickCaloriesDialog = viewModel::onOpenAddQuickCaloriesDialog,
        onOpenEditQuickCaloriesDialog = viewModel::onOpenEditQuickCaloriesDialog,
        onQuickKcalChanged = viewModel::onQuickKcalChanged,
        onQuickProteinChanged = viewModel::onQuickProteinChanged,
        onQuickCarbsChanged = viewModel::onQuickCarbsChanged,
        onQuickFatChanged = viewModel::onQuickFatChanged,
        onQuickDescriptionChanged = viewModel::onQuickDescriptionChanged,
        onConfirmQuickCalories = viewModel::onConfirmQuickCalories,
        onDismissQuickCaloriesDialog = viewModel::onDismissQuickCaloriesDialog,
        onRepeatMeal = viewModel::onRepeatMeal,
        onCopyYesterday = viewModel::onCopyYesterday,
        onToggleDropdown = viewModel::onToggleDropdown,
        onToggleOptionsMenu = viewModel::onToggleOptionsMenu,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailScreen(
    uiState: MealDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onNavigateToAddFood: () -> Unit,
    onSwitchMeal: (String) -> Unit,
    onIncreaseEntryQuantity: (MealEntry) -> Unit,
    onDecreaseEntryQuantity: (MealEntry) -> Unit,
    onOpenTimePicker: () -> Unit,
    onDismissTimePicker: () -> Unit,
    onMealTimeChanged: (LocalTime) -> Unit,
    onDeleteEntry: (MealEntry) -> Unit,
    onOpenTareDialog: () -> Unit,
    onTareProductSelected: (Product) -> Unit,
    onTareGramsChanged: (String) -> Unit,
    onConfirmTare: () -> Unit,
    onDismissTareDialog: () -> Unit,
    onOpenAddQuickCaloriesDialog: () -> Unit,
    onOpenEditQuickCaloriesDialog: (MealEntry) -> Unit,
    onQuickKcalChanged: (String) -> Unit,
    onQuickProteinChanged: (String) -> Unit,
    onQuickCarbsChanged: (String) -> Unit,
    onQuickFatChanged: (String) -> Unit,
    onQuickDescriptionChanged: (String) -> Unit,
    onConfirmQuickCalories: () -> Unit,
    onDismissQuickCaloriesDialog: () -> Unit,
    onRepeatMeal: () -> Unit,
    onCopyYesterday: () -> Unit,
    onToggleDropdown: (Boolean) -> Unit,
    onToggleOptionsMenu: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(role = Role.Button) { onToggleDropdown(true) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = uiState.currentMealName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    painter = painterResource(R.drawable.ic_chevron_right),
                                    contentDescription = stringResource(R.string.meal_detail_switch_meal),
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            DropdownMenu(
                                expanded = uiState.isDropdownExpanded,
                                onDismissRequest = { onToggleDropdown(false) },
                            ) {
                                uiState.availableMeals.forEach { meal ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = meal.name,
                                                fontWeight = if (meal.id == uiState.currentMealId) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        onClick = { onSwitchMeal(meal.id) },
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button) { onOpenTimePicker() }
                                .padding(horizontal = 4.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_schedule),
                                contentDescription = stringResource(R.string.meal_detail_time_picker_description),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = uiState.formattedMealTime,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.scanner_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onBack) {
                        Text(
                            text = stringResource(R.string.meal_detail_done_action),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Box {
                        IconButton(onClick = { onToggleOptionsMenu(true) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = stringResource(R.string.meal_detail_options_menu),
                            )
                        }
                        DropdownMenu(
                            expanded = uiState.isOptionsMenuExpanded,
                            onDismissRequest = { onToggleOptionsMenu(false) },
                        ) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.meal_detail_add_quick_calories_action)) },
                                onClick = {
                                    onToggleOptionsMenu(false)
                                    onOpenAddQuickCaloriesDialog()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.meal_detail_tare_action)) },
                                onClick = onOpenTareDialog,
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.meal_detail_repeat_meal_action)) },
                                onClick = onRepeatMeal,
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.meal_detail_copy_yesterday_action)) },
                                onClick = onCopyYesterday,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onOpenAddQuickCaloriesDialog,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.meal_detail_add_quick_calories_action))
                    }
                    Button(
                        onClick = onNavigateToAddFood,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.meal_detail_add_food_action))
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                MealNutritionSummaryCard(totals = uiState.totals)
            }

            if (uiState.entries.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.meal_detail_empty_entries),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(onClick = onOpenAddQuickCaloriesDialog) {
                                Text(text = stringResource(R.string.meal_detail_add_quick_calories_action))
                            }
                            Button(onClick = onNavigateToAddFood) {
                                Text(text = stringResource(R.string.meal_detail_add_food_action))
                            }
                        }
                    }
                }
            } else {
                items(uiState.entries, key = { it.entry.id }) { itemUi ->
                    MealDetailEntryRow(
                        item = itemUi,
                        onIncreaseClick = { onIncreaseEntryQuantity(itemUi.entry) },
                        onDecreaseClick = { onDecreaseEntryQuantity(itemUi.entry) },
                        onEditClick = { onOpenEditQuickCaloriesDialog(itemUi.entry) },
                        onDeleteClick = { onDeleteEntry(itemUi.entry) },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (uiState.showTareDialog) {
        TareDialog(
            products = uiState.tareEligibleProducts,
            selectedProduct = uiState.selectedTareProduct,
            gramsText = uiState.tareGramsText,
            gramsError = uiState.tareGramsError,
            isSaving = uiState.isSaving,
            onProductSelected = onTareProductSelected,
            onGramsChanged = onTareGramsChanged,
            onConfirm = onConfirmTare,
            onDismiss = onDismissTareDialog,
        )
    }

    if (uiState.showTimePickerDialog) {
        MealTimePickerDialog(
            initialTime = uiState.mealTime,
            isSaving = uiState.isSaving,
            onConfirm = onMealTimeChanged,
            onDismiss = onDismissTimePicker,
        )
    }

    if (uiState.showQuickCaloriesDialog) {
        QuickCaloriesDialog(
            isEditing = uiState.editingQuickCaloriesEntry != null,
            kcalText = uiState.quickKcalText,
            proteinText = uiState.quickProteinText,
            carbsText = uiState.quickCarbsText,
            fatText = uiState.quickFatText,
            descriptionText = uiState.quickDescriptionText,
            kcalError = uiState.quickKcalError,
            atwaterWarning = uiState.quickAtwaterWarning,
            isSaveEnabled = uiState.isQuickCaloriesSaveEnabled,
            isSaving = uiState.isSaving,
            onKcalChanged = onQuickKcalChanged,
            onProteinChanged = onQuickProteinChanged,
            onCarbsChanged = onQuickCarbsChanged,
            onFatChanged = onQuickFatChanged,
            onDescriptionChanged = onQuickDescriptionChanged,
            onConfirm = onConfirmQuickCalories,
            onDismiss = onDismissQuickCaloriesDialog,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealTimePickerDialog(
    initialTime: LocalTime,
    isSaving: Boolean = false,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.meal_detail_time_picker_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute))
                },
                enabled = !isSaving,
            ) {
                Text(text = stringResource(R.string.date_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(text = stringResource(R.string.daily_panel_cancel_action))
            }
        },
    )
}

@Composable
private fun MealNutritionSummaryCard(
    totals: NutrientTotals,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${NumberFormatHelper.formatDecimal(totals.kcal)} ${stringResource(R.string.daily_panel_kcal_label)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MacroSummaryBadge(
                    label = stringResource(R.string.daily_panel_protein_label),
                    value = totals.protein,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                MacroSummaryBadge(
                    label = stringResource(R.string.daily_panel_carbs_label),
                    value = totals.carbs,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                MacroSummaryBadge(
                    label = stringResource(R.string.daily_panel_fat_label),
                    value = totals.fat,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MacroSummaryBadge(
    label: String,
    value: Double,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = "${NumberFormatHelper.formatDecimal(value)} g",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MealDetailEntryRow(
    item: MealDetailEntryUiModel,
    onIncreaseClick: () -> Unit,
    onDecreaseClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (item.isQuickCalories) {
                    Modifier.clickable(role = Role.Button, onClick = onEditClick)
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = if (item.isTare) {
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
            )
        } else {
            CardDefaults.outlinedCardColors()
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.entry.productName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (item.isTare) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.meal_detail_tare_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                val subtitle = if (item.isQuickCalories) {
                    item.formattedKcal
                } else {
                    "${item.formattedKcal} · ${item.formattedQuantity}"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.isTare) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (item.isQuickCalories) {
                    IconButton(
                        onClick = onEditClick,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = stringResource(R.string.quick_calories_dialog_title_edit),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else if (!item.isTare) {
                    IconButton(
                        onClick = onDecreaseClick,
                        enabled = item.canDecrease,
                    ) {
                        Text(
                            text = "−",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (item.canDecrease) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
                        )
                    }
                    IconButton(
                        onClick = onIncreaseClick,
                    ) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                IconButton(
                    onClick = onDeleteClick,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.meal_detail_delete_food_description, item.entry.productName),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun TareDialog(
    products: List<Product>,
    selectedProduct: Product?,
    gramsText: String,
    gramsError: String?,
    isSaving: Boolean = false,
    onProductSelected: (Product) -> Unit,
    onGramsChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.meal_detail_tare_dialog_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.meal_detail_tare_dialog_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = stringResource(R.string.meal_detail_tare_select_food),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    products.forEach { product ->
                        FilterChip(
                            selected = product.id == selectedProduct?.id,
                            onClick = { onProductSelected(product) },
                            label = { Text(text = product.name) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                OutlinedTextField(
                    value = gramsText,
                    onValueChange = onGramsChanged,
                    label = { Text(text = stringResource(R.string.meal_detail_tare_leftover_grams)) },
                    isError = gramsError != null,
                    supportingText = gramsError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = gramsError == null && gramsText.isNotBlank() && !isSaving,
            ) {
                Text(text = stringResource(R.string.meal_detail_tare_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(text = stringResource(R.string.daily_panel_cancel_action))
            }
        },
    )
}

@Composable
private fun QuickCaloriesDialog(
    isEditing: Boolean,
    kcalText: String,
    proteinText: String,
    carbsText: String,
    fatText: String,
    descriptionText: String,
    kcalError: String?,
    atwaterWarning: String?,
    isSaveEnabled: Boolean,
    isSaving: Boolean = false,
    onKcalChanged: (String) -> Unit,
    onProteinChanged: (String) -> Unit,
    onCarbsChanged: (String) -> Unit,
    onFatChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                text = stringResource(
                    if (isEditing) R.string.quick_calories_dialog_title_edit
                    else R.string.quick_calories_dialog_title_add
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = kcalText,
                    onValueChange = onKcalChanged,
                    label = { Text(text = stringResource(R.string.quick_calories_kcal_label)) },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = kcalError != null,
                    supportingText = kcalError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = proteinText,
                        onValueChange = onProteinChanged,
                        label = { Text(text = stringResource(R.string.quick_calories_protein_label)) },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = carbsText,
                        onValueChange = onCarbsChanged,
                        label = { Text(text = stringResource(R.string.quick_calories_carbs_label)) },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = fatText,
                        onValueChange = onFatChanged,
                        label = { Text(text = stringResource(R.string.quick_calories_fat_label)) },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = onDescriptionChanged,
                    label = { Text(text = stringResource(R.string.quick_calories_description_label)) },
                    placeholder = { Text(stringResource(R.string.quick_calories_default_description)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (atwaterWarning != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = atwaterWarning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = isSaveEnabled && !isSaving,
            ) {
                Text(text = stringResource(R.string.date_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(text = stringResource(R.string.daily_panel_cancel_action))
            }
        },
    )
}
