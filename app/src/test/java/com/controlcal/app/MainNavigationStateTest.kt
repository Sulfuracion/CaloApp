package com.controlcal.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavigationStateTest {

    @Test
    fun `navigating to a new destination appends it to the stack`() {
        val state = MainNavigationState()

        state.currentDestination = MainDestination.MEAL_DETAIL
        state.currentDestination = MainDestination.ADD_ENTRY

        assertEquals(MainDestination.ADD_ENTRY, state.currentDestination)
        assertEquals(
            listOf(MainDestination.DAILY_PANEL, MainDestination.MEAL_DETAIL, MainDestination.ADD_ENTRY),
            state.savedDestinationStack,
        )
    }

    @Test
    fun `navigating to an existing destination pops the newer destinations`() {
        val state = MainNavigationState()

        state.currentDestination = MainDestination.MEAL_DETAIL
        state.currentDestination = MainDestination.ADD_ENTRY
        state.currentDestination = MainDestination.MEAL_DETAIL

        assertEquals(MainDestination.MEAL_DETAIL, state.currentDestination)
        assertEquals(
            listOf(MainDestination.DAILY_PANEL, MainDestination.MEAL_DETAIL),
            state.savedDestinationStack,
        )
    }
}
