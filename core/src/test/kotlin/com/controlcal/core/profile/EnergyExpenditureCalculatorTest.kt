package com.controlcal.core.profile

import com.controlcal.core.tdee.TdeeProposalCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EnergyExpenditureCalculatorTest {

    private val referenceDate = LocalDate.parse("2026-11-10")

    @Test
    fun calculateBmrForMaleKnownCase() {
        // 80 kg, 180 cm, 30 years -> 10*80 + 6.25*180 - 5*30 + 5 = 1780.0 kcal
        val bmr = EnergyExpenditureCalculator.calculateBmr(
            sex = UserSex.MALE,
            weightKg = 80.0,
            heightCm = 180.0,
            ageYears = 30,
        )
        assertEquals(1780.0, bmr, 0.001)
    }

    @Test
    fun calculateBmrForFemaleKnownCase() {
        // 60 kg, 165 cm, 28 years -> 10*60 + 6.25*165 - 5*28 - 161 = 1330.25 kcal
        val bmr = EnergyExpenditureCalculator.calculateBmr(
            sex = UserSex.FEMALE,
            weightKg = 60.0,
            heightCm = 165.0,
            ageYears = 28,
        )
        assertEquals(1330.25, bmr, 0.001)
    }

    @Test
    fun calculateAgeRightOnBirthdayChangesBmr() {
        // Birthday is Nov 10, 1996.
        // On Nov 10, 2026 -> Age 30.
        // On Nov 09, 2026 -> Age 29.
        val profile = UserProfile(
            sex = UserSex.MALE,
            birthDate = LocalDate.parse("1996-11-10"),
            heightCm = 180.0,
        )

        val resultOnBirthday = EnergyExpenditureCalculator.calculate(
            profile = profile,
            weightTrendKg = 80.0,
            stepsInWindow = emptyList(),
            weeklyTrainingDays = 0,
            referenceDate = LocalDate.parse("2026-11-10"),
        )
        assertTrue(resultOnBirthday is EnergyExpenditureResult.Success)
        val successOnBirthday = (resultOnBirthday as EnergyExpenditureResult.Success).breakdown
        assertEquals(30, successOnBirthday.ageYears)
        assertEquals(1780.0, successOnBirthday.basalKcal, 0.001)

        val resultDayBefore = EnergyExpenditureCalculator.calculate(
            profile = profile,
            weightTrendKg = 80.0,
            stepsInWindow = emptyList(),
            weeklyTrainingDays = 0,
            referenceDate = LocalDate.parse("2026-11-09"),
        )
        assertTrue(resultDayBefore is EnergyExpenditureResult.Success)
        val successDayBefore = (resultDayBefore as EnergyExpenditureResult.Success).breakdown
        assertEquals(29, successDayBefore.ageYears)
        // 5 kcal more because age is 29 instead of 30 (-5*29 vs -5*30)
        assertEquals(1785.0, successDayBefore.basalKcal, 0.001)
    }

    @Test
    fun calculateStepsExcludesZeroDays() {
        // 7 days window with non-synced 0s
        val steps = listOf(10000, 0, 8000, 0, 12000, 0, 0)
        val weightKg = 80.0

        val activity = EnergyExpenditureCalculator.calculateStepsActivity(steps, weightKg)
        // Average of [10000, 8000, 12000] = 30000 / 3 = 10000
        assertEquals(10000, activity.averageSteps)
        assertEquals(3, activity.validDaysCount)
        assertTrue(activity.hasStepsData)
        // 10000 * 80.0 * 0.0004 = 320.0 kcal
        assertEquals(320.0, activity.stepsKcal, 0.001)
    }

    @Test
    fun calculateStepsWhenAllDaysZeroOrEmptyReturnsZeroAndNoDataFlag() {
        val allZeros = listOf(0, 0, 0, 0, 0, 0, 0)
        val activityZeros = EnergyExpenditureCalculator.calculateStepsActivity(allZeros, 75.0)
        assertEquals(0, activityZeros.averageSteps)
        assertEquals(0, activityZeros.validDaysCount)
        assertFalse(activityZeros.hasStepsData)
        assertEquals(0.0, activityZeros.stepsKcal, 0.001)

        val empty = emptyList<Int>()
        val activityEmpty = EnergyExpenditureCalculator.calculateStepsActivity(empty, 75.0)
        assertEquals(0, activityEmpty.averageSteps)
        assertFalse(activityEmpty.hasStepsData)
        assertEquals(0.0, activityEmpty.stepsKcal, 0.001)
    }

    @Test
    fun calculateWorkoutAccordingToWeeklyPattern() {
        val profile = UserProfile(
            sex = UserSex.MALE,
            birthDate = LocalDate.parse("1996-11-10"),
            heightCm = 180.0,
            workoutKcalPerSession = 350.0,
        )

        val result = EnergyExpenditureCalculator.calculate(
            profile = profile,
            weightTrendKg = 80.0,
            stepsInWindow = emptyList(),
            weeklyTrainingDays = 3,
            referenceDate = referenceDate,
        )
        assertTrue(result is EnergyExpenditureResult.Success)
        val breakdown = (result as EnergyExpenditureResult.Success).breakdown

        assertEquals(350.0, breakdown.workoutKcalPerSession, 0.001)
        assertEquals(3, breakdown.weeklyTrainingDays)
        // 3 * 350 / 7 = 150.0 kcal/day
        assertEquals(150.0, breakdown.averageDailyWorkoutKcal, 0.001)

        // Training day workout = 350.0
        assertEquals(350.0, breakdown.trainingDay.workoutKcal, 0.001)
        // Rest day workout = 0.0
        assertEquals(0.0, breakdown.restDay.workoutKcal, 0.001)
        // Average day workout = 150.0
        assertEquals(150.0, breakdown.averageDay.workoutKcal, 0.001)
    }

    @Test
    fun calculateCompleteBreakdownWithDigestionTef() {
        val profile = UserProfile(
            sex = UserSex.MALE,
            birthDate = LocalDate.parse("1996-11-10"),
            heightCm = 180.0,
            workoutKcalPerSession = 300.0,
        )
        val steps = listOf(10000, 10000, 10000, 10000, 10000, 10000, 10000)
        val weightKg = 80.0
        // Basal: 1780.0
        // Steps: 10000 * 80 * 0.0004 = 320.0
        // Workout per session: 300.0 (3 training days -> avg 3*300/7 = 128.5714...)

        val result = EnergyExpenditureCalculator.calculate(
            profile = profile,
            weightTrendKg = weightKg,
            stepsInWindow = steps,
            weeklyTrainingDays = 3,
            referenceDate = referenceDate,
        )
        assertTrue(result is EnergyExpenditureResult.Success)
        val b = (result as EnergyExpenditureResult.Success).breakdown

        // Training day:
        // Subtotal = 1780 + 320 + 300 = 2400.0
        // Total = 2400.0 / 0.9 = 2666.6666...
        // Digestion = 2666.6666... - 2400.0 = 266.6666...
        assertEquals(1780.0, b.trainingDay.basalKcal, 0.001)
        assertEquals(320.0, b.trainingDay.stepsKcal, 0.001)
        assertEquals(300.0, b.trainingDay.workoutKcal, 0.001)
        assertEquals(2666.667, b.trainingDay.totalKcal, 0.01)
        assertEquals(266.667, b.trainingDay.digestionKcal, 0.01)

        // Rest day:
        // Subtotal = 1780 + 320 + 0 = 2100.0
        // Total = 2100.0 / 0.9 = 2333.3333...
        // Digestion = 233.3333...
        assertEquals(1780.0, b.restDay.basalKcal, 0.001)
        assertEquals(320.0, b.restDay.stepsKcal, 0.001)
        assertEquals(0.0, b.restDay.workoutKcal, 0.001)
        assertEquals(2333.333, b.restDay.totalKcal, 0.01)
        assertEquals(233.333, b.restDay.digestionKcal, 0.01)

        // Average day:
        // Subtotal = 1780 + 320 + (300*3/7) = 2228.5714...
        // Total = 2228.5714... / 0.9 = 2476.1904...
        assertEquals(2476.190, b.averageDay.totalKcal, 0.01)
    }

    @Test
    fun calculateWithIncompleteDataReportsMissingElements() {
        val missingSexProfile = UserProfile(
            birthDate = LocalDate.parse("1990-01-01"),
            heightCm = 170.0,
        )
        val resultNoSex = EnergyExpenditureCalculator.calculate(
            profile = missingSexProfile,
            weightTrendKg = 70.0,
            stepsInWindow = emptyList(),
            weeklyTrainingDays = 3,
            referenceDate = referenceDate,
        )
        assertTrue(resultNoSex is EnergyExpenditureResult.IncompleteData)
        val incompleteNoSex = resultNoSex as EnergyExpenditureResult.IncompleteData
        assertTrue(incompleteNoSex.missingSex)
        assertFalse(incompleteNoSex.missingBirthDate)
        assertFalse(incompleteNoSex.missingHeight)
        assertFalse(incompleteNoSex.missingWeightTrend)

        val completeProfile = UserProfile(
            sex = UserSex.FEMALE,
            birthDate = LocalDate.parse("1990-01-01"),
            heightCm = 170.0,
        )
        val resultNoWeight = EnergyExpenditureCalculator.calculate(
            profile = completeProfile,
            weightTrendKg = null,
            stepsInWindow = emptyList(),
            weeklyTrainingDays = 3,
            referenceDate = referenceDate,
        )
        assertTrue(resultNoWeight is EnergyExpenditureResult.IncompleteData)
        val incompleteNoWeight = resultNoWeight as EnergyExpenditureResult.IncompleteData
        assertTrue(incompleteNoWeight.missingWeightTrend)
        assertFalse(incompleteNoWeight.missingSex)
    }

    @Test
    fun formulaInitialProposalSetsBaselineRespectingSafetyFloor() {
        val currentKcal = 2000.0
        val formulaTdee = 2476.0
        val minKcalLimit = 1200.0

        val proposal = TdeeProposalCalculator.calculateFormulaInitialProposal(
            currentKcal = currentKcal,
            estimatedExpenditureKcal = formulaTdee,
            minKcalLimit = minKcalLimit,
        )

        assertEquals(2476.0, proposal.proposedKcal, 0.001)
        assertEquals(476.0, proposal.deltaKcal, 0.001)
        assertFalse(proposal.isClampedByWeeklyLimit)
        assertFalse(proposal.isClampedByMinLimit)

        // Proposal clamped by minimum limit:
        val lowFormulaTdee = 900.0
        val proposalClamped = TdeeProposalCalculator.calculateFormulaInitialProposal(
            currentKcal = currentKcal,
            estimatedExpenditureKcal = lowFormulaTdee,
            minKcalLimit = minKcalLimit,
        )
        assertEquals(1200.0, proposalClamped.proposedKcal, 0.001)
        assertTrue(proposalClamped.isClampedByMinLimit)
    }
}
