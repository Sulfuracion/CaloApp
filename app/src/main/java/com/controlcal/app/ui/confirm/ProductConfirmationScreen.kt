package com.controlcal.app.ui.confirm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlcal.app.R
import com.controlcal.app.data.off.OffProduct
import com.controlcal.core.model.Meal
import com.controlcal.core.nutrition.NumberFormatHelper

@Composable
fun ProductConfirmationRoute(
    offProduct: OffProduct,
    viewModel: ProductConfirmationViewModel,
    onConfirmed: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(offProduct) {
        viewModel.initialize(offProduct)
    }

    ProductConfirmationScreen(
        uiState = uiState,
        onBarcodeChanged = viewModel::onBarcodeChanged,
        onNameChanged = viewModel::onNameChanged,
        onBrandChanged = viewModel::onBrandChanged,
        onKcalChanged = viewModel::onKcalChanged,
        onProteinChanged = viewModel::onProteinChanged,
        onCarbsChanged = viewModel::onCarbsChanged,
        onFatChanged = viewModel::onFatChanged,
        onFiberChanged = viewModel::onFiberChanged,
        onSugarsChanged = viewModel::onSugarsChanged,
        onSatFatChanged = viewModel::onSatFatChanged,
        onSaltChanged = viewModel::onSaltChanged,
        onIsLiquidChanged = viewModel::onIsLiquidChanged,
        onServingChanged = viewModel::onServingChanged,
        onUnitNameChanged = viewModel::onUnitNameChanged,
        onUnitGChanged = viewModel::onUnitGChanged,
        onAddToDiaryChanged = viewModel::onAddToDiaryChanged,
        onMealSelected = viewModel::onMealSelected,
        onDiaryGramsChanged = viewModel::onDiaryGramsChanged,
        onConfirmAndSave = { viewModel.onConfirmAndSave(onConfirmed) },
        onDiscard = onDiscard,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductConfirmationScreen(
    uiState: ProductConfirmationUiState,
    onBarcodeChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onBrandChanged: (String) -> Unit,
    onKcalChanged: (String) -> Unit,
    onProteinChanged: (String) -> Unit,
    onCarbsChanged: (String) -> Unit,
    onFatChanged: (String) -> Unit,
    onFiberChanged: (String) -> Unit,
    onSugarsChanged: (String) -> Unit,
    onSatFatChanged: (String) -> Unit,
    onSaltChanged: (String) -> Unit,
    onIsLiquidChanged: (Boolean) -> Unit,
    onServingChanged: (String) -> Unit,
    onUnitNameChanged: (String) -> Unit,
    onUnitGChanged: (String) -> Unit,
    onAddToDiaryChanged: (Boolean) -> Unit,
    onMealSelected: (Meal) -> Unit,
    onDiaryGramsChanged: (String) -> Unit,
    onConfirmAndSave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.confirm_product_title)) },
                navigationIcon = {
                    IconButton(onClick = onDiscard) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.confirm_product_discard_button)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Warning banner if any mandatory fields were originally missing in Open Food Facts
            if (uiState.hasOriginallyMissingFields) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.confirm_product_missing_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Section 1: General Info
            Text(
                text = stringResource(R.string.confirm_product_section_general),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = uiState.barcode,
                onValueChange = onBarcodeChanged,
                label = { Text(stringResource(R.string.confirm_product_field_barcode)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            val nameHasError = uiState.nameError != null || (uiState.originallyMissingName && uiState.name.isBlank())
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChanged,
                label = { Text(stringResource(R.string.confirm_product_field_name)) },
                modifier = Modifier.fillMaxWidth(),
                isError = nameHasError,
                supportingText = if (nameHasError) {
                    { Text(uiState.nameError ?: stringResource(R.string.confirm_product_field_required)) }
                } else null,
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.brand,
                onValueChange = onBrandChanged,
                label = { Text(stringResource(R.string.confirm_product_field_brand)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = uiState.isLiquid,
                    onCheckedChange = onIsLiquidChanged
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.confirm_product_field_is_liquid),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Section 2: Nutrients
            val sectionNutrimentsTitle = if (uiState.isLiquid) {
                stringResource(R.string.confirm_product_section_nutriments_100ml)
            } else {
                stringResource(R.string.confirm_product_section_nutriments_100g)
            }

            Text(
                text = sectionNutrimentsTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Mandatory macros (Row 1: Kcal and Protein)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val kcalHasError = uiState.kcalError != null || (uiState.originallyMissingKcal && uiState.kcal100g.isBlank())
                OutlinedTextField(
                    value = uiState.kcal100g,
                    onValueChange = onKcalChanged,
                    label = { Text(stringResource(R.string.confirm_product_field_kcal)) },
                    modifier = Modifier.weight(1f),
                    isError = kcalHasError,
                    supportingText = if (kcalHasError) {
                        { Text(uiState.kcalError ?: stringResource(R.string.confirm_product_field_required)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                val proteinHasError = uiState.proteinError != null || (uiState.originallyMissingProtein && uiState.protein100g.isBlank())
                OutlinedTextField(
                    value = uiState.protein100g,
                    onValueChange = onProteinChanged,
                    label = { Text(stringResource(R.string.confirm_product_field_protein)) },
                    modifier = Modifier.weight(1f),
                    isError = proteinHasError,
                    supportingText = if (proteinHasError) {
                        { Text(uiState.proteinError ?: stringResource(R.string.confirm_product_field_required)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }

            // Mandatory macros (Row 2: Carbs and Fat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val carbsHasError = uiState.carbsError != null || (uiState.originallyMissingCarbs && uiState.carbs100g.isBlank())
                OutlinedTextField(
                    value = uiState.carbs100g,
                    onValueChange = onCarbsChanged,
                    label = { Text(stringResource(R.string.confirm_product_field_carbs)) },
                    modifier = Modifier.weight(1f),
                    isError = carbsHasError,
                    supportingText = if (carbsHasError) {
                        { Text(uiState.carbsError ?: stringResource(R.string.confirm_product_field_required)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                val fatHasError = uiState.fatError != null || (uiState.originallyMissingFat && uiState.fat100g.isBlank())
                OutlinedTextField(
                    value = uiState.fat100g,
                    onValueChange = onFatChanged,
                    label = { Text(stringResource(R.string.confirm_product_field_fat)) },
                    modifier = Modifier.weight(1f),
                    isError = fatHasError,
                    supportingText = if (fatHasError) {
                        { Text(uiState.fatError ?: stringResource(R.string.confirm_product_field_required)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }

            // Optional macros (Fiber and Sugars)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.fiber100g,
                    onValueChange = onFiberChanged,
                    label = { Text(stringResource(R.string.confirm_product_field_fiber)) },
                    modifier = Modifier.weight(1f),
                    isError = uiState.fiberError != null,
                    supportingText = uiState.fiberError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.sugars100g,
                    onValueChange = onSugarsChanged,
                    label = { Text(stringResource(R.string.confirm_product_field_sugars)) },
                    modifier = Modifier.weight(1f),
                    isError = uiState.sugarsError != null,
                    supportingText = uiState.sugarsError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }

            // Optional macros (Saturated Fat and Salt)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.satFat100g,
                    onValueChange = onSatFatChanged,
                    label = { Text(stringResource(R.string.confirm_product_field_sat_fat)) },
                    modifier = Modifier.weight(1f),
                    isError = uiState.satFatError != null,
                    supportingText = uiState.satFatError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.salt100g,
                    onValueChange = onSaltChanged,
                    label = { Text(stringResource(R.string.confirm_product_field_salt)) },
                    modifier = Modifier.weight(1f),
                    isError = uiState.saltError != null,
                    supportingText = uiState.saltError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }

            // Section 3: Portions and Units
            Text(
                text = stringResource(R.string.confirm_product_section_portions),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = uiState.servingG,
                onValueChange = onServingChanged,
                label = { Text(stringResource(R.string.confirm_product_field_serving_g)) },
                modifier = Modifier.fillMaxWidth(),
                isError = uiState.servingError != null,
                supportingText = uiState.servingError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.unitName,
                    onValueChange = onUnitNameChanged,
                    label = { Text(stringResource(R.string.confirm_product_field_unit_name)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.unitG,
                    onValueChange = onUnitGChanged,
                    label = { Text(stringResource(R.string.confirm_product_field_unit_g)) },
                    modifier = Modifier.weight(1f),
                    isError = uiState.unitGError != null,
                    supportingText = uiState.unitGError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }

            // Section 4: Add to diary
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.confirm_product_section_diary),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Switch(
                            checked = uiState.addToDiary,
                            onCheckedChange = onAddToDiaryChanged
                        )
                    }

                    if (uiState.addToDiary) {
                        Text(
                            text = stringResource(R.string.confirm_product_meal_label),
                            style = MaterialTheme.typography.labelLarge
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = uiState.selectedMeal == Meal.BREAKFAST,
                                onClick = { onMealSelected(Meal.BREAKFAST) },
                                label = { Text(stringResource(R.string.confirm_product_meal_breakfast)) }
                            )
                            FilterChip(
                                selected = uiState.selectedMeal == Meal.LUNCH,
                                onClick = { onMealSelected(Meal.LUNCH) },
                                label = { Text(stringResource(R.string.confirm_product_meal_lunch)) }
                            )
                            FilterChip(
                                selected = uiState.selectedMeal == Meal.SNACK,
                                onClick = { onMealSelected(Meal.SNACK) },
                                label = { Text(stringResource(R.string.confirm_product_meal_snack)) }
                            )
                            FilterChip(
                                selected = uiState.selectedMeal == Meal.DINNER,
                                onClick = { onMealSelected(Meal.DINNER) },
                                label = { Text(stringResource(R.string.confirm_product_meal_dinner)) }
                            )
                        }

                        OutlinedTextField(
                            value = uiState.diaryGrams,
                            onValueChange = onDiaryGramsChanged,
                            label = { Text(stringResource(R.string.confirm_product_quantity_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            isError = uiState.diaryGramsError != null,
                            supportingText = uiState.diaryGramsError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )

                        Text(
                            text = stringResource(
                                R.string.confirm_product_portion_preview,
                                NumberFormatHelper.formatDecimal(uiState.portionKcal),
                                NumberFormatHelper.formatDecimal(uiState.portionProtein),
                                NumberFormatHelper.formatDecimal(uiState.portionCarbs),
                                NumberFormatHelper.formatDecimal(uiState.portionFat),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Button(
                onClick = onConfirmAndSave,
                enabled = uiState.isSaveEnabled && !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.height(20.dp).width(20.dp)
                    )
                } else {
                    Text(text = stringResource(R.string.confirm_product_save_button))
                }
            }

            OutlinedButton(
                onClick = onDiscard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.confirm_product_discard_button))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
