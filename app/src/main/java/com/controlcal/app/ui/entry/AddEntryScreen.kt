package com.controlcal.app.ui.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlcal.app.R
import com.controlcal.core.model.Product
import com.controlcal.app.data.off.OffProduct
import com.controlcal.app.data.off.OffSearchItem
import com.controlcal.core.nutrition.NumberFormatHelper

@Composable
fun AddEntryRoute(
    viewModel: AddEntryViewModel,
    onBack: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToOcrCapture: () -> Unit,
    onNavigateToManualEntry: () -> Unit,
    onNavigateToConfirmation: (OffProduct) -> Unit,
    onEntryAdded: (com.controlcal.core.nutrition.MealEntrySaveResult?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isAddedSuccessfully) {
        if (uiState.isAddedSuccessfully) {
            onEntryAdded(uiState.lastSaveResult)
        }
    }

    AddEntryScreen(
        uiState = uiState,
        onBack = onBack,
        onNavigateToScanner = onNavigateToScanner,
        onNavigateToOcrCapture = onNavigateToOcrCapture,
        onNavigateToManualEntry = onNavigateToManualEntry,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onSelectProduct = viewModel::onSelectProductForPortion,
        onQuickAddHabitual = { habitual ->
            viewModel.onQuickAddHabitual(habitual)
        },
        onDismissPortionDialog = viewModel::onDismissPortionDialog,
        onToggleMeasuringUnit = viewModel::onToggleMeasuringUnit,
        onQuantityInputChanged = viewModel::onQuantityInputChanged,
        onConfirmAddProduct = {
            viewModel.onConfirmAddProduct()
        },
        onSearchInOff = viewModel::onSearchInOff,
        onSelectOffProduct = { item -> viewModel.onSelectOffProduct(item, onNavigateToConfirmation) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryScreen(
    uiState: AddEntryUiState,
    onBack: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToOcrCapture: () -> Unit,
    onNavigateToManualEntry: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSelectProduct: (Product) -> Unit,
    onQuickAddHabitual: (HabitualFoodUiModel) -> Unit,
    onDismissPortionDialog: () -> Unit,
    onToggleMeasuringUnit: () -> Unit,
    onQuantityInputChanged: (String) -> Unit,
    onConfirmAddProduct: () -> Unit,
    onSearchInOff: () -> Unit,
    onSelectOffProduct: (OffSearchItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.add_entry_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (uiState.targetMealName.isNotEmpty()) {
                            Text(
                                text = uiState.targetMealName,
                                style = MaterialTheme.typography.bodySmall,
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
                    IconButton(onClick = onNavigateToScanner) {
                        Icon(
                            painter = painterResource(R.drawable.ic_document_scanner),
                            contentDescription = stringResource(R.string.add_entry_scan_barcode_action),
                        )
                    }
                    IconButton(onClick = onNavigateToOcrCapture) {
                        Icon(
                            painter = painterResource(R.drawable.ic_camera),
                            contentDescription = stringResource(R.string.add_entry_photo_ocr_action),
                        )
                    }
                },
            )
        },
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
                // Search box
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text(text = stringResource(R.string.add_entry_search_placeholder)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
            }

            // Quick Habituals section when query is empty
            if (uiState.searchQuery.isEmpty() && uiState.habitualFoods.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.add_entry_recent_frequent_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                items(uiState.habitualFoods, key = { "habitual_${it.product.id}" }) { habitual ->
                    HabitualFoodRow(
                        habitual = habitual,
                        onRowClick = { onSelectProduct(habitual.product) },
                        onQuickAddClick = { onQuickAddHabitual(habitual) },
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                }
            }

            // Products list header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (uiState.searchQuery.isEmpty()) {
                            stringResource(R.string.add_entry_all_products_title)
                        } else {
                            stringResource(R.string.add_entry_all_products_title) + " (${uiState.searchResults.size})"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = onNavigateToManualEntry) {
                        Text(text = stringResource(R.string.add_entry_create_manual_action))
                    }
                }
            }

            if (uiState.searchResults.isEmpty() && uiState.searchQuery.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.add_entry_empty_search),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onNavigateToManualEntry) {
                            Text(text = stringResource(R.string.add_entry_create_manual_action))
                        }
                    }
                }
            } else {
                items(uiState.searchResults, key = { "product_${it.id}" }) { product ->
                    ProductRow(
                        product = product,
                        onClick = { onSelectProduct(product) },
                    )
                }
            }

            if (uiState.searchQuery.isNotBlank()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (uiState.isOffSearching) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.add_entry_search_off_loading),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                } else {
                    item {
                        OutlinedCard(
                            onClick = onSearchInOff,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_search),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = stringResource(R.string.add_entry_search_off_action, uiState.searchQuery.trim()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                if (!uiState.offSearchMessage.isNullOrBlank()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                        ) {
                            Text(
                                text = uiState.offSearchMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }

                if (uiState.offSearchResults.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.add_entry_off_results_title, uiState.offSearchResults.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    items(uiState.offSearchResults, key = { "off_${it.code}_${it.name}_${it.kcal100g}" }) { offItem ->
                        OffSearchItemRow(
                            item = offItem,
                            onClick = { onSelectOffProduct(offItem) },
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (uiState.isFetchingOffProductDetails) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        text = stringResource(R.string.add_entry_off_fetching_details),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    // Portion Dialog
    val selectedProduct = uiState.selectedProductForPortion
    if (selectedProduct != null) {
        PortionQuantityDialog(
            product = selectedProduct,
            isMeasuringInUnits = uiState.isMeasuringInUnits,
            inputQuantityText = uiState.inputQuantityText,
            inputQuantityError = uiState.inputQuantityError,
            portionKcal = uiState.portionPreviewKcal,
            portionProtein = uiState.portionPreviewProtein,
            portionCarbs = uiState.portionPreviewCarbs,
            portionFat = uiState.portionPreviewFat,
            isSaving = uiState.isSaving,
            onToggleUnit = onToggleMeasuringUnit,
            onQuantityChanged = onQuantityInputChanged,
            onConfirm = onConfirmAddProduct,
            onDismiss = onDismissPortionDialog,
        )
    }
}

@Composable
private fun HabitualFoodRow(
    habitual: HabitualFoodUiModel,
    onRowClick: () -> Unit,
    onQuickAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onRowClick),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habitual.product.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                val subtitle = if (!habitual.product.brand.isNullOrBlank()) {
                    "${habitual.product.brand} · ${habitual.formattedQuantity} · ${habitual.formattedKcal}"
                } else {
                    "${habitual.formattedQuantity} · ${habitual.formattedKcal}"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = onQuickAddClick,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.add_entry_confirm_add),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ProductRow(
    product: Product,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                val brandText = if (!product.brand.isNullOrBlank()) "${product.brand} · " else ""
                val unitG = product.unitG
                val unitText = if (unitG != null && unitG > 0) {
                    val label = product.unitName ?: "ud"
                    " · 1 $label = ${NumberFormatHelper.formatDecimal(unitG)} g"
                } else ""
                Text(
                    text = "$brandText${NumberFormatHelper.formatDecimal(product.kcal100g)} kcal/100 g$unitText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PortionQuantityDialog(
    product: Product,
    isMeasuringInUnits: Boolean,
    inputQuantityText: String,
    inputQuantityError: String?,
    portionKcal: Double,
    portionProtein: Double,
    portionCarbs: Double,
    portionFat: Double,
    isSaving: Boolean = false,
    onToggleUnit: () -> Unit,
    onQuantityChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val unitG = product.unitG
    val hasUnit = unitG != null && unitG > 0.0
    val unitLabel = product.unitName?.ifEmpty { null } ?: "ud"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hasUnit) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = isMeasuringInUnits,
                            onClick = { if (!isMeasuringInUnits) onToggleUnit() },
                            label = { Text("En $unitLabel") },
                        )
                        FilterChip(
                            selected = !isMeasuringInUnits,
                            onClick = { if (isMeasuringInUnits) onToggleUnit() },
                            label = { Text("En gramos") },
                        )
                    }
                }

                OutlinedTextField(
                    value = inputQuantityText,
                    onValueChange = onQuantityChanged,
                    label = {
                        Text(
                            text = if (isMeasuringInUnits && hasUnit) {
                                stringResource(R.string.add_entry_quantity_units, unitLabel)
                            } else {
                                stringResource(R.string.add_entry_quantity_grams)
                            }
                        )
                    },
                    isError = inputQuantityError != null,
                    supportingText = inputQuantityError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Macro preview card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "${NumberFormatHelper.formatDecimal(portionKcal)} kcal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${NumberFormatHelper.formatDecimal(portionProtein)} g Prot · " +
                                "${NumberFormatHelper.formatDecimal(portionCarbs)} g Carb · " +
                                "${NumberFormatHelper.formatDecimal(portionFat)} g Gras",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = inputQuantityError == null && inputQuantityText.isNotBlank() && !isSaving,
            ) {
                Text(text = stringResource(R.string.add_entry_confirm_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.daily_panel_cancel_action))
            }
        },
    )
}

@Composable
private fun OffSearchItemRow(
    item: OffSearchItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name ?: "Sin nombre",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                val brandText = if (!item.brand.isNullOrBlank()) "${item.brand} · " else ""
                val kcalText = item.kcal100g?.let {
                    stringResource(R.string.add_entry_off_kcal_format, NumberFormatHelper.formatDecimal(it))
                } ?: stringResource(R.string.add_entry_off_no_kcal)
                Text(
                    text = "$brandText$kcalText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

