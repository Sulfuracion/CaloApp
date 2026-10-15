package com.controlcal.app.ui.weight

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlcal.app.R
import com.controlcal.core.date.DateHelper
import com.controlcal.core.model.WeightEntry
import com.controlcal.core.nutrition.NumberFormatHelper
import java.time.LocalDate

@Composable
fun WeightRoute(
    viewModel: WeightViewModel,
    onBack: () -> Unit,
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

    WeightScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onTodayInputChanged = viewModel::onTodayInputChanged,
        onQuickAddDelta = viewModel::onQuickAddDelta,
        onSaveTodayWeight = viewModel::onSaveTodayWeight,
        onFilterSelected = viewModel::onFilterSelected,
        onStartEditEntry = viewModel::onStartEditEntry,
        onEditingWeightChanged = viewModel::onEditingWeightChanged,
        onEditingDateChanged = viewModel::onEditingDateChanged,
        onSaveEditEntry = viewModel::onSaveEditEntry,
        onDismissEditDialog = viewModel::onDismissEditDialog,
        onStartDeleteEntry = viewModel::onStartDeleteEntry,
        onConfirmDeleteEntry = viewModel::onConfirmDeleteEntry,
        onDismissDeleteDialog = viewModel::onDismissDeleteDialog,
        onStartAddPastEntry = viewModel::onStartAddPastEntry,
        onPastEntryDateChanged = viewModel::onPastEntryDateChanged,
        onPastEntryWeightChanged = viewModel::onPastEntryWeightChanged,
        onSavePastEntry = viewModel::onSavePastEntry,
        onDismissAddPastEntry = viewModel::onDismissAddPastEntry,
        onConfirmConflict = viewModel::onConfirmConflict,
        onDismissConflict = viewModel::onDismissConflict,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(
    uiState: WeightUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onTodayInputChanged: (String) -> Unit,
    onQuickAddDelta: (Double) -> Unit,
    onSaveTodayWeight: () -> Unit,
    onFilterSelected: (WeightChartFilter) -> Unit,
    onStartEditEntry: (WeightEntry) -> Unit,
    onEditingWeightChanged: (String) -> Unit,
    onEditingDateChanged: (String) -> Unit,
    onSaveEditEntry: () -> Unit,
    onDismissEditDialog: () -> Unit,
    onStartDeleteEntry: (WeightEntry) -> Unit,
    onConfirmDeleteEntry: () -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onStartAddPastEntry: () -> Unit,
    onPastEntryDateChanged: (String) -> Unit,
    onPastEntryWeightChanged: (String) -> Unit,
    onSavePastEntry: () -> Unit,
    onDismissAddPastEntry: () -> Unit,
    onConfirmConflict: () -> Unit,
    onDismissConflict: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.weight_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.weight_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onStartAddPastEntry) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = stringResource(R.string.weight_add_past_action),
                        )
                    }
                },
            )
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
                TodayWeightCard(
                    todayWeightKg = uiState.todayWeightKg,
                    inputText = uiState.todayInputText,
                    errorText = uiState.todayInputError,
                    onInputChanged = onTodayInputChanged,
                    onQuickAddDelta = onQuickAddDelta,
                    onSaveWeight = onSaveTodayWeight,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = uiState.selectedFilter == WeightChartFilter.DAYS_30,
                        onClick = { onFilterSelected(WeightChartFilter.DAYS_30) },
                        label = { Text(text = stringResource(R.string.weight_chart_filter_30)) },
                    )
                    FilterChip(
                        selected = uiState.selectedFilter == WeightChartFilter.DAYS_90,
                        onClick = { onFilterSelected(WeightChartFilter.DAYS_90) },
                        label = { Text(text = stringResource(R.string.weight_chart_filter_90)) },
                    )
                    FilterChip(
                        selected = uiState.selectedFilter == WeightChartFilter.ALL,
                        onClick = { onFilterSelected(WeightChartFilter.ALL) },
                        label = { Text(text = stringResource(R.string.weight_chart_filter_all)) },
                    )
                }
            }

            item {
                WeightTrendChart(points = uiState.chartPoints)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.weight_history_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = onStartAddPastEntry) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.weight_add_past_action))
                    }
                }
            }

            if (uiState.entries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.weight_history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(
                    items = uiState.entries,
                    key = { it.date },
                ) { entry ->
                    val trendPoint = uiState.trendPoints.find { it.date == entry.date }
                    WeightHistoryItem(
                        entry = entry,
                        trendKg = trendPoint?.trendKg,
                        todayDate = uiState.todayDate,
                        onEdit = { onStartEditEntry(entry) },
                        onDelete = { onStartDeleteEntry(entry) },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Edit entry dialog
    uiState.editingEntry?.let {
        EditWeightDialog(
            dateText = uiState.editingDateText,
            weightText = uiState.editingWeightText,
            errorText = uiState.editingError,
            onDateChanged = onEditingDateChanged,
            onWeightChanged = onEditingWeightChanged,
            onSave = onSaveEditEntry,
            onDismiss = onDismissEditDialog,
        )
    }

    // Delete confirmation dialog
    uiState.deletingEntry?.let { entry ->
        DeleteWeightDialog(
            date = entry.date,
            onConfirm = onConfirmDeleteEntry,
            onDismiss = onDismissDeleteDialog,
        )
    }

    // Add past entry dialog
    if (uiState.isAddingPastEntry) {
        AddPastWeightDialog(
            dateText = uiState.pastEntryDate,
            weightText = uiState.pastEntryWeightText,
            errorText = uiState.pastEntryError,
            onDateChanged = onPastEntryDateChanged,
            onWeightChanged = onPastEntryWeightChanged,
            onSave = onSavePastEntry,
            onDismiss = onDismissAddPastEntry,
        )
    }

    // Conflict confirmation dialog
    uiState.conflictState?.let { conflict ->
        WeightConflictDialog(
            conflict = conflict,
            onConfirm = onConfirmConflict,
            onDismiss = onDismissConflict,
        )
    }
}

@Composable
private fun TodayWeightCard(
    todayWeightKg: Double?,
    inputText: String,
    errorText: String?,
    onInputChanged: (String) -> Unit,
    onQuickAddDelta: (Double) -> Unit,
    onSaveWeight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.weight_today_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (todayWeightKg != null) {
                    val formatted = NumberFormatHelper.formatDecimal(todayWeightKg)
                    Text(
                        text = stringResource(R.string.weight_today_logged, formatted),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.weight_today_not_logged),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChanged,
                    label = { Text(text = stringResource(R.string.weight_input_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(text = it) } },
                    modifier = Modifier.weight(1f),
                )

                Button(
                    onClick = onSaveWeight,
                    modifier = Modifier.align(Alignment.CenterVertically),
                ) {
                    Text(text = stringResource(R.string.weight_save_action))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick adjustment chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuggestionChip(
                    onClick = { onQuickAddDelta(-0.5) },
                    label = { Text("-0,5") },
                )
                SuggestionChip(
                    onClick = { onQuickAddDelta(-0.1) },
                    label = { Text("-0,1") },
                )
                SuggestionChip(
                    onClick = { onQuickAddDelta(0.1) },
                    label = { Text("+0,1") },
                )
                SuggestionChip(
                    onClick = { onQuickAddDelta(0.5) },
                    label = { Text("+0,5") },
                )
            }
        }
    }
}

@Composable
private fun WeightHistoryItem(
    entry: WeightEntry,
    trendKg: Double?,
    todayDate: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentYear = remember { DateHelper.getToday().year }
    val sameYearPattern = stringResource(R.string.date_pattern_full_current_year)
    val otherYearPattern = stringResource(R.string.date_pattern_full_other_year)

    val displayDate = remember(entry.date, todayDate, currentYear, sameYearPattern, otherYearPattern) {
        val parsed = try {
            LocalDate.parse(entry.date)
        } catch (_: Exception) {
            null
        }
        val today = try {
            LocalDate.parse(todayDate)
        } catch (_: Exception) {
            DateHelper.getToday()
        }

        when {
            parsed == today -> "Hoy"
            parsed == today.minusDays(1) -> "Ayer"
            parsed != null -> DateHelper.formatDate(parsed, currentYear, sameYearPattern, otherYearPattern)
            else -> entry.date
        }
    }

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayDate,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${NumberFormatHelper.formatDecimal(entry.weightKg)} kg",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (trendKg != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "· ${stringResource(R.string.weight_history_trend_label, NumberFormatHelper.formatDecimal(trendKg))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        painter = painterResource(R.drawable.ic_edit),
                        contentDescription = stringResource(R.string.weight_edit_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.weight_delete_action),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditWeightDialog(
    dateText: String,
    weightText: String,
    errorText: String?,
    onDateChanged: (String) -> Unit,
    onWeightChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    val currentYear = remember { DateHelper.getToday().year }
    val sameYearPattern = stringResource(R.string.date_pattern_full_current_year)
    val otherYearPattern = stringResource(R.string.date_pattern_full_other_year)
    val displayDate = remember(dateText, currentYear, sameYearPattern, otherYearPattern) {
        try {
            val parsed = LocalDate.parse(dateText)
            DateHelper.formatDate(parsed, currentYear, sameYearPattern, otherYearPattern)
        } catch (_: Exception) {
            dateText
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.weight_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = displayDate,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(text = stringResource(R.string.weight_date_label)) },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_calendar),
                                    contentDescription = stringResource(R.string.date_picker_field_content_description),
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showDatePicker = true }
                    )
                }

                OutlinedTextField(
                    value = weightText,
                    onValueChange = onWeightChanged,
                    label = { Text(text = stringResource(R.string.weight_input_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(text = it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text(text = stringResource(R.string.weight_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.daily_panel_cancel_action))
            }
        },
    )

    if (showDatePicker) {
        val initialDate = remember(dateText) {
            try {
                LocalDate.parse(dateText)
            } catch (_: Exception) {
                DateHelper.getToday()
            }
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateHelper.localDateToUtcMillis(initialDate),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return DateHelper.isNotFuture(utcTimeMillis)
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selected = DateHelper.utcMillisToLocalDate(millis)
                            onDateChanged(selected.toString())
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(text = stringResource(R.string.date_picker_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = stringResource(R.string.daily_panel_cancel_action))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun DeleteWeightDialog(
    date: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.weight_delete_title)) },
        text = {
            Text(text = stringResource(R.string.weight_delete_confirmation, date))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(text = stringResource(R.string.weight_delete_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.daily_panel_cancel_action))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPastWeightDialog(
    dateText: String,
    weightText: String,
    errorText: String?,
    onDateChanged: (String) -> Unit,
    onWeightChanged: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    val currentYear = remember { DateHelper.getToday().year }
    val sameYearPattern = stringResource(R.string.date_pattern_full_current_year)
    val otherYearPattern = stringResource(R.string.date_pattern_full_other_year)
    val displayDate = remember(dateText, currentYear, sameYearPattern, otherYearPattern) {
        try {
            val parsed = LocalDate.parse(dateText)
            DateHelper.formatDate(parsed, currentYear, sameYearPattern, otherYearPattern)
        } catch (_: Exception) {
            dateText
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.weight_add_past_action)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = displayDate,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(text = stringResource(R.string.weight_date_label)) },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_calendar),
                                    contentDescription = stringResource(R.string.date_picker_field_content_description),
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showDatePicker = true }
                    )
                }

                OutlinedTextField(
                    value = weightText,
                    onValueChange = onWeightChanged,
                    label = { Text(text = stringResource(R.string.weight_input_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(text = it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text(text = stringResource(R.string.weight_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.daily_panel_cancel_action))
            }
        },
    )

    if (showDatePicker) {
        val initialDate = remember(dateText) {
            try {
                LocalDate.parse(dateText)
            } catch (_: Exception) {
                DateHelper.getToday()
            }
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateHelper.localDateToUtcMillis(initialDate),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return DateHelper.isNotFuture(utcTimeMillis)
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selected = DateHelper.utcMillisToLocalDate(millis)
                            onDateChanged(selected.toString())
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(text = stringResource(R.string.date_picker_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = stringResource(R.string.daily_panel_cancel_action))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun WeightConflictDialog(
    conflict: WeightConflict,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentYear = remember { DateHelper.getToday().year }
    val sameYearPattern = stringResource(R.string.date_pattern_full_current_year)
    val otherYearPattern = stringResource(R.string.date_pattern_full_other_year)
    val formattedDate = remember(conflict.targetDate, currentYear, sameYearPattern, otherYearPattern) {
        try {
            val parsed = LocalDate.parse(conflict.targetDate)
            DateHelper.formatDate(parsed, currentYear, sameYearPattern, otherYearPattern)
        } catch (_: Exception) {
            conflict.targetDate
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.weight_conflict_title)) },
        text = {
            Text(
                text = stringResource(
                    R.string.weight_conflict_message,
                    NumberFormatHelper.formatDecimal(conflict.existingWeightKg),
                    formattedDate,
                    NumberFormatHelper.formatDecimal(conflict.newWeightKg),
                )
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = stringResource(R.string.weight_conflict_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.daily_panel_cancel_action))
            }
        },
    )
}
