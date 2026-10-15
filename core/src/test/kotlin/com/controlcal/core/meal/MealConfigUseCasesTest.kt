package com.controlcal.core.meal

import com.controlcal.core.model.ConfiguredMeal
import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.DefaultMeals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class MealConfigUseCasesTest {

    private val addMealConfig = AddMealConfigUseCase()
    private val renameMealConfig = RenameMealConfigUseCase()
    private val changeMealStartTime = ChangeMealStartTimeUseCase()
    private val deactivateMeal = DeactivateMealUseCase()
    private val reactivateMeal = ReactivateMealUseCase()
    private val deleteMealConfig = DeleteMealConfigUseCase()

    private val initialMeals = DefaultMeals.defaults()

    // -------------------------------------------------------------
    // AddMealConfigUseCase Tests
    // -------------------------------------------------------------

    @Test
    fun addMeal_successWithValidParameters() {
        val result = addMealConfig(
            currentMeals = initialMeals,
            name = "Media Mañana",
            startTime = LocalTime.of(10, 30),
            isActive = true,
            id = "custom-1",
        )

        assertTrue(result is MealConfigResult.Success)
        val updated = (result as MealConfigResult.Success).meals
        assertEquals(5, updated.size)
        assertEquals("Media Mañana", updated.last().name)
        assertEquals(LocalTime.of(10, 30), updated.last().startTime)
        assertEquals("custom-1", updated.last().id)
        assertTrue(updated.last().isActive)
    }

    @Test
    fun addMeal_rejectsEmptyOrBlankName() {
        val emptyResult = addMealConfig(
            currentMeals = initialMeals,
            name = "",
            startTime = LocalTime.of(10, 0),
        )
        assertTrue(emptyResult is MealConfigResult.Failure.EmptyName)

        val blankResult = addMealConfig(
            currentMeals = initialMeals,
            name = "   \t  ",
            startTime = LocalTime.of(10, 0),
        )
        assertTrue(blankResult is MealConfigResult.Failure.EmptyName)
    }

    @Test
    fun addMeal_rejectsDuplicateNameCaseInsensitiveAndTrimmed() {
        val dupExact = addMealConfig(
            currentMeals = initialMeals,
            name = "Desayuno",
            startTime = LocalTime.of(10, 0),
        )
        assertTrue(dupExact is MealConfigResult.Failure.DuplicateName)

        val dupCase = addMealConfig(
            currentMeals = initialMeals,
            name = "  dEsAyUnO  ",
            startTime = LocalTime.of(10, 0),
        )
        assertTrue(dupCase is MealConfigResult.Failure.DuplicateName)
    }

    @Test
    fun addMeal_rejectsConflictingStartTimeWithActiveMeal() {
        // BREAKFAST starts at 06:00
        val conflict = addMealConfig(
            currentMeals = initialMeals,
            name = "Pre-entreno",
            startTime = LocalTime.of(6, 0),
            isActive = true,
        )
        assertTrue(conflict is MealConfigResult.Failure.ConflictingStartTime)
    }

    @Test
    fun addMeal_allowsSameStartTimeIfOneOrBothAreInactive() {
        // Deactivate breakfast
        val withoutBreakfast = (deactivateMeal(initialMeals, DefaultMealIds.BREAKFAST) as MealConfigResult.Success).meals

        // Now adding another active meal at 06:00 is allowed
        val result = addMealConfig(
            currentMeals = withoutBreakfast,
            name = "Desayuno Tardío",
            startTime = LocalTime.of(6, 0),
            isActive = true,
        )
        assertTrue(result is MealConfigResult.Success)

        // Or adding an inactive meal at 12:00 (when Lunch is active) is allowed
        val inactiveResult = addMealConfig(
            currentMeals = initialMeals,
            name = "Almuerzo Alternativo",
            startTime = LocalTime.of(12, 0),
            isActive = false,
        )
        assertTrue(inactiveResult is MealConfigResult.Success)
    }

    // -------------------------------------------------------------
    // RenameMealConfigUseCase Tests
    // -------------------------------------------------------------

    @Test
    fun renameMeal_successWithValidName() {
        val result = renameMealConfig(
            currentMeals = initialMeals,
            mealId = DefaultMealIds.LUNCH,
            newName = "Comida Principal",
        )

        assertTrue(result is MealConfigResult.Success)
        val updated = (result as MealConfigResult.Success).meals
        val renamed = updated.first { it.id == DefaultMealIds.LUNCH }
        assertEquals("Comida Principal", renamed.name)
    }

    @Test
    fun renameMeal_mealNotFound() {
        val result = renameMealConfig(
            currentMeals = initialMeals,
            mealId = "non-existent",
            newName = "Nuevo",
        )
        assertTrue(result is MealConfigResult.Failure.MealNotFound)
    }

    @Test
    fun renameMeal_rejectsEmptyOrBlankName() {
        val result = renameMealConfig(
            currentMeals = initialMeals,
            mealId = DefaultMealIds.BREAKFAST,
            newName = "   ",
        )
        assertTrue(result is MealConfigResult.Failure.EmptyName)
    }

    @Test
    fun renameMeal_rejectsDuplicateNameWithOtherMeal() {
        val result = renameMealConfig(
            currentMeals = initialMeals,
            mealId = DefaultMealIds.BREAKFAST,
            newName = "Cena",
        )
        assertTrue(result is MealConfigResult.Failure.DuplicateName)
    }

    @Test
    fun renameMeal_allowsKeepingOwnName() {
        val result = renameMealConfig(
            currentMeals = initialMeals,
            mealId = DefaultMealIds.BREAKFAST,
            newName = "Desayuno",
        )
        assertTrue(result is MealConfigResult.Success)
    }

    // -------------------------------------------------------------
    // ChangeMealStartTimeUseCase Tests
    // -------------------------------------------------------------

    @Test
    fun changeStartTime_success() {
        val result = changeMealStartTime(
            currentMeals = initialMeals,
            mealId = DefaultMealIds.BREAKFAST,
            newStartTime = LocalTime.of(7, 30),
        )

        assertTrue(result is MealConfigResult.Success)
        val updated = (result as MealConfigResult.Success).meals
        val changed = updated.first { it.id == DefaultMealIds.BREAKFAST }
        assertEquals(LocalTime.of(7, 30), changed.startTime)
    }

    @Test
    fun changeStartTime_mealNotFound() {
        val result = changeMealStartTime(
            currentMeals = initialMeals,
            mealId = "unknown",
            newStartTime = LocalTime.of(8, 0),
        )
        assertTrue(result is MealConfigResult.Failure.MealNotFound)
    }

    @Test
    fun changeStartTime_rejectsConflictingStartTimeWithAnotherActiveMeal() {
        // Trying to move Breakfast to Lunch's time (12:00)
        val result = changeMealStartTime(
            currentMeals = initialMeals,
            mealId = DefaultMealIds.BREAKFAST,
            newStartTime = LocalTime.of(12, 0),
        )
        assertTrue(result is MealConfigResult.Failure.ConflictingStartTime)
    }

    @Test
    fun changeStartTime_allowsKeepingOwnStartTime() {
        val result = changeMealStartTime(
            currentMeals = initialMeals,
            mealId = DefaultMealIds.BREAKFAST,
            newStartTime = LocalTime.of(6, 0),
        )
        assertTrue(result is MealConfigResult.Success)
    }

    // -------------------------------------------------------------
    // DeactivateMealUseCase Tests
    // -------------------------------------------------------------

    @Test
    fun deactivateMeal_successWhenMultipleActiveRemain() {
        val result = deactivateMeal(
            currentMeals = initialMeals,
            mealId = DefaultMealIds.SNACK,
        )

        assertTrue(result is MealConfigResult.Success)
        val updated = (result as MealConfigResult.Success).meals
        val snack = updated.first { it.id == DefaultMealIds.SNACK }
        assertTrue(!snack.isActive)
        assertEquals(3, updated.count { it.isActive })
    }

    @Test
    fun deactivateMeal_mealNotFound() {
        val result = deactivateMeal(
            currentMeals = initialMeals,
            mealId = "unknown",
        )
        assertTrue(result is MealConfigResult.Failure.MealNotFound)
    }

    @Test
    fun deactivateMeal_alreadyInactiveIsIdempotent() {
        val inactiveSnack = (deactivateMeal(initialMeals, DefaultMealIds.SNACK) as MealConfigResult.Success).meals
        val result = deactivateMeal(inactiveSnack, DefaultMealIds.SNACK)
        assertTrue(result is MealConfigResult.Success)
    }

    @Test
    fun deactivateMeal_rejectsDeactivatingTheOnlyActiveMeal() {
        val singleActive = listOf(
            ConfiguredMeal("1", "Solo", LocalTime.of(12, 0), isActive = true),
            ConfiguredMeal("2", "Inactiva", LocalTime.of(20, 0), isActive = false),
        )

        val result = deactivateMeal(singleActive, "1")
        assertTrue(result is MealConfigResult.Failure.AtLeastOneActiveRequired)
    }

    // -------------------------------------------------------------
    // ReactivateMealUseCase Tests
    // -------------------------------------------------------------

    @Test
    fun reactivateMeal_successWhenNoTimeConflict() {
        val inactiveSnack = (deactivateMeal(initialMeals, DefaultMealIds.SNACK) as MealConfigResult.Success).meals
        val result = reactivateMeal(inactiveSnack, DefaultMealIds.SNACK)

        assertTrue(result is MealConfigResult.Success)
        val updated = (result as MealConfigResult.Success).meals
        assertTrue(updated.first { it.id == DefaultMealIds.SNACK }.isActive)
    }

    @Test
    fun reactivateMeal_mealNotFound() {
        val result = reactivateMeal(initialMeals, "unknown")
        assertTrue(result is MealConfigResult.Failure.MealNotFound)
    }

    @Test
    fun reactivateMeal_rejectsWhenStartTimeClashesWithActiveMeal() {
        // Create an inactive meal with same start time as lunch (12:00)
        val meals = initialMeals + ConfiguredMeal("custom-lunch", "Otro Almuerzo", LocalTime.of(12, 0), isActive = false)
        val result = reactivateMeal(meals, "custom-lunch")
        assertTrue(result is MealConfigResult.Failure.ConflictingStartTime)
    }

    // -------------------------------------------------------------
    // DeleteMealConfigUseCase Tests
    // -------------------------------------------------------------

    @Test
    fun deleteMeal_successWhenNoEntries() {
        val result = deleteMealConfig(
            currentMeals = initialMeals,
            mealId = DefaultMealIds.SNACK,
            hasEntries = false,
        )

        assertTrue(result is MealConfigResult.Success)
        val updated = (result as MealConfigResult.Success).meals
        assertEquals(3, updated.size)
        assertTrue(updated.none { it.id == DefaultMealIds.SNACK })
    }

    @Test
    fun deleteMeal_mealNotFound() {
        val result = deleteMealConfig(
            currentMeals = initialMeals,
            mealId = "unknown",
            hasEntries = false,
        )
        assertTrue(result is MealConfigResult.Failure.MealNotFound)
    }

    @Test
    fun deleteMeal_rejectsWhenMealHasEntries() {
        val result = deleteMealConfig(
            currentMeals = initialMeals,
            mealId = DefaultMealIds.BREAKFAST,
            hasEntries = true, // Has logged food entries!
        )

        assertTrue(result is MealConfigResult.Failure.MealHasEntriesCannotDelete)
    }

    @Test
    fun deleteMeal_rejectsDeletingTheOnlyActiveMeal() {
        val singleActive = listOf(
            ConfiguredMeal("1", "Solo", LocalTime.of(12, 0), isActive = true),
        )

        val result = deleteMealConfig(singleActive, "1", hasEntries = false)
        assertTrue(result is MealConfigResult.Failure.AtLeastOneActiveRequired)
    }

    @Test
    fun deleteMeal_allowsDeletingInactiveMealEvenIfOnlyOneActiveRemains() {
        val meals = listOf(
            ConfiguredMeal("1", "Solo", LocalTime.of(12, 0), isActive = true),
            ConfiguredMeal("2", "Inactiva", LocalTime.of(20, 0), isActive = false),
        )

        val result = deleteMealConfig(meals, "2", hasEntries = false)
        assertTrue(result is MealConfigResult.Success)
        val updated = (result as MealConfigResult.Success).meals
        assertEquals(1, updated.size)
        assertEquals("1", updated.first().id)
    }
}
