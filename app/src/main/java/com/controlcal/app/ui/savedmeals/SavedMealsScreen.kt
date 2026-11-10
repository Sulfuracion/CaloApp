package com.controlcal.app.ui.savedmeals

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlcal.app.R
import com.controlcal.app.ui.feedback.HandleFeedbackEvents
import com.controlcal.core.model.Product
import com.controlcal.core.model.ResolvedSavedMeal
import com.controlcal.core.model.ResolvedSavedMealItem
import com.controlcal.core.model.SavedMealItem
import com.controlcal.core.nutrition.NumberFormatHelper

@Composable
fun SavedMealsRoute(
    viewModel: SavedMealsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    HandleFeedbackEvents(
        events = viewModel.feedbackEvents,
        snackbarHostState = snackbarHostState,
    )

    SavedMealsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onToggleExpand = viewModel::onToggleExpandMeal,
        onOpenRename = viewModel::onOpenRenameDialog,
        onOpenDelete = viewModel::onOpenDeleteDialog,
        onOpenEditGrams = viewModel::onOpenEditGramsDialog,
        onRemoveItem = viewModel::onRemoveItem,
        onOpenAddProduct = viewModel::onOpenAddProductDialog,
        onRenameTextChanged = viewModel::onRenameTextChanged,
        onConfirmRename = viewModel::onConfirmRename,
        onDismissRename = viewModel::onDismissRenameDialog,
        onConfirmDelete = viewModel::onConfirmDelete,
        onDismissDelete = viewModel::onDismissDeleteDialog,
        onEditGramsChanged = viewModel::onEditGramsChanged,
        onConfirmEditGrams = viewModel::onConfirmEditGrams,
        onDismissEditGrams = viewModel::onDismissEditGramsDialog,
        onAddProductSearchChanged = viewModel::onAddProductSearchQueryChanged,
        onSelectProductForAdd = viewModel::onSelectProductForAdd,
        onAddGramsChanged = viewModel::onAddGramsChanged,
        onConfirmAddProduct = viewModel::onConfirmAddProduct,
        onDismissAddProduct = viewModel::onDismissAddProductDialog,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedMealsScreen(
    uiState: SavedMealsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onToggleExpand: (Long) -> Unit,
    onOpenRename: (ResolvedSavedMeal) -> Unit,
    onOpenDelete: (ResolvedSavedMeal) -> Unit,
    onOpenEditGrams: (Long, SavedMealItem) -> Unit,
    onRemoveItem: (Long, SavedMealItem) -> Unit,
    onOpenAddProduct: (Long) -> Unit,
    onRenameTextChanged: (String) -> Unit,
    onConfirmRename: () -> Unit,
    onDismissRename: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onEditGramsChanged: (String) -> Unit,
    onConfirmEditGrams: () -> Unit,
    onDismissEditGrams: () -> Unit,
    onAddProductSearchChanged: (String) -> Unit,
    onSelectProductForAdd: (Product) -> Unit,
    onAddGramsChanged: (String) -> Unit,
    onConfirmAddProduct: () -> Unit,
    onDismissAddProduct: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.saved_meals_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (uiState.meals.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pantry),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.saved_meals_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.saved_meals_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                items(uiState.meals, key = { it.meal.id }) { resolvedMeal ->
                    SavedMealCard(
                        resolvedMeal = resolvedMeal,
                        isExpanded = uiState.expandedMealId == resolvedMeal.meal.id,
                        onToggleExpand = { onToggleExpand(resolvedMeal.meal.id) },
                        onRename = { onOpenRename(resolvedMeal) },
                        onDelete = { onOpenDelete(resolvedMeal) },
                        onEditItemGrams = { item -> onOpenEditGrams(resolvedMeal.meal.id, item) },
                        onRemoveItem = { item -> onRemoveItem(resolvedMeal.meal.id, item) },
                        onAddProduct = { onOpenAddProduct(resolvedMeal.meal.id) },
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    // --- Dialogs ---
    uiState.renamingMeal?.let {
        RenameSavedMealDialog(
            name = uiState.renameText,
            nameError = uiState.renameError,
            isSaving = uiState.isSaving,
            onNameChanged = onRenameTextChanged,
            onConfirm = onConfirmRename,
            onDismiss = onDismissRename,
        )
    }

    uiState.deletingMeal?.let { meal ->
        DeleteSavedMealDialog(
            mealName = meal.meal.name,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete,
        )
    }

    uiState.editingMealItem?.let { (_, item) ->
        EditItemGramsDialog(
            itemName = item.productName,
            gramsText = uiState.editGramsText,
            gramsError = uiState.editGramsError,
            onGramsChanged = onEditGramsChanged,
            onConfirm = onConfirmEditGrams,
            onDismiss = onDismissEditGrams,
        )
    }

    if (uiState.addingItemToMealId != null) {
        val currentMeal = uiState.meals.find { it.meal.id == uiState.addingItemToMealId }
        AddProductToMealDialog(
            mealName = currentMeal?.meal?.name ?: "",
            searchQuery = uiState.addProductSearchQuery,
            searchResults = uiState.addProductSearchResults,
            selectedProduct = uiState.selectedProductForAdd,
            gramsText = uiState.addGramsText,
            gramsError = uiState.addGramsError,
            onSearchQueryChanged = onAddProductSearchChanged,
            onSelectProduct = onSelectProductForAdd,
            onGramsChanged = onAddGramsChanged,
            onConfirm = onConfirmAddProduct,
            onDismiss = onDismissAddProduct,
        )
    }
}

@Composable
private fun SavedMealCard(
    resolvedMeal: ResolvedSavedMeal,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onEditItemGrams: (SavedMealItem) -> Unit,
    onRemoveItem: (SavedMealItem) -> Unit,
    onAddProduct: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header: Name and action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = resolvedMeal.meal.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${NumberFormatHelper.formatInteger(resolvedMeal.totalKcal.toInt())} kcal  •  " +
                            "P: ${NumberFormatHelper.formatDecimal(resolvedMeal.totalProtein)} g  •  " +
                            "C: ${NumberFormatHelper.formatDecimal(resolvedMeal.totalCarbs)} g  •  " +
                            "G: ${NumberFormatHelper.formatDecimal(resolvedMeal.totalFat)} g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRename,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = stringResource(R.string.saved_meals_rename_dialog_title),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.saved_meals_delete_confirm),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isExpanded) R.drawable.ic_arrow_back else R.drawable.ic_chevron_right
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Warning if any product is archived
            if (resolvedMeal.hasArchivedProducts) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                ) {
                    Text(
                        text = stringResource(R.string.saved_meals_archived_product_warning),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // Expandable items list
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    resolvedMeal.resolvedItems.forEach { resolvedItem ->
                        SavedMealItemRow(
                            resolvedItem = resolvedItem,
                            onEditGrams = { onEditItemGrams(resolvedItem.item) },
                            onRemove = { onRemoveItem(resolvedItem.item) },
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onAddProduct,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.saved_meals_add_item_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedMealItemRow(
    resolvedItem: ResolvedSavedMealItem,
    onEditGrams: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onEditGrams),
        ) {
            Text(
                text = resolvedItem.item.productName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            val quantityText = if (resolvedItem.item.productId != null) {
                "${NumberFormatHelper.formatDecimal(resolvedItem.resolvedGrams)} g  •  "
            } else ""
            Text(
                text = "$quantityText${NumberFormatHelper.formatInteger(resolvedItem.resolvedKcal.toInt())} kcal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (resolvedItem.isArchived) {
                Text(
                    text = "Archivado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onEditGrams,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.saved_meals_edit_grams_title),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.saved_meals_remove_item_action),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun RenameSavedMealDialog(
    name: String,
    nameError: String?,
    isSaving: Boolean,
    onNameChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(text = stringResource(R.string.saved_meals_rename_dialog_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChanged,
                label = { Text(stringResource(R.string.saved_meals_save_dialog_label)) },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSaving && name.isNotBlank(),
            ) {
                Text(text = stringResource(R.string.saved_meals_save_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(text = stringResource(R.string.saved_meals_save_dialog_cancel))
            }
        },
    )
}

@Composable
private fun DeleteSavedMealDialog(
    mealName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.saved_meals_delete_dialog_title, mealName)) },
        text = { Text(text = stringResource(R.string.saved_meals_delete_dialog_message)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = stringResource(R.string.saved_meals_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.saved_meals_save_dialog_cancel))
            }
        },
    )
}

@Composable
private fun EditItemGramsDialog(
    itemName: String,
    gramsText: String,
    gramsError: String?,
    onGramsChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.saved_meals_edit_grams_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = itemName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = onGramsChanged,
                    label = { Text("Gramos") },
                    isError = gramsError != null,
                    supportingText = gramsError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = gramsText.isNotBlank()) {
                Text(text = stringResource(R.string.saved_meals_save_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.saved_meals_save_dialog_cancel))
            }
        },
    )
}

@Composable
private fun AddProductToMealDialog(
    mealName: String,
    searchQuery: String,
    searchResults: List<Product>,
    selectedProduct: Product?,
    gramsText: String,
    gramsError: String?,
    onSearchQueryChanged: (String) -> Unit,
    onSelectProduct: (Product) -> Unit,
    onGramsChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.saved_meals_add_item_title, mealName)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (selectedProduct == null) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChanged,
                        placeholder = { Text(stringResource(R.string.add_entry_search_placeholder)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = null,
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(searchResults, key = { it.id }) { product ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectProduct(product) }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = product.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${NumberFormatHelper.formatInteger(product.kcal100g.toInt())} kcal/100g",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                } else {
                    Text(
                        text = selectedProduct.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = gramsText,
                        onValueChange = onGramsChanged,
                        label = { Text("Gramos") },
                        isError = gramsError != null,
                        supportingText = gramsError?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (selectedProduct != null) {
                Button(onClick = onConfirm, enabled = gramsText.isNotBlank()) {
                    Text(text = stringResource(R.string.saved_meals_save_dialog_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.saved_meals_save_dialog_cancel))
            }
        },
    )
}
