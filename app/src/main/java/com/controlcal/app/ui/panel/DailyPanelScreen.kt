package com.controlcal.app.ui.panel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlcal.app.R
import com.controlcal.core.date.DateHelper
import com.controlcal.core.model.DayType
import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.MealEntry
import com.controlcal.core.nutrition.NumberFormatHelper
import com.controlcal.core.suggest.QuickLogSuggestion
import com.controlcal.core.tdee.TdeeGoalProposal
import com.controlcal.app.ui.feedback.HandleFeedbackEvents
import java.time.LocalDate

@Composable
fun DailyPanelRoute(
    viewModel: DailyPanelViewModel,
    onNavigateToScanner: () -> Unit,
    onNavigateToWeight: () -> Unit = {},
    onNavigateToPantry: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToMacroTetris: () -> Unit = {},
    onNavigateToMealDetail: (mealId: String) -> Unit = {},
    onNavigateToAddFood: (mealId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    HandleFeedbackEvents(
        events = viewModel.feedbackEvents,
        snackbarHostState = snackbarHostState,
    )

    DailyPanelScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateToScanner = onNavigateToScanner,
        onNavigateToWeight = onNavigateToWeight,
        onNavigateToPantry = onNavigateToPantry,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToMacroTetris = onNavigateToMacroTetris,
        onNavigateToMealDetail = onNavigateToMealDetail,
        onNavigateToAddFood = onNavigateToAddFood,
        onPreviousDay = viewModel::onPreviousDay,
        onNextDay = viewModel::onNextDay,
        onSelectToday = viewModel::onSelectToday,
        onDateSelected = viewModel::onDateSelected,
        onCopyYesterday = viewModel::onCopyYesterday,
        onRepeatMeal = viewModel::onRepeatMeal,
        onDayTypeChanged = viewModel::onDayTypeChanged,
        onSuggestionTapped = viewModel::onSuggestionTapped,
        onSuggestionLongPressed = viewModel::onSuggestionLongPressed,
        onEditingGramsChanged = viewModel::onEditingGramsChanged,
        onConfirmQuickLog = viewModel::onConfirmQuickLog,
        onDismissEditDialog = viewModel::onDismissEditDialog,
        onAcceptTdeeProposal = viewModel::onAcceptTdeeProposal,
        onRejectTdeeProposal = viewModel::onRejectTdeeProposal,
        onAddWater = viewModel::onAddWater,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyPanelScreen(
    uiState: DailyPanelUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateToScanner: () -> Unit,
    onNavigateToWeight: () -> Unit,
    onNavigateToPantry: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMacroTetris: () -> Unit = {},
    onNavigateToMealDetail: (mealId: String) -> Unit = {},
    onNavigateToAddFood: (mealId: String) -> Unit = {},
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onSelectToday: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onCopyYesterday: () -> Unit,
    onRepeatMeal: (String) -> Unit,
    onDayTypeChanged: (DayType) -> Unit,
    onSuggestionTapped: (QuickLogSuggestion) -> Unit,
    onSuggestionLongPressed: (QuickLogSuggestion) -> Unit,
    onEditingGramsChanged: (String) -> Unit,
    onConfirmQuickLog: () -> Unit,
    onDismissEditDialog: () -> Unit,
    onAcceptTdeeProposal: () -> Unit,
    onRejectTdeeProposal: () -> Unit,
    onAddWater: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showOptionsMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.daily_panel_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        DateNavigator(
                            selectedDate = uiState.selectedDate,
                            isToday = uiState.isToday,
                            onPreviousDay = onPreviousDay,
                            onNextDay = onNextDay,
                            onSelectToday = onSelectToday,
                            onDateSelected = onDateSelected,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToMacroTetris,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_macro_tetris),
                            contentDescription = stringResource(R.string.daily_panel_tetris_action),
                        )
                    }
                    IconButton(
                        onClick = onNavigateToWeight,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_scale),
                            contentDescription = stringResource(R.string.weight_navigate_action),
                        )
                    }
                    Box {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = stringResource(R.string.meal_detail_options_menu),
                            )
                        }
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.pantry_action)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_pantry),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    onNavigateToPantry()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.daily_panel_copy_yesterday_action)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_copy),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    onCopyYesterday()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(text = stringResource(R.string.settings_action)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_settings),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    onNavigateToSettings()
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToScanner,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_document_scanner),
                    contentDescription = stringResource(R.string.daily_panel_scanner_action),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        var totalDragX by remember { mutableFloatStateOf(0f) }
        val density = LocalDensity.current
        val thresholdPx = remember(density) { with(density) { 50.dp.toPx() } }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .pointerInput(uiState.selectedDate) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (totalDragX > thresholdPx) {
                                onPreviousDay()
                            } else if (totalDragX < -thresholdPx) {
                                onNextDay()
                            }
                            totalDragX = 0f
                        },
                        onDragCancel = {
                            totalDragX = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount
                        }
                    )
                },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.isWeeklyProposalVisible && uiState.tdeeProposal != null) {
                item {
                    WeeklyTdeeProposalCard(
                        proposal = uiState.tdeeProposal,
                        onAcceptProposal = onAcceptTdeeProposal,
                        onRejectProposal = onRejectTdeeProposal,
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                DailySummaryCard(
                    uiState = uiState,
                    onDayTypeChanged = onDayTypeChanged,
                    onNavigateToMacroTetris = onNavigateToMacroTetris,
                )
            }

            item {
                QuickLogSuggestionsSection(
                    suggestions = uiState.suggestions,
                    currentSlotMealName = uiState.currentSlotMealName,
                    onSuggestionTapped = onSuggestionTapped,
                    onSuggestionLongPressed = onSuggestionLongPressed,
                )
            }

            items(uiState.mealSections, key = { it.mealId }) { section ->
                MealCard(
                    section = section,
                    onCardClick = { onNavigateToMealDetail(section.mealId) },
                    onAddFoodClick = { onNavigateToAddFood(section.mealId) },
                )
            }

            item {
                WaterIntakeCard(
                    waterMl = uiState.waterMl,
                    onAddWater = { onAddWater(250) },
                    onSubtractWater = { onAddWater(-250) },
                )
            }

            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }

    val editing = uiState.editingSuggestion
    if (editing != null) {
        QuickLogQuantityDialog(
            suggestion = editing,
            gramsText = uiState.editingGramsText,
            errorText = uiState.editingGramsError,
            portionKcal = uiState.portionPreviewKcal,
            portionProtein = uiState.portionPreviewProtein,
            portionCarbs = uiState.portionPreviewCarbs,
            portionFat = uiState.portionPreviewFat,
            isSaving = uiState.isSaving,
            onGramsChanged = onEditingGramsChanged,
            onConfirm = onConfirmQuickLog,
            onDismiss = onDismissEditDialog,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateNavigator(
    selectedDate: LocalDate,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onSelectToday: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    val currentYear = remember { DateHelper.getToday().year }
    val sameYearPattern = stringResource(R.string.date_pattern_nav_current_year)
    val otherYearPattern = stringResource(R.string.date_pattern_nav_other_year)
    val dateFormatted = remember(selectedDate, currentYear, sameYearPattern, otherYearPattern) {
        DateHelper.formatDate(selectedDate, currentYear, sameYearPattern, otherYearPattern)
    }

    val dateText = if (isToday) {
        "${stringResource(R.string.daily_panel_today)}, $dateFormatted"
    } else {
        dateFormatted
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPreviousDay,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = stringResource(R.string.daily_panel_previous_day),
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = dateText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.date_picker_select_date),
                onClick = { showDatePicker = true },
            ),
        )
        IconButton(
            onClick = onNextDay,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.daily_panel_next_day),
                modifier = Modifier.size(20.dp),
            )
        }
        if (!isToday) {
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
                onClick = onSelectToday,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = stringResource(R.string.daily_panel_today),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = remember(selectedDate) {
            DateHelper.localDateToUtcMillis(selectedDate)
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selected = DateHelper.utcMillisToLocalDate(millis)
                            onDateSelected(selected)
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
private fun DailySummaryCard(
    uiState: DailyPanelUiState,
    onDayTypeChanged: (DayType) -> Unit,
    onNavigateToMacroTetris: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val totals = uiState.dayTotals.totals
    val targetProfile = uiState.activeGoalProfile
    val remaining = uiState.dayRemaining
    val targetKcal = targetProfile?.kcal ?: 0.0

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Day Type Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = uiState.dayType == DayType.TRAINING,
                    onClick = { onDayTypeChanged(DayType.TRAINING) },
                    label = { Text(stringResource(R.string.day_type_training)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
                FilterChip(
                    selected = uiState.dayType == DayType.REST,
                    onClick = { onDayTypeChanged(DayType.REST) },
                    label = { Text(stringResource(R.string.day_type_rest)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Calories with circular ring (Task 1.4)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (targetKcal > 0.0) {
                    val progress = (totals.kcal / targetKcal.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
                    val isSurplus = (remaining?.surplusKcal ?: 0.0) > 0.0
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(64.dp),
                    ) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 7.dp,
                            color = if (isSurplus) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f),
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSurplus) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (targetKcal > 0.0) {
                            "${NumberFormatHelper.formatDecimal(totals.kcal)} / ${NumberFormatHelper.formatDecimal(targetKcal)} ${stringResource(R.string.daily_panel_kcal_label)}"
                        } else {
                            "${NumberFormatHelper.formatDecimal(totals.kcal)} ${stringResource(R.string.daily_panel_kcal_label)}"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (remaining != null && targetKcal > 0.0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        val remainingText = if (remaining.surplusKcal > 0.0) {
                            stringResource(R.string.daily_panel_surplus_kcal, NumberFormatHelper.formatDecimal(remaining.surplusKcal))
                        } else {
                            stringResource(R.string.daily_panel_remaining_kcal, NumberFormatHelper.formatDecimal(remaining.remainingKcal))
                        }
                        val textColor = if (remaining.surplusKcal > 0.0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = remainingText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = textColor,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Macro breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MacroBadge(
                    label = stringResource(R.string.daily_panel_protein_label),
                    value = totals.protein,
                    target = targetProfile?.proteinG ?: 0.0,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                MacroBadge(
                    label = stringResource(R.string.daily_panel_carbs_label),
                    value = totals.carbs,
                    target = targetProfile?.carbsG ?: 0.0,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                MacroBadge(
                    label = stringResource(R.string.daily_panel_fat_label),
                    value = totals.fat,
                    target = targetProfile?.fatG ?: 0.0,
                    modifier = Modifier.weight(1f),
                )
            }

            if (targetKcal > 0.0 && remaining != null && (remaining.remainingKcal > 0.0 || remaining.remainingProtein > 0.0 || remaining.remainingCarbs > 0.0 || remaining.remainingFat > 0.0)) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onNavigateToMacroTetris,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_macro_tetris),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.daily_panel_adjust_with_tetris))
                }
            }
        }
    }
}

@Composable
private fun MacroBadge(
    label: String,
    value: Double,
    target: Double,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = if (target > 0.0) {
                "${NumberFormatHelper.formatDecimal(value)} / ${NumberFormatHelper.formatDecimal(target)} g"
            } else {
                "${NumberFormatHelper.formatDecimal(value)} g"
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (target > 0.0) {
            Spacer(modifier = Modifier.height(4.dp))
            val progress = (value / target.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickLogSuggestionsSection(
    suggestions: List<QuickLogSuggestion>,
    currentSlotMealName: String,
    onSuggestionTapped: (QuickLogSuggestion) -> Unit,
    onSuggestionLongPressed: (QuickLogSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.daily_panel_quick_log_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "$currentSlotMealName · ${stringResource(R.string.daily_panel_quick_log_subtitle)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (suggestions.isEmpty()) {
                Text(
                    text = stringResource(R.string.daily_panel_quick_log_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { suggestion ->
                        QuickLogChip(
                            suggestion = suggestion,
                            onTap = { onSuggestionTapped(suggestion) },
                            onLongPress = { onSuggestionLongPressed(suggestion) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickLogChip(
    suggestion: QuickLogSuggestion,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress,
            ),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = suggestion.productName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.width(6.dp))
            val unitLabel = if (suggestion.isLiquid) "ml" else "g"
            Text(
                text = "· ${NumberFormatHelper.formatDecimal(suggestion.defaultGrams)} $unitLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun MealCard(
    section: MealSectionUiModel,
    onCardClick: () -> Unit,
    onAddFoodClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mealTitle = if (section.isUnassigned) stringResource(R.string.unassigned_meal_name) else section.name
    val mealKcal = section.totalKcal
    val summaryText = section.summary.ifEmpty { stringResource(R.string.daily_panel_empty_meal) }

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mealTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (section.isInactive) {
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = "${NumberFormatHelper.formatDecimal(mealKcal)} ${stringResource(R.string.daily_panel_kcal_label)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (section.summary.isEmpty()) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            FilledTonalIconButton(
                onClick = onAddFoodClick,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.daily_panel_meal_add_food_description, mealTitle),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun WaterIntakeCard(
    waterMl: Int,
    onAddWater: () -> Unit,
    onSubtractWater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glasses = waterMl / 250
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.daily_panel_water_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.daily_panel_water_summary, waterMl, glasses),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onSubtractWater,
                    enabled = waterMl > 0,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(text = stringResource(R.string.daily_panel_water_subtract_action))
                }
                Button(
                    onClick = onAddWater,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(text = stringResource(R.string.daily_panel_water_add_action))
                }
            }
        }
    }
}

@Composable
private fun WeeklyTdeeProposalCard(
    proposal: TdeeGoalProposal,
    onAcceptProposal: () -> Unit,
    onRejectProposal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_scale),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = stringResource(R.string.tdee_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.tdee_card_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

            Text(
                text = proposal.explanation,
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = proposal.disclaimer,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onRejectProposal) {
                    Text(
                        text = stringResource(R.string.tdee_card_reject_action),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onAcceptProposal) {
                    Text(stringResource(R.string.tdee_card_accept_action))
                }
            }
        }
    }
}
