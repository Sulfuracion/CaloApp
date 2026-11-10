package com.controlcal.core.backup

/**
 * Preview summary of a backup file, presented to the user before confirming
 * a destructive database restoration.
 */
data class BackupSummary(
    val createdAt: Long,
    val schemaVersion: Int,
    val appVersion: String,
    val productCount: Int,
    val dayCount: Int,
    val mealEntryCount: Int,
    val weightEntryCount: Int,
    val supplementCount: Int = 0,
)
