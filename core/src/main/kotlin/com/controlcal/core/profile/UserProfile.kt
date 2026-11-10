package com.controlcal.core.profile

import java.time.LocalDate

/**
 * User personal profile used for energy expenditure estimation.
 * Persisted in Preferences DataStore without database schema migrations.
 */
data class UserProfile(
    val sex: UserSex? = null,
    val birthDate: LocalDate? = null,
    val heightCm: Double? = null,
    val workoutKcalPerSession: Double = DEFAULT_WORKOUT_KCAL_PER_SESSION,
) {
    val isComplete: Boolean
        get() = sex != null && birthDate != null && heightCm != null && heightCm > 0.0

    companion object {
        const val DEFAULT_WORKOUT_KCAL_PER_SESSION = 300.0
    }
}
