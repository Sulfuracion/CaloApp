package com.controlcal.core.nutrition

import com.controlcal.core.model.DayType
import com.controlcal.core.model.DefaultGoalProfiles
import com.controlcal.core.model.GoalProfile
import com.controlcal.core.repo.GoalProfileRepository

/**
 * Retrieves the goal profile in force for a given [dayType] and [date]
 * (the latest profile whose valid_from is not after [date]).
 *
 * If no custom profile has been created yet, returns [DefaultGoalProfiles.defaultFor].
 *
 * Section 4.3 and 5.1 of docs/PLAN.md: Goal of the day = profile in goal_profiles
 * in force for the day_type of that day.
 */
class GetActiveGoalProfileUseCase(
    private val goalProfileRepository: GoalProfileRepository,
) {
    suspend operator fun invoke(dayType: DayType, date: String): GoalProfile {
        return goalProfileRepository.getProfileForDayType(dayType, date)
            ?: DefaultGoalProfiles.defaultFor(dayType)
    }
}
