package com.controlcal.core.model

/**
 * Remaining and surplus macronutrients compared to daily targets.
 * [remaining*] is >= 0.0, [surplus*] is >= 0.0.
 */
data class DayRemaining(
    val remainingKcal: Double,
    val remainingProtein: Double,
    val remainingCarbs: Double,
    val remainingFat: Double,
    val surplusKcal: Double = 0.0,
    val surplusProtein: Double = 0.0,
    val surplusCarbs: Double = 0.0,
    val surplusFat: Double = 0.0,
)
