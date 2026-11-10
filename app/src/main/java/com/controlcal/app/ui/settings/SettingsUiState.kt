package com.controlcal.app.ui.settings

import android.net.Uri
import com.controlcal.app.R
import com.controlcal.app.ui.feedback.UiText
import com.controlcal.core.backup.BackupSummary
import com.controlcal.core.model.ConfiguredMeal
import com.controlcal.core.model.WeeklyDayPattern
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class SettingsUiState(
    val weeklyPattern: WeeklyDayPattern = WeeklyDayPattern(),
    val trainingKcalText: String = "",
    val trainingProteinText: String = "",
    val trainingCarbsText: String = "",
    val trainingFatText: String = "",
    val restKcalText: String = "",
    val restProteinText: String = "",
    val restCarbsText: String = "",
    val restFatText: String = "",
    val minKcalText: String = "1200",
    val minKcalError: String? = null,
    val trainingKcalError: String? = null,
    val trainingProteinError: String? = null,
    val trainingCarbsError: String? = null,
    val trainingFatError: String? = null,
    val restKcalError: String? = null,
    val restProteinError: String? = null,
    val restCarbsError: String? = null,
    val restFatError: String? = null,
    val isSavingGoals: Boolean = false,
    val isSavingMeal: Boolean = false,
    val configuredMeals: List<ConfiguredMeal> = emptyList(),
    val showAddMealDialog: Boolean = false,
    val editingMeal: ConfiguredMeal? = null,
    val changingTimeMeal: ConfiguredMeal? = null,
    val deactivateOfferMeal: ConfiguredMeal? = null,
    val deleteConfirmMeal: ConfiguredMeal? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isLoading: Boolean = false,
    val isHealthConnectAvailable: Boolean = true,
    val isHealthConnectStepsSyncEnabled: Boolean = false,
    val showHealthConnectRationaleDialog: Boolean = false,
    // Backup (Phase 7.2)
    val lastBackupTimestamp: Long? = null,
    val isExportPasswordDialogOpen: Boolean = false,
    val exportPasswordText: String = "",
    val exportConfirmPasswordText: String = "",
    val exportPasswordError: UiText? = null,
    val isImportPasswordDialogOpen: Boolean = false,
    val importPasswordText: String = "",
    val importPasswordError: UiText? = null,
    val pendingImportUri: Uri? = null,
    val restoreSummary: BackupSummary? = null,
    val isRestoreConfirmDialogOpen: Boolean = false,
    val isBackupLoading: Boolean = false,
) {
    fun formatLastBackup(): UiText {
        val timestamp = lastBackupTimestamp ?: return UiText.StringResource(
            R.string.backup_last_copy_prefix,
            UiText.StringResource(R.string.backup_last_copy_never)
        )
        val backupDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        val days = ChronoUnit.DAYS.between(backupDate, today)
        val timeDesc = when {
            days <= 0 -> UiText.StringResource(R.string.backup_last_copy_today)
            days == 1L -> UiText.StringResource(R.string.backup_last_copy_yesterday)
            else -> UiText.StringResource(R.string.backup_last_copy_days_ago, days.toInt())
        }
        return UiText.StringResource(R.string.backup_last_copy_prefix, timeDesc)
    }
}
