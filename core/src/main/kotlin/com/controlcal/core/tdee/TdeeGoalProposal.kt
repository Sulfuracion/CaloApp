package com.controlcal.core.tdee

/**
 * Weekly goal proposal model adhering to section 5.5 of docs/PLAN.md:
 * - Proposes a new daily kcal target.
 * - Limited to +/- 150 kcal per week.
 * - Never below the minimum kcal limit configured in user settings.
 * - Transparent explanation of how it was derived.
 * - Explicit medical disclaimer.
 */
data class TdeeGoalProposal(
    val currentKcal: Double,
    val proposedKcal: Double,
    val deltaKcal: Double,
    val rawDeltaKcal: Double,
    val isClampedByWeeklyLimit: Boolean,
    val isClampedByMinLimit: Boolean,
    val minKcalLimit: Double,
    val explanation: String,
    val disclaimer: String = DEFAULT_DISCLAIMER,
) {
    companion object {
        const val DEFAULT_DISCLAIMER =
            "Aviso: Este cálculo es una estimación estadística y no constituye consejo médico ni nutricional clínico."
    }
}
