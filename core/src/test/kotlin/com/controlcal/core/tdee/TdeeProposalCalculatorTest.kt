package com.controlcal.core.tdee

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TdeeProposalCalculatorTest {

    @Test
    fun weeklyDeltaCappedAtPlus150Kcal() {
        val proposal = TdeeProposalCalculator.calculateProposal(
            currentKcal = 2000.0,
            estimatedTdee = 2400.0,
            minKcalLimit = 1200.0,
        )

        assertEquals(2000.0, proposal.currentKcal, 0.01)
        assertEquals(2150.0, proposal.proposedKcal, 0.01)
        assertEquals(150.0, proposal.deltaKcal, 0.01)
        assertEquals(400.0, proposal.rawDeltaKcal, 0.01)
        assertTrue(proposal.isClampedByWeeklyLimit)
        assertFalse(proposal.isClampedByMinLimit)
    }

    @Test
    fun weeklyDeltaCappedAtMinus150Kcal() {
        val proposal = TdeeProposalCalculator.calculateProposal(
            currentKcal = 2000.0,
            estimatedTdee = 1600.0,
            minKcalLimit = 1200.0,
        )

        assertEquals(2000.0, proposal.currentKcal, 0.01)
        assertEquals(1850.0, proposal.proposedKcal, 0.01)
        assertEquals(-150.0, proposal.deltaKcal, 0.01)
        assertEquals(-400.0, proposal.rawDeltaKcal, 0.01)
        assertTrue(proposal.isClampedByWeeklyLimit)
        assertFalse(proposal.isClampedByMinLimit)
    }

    @Test
    fun proposedKcalNeverBelowMinKcalLimitFromSettings() {
        // Current: 1250 kcal. TDEE: 1000 kcal (-250 kcal).
        // Capped by weekly limit: 1250 - 150 = 1100 kcal.
        // But min limit in settings is 1200 kcal -> Clamped to 1200 kcal.
        val proposal = TdeeProposalCalculator.calculateProposal(
            currentKcal = 1250.0,
            estimatedTdee = 1000.0,
            minKcalLimit = 1200.0,
        )

        assertEquals(1200.0, proposal.proposedKcal, 0.01)
        assertEquals(-50.0, proposal.deltaKcal, 0.01)
        assertTrue(proposal.isClampedByWeeklyLimit)
        assertTrue(proposal.isClampedByMinLimit)
    }

    @Test
    fun smallDeltaWithinWeeklyLimitIsNotClamped() {
        val proposal = TdeeProposalCalculator.calculateProposal(
            currentKcal = 2000.0,
            estimatedTdee = 2060.0,
            minKcalLimit = 1200.0,
        )

        assertEquals(2060.0, proposal.proposedKcal, 0.01)
        assertEquals(60.0, proposal.deltaKcal, 0.01)
        assertFalse(proposal.isClampedByWeeklyLimit)
        assertFalse(proposal.isClampedByMinLimit)
    }

    @Test
    fun includesMedicalDisclaimer() {
        val proposal = TdeeProposalCalculator.calculateProposal(
            currentKcal = 2000.0,
            estimatedTdee = 2100.0,
        )

        assertTrue(proposal.disclaimer.contains("consejo médico"))
    }

    @Test
    fun applyProposalToProfileScalesMacrosProportionallyAndRespectsMinLimit() {
        val currentProfile = com.controlcal.core.model.GoalProfile(
            id = 1L,
            dayType = com.controlcal.core.model.DayType.TRAINING,
            kcal = 2000.0,
            proteinG = 160.0,
            carbsG = 200.0,
            fatG = 60.0,
            validFrom = "2026-10-01",
            origin = com.controlcal.core.model.GoalOrigin.MANUAL,
        )
        val proposal = TdeeProposalCalculator.calculateProposal(
            currentKcal = 2000.0,
            estimatedTdee = 2200.0,
            minKcalLimit = 1200.0,
        ) // delta = +150 -> newKcal = 2150 (ratio = 1.075)

        val scaled = TdeeProposalCalculator.applyProposalToProfile(currentProfile, proposal, "2026-10-21")
        assertEquals(2150.0, scaled.kcal, 0.01)
        assertEquals(172.0, scaled.proteinG, 0.01)
        assertEquals(215.0, scaled.carbsG, 0.01)
        assertEquals(64.5, scaled.fatG, 0.01)
        assertEquals(com.controlcal.core.model.GoalOrigin.TDEE, scaled.origin)
        assertEquals("2026-10-21", scaled.validFrom)
    }
}
