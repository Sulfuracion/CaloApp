package com.controlcal.core.nutrition

import com.controlcal.core.model.ConfiguredMeal
import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.DefaultMeals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class MealClassifierTest {

    @Test
    fun suggestMealForHour_breakfastHours() {
        for (h in 6..11) {
            assertEquals(DefaultMealIds.BREAKFAST, MealClassifier.suggestMealForHour(h))
        }
    }

    @Test
    fun suggestMealForHour_lunchHours() {
        for (h in 12..16) {
            assertEquals(DefaultMealIds.LUNCH, MealClassifier.suggestMealForHour(h))
        }
    }

    @Test
    fun suggestMealForHour_snackHours() {
        for (h in 17..20) {
            assertEquals(DefaultMealIds.SNACK, MealClassifier.suggestMealForHour(h))
        }
    }

    @Test
    fun suggestMealForHour_dinnerHours() {
        val dinnerHours = listOf(21, 22, 23, 0, 1, 2, 3, 4, 5)
        for (h in dinnerHours) {
            assertEquals(DefaultMealIds.DINNER, MealClassifier.suggestMealForHour(h))
        }
    }

    @Test
    fun suggestMeal_defaultMealsMatchesSuggestMealForHourAcrossAll24Hours() {
        val defaults = DefaultMeals.defaults()
        for (h in 0..23) {
            val expectedId = MealClassifier.suggestMealForHour(h)
            val suggestedAtHourStart = MealClassifier.suggestMeal(defaults, LocalTime.of(h, 0))
            val suggestedAtHourHalf = MealClassifier.suggestMeal(defaults, LocalTime.of(h, 30))
            val suggestedAtHourEnd = MealClassifier.suggestMeal(defaults, LocalTime.of(h, 59))

            assertEquals(expectedId, suggestedAtHourStart?.id)
            assertEquals(expectedId, suggestedAtHourHalf?.id)
            assertEquals(expectedId, suggestedAtHourEnd?.id)
        }
    }

    @Test
    fun suggestMeal_emptyOrInactiveMealsReturnsNull() {
        assertNull(MealClassifier.suggestMeal(emptyList(), LocalTime.of(12, 0)))
        val inactive = listOf(
            ConfiguredMeal("1", "M1", LocalTime.of(8, 0), isActive = false)
        )
        assertNull(MealClassifier.suggestMeal(inactive, LocalTime.of(12, 0)))
    }

    @Test
    fun suggestMeal_beforeFirstMealSelectsLastMealOfDay() {
        val meals = listOf(
            ConfiguredMeal("B", "Desayuno", LocalTime.of(8, 0)),
            ConfiguredMeal("L", "Comida", LocalTime.of(14, 0)),
            ConfiguredMeal("D", "Cena", LocalTime.of(21, 0)),
        )
        // 07:30 is before 08:00 (first meal) -> selects Cena (21:00)
        val selected = MealClassifier.suggestMeal(meals, LocalTime.of(7, 30))
        assertEquals("D", selected?.id)
    }

    @Test
    fun suggestMeal_atExactStartTimeSelectsThatMeal() {
        val meals = DefaultMeals.defaults()
        assertEquals(DefaultMealIds.BREAKFAST, MealClassifier.suggestMeal(meals, LocalTime.of(6, 0))?.id)
        assertEquals(DefaultMealIds.LUNCH, MealClassifier.suggestMeal(meals, LocalTime.of(12, 0))?.id)
        assertEquals(DefaultMealIds.SNACK, MealClassifier.suggestMeal(meals, LocalTime.of(17, 0))?.id)
        assertEquals(DefaultMealIds.DINNER, MealClassifier.suggestMeal(meals, LocalTime.of(21, 0))?.id)
    }

    @Test
    fun suggestMeal_madrugadaBeforeFirstMealAlwaysSelectsLastMeal() {
        val meals = listOf(
            ConfiguredMeal("B", "Desayuno", LocalTime.of(7, 30)),
            ConfiguredMeal("L", "Comida", LocalTime.of(13, 30)),
            ConfiguredMeal("D", "Cena", LocalTime.of(21, 0)),
        )

        // All early morning times (madrugada) before 07:30 must select Cena ("D")
        val madrugadaTimes = listOf(
            LocalTime.of(0, 0),
            LocalTime.of(0, 1),
            LocalTime.of(1, 30),
            LocalTime.of(3, 45),
            LocalTime.of(5, 0),
            LocalTime.of(6, 59),
            LocalTime.of(7, 29),
        )

        for (time in madrugadaTimes) {
            val result = MealClassifier.suggestMeal(meals, time)
            assertEquals("At $time, last meal of the day (Cena) should be suggested", "D", result?.id)
        }

        // At exactly 07:30, it switches to Desayuno
        assertEquals("B", MealClassifier.suggestMeal(meals, LocalTime.of(7, 30))?.id)
    }

    @Test
    fun suggestMeal_inactiveMealsAreNeverSuggested() {
        val meals = listOf(
            ConfiguredMeal("B", "Desayuno", LocalTime.of(8, 0), isActive = true),
            ConfiguredMeal("MM", "Media Mañana", LocalTime.of(11, 0), isActive = false),
            ConfiguredMeal("L", "Comida", LocalTime.of(14, 0), isActive = true),
            ConfiguredMeal("M", "Merienda", LocalTime.of(18, 0), isActive = false),
            ConfiguredMeal("D", "Cena", LocalTime.of(21, 30), isActive = true),
        )

        // At 11:00 or 12:00, inactive "MM" must be ignored -> suggests active Breakfast (08:00)
        assertEquals("B", MealClassifier.suggestMeal(meals, LocalTime.of(11, 0))?.id)
        assertEquals("B", MealClassifier.suggestMeal(meals, LocalTime.of(12, 30))?.id)

        // At 18:00 or 19:00, inactive "M" must be ignored -> suggests active Comida (14:00)
        assertEquals("L", MealClassifier.suggestMeal(meals, LocalTime.of(18, 0))?.id)
        assertEquals("L", MealClassifier.suggestMeal(meals, LocalTime.of(20, 0))?.id)

        // At 02:00 (madrugada), suggests active Cena (21:30)
        assertEquals("D", MealClassifier.suggestMeal(meals, LocalTime.of(2, 0))?.id)
    }

    @Test
    fun suggestMeal_singleActiveMealSuggestedAcrossAll24Hours() {
        val single = listOf(
            ConfiguredMeal("OMAD", "Comida Única", LocalTime.of(14, 0), isActive = true),
            ConfiguredMeal("INACTIVE", "Omitida", LocalTime.of(8, 0), isActive = false),
        )

        for (h in 0..23) {
            val suggested = MealClassifier.suggestMeal(single, LocalTime.of(h, 15))
            assertEquals("OMAD", suggested?.id)
        }
    }

    @Test
    fun suggestMeal_firstMealAtMidnight() {
        val meals = listOf(
            ConfiguredMeal("MIDNIGHT", "Recena", LocalTime.of(0, 0), isActive = true),
            ConfiguredMeal("NOON", "Almuerzo", LocalTime.of(12, 0), isActive = true),
        )

        assertEquals("MIDNIGHT", MealClassifier.suggestMeal(meals, LocalTime.of(0, 0))?.id)
        assertEquals("MIDNIGHT", MealClassifier.suggestMeal(meals, LocalTime.of(0, 30))?.id)
        assertEquals("MIDNIGHT", MealClassifier.suggestMeal(meals, LocalTime.of(11, 59))?.id)
        assertEquals("NOON", MealClassifier.suggestMeal(meals, LocalTime.of(12, 0))?.id)
        assertEquals("NOON", MealClassifier.suggestMeal(meals, LocalTime.of(23, 59))?.id)
    }

    @Test
    fun suggestMeal_customFiveMealsTimeline() {
        val meals = listOf(
            ConfiguredMeal("1", "Desayuno", LocalTime.of(7, 0)),
            ConfiguredMeal("2", "Almuerzo Ligero", LocalTime.of(11, 0)),
            ConfiguredMeal("3", "Comida", LocalTime.of(14, 30)),
            ConfiguredMeal("4", "Merienda", LocalTime.of(18, 0)),
            ConfiguredMeal("5", "Cena", LocalTime.of(22, 0)),
        )

        // Madrugada: before 07:00 -> Cena ("5")
        assertEquals("5", MealClassifier.suggestMeal(meals, LocalTime.of(5, 30))?.id)

        // Timeline progression
        assertEquals("1", MealClassifier.suggestMeal(meals, LocalTime.of(7, 0))?.id)
        assertEquals("1", MealClassifier.suggestMeal(meals, LocalTime.of(10, 59))?.id)
        assertEquals("2", MealClassifier.suggestMeal(meals, LocalTime.of(11, 0))?.id)
        assertEquals("3", MealClassifier.suggestMeal(meals, LocalTime.of(14, 30))?.id)
        assertEquals("4", MealClassifier.suggestMeal(meals, LocalTime.of(18, 0))?.id)
        assertEquals("5", MealClassifier.suggestMeal(meals, LocalTime.of(22, 0))?.id)
        assertEquals("5", MealClassifier.suggestMeal(meals, LocalTime.of(23, 59))?.id)
    }
}
