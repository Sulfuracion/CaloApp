package com.controlcal.core.nutrition

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalProfileValidatorTest {

    @Test
    fun validateKcal() {
        assertFalse(GoalProfileValidator.validateKcal(null))
        assertFalse(GoalProfileValidator.validateKcal(""))
        assertFalse(GoalProfileValidator.validateKcal("0"))
        assertFalse(GoalProfileValidator.validateKcal("-100"))
        assertFalse(GoalProfileValidator.validateKcal("abc"))
        assertTrue(GoalProfileValidator.validateKcal("2000"))
        assertTrue(GoalProfileValidator.validateKcal("1850,5"))
    }

    @Test
    fun validateMacro() {
        assertFalse(GoalProfileValidator.validateMacro(null))
        assertFalse(GoalProfileValidator.validateMacro(""))
        assertFalse(GoalProfileValidator.validateMacro("-5"))
        assertFalse(GoalProfileValidator.validateMacro("xyz"))
        assertTrue(GoalProfileValidator.validateMacro("0"))
        assertTrue(GoalProfileValidator.validateMacro("150"))
        assertTrue(GoalProfileValidator.validateMacro("75,5"))
    }

    @Test
    fun validateAll() {
        assertTrue(
            GoalProfileValidator.validateAll(
                minKcal = "1200",
                trainingKcal = "2200",
                trainingProtein = "160",
                trainingCarbs = "240",
                trainingFat = "65",
                restKcal = "1800",
                restProtein = "160",
                restCarbs = "150",
                restFat = "60",
            )
        )

        assertFalse(
            GoalProfileValidator.validateAll(
                minKcal = "0",
                trainingKcal = "2200",
                trainingProtein = "160",
                trainingCarbs = "240",
                trainingFat = "65",
                restKcal = "1800",
                restProtein = "160",
                restCarbs = "150",
                restFat = "60",
            )
        )

        assertFalse(
            GoalProfileValidator.validateAll(
                minKcal = "1200",
                trainingKcal = "2200",
                trainingProtein = "-1",
                trainingCarbs = "240",
                trainingFat = "65",
                restKcal = "1800",
                restProtein = "160",
                restCarbs = "150",
                restFat = "60",
            )
        )
    }
}
