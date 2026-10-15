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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    LaunchedEffect(uiState.infoMessage, uiState.errorMessage) {
        val msg = uiState.infoMessage ?: uiState.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.onMessageConsumed()
        }
    }

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
                            contentDescription = stringResource(R.string.weight_back),
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
                    onKcalChanged = onRestKcalChanged,
                    onProteinChanged = onRestProteinChanged,
                    onCarbsChanged = onRestCarbsChanged,
                    onFatChanged = onRestFatChanged,
                )
            }

            item {
                SafetyLimitsCard(
                    minKcalText = uiState.minKcalText,
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
                Button(
                    onClick = onSaveGoals,
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
            onDismiss = onDismissAddMealDialog,
            onConfirm = onConfirmAddMeal,
        )
    }

    val editing = uiState.editingMeal
    if (editing != null) {
        RenameMealDialog(
            meal = editing,
            onDismiss = onDismissRenameMealDialog,
            onConfirm = onConfirmRenameMeal,
        )
    }

    val changingTime = uiState.changingTimeMeal
    if (changingTime != null) {
        ChangeMealTimeDialog(
            meal = changingTime,
            onDismiss = onDismissChangeTimeDialog,
            onConfirm = onConfirmChangeStartTime,
        )
    }

    val deactivateOffer = uiState.deactivateOfferMeal
    if (deactivateOffer != null) {
        DeactivateOfferDialog(
            meal = deactivateOffer,
            onDismiss = onDismissDeleteDialogs,
            onConfirmDeactivate = { onDeactivateMeal(deactivateOffer.id) },
        )
    }

    val deleteConfirm = uiState.deleteConfirmMeal
    if (deleteConfirm != null) {
        DeleteMealConfirmDialog(
            meal = deleteConfirm,
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = carbsText,
                    onValueChange = onCarbsChanged,
                    label = { Text(stringResource(R.string.settings_carbs_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = fatText,
                    onValueChange = onFatChanged,
                    label = { Text(stringResource(R.string.settings_fat_label)) },
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
                supportingText = { Text(stringResource(R.string.settings_min_kcal_explanation)) },
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.settings_meal_rename_action),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onChangeTime, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_schedule),
                    contentDescription = stringResource(R.string.settings_meal_change_time_action),
                    modifier = Modifier.size(18.dp),
                )
            }
            if (meal.isActive) {
                TextButton(
                    onClick = onDeactivate,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_meal_deactivate_action),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            } else {
                TextButton(
                    onClick = onReactivate,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_meal_reactivate_action),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.settings_meal_delete_action),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AddMealDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, LocalTime) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var hourText by remember { mutableStateOf("08") }
    var minuteText by remember { mutableStateOf("00") }
    var inputError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.meal_dialog_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; inputError = null },
                    label = { Text(stringResource(R.string.meal_dialog_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { hourText = it; inputError = null },
                        label = { Text(stringResource(R.string.meal_dialog_hour_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { minuteText = it; inputError = null },
                        label = { Text(stringResource(R.string.meal_dialog_minute_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (inputError != null) {
                    Text(
                        text = inputError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanName = name.trim()
                    if (cleanName.isEmpty()) {
                        inputError = "El nombre no puede estar vacío"
                        return@TextButton
                    }
                    val h = hourText.trim().toIntOrNull()
                    val m = minuteText.trim().toIntOrNull()
                    if (h == null || h !in 0..23 || m == null || m !in 0..59) {
                        inputError = "Hora inválida (0–23h, 0–59m)"
                        return@TextButton
                    }
                    onConfirm(cleanName, LocalTime.of(h, m))
                }
            ) {
                Text(text = stringResource(R.string.meal_dialog_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.meal_dialog_cancel_action))
            }
        }
    )
}

@Composable
private fun RenameMealDialog(
    meal: ConfiguredMeal,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(meal.name) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.meal_dialog_rename_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text(stringResource(R.string.meal_dialog_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clean = name.trim()
                    if (clean.isEmpty()) {
                        error = "El nombre no puede estar vacío"
                        return@TextButton
                    }
                    onConfirm(meal.id, clean)
                }
            ) {
                Text(text = stringResource(R.string.meal_dialog_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.meal_dialog_cancel_action))
            }
        }
    )
}

@Composable
private fun ChangeMealTimeDialog(
    meal: ConfiguredMeal,
    onDismiss: () -> Unit,
    onConfirm: (String, LocalTime) -> Unit,
) {
    var hourText by remember { mutableStateOf(String.format("%02d", meal.startTime.hour)) }
    var minuteText by remember { mutableStateOf(String.format("%02d", meal.startTime.minute)) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                        onValueChange = { hourText = it; error = null },
                        label = { Text(stringResource(R.string.meal_dialog_hour_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { minuteText = it; error = null },
                        label = { Text(stringResource(R.string.meal_dialog_minute_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val h = hourText.trim().toIntOrNull()
                    val m = minuteText.trim().toIntOrNull()
                    if (h == null || h !in 0..23 || m == null || m !in 0..59) {
                        error = "Hora inválida (0–23h, 0–59m)"
                        return@TextButton
                    }
                    onConfirm(meal.id, LocalTime.of(h, m))
                }
            ) {
                Text(text = stringResource(R.string.meal_dialog_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.meal_dialog_cancel_action))
            }
        }
    )
}

@Composable
private fun DeactivateOfferDialog(
    meal: ConfiguredMeal,
    onDismiss: () -> Unit,
    onConfirmDeactivate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.meal_deactivate_offer_title)) },
        text = {
            Text(text = stringResource(R.string.meal_deactivate_offer_message, meal.name))
        },
        confirmButton = {
            Button(onClick = onConfirmDeactivate) {
                Text(text = stringResource(R.string.meal_deactivate_offer_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.meal_dialog_cancel_action))
            }
        }
    )
}

@Composable
private fun DeleteMealConfirmDialog(
    meal: ConfiguredMeal,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.meal_delete_confirm_title)) },
        text = {
            Text(text = stringResource(R.string.meal_delete_confirm_message, meal.name))
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
            ) {
                Text(text = stringResource(R.string.meal_delete_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.meal_dialog_cancel_action))
            }
        }
    )
}
