package com.controlcal.app.ui.panel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.controlcal.app.R
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.suggest.QuickLogSuggestion

@Composable
fun QuickLogQuantityDialog(
    suggestion: QuickLogSuggestion,
    gramsText: String,
    errorText: String?,
    portionKcal: Double,
    portionProtein: Double,
    portionCarbs: Double,
    portionFat: Double,
    isSaving: Boolean = false,
    onGramsChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.daily_panel_edit_quantity_title))
        },
        text = {
            Column(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = suggestion.productName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = onGramsChanged,
                    label = { Text(text = stringResource(R.string.daily_panel_quantity_label)) },
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(text = it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.confirm_product_portion_preview,
                        NumberFormatHelper.formatDecimal(portionKcal),
                        NumberFormatHelper.formatDecimal(portionProtein),
                        NumberFormatHelper.formatDecimal(portionCarbs),
                        NumberFormatHelper.formatDecimal(portionFat),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = errorText == null && gramsText.isNotBlank() && !isSaving,
            ) {
                Text(text = stringResource(R.string.daily_panel_add_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.daily_panel_cancel_action))
            }
        },
    )
}
