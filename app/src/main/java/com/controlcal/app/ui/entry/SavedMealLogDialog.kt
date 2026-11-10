package com.controlcal.app.ui.entry

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.controlcal.app.R
import com.controlcal.core.model.ResolvedSavedMeal
import com.controlcal.core.nutrition.NumberFormatHelper

@Composable
fun SavedMealLogDialog(
    resolvedMeal: ResolvedSavedMeal,
    targetMealName: String,
    multiplier: Double,
    customMultiplierText: String,
    isSaving: Boolean,
    onMultiplierSelected: (Double) -> Unit,
    onCustomMultiplierChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val quickMultipliers = listOf(0.5, 1.0, 1.5, 2.0)

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.saved_meals_log_dialog_title, resolvedMeal.meal.name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                // Archived products warning
                if (resolvedMeal.hasArchivedProducts) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.saved_meals_archived_product_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Multiplier label
                Text(
                    text = stringResource(R.string.saved_meals_log_multiplier_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Quick chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    quickMultipliers.forEach { mult ->
                        val label = "×${NumberFormatHelper.formatDecimal(mult)}"
                        FilterChip(
                            selected = customMultiplierText.isEmpty() && multiplier == mult,
                            onClick = { onMultiplierSelected(mult) },
                            label = { Text(label) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Custom multiplier input
                OutlinedTextField(
                    value = customMultiplierText,
                    onValueChange = onCustomMultiplierChanged,
                    label = { Text("Multiplicador personalizado") },
                    placeholder = { Text("Ej. 1,25") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Items preview with scaled macros
                Text(
                    text = "Alimentos (${resolvedMeal.resolvedItems.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                ) {
                    items(resolvedMeal.resolvedItems) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.item.productName,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                val quantity = if (item.item.productId != null) {
                                    "${NumberFormatHelper.formatDecimal(item.resolvedGrams)} g  •  "
                                } else ""
                                Text(
                                    text = "$quantity${NumberFormatHelper.formatInteger(item.resolvedKcal.toInt())} kcal",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (item.isArchived) {
                                Text(
                                    text = "Archivado",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Total summary
                Text(
                    text = "Total: ${NumberFormatHelper.formatInteger(resolvedMeal.totalKcal.toInt())} kcal  •  " +
                        "P: ${NumberFormatHelper.formatDecimal(resolvedMeal.totalProtein)} g  •  " +
                        "C: ${NumberFormatHelper.formatDecimal(resolvedMeal.totalCarbs)} g  •  " +
                        "G: ${NumberFormatHelper.formatDecimal(resolvedMeal.totalFat)} g",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSaving && multiplier > 0.0,
            ) {
                Text(
                    text = if (targetMealName.isNotEmpty()) {
                        "Añadir a $targetMealName"
                    } else {
                        stringResource(R.string.saved_meals_log_dialog_confirm)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
            ) {
                Text(text = stringResource(R.string.saved_meals_save_dialog_cancel))
            }
        },
    )
}
