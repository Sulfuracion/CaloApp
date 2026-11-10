package com.controlcal.core.supplement

import java.time.LocalDate

/**
 * Pure domain logic for calculating supplement adherence over a time window (e.g. last 30 days).
 */
object SupplementAdherenceCalculator {

    const val DEFAULT_DAYS_WINDOW = 30

    /**
     * Calculates adherence statistics for [supplements] over the period from [startDate] to [endDate] (inclusive).
     */
    fun calculateAdherence(
        supplements: List<Supplement>,
        intakes: List<SupplementIntake>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): OverallAdherence {
        if (startDate.isAfter(endDate)) {
            return OverallAdherence(
                totalScheduledDosesCount = 0,
                totalTakenCount = 0,
                totalSkippedCount = 0,
                totalPendingCount = 0,
                adherencePercentage = 100.0,
                supplementSummaries = emptyList(),
            )
        }

        val intakesBySupplementDateAndTime = intakes.associateBy { intake ->
            Triple(intake.supplementId, intake.scheduledDate, intake.scheduledTime)
        }

        val supplementSummaries = mutableListOf<SupplementAdherence>()
        var overallScheduled = 0
        var overallTaken = 0
        var overallSkipped = 0
        var overallPending = 0

        for (supplement in supplements) {
            var scheduledCount = 0
            var takenCount = 0
            var skippedCount = 0
            var pendingCount = 0

            var currentDate = startDate
            while (!currentDate.isAfter(endDate)) {
                if (SupplementScheduleCalculator.isScheduledOn(supplement, currentDate)) {
                    for (time in supplement.times) {
                        scheduledCount++
                        val intake = intakesBySupplementDateAndTime[Triple(supplement.id, currentDate, time)]
                        when (intake?.status) {
                            IntakeStatus.TAKEN -> takenCount++
                            IntakeStatus.SKIPPED -> skippedCount++
                            null -> pendingCount++
                        }
                    }
                }
                currentDate = currentDate.plusDays(1)
            }

            val percentage = if (scheduledCount == 0) {
                100.0
            } else {
                (takenCount.toDouble() / scheduledCount) * 100.0
            }

            supplementSummaries.add(
                SupplementAdherence(
                    supplementId = supplement.id,
                    supplementName = supplement.name,
                    scheduledDosesCount = scheduledCount,
                    takenCount = takenCount,
                    skippedCount = skippedCount,
                    pendingCount = pendingCount,
                    adherencePercentage = percentage,
                )
            )

            overallScheduled += scheduledCount
            overallTaken += takenCount
            overallSkipped += skippedCount
            overallPending += pendingCount
        }

        val overallPercentage = if (overallScheduled == 0) {
            100.0
        } else {
            (overallTaken.toDouble() / overallScheduled) * 100.0
        }

        return OverallAdherence(
            totalScheduledDosesCount = overallScheduled,
            totalTakenCount = overallTaken,
            totalSkippedCount = overallSkipped,
            totalPendingCount = overallPending,
            adherencePercentage = overallPercentage,
            supplementSummaries = supplementSummaries,
        )
    }
}
