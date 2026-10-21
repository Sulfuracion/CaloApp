package com.controlcal.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.controlcal.core.model.DayType
import java.time.DayOfWeek

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.controlcal.app.ui.feedback.HandleFeedbackEvents
import com.controlcal.core.model.ConfiguredMeal
import java.time.LocalTime

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    HandleFeedbackEvents(
        events = viewModel.feedbackEvents,
        snackbarHostState = snackbarHostState,
    )

    SettingsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onDayPatternToggled = viewModel::onDayPatternToggled,
        onTrainingKcalChanged = viewModel::onTrainingKcalChanged,
        onTrainingProteinChanged = viewModel::onTrainingProteinChanged,
        onTrainingCarbsChanged = viewModel::onTrainingCarbsChanged,
        onTrainingFatChanged = viewModel::onTrainingFatChanged,
        onRestKcalChanged = viewModel::onRestKcalChanged,
        onRestProteinChanged = viewModel::onRestProteinChanged,
        onRestCarbsChanged = viewModel::onRestCarbsChanged,
        onRestFatChanged = viewModel::onRestFatChanged,
        onMinKcalChanged = viewModel::onMinKcalChanged,
        onSaveGoals = viewModel::onSaveGoals,
        onOpenAddMealDialog = viewModel::onOpenAddMealDialog,
        onDismissAddMealDialog = viewModel::onDismissAddMealDialog,
        onConfirmAddMeal = viewModel::onConfirmAddMeal,
        onOpenRenameMealDialog = viewModel::onOpenRenameMealDialog,
        onDismissRenameMealDialog = viewModel::onDismissRenameMealDialog,
        onConfirmRenameMeal = viewModel::onConfirmRenameMeal,
        onOpenChangeTimeDialog = viewModel::onOpenChangeTimeDialog,
        onDismissChangeTimeDialog = viewModel::onDismissChangeTimeDialog,
        onConfirmChangeStartTime = viewModel::onConfirmChangeStartTime,
        onDeactivateMeal = viewModel::onDeactivateMeal,
        onReactivateMeal = viewModel::onReactivateMeal,
        onDeleteMealRequested = viewModel::onDeleteMealRequested,
        onConfirmDeleteMeal = viewModel::onConfirmDeleteMeal,
        onDismissDeleteDialogs = viewModel::onDismissDeleteDialogs,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onDayPatternToggled: (DayOfWeek, DayType) -> Unit,
    onTrainingKcalChanged: (String) -> Unit,
    onTrainingProteinChanged: (String) -> Unit,
    onTrainingCarbsChanged: (String) -> Unit,
    onTrainingFatChanged: (String) -> Unit,
    onRestKcalChanged: (String) -> Unit,
    onRestProteinChanged: (String) -> Unit,
    onRestCarbsChanged: (String) -> Unit,
    onRestFatChanged: (String) -> Unit,
    onMinKcalChanged: (String) -> Unit,
    onSaveGoals: () -> Unit,
    onOpenAddMealDialog: () -> Unit,
    onDismissAddMealDialog: () -> Unit,
    onConfirmAddMeal: (String, LocalTime) -> Unit,
    onOpenRenameMealDialog: (ConfiguredMeal) -> Unit,
    onDismissRenameMealDialog: () -> Unit,
    onConfirmRenameMeal: (String, String) -> Unit,
    onOpenChangeTimeDialog: (ConfiguredMeal) -> Unit,
    onDismissChangeTimeDialog: () -> Unit,
    onConfirmChangeStartTime: (String, LocalTime) -> Unit,
    onDeactivateMeal: (String) -> Unit,
    onReactivateMeal: (String) -> Unit,
    onDeleteMealRequested: (ConfiguredMeal) -> Unit,
    onConfirmDeleteMeal: (String) -> Unit,
    onDismissDeleteDialogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.settings_back),
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                WeeklyPatternCard(
                    uiState = uiState,
                    onDayPatternToggled = onDayPatternToggled,
                )
            }

            item {
                GoalProfileCard(
                    title = stringResource(R.string.settings_training_goals_title),
                    subtitle = stringResource(R.string.settings_training_goals_subtitle),
                    kcalText = uiState.trainingKcalText,
                    proteinText = uiState.trainingProteinText,
                    carbsText = uiState.trainingCarbsText,
                    fatText = uiState.trainingFatText,
                    kcalError = uiState.trainingKcalError,
                    proteinError = uiState.trainingProteinError,
                    carbsError = uiState.trainingCarbsError,
                    fatError = uiState.trainingFatError,
                    onKcalChanged = onTrainingKcalChanged,
                    onProteinChanged = onTrainingProteinChanged,
                    onCarbsChanged = onTrainingCarbsChanged,
                    onFatChanged = onTrainingFatChanged,
                )
            }

            item {
                GoalProfileCard(
                    title = stringResource(R.string.settings_rest_goals_title),
                    subtitle = stringResource(R.string.settings_rest_goals_subtitle),
                    kcalText = uiState.restKcalText,
                    proteinText = uiState.restProteinText,
                    carbsText = uiState.restCarbsText,
                    fatText = uiState.restFatText,
                    kcalError = uiState.restKcalError,
                    proteinError = uiState.restProteinError,
                    carbsError = uiState.restCarbsError,
                    fatError = uiState.restFatError,
                    onKcalChanged = onRestKcalChanged,
                    onProteinChanged = onRestProteinChanged,
                    onCarbsChanged = onRestCarbsChanged,
                    onFatChanged = onRestFatChanged,
                )
            }

            item {
                SafetyLimitsCard(
                    minKcalText = uiState.minKcalText,
                    minKcalError = uiState.minKcalError,
                    onMinKcalChanged = onMinKcalChanged,
                )
            }

            item {
                MealConfigurationCard(
                    meals = uiState.configuredMeals,
                    onAddMeal = onOpenAddMealDialog,
                    onRenameMeal = onOpenRenameMealDialog,
                    onChangeTime = onOpenChangeTimeDialog,
                    onDeactivateMeal = onDeactivateMeal,
                    onReactivateMeal = onReactivateMeal,
                    onDeleteMeal = onDeleteMealRequested,
                )
            }

            item {
                val isGoalsValid = uiState.minKcalError == null &&
                    uiState.trainingKcalError == null && uiState.trainingProteinError == null &&
                    uiState.trainingCarbsError == null && uiState.trainingFatError == null &&
                    uiState.restKcalError == null && uiState.restProteinError == null &&
                    uiState.restCarbsError == null && uiState.restFatError == null &&
                    uiState.minKcalText.isNotBlank() && uiState.trainingKcalText.isNotBlank() &&
                    uiState.trainingProteinText.isNotBlank() && uiState.trainingCarbsText.isNotBlank() &&
                    uiState.trainingFatText.isNotBlank() && uiState.restKcalText.isNotBlank() &&
                    uiState.restProteinText.isNotBlank() && uiState.restCarbsText.isNotBlank() &&
                    uiState.restFatText.isNotBlank()

                Button(
                    onClick = onSaveGoals,
                    enabled = isGoalsValid && !uiState.isSavingGoals,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Text(text = stringResource(R.string.settings_save_goals_action))
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (uiState.showAddMealDialog) {
        AddMealDialog(
            isSaving = uiState.isSavingMeal,
            onDismiss = onDismissAddMealDialog,
            onConfirm = onConfirmAddMeal,
        )
    }

    val editing = uiState.editingMeal
    if (editing != null) {
        RenameMealDialog(
            meal = editing,
            isSaving = uiState.isSavingMeal,
            onDismiss = onDismissRenameMealDialog,
            onConfirm = onConfirmRenameMeal,
        )
    }

    val changingTime = uiState.changingTimeMeal
    if (changingTime != null) {
        ChangeMealTimeDialog(
            meal = changingTime,
            isSaving = uiState.isSavingMeal,
            onDismiss = onDismissChangeTimeDialog,
            onConfirm = onConfirmChangeStartTime,
        )
    }

    val deactivateOffer = uiState.deactivateOfferMeal
    if (deactivateOffer != null) {
        DeactivateOfferDialog(
            meal = deactivateOffer,
            isSaving = uiState.isSavingMeal,
            onDismiss = onDismissDeleteDialogs,
            onConfirmDeactivate = { onDeactivateMeal(deactivateOffer.id) },
        )
    }

    val deleteConfirm = uiState.deleteConfirmMeal
    if (deleteConfirm != null) {
        DeleteMealConfirmDialog(
            meal = deleteConfirm,
            isSaving = uiState.isSavingMeal,
            onDismiss = onDismissDeleteDialogs,
            onConfirmDelete = { onConfirmDeleteMeal(deleteConfirm.id) },
        )
    }
}

@Composable
private fun WeeklyPatternCard(
    uiState: SettingsUiState,
    onDayPatternToggled: (DayOfWeek, DayType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_weekly_pattern_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_weekly_pattern_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            val days = listOf(
                DayOfWeek.MONDAY to stringResource(R.string.settings_day_monday_full),
                DayOfWeek.TUESDAY to stringResource(R.string.settings_day_tuesday_full),
                DayOfWeek.WEDNESDAY to stringResource(R.string.settings_day_wednesday_full),
                DayOfWeek.THURSDAY to stringResource(R.string.settings_day_thursday_full),
                DayOfWeek.FRIDAY to stringResource(R.string.settings_day_friday_full),
                DayOfWeek.SATURDAY to stringResource(R.string.settings_day_saturday_full),
                DayOfWeek.SUNDAY to stringResource(R.string.settings_day_sunday_full),
            )

            for ((dayOfWeek, dayName) in days) {
                val currentType = uiState.weeklyPattern.dayTypeFor(dayOfWeek)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = currentType == DayType.TRAINING,
                            onClick = { onDayPatternToggled(dayOfWeek, DayType.TRAINING) },
                            label = { Text(stringResource(R.string.day_type_training)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                        FilterChip(
                            selected = currentType == DayType.REST,
                            onClick = { onDayPatternToggled(dayOfWeek, DayType.REST) },
                            label = { Text(stringResource(R.string.day_type_rest)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalProfileCard(
    title: String,
    subtitle: String,
    kcalText: String,
    proteinText: String,
    carbsText: String,
    fatText: String,
    kcalError: String? = null,
    proteinError: String? = null,
    carbsError: String? = null,
    fatError: String? = null,
    onKcalChanged: (String) -> Unit,
    onProteinChanged: (String) -> Unit,
    onCarbsChanged: (String) -> Unit,
    onFatChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = kcalText,
                onValueChange = onKcalChanged,
                label = { Text(stringResource(R.string.settings_kcal_label)) },
                isError = kcalError != null,
                supportingText = kcalError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = proteinText,
                    onValueChange = onProteinChanged,
                    label = { Text(stringResource(R.string.settings_protein_label)) },
                    isError = proteinError != null,
                    supportingText = proteinError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = carbsText,
                    onValueChange = onCarbsChanged,
                    label = { Text(stringResource(R.string.settings_carbs_label)) },
                    isError = carbsError != null,
                    supportingText = carbsError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = fatText,
                    onValueChange = onFatChanged,
                    label = { Text(stringResource(R.string.settings_fat_label)) },
                    isError = fatError != null,
                    supportingText = fatError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SafetyLimitsCard(
    minKcalText: String,
    minKcalError: String? = null,
    onMinKcalChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_safety_limits_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.settings_safety_limits_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = minKcalText,
                onValueChange = onMinKcalChanged,
                label = { Text(stringResource(R.string.settings_min_kcal_label)) },
                isError = minKcalError != null,
                supportingText = { Text(minKcalError ?: stringResource(R.string.settings_min_kcal_explanation)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MealConfigurationCard(
    meals: List<ConfiguredMeal>,
    onAddMeal: () -> Unit,
    onRenameMeal: (ConfiguredMeal) -> Unit,
    onChangeTime: (ConfiguredMeal) -> Unit,
    onDeactivateMeal: (String) -> Unit,
    onReactivateMeal: (String) -> Unit,
    onDeleteMeal: (ConfiguredMeal) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_meals_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.settings_meals_section_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                meals.forEachIndexed { index, meal ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                    MealItemRow(
                        meal = meal,
                        onRename = { onRenameMeal(meal) },
                        onChangeTime = { onChangeTime(meal) },
                        onDeactivate = { onDeactivateMeal(meal.id) },
                        onReactivate = { onReactivateMeal(meal.id) },
                        onDelete = { onDeleteMeal(meal) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onAddMeal,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.settings_meals_add_action))
            }
        }
    }
}

@Composable
private fun MealItemRow(
    meal: ConfiguredMeal,
    onRename: () -> Unit,
    onChangeTime: () -> Unit,
    onDeactivate: () -> Unit,
    onReactivate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formattedTime = remember(meal.startTime) {
        String.format("%02d:%02d", meal.startTime.hour, meal.startTime.minute)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = meal.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!meal.isActive) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_meal_inactive_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onRename) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.settings_meal_rename_action),
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onChangeTime) {
                Icon(
                    painter = painterResource(R.drawable.ic_schedule),
                    contentDescription = stringResource(R.string.settings_meal_change_time_action),
                    modifier = Modifier.size(20.dp),
                )
            }
            if (meal.isActive) {
                TextButton(
                    onClick = onDeactivate,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_meal_deactivate_action),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            } else {
                TextButton(
                    onClick = onReactivate,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_meal_reactivate_action),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.settings_meal_delete_action),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun AddMealDialog(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, LocalTime) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var hourText by remember { mutableStateOf("08") }
    var minuteText by remember { mutableStateOf("00") }
    var touched by remember { mutableStateOf(false) }

    val nameError = if (touched && name.trim().isEmpty()) "El nombre no puede estar vacío" else null
    val h = hourText.trim().toIntOrNull()
    val m = minuteText.trim().toIntOrNull()
    val timeError = if (touched && (h == null || h !in 0..23 || m == null || m !in 0..59)) "Hora inválida (0–23h, 0–59m)" else null
    val isValid = name.trim().isNotEmpty() && h != null && h in 0..23 && m != null && m in 0..59

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(text = stringResource(R.string.meal_dialog_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; touched = true },
                    label = { Text(stringResource(R.string.meal_dialog_name_label)) },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { hourText = it; touched = true },
                        label = { Text(stringResource(R.string.meal_dialog_hour_label)) },
                        isError = timeError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { minuteText = it; touched = true },
                        label = { Text(stringResource(R.string.meal_dialog_minute_label)) },
                        isError = timeError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (timeError != null) {
                    Text(
                        text = timeError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    touched = true
                    if (isValid && !isSaving) {
                        onConfirm(name.trim(), LocalTime.of(h, m))
                    }
                },
                enabled = isValid && !isSaving,
            ) {
                Text(text = stringResource(R.string.meal_dialog_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(text = stringResource(R.string.meal_dialog_cancel_action))
            }
        }
    )
}

@Composable
private fun RenameMealDialog(
    meal: ConfiguredMeal,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(meal.name) }
    var touched by remember { mutableStateOf(false) }
    val nameError = if (touched && name.trim().isEmpty()) "El nombre no puede estar vacío" else null
    val isValid = name.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(text = stringResource(R.string.meal_dialog_rename_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; touched = true },
                    label = { Text(stringResource(R.string.meal_dialog_name_label)) },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    touched = true
                    if (isValid && !isSaving) {
                        onConfirm(meal.id, name.trim())
                    }
                },
                enabled = isValid && !isSaving,
            ) {
                Text(text = stringResource(R.string.meal_dialog_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(text = stringResource(R.string.meal_dialog_cancel_action))
            }
        }
    )
}

@Composable
private fun ChangeMealTimeDialog(
    meal: ConfiguredMeal,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, LocalTime) -> Unit,
) {
    var hourText by remember { mutableStateOf(String.format("%02d", meal.startTime.hour)) }
    var minuteText by remember { mutableStateOf(String.format("%02d", meal.startTime.minute)) }
    var touched by remember { mutableStateOf(false) }

    val h = hourText.trim().toIntOrNull()
    val m = minuteText.trim().toIntOrNull()
    val isValid = h != null && h in 0..23 && m != null && m in 0..59
    val error = if (touched && !isValid) "Hora inválida (0–23h, 0–59m)" else null

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(text = stringResource(R.string.meal_dialog_change_time_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = meal.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { hourText = it; touched = true },
                        label = { Text(stringResource(R.string.meal_dialog_hour_label)) },
                        isError = error != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { minuteText = it; touched = true },
                        label = { Text(stringResource(R.string.meal_dialog_minute_label)) },
                        isError = error != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    touched = true
                    if (isValid && !isSaving) {
                        onConfirm(meal.id, LocalTime.of(h, m))
                    }
                },
                enabled = isValid && !isSaving,
            ) {
                Text(text = stringResource(R.string.meal_dialog_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(text = stringResource(R.string.meal_dialog_cancel_action))
            }
        }
    )
}

@Composable
private fun DeactivateOfferDialog(
    meal: ConfiguredMeal,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirmDeactivate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(text = stringResource(R.string.meal_deactivate_offer_title)) },
        text = {
            Text(text = stringResource(R.string.meal_deactivate_offer_message, meal.name))
        },
        confirmButton = {
            Button(onClick = onConfirmDeactivate, enabled = !isSaving) {
                Text(text = stringResource(R.string.meal_deactivate_offer_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(text = stringResource(R.string.meal_dialog_cancel_action))
            }
        }
    )
}

@Composable
private fun DeleteMealConfirmDialog(
    meal: ConfiguredMeal,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(text = stringResource(R.string.meal_delete_confirm_title)) },
        text = {
            Text(text = stringResource(R.string.meal_delete_confirm_message, meal.name))
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                enabled = !isSaving,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
            ) {
                Text(text = stringResource(R.string.meal_delete_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(text = stringResource(R.string.meal_dialog_cancel_action))
            }
        }
    )
}
