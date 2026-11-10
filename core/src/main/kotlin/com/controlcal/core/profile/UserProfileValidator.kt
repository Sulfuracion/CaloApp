package com.controlcal.core.profile

import java.time.LocalDate
import java.time.Period

sealed interface UserProfileValidationResult {
    data object Valid : UserProfileValidationResult
    data class Invalid(val issues: List<UserProfileIssue>) : UserProfileValidationResult
}

enum class UserProfileIssue {
    MISSING_SEX,
    MISSING_BIRTH_DATE,
    FUTURE_BIRTH_DATE,
    AGE_TOO_YOUNG,
    AGE_TOO_OLD,
    MISSING_HEIGHT,
    HEIGHT_OUT_OF_RANGE,
    WORKOUT_KCAL_OUT_OF_RANGE,
}

object UserProfileValidator {
    const val MIN_HEIGHT_CM = 80.0
    const val MAX_HEIGHT_CM = 250.0
    const val MIN_AGE_YEARS = 10
    const val MAX_AGE_YEARS = 120
    const val MIN_WORKOUT_KCAL = 0.0
    const val MAX_WORKOUT_KCAL = 2000.0

    /**
     * Calculates completed full years between [birthDate] and [referenceDate].
     * Exactly on the birthday, the age increases by 1.
     */
    fun calculateAge(birthDate: LocalDate, referenceDate: LocalDate): Int {
        require(!birthDate.isAfter(referenceDate)) { "Birth date cannot be after reference date" }
        return Period.between(birthDate, referenceDate).years
    }

    /**
     * Validates [profile] against sensible physiological ranges.
     */
    fun validate(profile: UserProfile, referenceDate: LocalDate): UserProfileValidationResult {
        val issues = mutableListOf<UserProfileIssue>()

        if (profile.sex == null) {
            issues.add(UserProfileIssue.MISSING_SEX)
        }

        if (profile.birthDate == null) {
            issues.add(UserProfileIssue.MISSING_BIRTH_DATE)
        } else if (profile.birthDate.isAfter(referenceDate)) {
            issues.add(UserProfileIssue.FUTURE_BIRTH_DATE)
        } else {
            val age = calculateAge(profile.birthDate, referenceDate)
            if (age < MIN_AGE_YEARS) {
                issues.add(UserProfileIssue.AGE_TOO_YOUNG)
            } else if (age > MAX_AGE_YEARS) {
                issues.add(UserProfileIssue.AGE_TOO_OLD)
            }
        }

        if (profile.heightCm == null) {
            issues.add(UserProfileIssue.MISSING_HEIGHT)
        } else if (profile.heightCm < MIN_HEIGHT_CM || profile.heightCm > MAX_HEIGHT_CM) {
            issues.add(UserProfileIssue.HEIGHT_OUT_OF_RANGE)
        }

        if (profile.workoutKcalPerSession < MIN_WORKOUT_KCAL || profile.workoutKcalPerSession > MAX_WORKOUT_KCAL) {
            issues.add(UserProfileIssue.WORKOUT_KCAL_OUT_OF_RANGE)
        }

        return if (issues.isEmpty()) UserProfileValidationResult.Valid else UserProfileValidationResult.Invalid(issues)
    }
}
