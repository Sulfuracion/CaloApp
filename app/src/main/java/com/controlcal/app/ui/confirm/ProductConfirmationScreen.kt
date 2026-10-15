package com.controlcal.app.ui.confirm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlcal.app.R
import com.controlcal.app.data.off.OffProduct
import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.ProductSource
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.ocr.model.OcrParsedNutrients
import java.io.File

data class OcrConfirmationData(
    val photoFile: File,
    val parsedNutrients: OcrParsedNutrients,
    val barcode: String? = null
)

@Composable
fun ProductConfirmationRoute(
    viewModel: ProductConfirmationViewModel,
    onConfirmed: () -> Unit,
    onDiscard: () -> Unit,
    offProduct: OffProduct? = null,
    ocrData: OcrConfirmationData? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(offProduct, ocrData) {
        if (ocrData != null) {
            viewModel.initializeFromOcr(ocrData.photoFile, ocrData.parsedNutrients, ocrData.barcode)
        } else if (offProduct != null) {
            viewModel.initialize(offProduct)
        }
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
        onDiscard = { viewModel.onDiscard(onDiscard) },
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
    onMealSelected: (String) -> Unit,
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
            // Source Badge if OCR
            if (uiState.source == ProductSource.OCR) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.confirm_product_source_ocr_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Visible photo preview if available
            val photoFile = uiState.photoFile
            if (photoFile != null) {
                CapturedPhotoCard(photoFile = photoFile)
            }

            // Warning banner if any mandatory fields were originally missing in Open Food Facts or OCR
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

            // General validation issues banner (e.g. Caloric balance mismatch > 15%)
            if (uiState.generalValidationIssues.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.confirm_product_validation_error_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        uiState.generalValidationIssues.forEach { issue ->
                            Text(
                                text = "• $issue",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
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
                val (kcalIsError, kcalMsg) = resolveNutrientSupport(
                    key = "kcal",
                    rawError = uiState.kcalError,
                    validationIssue = uiState.validationIssues["kcal"],
                    isDubious = uiState.dubiousFields.contains("kcal"),
                    isApproximate = uiState.approximations.contains("kcal"),
                    isRequired = uiState.originallyMissingKcal,
                    valueText = uiState.kcal100g
                )
                OutlinedTextField(
                    value = uiState.kcal100g,
                    onValueChange = onKcalChanged,
                    label = { Text(stringResource(R.string.confirm_product_field_kcal)) },
                    modifier = Modifier.weight(1f),
                    isError = kcalIsError,
                    supportingText = kcalMsg?.let { msg ->
                        {
                            Text(
                                text = msg,
                                color = if (kcalIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                val (proteinIsError, proteinMsg) = resolveNutrientSupport(
                    key = "protein",
                    rawError = uiState.proteinError,
                    validationIssue = uiState.validationIssues["protein"],
                    isDubious = uiState.dubiousFields.contains("protein"),
                    isApproximate = uiState.approximations.contains("protein"),
                    isRequired = uiState.originallyMissingProtein,
                    valueText = uiState.protein100g
                )
                OutlinedTextField(
                    value = uiState.protein100g,
                    onValueChange = onProteinChanged,
                    label = { Text(stringResource(R.string.confirm_product_field_protein)) },
                    modifier = Modifier.weight(1f),
                    isError = proteinIsError,
                    supportingText = proteinMsg?.let { msg ->
                        {
                            Text(
                                text = msg,
                                color = if (proteinIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }

            // Mandatory macros (Row 2: Carbs and Fat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val (carbsIsError, carbsMsg) = resolveNutrientSupport(
                    key = "carbs",
                    rawError = uiState.carbsError,
                    validationIssue = uiState.validationIssues["carbs"],
                    isDubious = uiState.dubiousFields.contains("carbs"),
                    isApproximate = uiState.approximations.contains("carbs"),
                    isRequired = uiState.originallyMissingCarbs,
                    valueText = uiState.carbs100g
                )
                OutlinedTextField(
                    value = uiState.carbs100g,
                    onValueChange = onCarbsChanged,
                    label = { Text(stringResource(R.string.confirm_product_field_carbs)) },
                    modifier = Modifier.weight(1f),
                    isError = carbsIsError,
                    supportingText = carbsMsg?.let { msg ->
                        {
                            Text(
                                text = msg,
                                color = if (carbsIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                val (fatIsError, fatMsg) = resolveNutrientSupport(
                    key = "fat",
                    rawError = uiState.fatError,
                    validationIssue = uiState.validationIssues["fat"],
                    isDubious = uiState.dubiousFields.contains("fat"),
                    isApproximate = uiState.approximations.contains("fat"),
                    isRequired = uiState.originallyMissingFat,
                    valueText = uiState.fat100g
                )
                OutlinedTextField(
                    value = uiState.fat100g,
                    onValueChange = onFatChanged,
                    label = { Text(stringResource(R.string.confirm_product_field_fat)) },
                    modifier = Modifier.weight(1f),
                    isError = fatIsError,
                    supportingText = fatMsg?.let { msg ->
                        {
                            Text(
                                text = msg,
                                color = if (fatIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }

            // Optional macros (Fiber and Sugars)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val (fiberIsError, fiberMsg) = resolveNutrientSupport(
                    key = "fiber",
                    rawError = uiState.fiberError,
                    validationIssue = uiState.validationIssues["fiber"],
                    isDubious = uiState.dubiousFields.contains("fiber"),
                    isApproximate = uiState.approximations.contains("fiber"),
                    isRequired = false,
                    valueText = uiState.fiber100g
                )
                OutlinedTextField(
                    value = uiState.fiber100g,
                    onValueChange = onFiberChanged,
                    label = { Text(stringResource(R.string.confirm_product_field_fiber)) },
                    modifier = Modifier.weight(1f),
                    isError = fiberIsError,
                    supportingText = fiberMsg?.let { msg ->
                        {
                            Text(
                                text = msg,
                                color = if (fiberIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                val (sugarsIsError, sugarsMsg) = resolveNutrientSupport(
                    key = "sugars",
                    rawError = uiState.sugarsError,
                    validationIssue = uiState.validationIssues["sugars"],
                    isDubious = uiState.dubiousFields.contains("sugars"),
                    isApproximate = uiState.approximations.contains("sugars"),
                    isRequired = false,
                    valueText = uiState.sugars100g
                )
                OutlinedTextField(
                    value = uiState.sugars100g,
                    onValueChange = onSugarsChanged,
                    label = { Text(stringResource(R.string.confirm_product_field_sugars)) },
                    modifier = Modifier.weight(1f),
                    isError = sugarsIsError,
                    supportingText = sugarsMsg?.let { msg ->
                        {
                            Text(
                                text = msg,
                                color = if (sugarsIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }

            // Optional macros (Saturated Fat and Salt)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val (satFatIsError, satFatMsg) = resolveNutrientSupport(
                    key = "satFat",
                    rawError = uiState.satFatError,
                    validationIssue = uiState.validationIssues["satFat"],
                    isDubious = uiState.dubiousFields.contains("satFat"),
                    isApproximate = uiState.approximations.contains("satFat"),
                    isRequired = false,
                    valueText = uiState.satFat100g
                )
                OutlinedTextField(
                    value = uiState.satFat100g,
                    onValueChange = onSatFatChanged,
                    label = { Text(stringResource(R.string.confirm_product_field_sat_fat)) },
                    modifier = Modifier.weight(1f),
                    isError = satFatIsError,
                    supportingText = satFatMsg?.let { msg ->
                        {
                            Text(
                                text = msg,
                                color = if (satFatIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                val (saltIsError, saltMsg) = resolveNutrientSupport(
                    key = "salt",
                    rawError = uiState.saltError,
                    validationIssue = uiState.validationIssues["salt"],
                    isDubious = uiState.dubiousFields.contains("salt"),
                    isApproximate = uiState.approximations.contains("salt"),
                    isRequired = false,
                    valueText = uiState.salt100g
                )
                OutlinedTextField(
                    value = uiState.salt100g,
                    onValueChange = onSaltChanged,
                    label = { Text(stringResource(R.string.confirm_product_field_salt)) },
                    modifier = Modifier.weight(1f),
                    isError = saltIsError,
                    supportingText = saltMsg?.let { msg ->
                        {
                            Text(
                                text = msg,
                                color = if (saltIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    },
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.availableMeals.forEach { meal ->
                                FilterChip(
                                    selected = uiState.selectedMeal == meal.id,
                                    onClick = { onMealSelected(meal.id) },
                                    label = { Text(meal.name) }
                                )
                            }
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

private fun resolveNutrientSupport(
    key: String,
    rawError: String?,
    validationIssue: String?,
    isDubious: Boolean,
    isApproximate: Boolean,
    isRequired: Boolean,
    valueText: String
): Pair<Boolean, String?> {
    val isMissing = isRequired && valueText.isBlank()
    val isError = rawError != null || isMissing || validationIssue != null
    val message = when {
        rawError != null -> rawError
        isMissing -> "Campo obligatorio"
        validationIssue != null -> validationIssue
        isApproximate -> "Valor aproximado (<0,5 g)"
        isDubious -> "Valor dudoso por OCR · Comprueba con la foto"
        else -> null
    }
    return isError to message
}

@Composable
private fun CapturedPhotoCard(
    photoFile: File,
    modifier: Modifier = Modifier
) {
    var showEnlargedDialog by remember { mutableStateOf(false) }
    val bitmap = remember(photoFile) {
        loadBitmapUpright(photoFile)
    }

    if (bitmap != null) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .clickable { showEnlargedDialog = true },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.confirm_product_photo_preview_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.confirm_product_photo_tap_to_enlarge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.confirm_product_photo_preview_title),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }

        if (showEnlargedDialog) {
            Dialog(
                onDismissRequest = { showEnlargedDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    color = Color.Black
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.confirm_product_photo_preview_title),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentScale = ContentScale.Fit
                        )
                        FilledIconButton(
                            onClick = { showEnlargedDialog = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(16.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.confirm_product_close_photo_dialog)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun loadBitmapUpright(file: File, maxDimension: Int = 1280): Bitmap? {
    try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        var inSampleSize = 1
        while (options.outWidth / inSampleSize > maxDimension || options.outHeight / inSampleSize > maxDimension) {
            inSampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        return if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    } catch (_: Exception) {
        return null
    }
}
