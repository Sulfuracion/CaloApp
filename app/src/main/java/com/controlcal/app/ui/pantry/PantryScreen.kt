package com.controlcal.app.ui.pantry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlcal.app.R
import com.controlcal.app.ui.feedback.HandleFeedbackEvents
import com.controlcal.core.model.Product
import com.controlcal.core.nutrition.NumberFormatHelper

@Composable
fun PantryRoute(
    viewModel: PantryViewModel,
    onBack: () -> Unit,
    onEditNutrition: (Product) -> Unit = {},
    onCreateProduct: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    HandleFeedbackEvents(
        events = viewModel.feedbackEvents,
        snackbarHostState = snackbarHostState,
    )

    PantryScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onFilterToggled = viewModel::onFilterToggled,
        onTogglePantry = viewModel::onTogglePantry,
        onStartEditingProduct = viewModel::onStartEditingProduct,
        onDismissEditDialog = viewModel::onDismissEditDialog,
        onEditInPantryChanged = viewModel::onEditInPantryChanged,
        onEditMinGChanged = viewModel::onEditMinGChanged,
        onEditMaxGChanged = viewModel::onEditMaxGChanged,
        onEditUnitNameChanged = viewModel::onEditUnitNameChanged,
        onEditUnitGChanged = viewModel::onEditUnitGChanged,
        onSaveProductConfig = viewModel::onSaveProductConfig,
        onEditNutrition = onEditNutrition,
        onCreateProduct = onCreateProduct,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryScreen(
    uiState: PantryUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onFilterToggled: (Boolean) -> Unit,
    onTogglePantry: (Product) -> Unit,
    onStartEditingProduct: (Product) -> Unit,
    onDismissEditDialog: () -> Unit,
    onEditInPantryChanged: (Boolean) -> Unit,
    onEditMinGChanged: (String) -> Unit,
    onEditMaxGChanged: (String) -> Unit,
    onEditUnitNameChanged: (String) -> Unit,
    onEditUnitGChanged: (String) -> Unit,
    onSaveProductConfig: () -> Unit,
    onEditNutrition: (Product) -> Unit = {},
    onCreateProduct: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.pantry_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.pantry_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onCreateProduct) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = stringResource(R.string.manual_entry_title),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            // Search box
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text(stringResource(R.string.pantry_search_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )

            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = uiState.showOnlyPantry,
                    onClick = { onFilterToggled(true) },
                    label = {
                        Text(
                            text = stringResource(
                                R.string.pantry_filter_in_pantry,
                                uiState.pantryProducts.size,
                            ),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )

                FilterChip(
                    selected = !uiState.showOnlyPantry,
                    onClick = { onFilterToggled(false) },
                    label = {
                        Text(
                            text = stringResource(
                                R.string.pantry_filter_all,
                                uiState.allProducts.size,
                            ),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }

            if (uiState.displayedProducts.isEmpty()) {
                if (uiState.showOnlyPantry && uiState.searchQuery.isBlank()) {
                    PantryEmptyCard(
                        onShowAll = { onFilterToggled(false) },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.pantry_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = uiState.displayedProducts,
                        key = { it.id },
                    ) { product ->
                        PantryProductItem(
                            product = product,
                            onTogglePantry = { onTogglePantry(product) },
                            onEdit = { onStartEditingProduct(product) },
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (uiState.editingProduct != null) {
        val editingProd = uiState.editingProduct
        PantryEditDialog(
            product = editingProd,
            editInPantry = uiState.editInPantry,
            minGText = uiState.editMinGText,
            maxGText = uiState.editMaxGText,
            unitNameText = uiState.editUnitNameText,
            unitGText = uiState.editUnitGText,
            minGError = uiState.editMinGError,
            maxGError = uiState.editMaxGError,
            unitNameError = uiState.editUnitNameError,
            unitGError = uiState.editUnitGError,
            isSaving = uiState.isSaving,
            onInPantryChanged = onEditInPantryChanged,
            onMinGChanged = onEditMinGChanged,
            onMaxGChanged = onEditMaxGChanged,
            onUnitNameChanged = onEditUnitNameChanged,
            onUnitGChanged = onEditUnitGChanged,
            onSave = onSaveProductConfig,
            onDismiss = onDismissEditDialog,
            onEditNutrition = {
                onDismissEditDialog()
                onEditNutrition(editingProd)
            },
        )
    }
}

@Composable
fun PantryProductItem(
    product: Product,
    onTogglePantry: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (product.inPantry) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val brand = product.brand
                if (!brand.isNullOrBlank()) {
                    Text(
                        text = brand,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Macros summary
                val kcalFormatted = NumberFormatHelper.formatDecimal(product.kcal100g)
                val protFormatted = NumberFormatHelper.formatDecimal(product.protein100g)
                val carbsFormatted = NumberFormatHelper.formatDecimal(product.carbs100g)
                val fatFormatted = NumberFormatHelper.formatDecimal(product.fat100g)
                Text(
                    text = "$kcalFormatted kcal · P: ${protFormatted}g · C: ${carbsFormatted}g · G: ${fatFormatted}g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Limits & unit line
                val limitsText = when {
                    product.minG != null && product.maxG != null -> {
                        stringResource(
                            R.string.pantry_limits_range,
                            NumberFormatHelper.formatDecimal(product.minG),
                            NumberFormatHelper.formatDecimal(product.maxG),
                        )
                    }
                    product.minG != null -> {
                        stringResource(
                            R.string.pantry_limits_min_only,
                            NumberFormatHelper.formatDecimal(product.minG),
                        )
                    }
                    product.maxG != null -> {
                        stringResource(
                            R.string.pantry_limits_max_only,
                            NumberFormatHelper.formatDecimal(product.maxG),
                        )
                    }
                    else -> stringResource(R.string.pantry_limits_none)
                }

                Text(
                    text = stringResource(R.string.pantry_limits_label, limitsText),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                val unitName = product.unitName
                val unitG = product.unitG
                if (!unitName.isNullOrBlank() && unitG != null) {
                    Text(
                        text = stringResource(
                            R.string.pantry_unit_label,
                            unitName,
                            NumberFormatHelper.formatDecimal(unitG),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onEdit) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.pantry_edit_limits_action),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Switch(
                checked = product.inPantry,
                onCheckedChange = { onTogglePantry() },
            )
        }
    }
}

@Composable
fun PantryEmptyCard(
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_pantry),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.pantry_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.pantry_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onShowAll) {
                Text(text = stringResource(R.string.pantry_empty_show_all_action))
            }
        }
    }
}

@Composable
fun PantryEditDialog(
    product: Product,
    editInPantry: Boolean,
    minGText: String,
    maxGText: String,
    unitNameText: String,
    unitGText: String,
    minGError: String?,
    maxGError: String?,
    unitNameError: String?,
    unitGError: String?,
    isSaving: Boolean = false,
    onInPantryChanged: (Boolean) -> Unit,
    onMinGChanged: (String) -> Unit,
    onMaxGChanged: (String) -> Unit,
    onUnitNameChanged: (String) -> Unit,
    onUnitGChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onEditNutrition: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        modifier = modifier,
        title = {
            Text(
                text = stringResource(R.string.pantry_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Product name header
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )

                // Quick toggle in dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.pantry_dialog_product_in_pantry),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = editInPantry,
                        onCheckedChange = onInPantryChanged,
                    )
                }

                HorizontalDivider()

                // Section: Limits
                Column {
                    Text(
                        text = stringResource(R.string.pantry_dialog_limits_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.pantry_dialog_limits_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = minGText,
                        onValueChange = onMinGChanged,
                        label = { Text(stringResource(R.string.pantry_dialog_min_g_label)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = minGError != null,
                        supportingText = minGError?.let { { Text(it) } },
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = maxGText,
                        onValueChange = onMaxGChanged,
                        label = { Text(stringResource(R.string.pantry_dialog_max_g_label)) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = maxGError != null,
                        supportingText = maxGError?.let { { Text(it) } },
                        singleLine = true,
                    )
                }

                HorizontalDivider()

                // Section: Unit
                Column {
                    Text(
                        text = stringResource(R.string.pantry_dialog_unit_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.pantry_dialog_unit_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = unitNameText,
                    onValueChange = onUnitNameChanged,
                    label = { Text(stringResource(R.string.pantry_dialog_unit_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = unitNameError != null,
                    supportingText = unitNameError?.let { { Text(it) } },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = unitGText,
                    onValueChange = onUnitGChanged,
                    label = { Text(stringResource(R.string.pantry_dialog_unit_g_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = unitGError != null,
                    supportingText = unitGError?.let { { Text(it) } },
                    singleLine = true,
                )

                HorizontalDivider()

                OutlinedButton(
                    onClick = onEditNutrition,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.pantry_edit_nutrition_action))
                }
            }
        },
        confirmButton = {
            val hasErrors = minGError != null || maxGError != null || unitNameError != null || unitGError != null
            Button(
                onClick = onSave,
                enabled = !hasErrors && !isSaving,
            ) {
                Text(stringResource(R.string.pantry_dialog_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.pantry_dialog_cancel_action))
            }
        },
    )
}
