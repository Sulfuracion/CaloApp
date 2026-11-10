package com.controlcal.core.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UserProfileValidatorTest {

    private val referenceDate = LocalDate.parse("2026-11-10")

    @Test
    fun calculateAgeExactlyOnBirthday() {
        val birthDate = LocalDate.parse("1996-11-10")
        val age = UserProfileValidator.calculateAge(birthDate, referenceDate)
        assertEquals(30, age)
    }

    @Test
    fun calculateAgeDayBeforeBirthday() {
        val birthDate = LocalDate.parse("1996-11-11")
        val age = UserProfileValidator.calculateAge(birthDate, referenceDate)
        assertEquals(29, age)
    }

    @Test
    fun calculateAgeDayAfterBirthday() {
        val birthDate = LocalDate.parse("1996-11-09")
        val age = UserProfileValidator.calculateAge(birthDate, referenceDate)
        assertEquals(30, age)
    }

    @Test
    fun calculateAgeLeapYear() {
        // Born on Feb 29, 2000. In 2026 (non-leap), Feb 28 is 25 years, Mar 1 is 26 years.
        val birthDate = LocalDate.parse("2000-02-29")
        val feb28 = LocalDate.parse("2026-02-28")
        val mar01 = LocalDate.parse("2026-03-01")

        assertEquals(25, UserProfileValidator.calculateAge(birthDate, feb28))
        assertEquals(26, UserProfileValidator.calculateAge(birthDate, mar01))
    }

    @Test
    fun validateCompleteValidProfileReturnsValid() {
        val profile = UserProfile(
            sex = UserSex.MALE,
            birthDate = LocalDate.parse("1990-05-15"),
            heightCm = 178.5,
            workoutKcalPerSession = 350.0,
        )

        val result = UserProfileValidator.validate(profile, referenceDate)
        assertTrue(result is UserProfileValidationResult.Valid)
    }

    @Test
    fun validateFutureBirthDateReportsIssue() {
        val profile = UserProfile(
            sex = UserSex.FEMALE,
            birthDate = referenceDate.plusDays(1),
            heightCm = 165.0,
        )

        val result = UserProfileValidator.validate(profile, referenceDate)
        assertTrue(result is UserProfileValidationResult.Invalid)
        assertTrue((result as UserProfileValidationResult.Invalid).issues.contains(UserProfileIssue.FUTURE_BIRTH_DATE))
    }

    @Test
    fun validateAgeTooYoungReportsIssue() {
        // 5 years old
        val profile = UserProfile(
            sex = UserSex.MALE,
            birthDate = referenceDate.minusYears(5),
            heightCm = 110.0,
        )

        val result = UserProfileValidator.validate(profile, referenceDate)
        assertTrue(result is UserProfileValidationResult.Invalid)
        assertTrue((result as UserProfileValidationResult.Invalid).issues.contains(UserProfileIssue.AGE_TOO_YOUNG))
    }

    @Test
    fun validateHeightOutOfRangeReportsIssue() {
        val profileTooShort = UserProfile(
            sex = UserSex.FEMALE,
            birthDate = LocalDate.parse("1995-01-01"),
            heightCm = 40.0,
        )
        val profileTooTall = UserProfile(
            sex = UserSex.MALE,
            birthDate = LocalDate.parse("1995-01-01"),
            heightCm = 280.0,
        )

        val resultShort = UserProfileValidator.validate(profileTooShort, referenceDate)
        assertTrue((resultShort as UserProfileValidationResult.Invalid).issues.contains(UserProfileIssue.HEIGHT_OUT_OF_RANGE))

        val resultTall = UserProfileValidator.validate(profileTooTall, referenceDate)
        assertTrue((resultTall as UserProfileValidationResult.Invalid).issues.contains(UserProfileIssue.HEIGHT_OUT_OF_RANGE))
    }

    @Test
    fun validateMissingFieldsReportsAllIssues() {
        val emptyProfile = UserProfile()

        val result = UserProfileValidator.validate(emptyProfile, referenceDate)
        assertTrue(result is UserProfileValidationResult.Invalid)
        val issues = (result as UserProfileValidationResult.Invalid).issues
        assertTrue(issues.contains(UserProfileIssue.MISSING_SEX))
        assertTrue(issues.contains(UserProfileIssue.MISSING_BIRTH_DATE))
        assertTrue(issues.contains(UserProfileIssue.MISSING_HEIGHT))
        assertFalse(emptyProfile.isComplete)
    }

    @Test
    fun validateWorkoutKcalOutOfRangeReportsIssue() {
        val profileNegativeKcal = UserProfile(
            sex = UserSex.MALE,
            birthDate = LocalDate.parse("1990-01-01"),
            heightCm = 175.0,
            workoutKcalPerSession = -50.0,
        )
        val profileExtremeKcal = UserProfile(
            sex = UserSex.MALE,
            birthDate = LocalDate.parse("1990-01-01"),
            heightCm = 175.0,
            workoutKcalPerSession = 5000.0,
        )

        val resultNegative = UserProfileValidator.validate(profileNegativeKcal, referenceDate)
        assertTrue((resultNegative as UserProfileValidationResult.Invalid).issues.contains(UserProfileIssue.WORKOUT_KCAL_OUT_OF_RANGE))

        val resultExtreme = UserProfileValidator.validate(profileExtremeKcal, referenceDate)
        assertTrue((resultExtreme as UserProfileValidationResult.Invalid).issues.contains(UserProfileIssue.WORKOUT_KCAL_OUT_OF_RANGE))
    }
}
