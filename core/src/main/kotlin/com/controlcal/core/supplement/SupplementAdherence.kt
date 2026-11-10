package com.controlcal.core.supplement

/**
 * Adherence summary for an individual supplement over a period (e.g. 30 days).
 */
data class SupplementAdherence(
    val supplementId: Long,
    val supplementName: String,
    val scheduledDosesCount: Int,
    val takenCount: Int,
    val skippedCount: Int,
    val pendingCount: Int,
    val adherencePercentage: Double,
)

/**
 * Overall adherence summary across all supplements over a period.
 */
data class OverallAdherence(
    val totalScheduledDosesCount: Int,
    val totalTakenCount: Int,
    val totalSkippedCount: Int,
    val totalPendingCount: Int,
    val adherencePercentage: Double,
    val supplementSummaries: List<SupplementAdherence>,
)
