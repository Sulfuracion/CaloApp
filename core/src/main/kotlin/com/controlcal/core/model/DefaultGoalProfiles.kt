package com.controlcal.core.model

/**
 * Sensible default goal profiles used as fallback when no custom profile has been defined.
 */
object DefaultGoalProfiles {

    const val DEFAULT_VALID_FROM = "2020-01-01"

    fun defaultFor(dayType: DayType): GoalProfile = when (dayType) {
        DayType.TRAINING -> GoalProfile(
            id = 0L,
            dayType = DayType.TRAINING,
            kcal = 2400.0,
            proteinG = 160.0,
            carbsG = 270.0,
            fatG = 75.0,
            validFrom = DEFAULT_VALID_FROM,
            origin = GoalOrigin.MANUAL,
        )
        DayType.REST -> GoalProfile(
            id = 0L,
            dayType = DayType.REST,
            kcal = 2000.0,
            proteinG = 160.0,
            carbsG = 180.0,
            fatG = 70.0,
            validFrom = DEFAULT_VALID_FROM,
            origin = GoalOrigin.MANUAL,
        )
    }
}
