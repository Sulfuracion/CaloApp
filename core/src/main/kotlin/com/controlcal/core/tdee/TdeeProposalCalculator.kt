package com.controlcal.core.tdee

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Calculates weekly calorie goal proposals according to section 5.5 of docs/PLAN.md:
 * - Proposal is capped at +/- 150 kcal per week relative to current target.
 * - Proposal is never below the minimum kcal limit configured in user settings.
 * - Generates human-readable numeric breakdown for transparency.
 */
object TdeeProposalCalculator {

    const val MAX_WEEKLY_DELTA_KCAL = 150.0
    const val DEFAULT_MIN_KCAL_LIMIT = 1200.0

    /**
     * Calculates a weekly calorie proposal based on estimated TDEE and the current calorie target.
     *
     * @param currentKcal Current active daily calorie target.
     * @param estimatedTdee Estimated Total Daily Energy Expenditure from [TdeeEstimator].
     * @param minKcalLimit Minimum allowable calorie target from user settings.
     * @return [TdeeGoalProposal] containing the proposed target, clamping flags, and explanations.
     */
    fun calculateProposal(
        currentKcal: Double,
        estimatedTdee: Double,
        minKcalLimit: Double = DEFAULT_MIN_KCAL_LIMIT,
    ): TdeeGoalProposal {
        val rawDeltaKcal = estimatedTdee - currentKcal
        val clampedDeltaKcal = rawDeltaKcal.coerceIn(-MAX_WEEKLY_DELTA_KCAL, MAX_WEEKLY_DELTA_KCAL)
        val rawProposedKcal = currentKcal + clampedDeltaKcal
        val proposedKcal = maxOf(minKcalLimit, rawProposedKcal)

        val isClampedByWeeklyLimit = abs(rawDeltaKcal) > MAX_WEEKLY_DELTA_KCAL
        val isClampedByMinLimit = rawProposedKcal < minKcalLimit

        val explanation = buildExplanation(
            currentKcal = currentKcal,
            estimatedTdee = estimatedTdee,
            rawDeltaKcal = rawDeltaKcal,
            clampedDeltaKcal = clampedDeltaKcal,
            proposedKcal = proposedKcal,
            isClampedByWeeklyLimit = isClampedByWeeklyLimit,
            isClampedByMinLimit = isClampedByMinLimit,
            minKcalLimit = minKcalLimit,
        )

        return TdeeGoalProposal(
            currentKcal = currentKcal,
            proposedKcal = proposedKcal,
            deltaKcal = proposedKcal - currentKcal,
            rawDeltaKcal = rawDeltaKcal,
            isClampedByWeeklyLimit = isClampedByWeeklyLimit,
            isClampedByMinLimit = isClampedByMinLimit,
            minKcalLimit = minKcalLimit,
            explanation = explanation,
        )
    }

    private fun buildExplanation(
        currentKcal: Double,
        estimatedTdee: Double,
        rawDeltaKcal: Double,
        clampedDeltaKcal: Double,
        proposedKcal: Double,
        isClampedByWeeklyLimit: Boolean,
        isClampedByMinLimit: Boolean,
        minKcalLimit: Double,
    ): String {
        val currentRound = currentKcal.roundToInt()
        val tdeeRound = estimatedTdee.roundToInt()
        val proposedRound = proposedKcal.roundToInt()
        val diffRound = rawDeltaKcal.roundToInt()
        val sign = if (diffRound >= 0) "+" else ""

        val sb = StringBuilder()
        sb.append("Gasto metabólico estimado: $tdeeRound kcal/día. ")
        sb.append("Objetivo actual: $currentRound kcal/día (diferencia de $sign$diffRound kcal). ")

        if (isClampedByWeeklyLimit) {
            val clampedSign = if (clampedDeltaKcal >= 0) "+" else ""
            sb.append("Por seguridad, el cambio semanal se limita a $clampedSign${clampedDeltaKcal.roundToInt()} kcal. ")
        }

        if (isClampedByMinLimit) {
            sb.append("Ajustado al mínimo de seguridad fijado en ajustes (${minKcalLimit.roundToInt()} kcal). ")
        }

        sb.append("Nuevo objetivo propuesto: $proposedRound kcal/día.")
        return sb.toString()
    }
}
