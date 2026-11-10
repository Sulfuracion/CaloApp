package com.controlcal.app.ui.supplement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlcal.app.R
import com.controlcal.app.ui.feedback.HandleFeedbackEvents
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.supplement.OverallAdherence
import com.controlcal.core.supplement.ScheduleType
import com.controlcal.core.supplement.Supplement
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun SupplementsRoute(
    viewModel: SupplementsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    HandleFeedbackEvents(
        events = viewModel.feedbackEvents,
        snackbarHostState = snackbarHostState,
    )

    SupplementsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAddSupplementClick = viewModel::onAddSupplementClick,
        onEditSupplementClick = viewModel::onEditSupplementClick,
        onToggleActive = viewModel::onToggleActive,
        onDismissDialog = viewModel::onDismissDialog,
        onNameChanged = viewModel::onNameChanged,
        onDoseAmountChanged = viewModel::onDoseAmountChanged,
        onDoseUnitChanged = viewModel::onDoseUnitChanged,
        onScheduleTypeChanged = viewModel::onScheduleTypeChanged,
        onToggleWeekday = viewModel::onToggleWeekday,
        onIntervalDaysChanged = viewModel::onIntervalDaysChanged,
        onStartDateChanged = viewModel::onStartDateChanged,
        onAddTime = viewModel::onAddTime,
        onRemoveTime = viewModel::onRemoveTime,
        onToggleReminders = viewModel::onToggleReminders,
        onNotesChanged = viewModel::onNotesChanged,
        onSaveSupplement = viewModel::onSaveSupplement,
        onDismissRationale = viewModel::onDismissRationale,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplementsScreen(
    uiState: SupplementsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAddSupplementClick: () -> Unit,
    onEditSupplementClick: (Supplement) -> Unit,
    onToggleActive: (Supplement) -> Unit,
    onDismissDialog: () -> Unit,
    onNameChanged: (String) -> Unit,
    onDoseAmountChanged: (String) -> Unit,
    onDoseUnitChanged: (String) -> Unit,
    onScheduleTypeChanged: (ScheduleType) -> Unit,
    onToggleWeekday: (DayOfWeek) -> Unit,
    onIntervalDaysChanged: (String) -> Unit,
    onStartDateChanged: (LocalDate) -> Unit,
    onAddTime: (LocalTime) -> Unit,
    onRemoveTime: (LocalTime) -> Unit,
    onToggleReminders: (Boolean) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSaveSupplement: () -> Unit,
    onDismissRationale: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.supplements_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.scanner_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onAddSupplementClick,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = stringResource(R.string.supplements_add_action),
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.activeSupplements.isEmpty() && uiState.inactiveSupplements.isEmpty()) {
            EmptySupplementsState(
                onAddClick = onAddSupplementClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
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
                }

                if (uiState.adherence != null && uiState.adherence.totalScheduledDosesCount > 0) {
                    item {
                        AdherenceCard(adherence = uiState.adherence)
                    }
                }

                if (uiState.activeSupplements.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.supplements_active_section),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    items(uiState.activeSupplements, key = { it.id }) { supplement ->
                        SupplementItemCard(
                            supplement = supplement,
                            onClick = { onEditSupplementClick(supplement) },
                            onToggleActive = { onToggleActive(supplement) },
                        )
                    }
                }

                if (uiState.inactiveSupplements.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.supplements_inactive_section),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }

                    items(uiState.inactiveSupplements, key = { it.id }) { supplement ->
                        SupplementItemCard(
                            supplement = supplement,
                            onClick = { onEditSupplementClick(supplement) },
                            onToggleActive = { onToggleActive(supplement) },
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    if (uiState.isEditing) {
        AddEditSupplementDialog(
            uiState = uiState,
            onDismiss = onDismissDialog,
            onNameChanged = onNameChanged,
            onDoseAmountChanged = onDoseAmountChanged,
            onDoseUnitChanged = onDoseUnitChanged,
            onScheduleTypeChanged = onScheduleTypeChanged,
            onToggleWeekday = onToggleWeekday,
            onIntervalDaysChanged = onIntervalDaysChanged,
            onStartDateChanged = onStartDateChanged,
            onAddTime = onAddTime,
            onRemoveTime = onRemoveTime,
            onToggleReminders = onToggleReminders,
            onNotesChanged = onNotesChanged,
            onSave = onSaveSupplement,
        )
    }

    if (uiState.showNotificationRationale) {
        AlertDialog(
            onDismissRequest = onDismissRationale,
            title = { Text(text = stringResource(R.string.supplement_permission_rationale_title)) },
            text = { Text(text = stringResource(R.string.supplement_permission_rationale_desc)) },
            confirmButton = {
                Button(onClick = onDismissRationale) {
                    Text(text = stringResource(R.string.supplement_permission_grant))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRationale) {
                    Text(text = stringResource(R.string.supplement_permission_cancel))
                }
            },
        )
    }
}

@Composable
private fun EmptySupplementsState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_pill),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.supplements_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.supplements_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAddClick,
            modifier = Modifier.height(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.supplements_add_action))
        }
    }
}

@Composable
private fun AdherenceCard(
    adherence: OverallAdherence,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.supplements_adherence_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${NumberFormatHelper.formatDecimal(adherence.adherencePercentage)} %",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (adherence.adherencePercentage / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.supplements_adherence_doses_summary,
                    adherence.totalTakenCount,
                    adherence.totalScheduledDosesCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (adherence.supplementSummaries.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                for (summary in adherence.supplementSummaries) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = summary.supplementName,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "${NumberFormatHelper.formatDecimal(summary.adherencePercentage)} %",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SupplementItemCard(
    supplement: Supplement,
    onClick: () -> Unit,
    onToggleActive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (supplement.active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = supplement.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!supplement.active) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        ) {
                            Text(
                                text = stringResource(R.string.supplements_deactivated_badge),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${NumberFormatHelper.formatDecimal(supplement.doseAmount)} ${supplement.doseUnit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val scheduleSummary = formatScheduleSummary(supplement)
                Text(
                    text = scheduleSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Switch(
                    checked = supplement.active,
                    onCheckedChange = { onToggleActive() },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AddEditSupplementDialog(
    uiState: SupplementsUiState,
    onDismiss: () -> Unit,
    onNameChanged: (String) -> Unit,
    onDoseAmountChanged: (String) -> Unit,
    onDoseUnitChanged: (String) -> Unit,
    onScheduleTypeChanged: (ScheduleType) -> Unit,
    onToggleWeekday: (DayOfWeek) -> Unit,
    onIntervalDaysChanged: (String) -> Unit,
    onStartDateChanged: (LocalDate) -> Unit,
    onAddTime: (LocalTime) -> Unit,
    onRemoveTime: (LocalTime) -> Unit,
    onToggleReminders: (Boolean) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSave: () -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = if (uiState.editingSupplementId == null) {
                        stringResource(R.string.supplements_add_action)
                    } else {
                        stringResource(R.string.supplements_edit_action)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Name field
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = onNameChanged,
                    label = { Text(text = stringResource(R.string.supplement_field_name)) },
                    placeholder = { Text(text = stringResource(R.string.supplement_field_name_placeholder)) },
                    isError = uiState.nameError != null,
                    supportingText = uiState.nameError?.let { { Text(text = it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Dose amount and unit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    OutlinedTextField(
                        value = uiState.doseAmountText,
                        onValueChange = onDoseAmountChanged,
                        label = { Text(text = stringResource(R.string.supplement_field_dose_amount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = uiState.doseAmountError != null,
                        supportingText = uiState.doseAmountError?.let { { Text(text = it) } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }

                // Dose unit selection chips
                Text(
                    text = stringResource(R.string.supplement_field_dose_unit),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (unit in SupplementsUiState.DOSE_UNITS) {
                        FilterChip(
                            selected = uiState.doseUnit == unit,
                            onClick = { onDoseUnitChanged(unit) },
                            label = { Text(text = unit) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Schedule type
                Text(
                    text = stringResource(R.string.supplement_field_schedule_type),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = uiState.scheduleType == ScheduleType.DAILY,
                        onClick = { onScheduleTypeChanged(ScheduleType.DAILY) },
                        label = { Text(text = stringResource(R.string.supplement_schedule_daily)) },
                    )
                    FilterChip(
                        selected = uiState.scheduleType == ScheduleType.WEEKDAYS,
                        onClick = { onScheduleTypeChanged(ScheduleType.WEEKDAYS) },
                        label = { Text(text = stringResource(R.string.supplement_schedule_weekdays)) },
                    )
                }
                Row {
                    FilterChip(
                        selected = uiState.scheduleType == ScheduleType.EVERY_N_DAYS,
                        onClick = { onScheduleTypeChanged(ScheduleType.EVERY_N_DAYS) },
                        label = { Text(text = stringResource(R.string.supplement_schedule_every_n_days)) },
                    )
                }

                if (uiState.scheduleType == ScheduleType.WEEKDAYS) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.supplement_schedule_weekdays),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val dayNames = listOf("L", "M", "X", "J", "V", "S", "D")
                        val days = DayOfWeek.entries
                        for (i in days.indices) {
                            val day = days[i]
                            val isSelected = uiState.selectedWeekdays.contains(day)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onToggleWeekday(day) },
                                label = { Text(text = dayNames[i]) },
                                modifier = Modifier.size(width = 40.dp, height = 40.dp),
                                colors = FilterChipDefaults.filterChipColors(),
                            )
                        }
                    }
                    if (uiState.weekdaysError != null) {
                        Text(
                            text = uiState.weekdaysError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                if (uiState.scheduleType == ScheduleType.EVERY_N_DAYS) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.intervalDaysText,
                        onValueChange = onIntervalDaysChanged,
                        label = { Text(text = stringResource(R.string.supplement_field_interval_days)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = uiState.intervalDaysError != null,
                        supportingText = uiState.intervalDaysError?.let { { Text(text = it) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Times section
                Text(
                    text = stringResource(R.string.supplement_field_times),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (time in uiState.times) {
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text(text = time.toString()) },
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onRemoveTime(time) },
                                )
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.height(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.supplement_add_time_action))
                    }
                }
                if (uiState.timesError != null) {
                    Text(
                        text = uiState.timesError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reminders switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.supplement_field_reminders),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = uiState.remindersEnabled,
                        onCheckedChange = onToggleReminders,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Notes field
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = onNotesChanged,
                    label = { Text(text = stringResource(R.string.supplement_field_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text(text = stringResource(R.string.saved_meals_save_dialog_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSave,
                        enabled = !uiState.isSaving,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text(text = stringResource(R.string.saved_meals_save_dialog_confirm))
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        SupplementTimePickerDialog(
            initialTime = LocalTime.of(8, 0),
            onConfirm = { time ->
                onAddTime(time)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplementTimePickerDialog(
    initialTime: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.supplement_add_time_action)) },
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
                modifier = Modifier.height(48.dp),
            ) {
                Text(text = stringResource(R.string.saved_meals_save_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp),
            ) {
                Text(text = stringResource(R.string.saved_meals_save_dialog_cancel))
            }
        },
    )
}

private fun formatScheduleSummary(supplement: Supplement): String {
    val timesStr = supplement.times.joinToString(", ")
    val freq = when (supplement.scheduleType) {
        ScheduleType.DAILY -> "Diario"
        ScheduleType.WEEKDAYS -> "Días seleccionados"
        ScheduleType.EVERY_N_DAYS -> "Cada ${supplement.intervalDays ?: 1} días"
    }
    return "$freq · $timesStr"
}
