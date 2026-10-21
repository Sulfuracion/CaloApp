package com.controlcal.core.nutrition

import com.controlcal.core.model.DayType
import com.controlcal.core.model.DefaultGoalProfiles
import com.controlcal.core.model.DefaultMealIds
import com.controlcal.core.model.GoalOrigin
import com.controlcal.core.model.GoalProfile
import com.controlcal.core.model.MacroTargets
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.NutrientTotals
import com.controlcal.core.repo.GoalProfileRepository
import com.controlcal.core.repo.MealEntryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class NutritionUseCasesTest {

    private fun <T> runSync(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { res ->
            result = res
        })
        return result!!.getOrThrow()
    }

    private val fakeRepo = object : MealEntryRepository {
        val storage = mutableListOf<MealEntry>()

        override suspend fun insert(entry: MealEntry): Long {
            storage.add(entry)
            return storage.size.toLong()
        }

        override suspend fun update(entry: MealEntry) {
            val idx = storage.indexOfFirst { it.id == entry.id }
            if (idx >= 0) storage[idx] = entry
        }

        override suspend fun delete(entry: MealEntry) {
            storage.removeAll { it.id == entry.id }
        }

        override suspend fun getByDate(date: String): List<MealEntry> {
            return storage.filter { it.date == date }
        }

        override suspend fun getByDateAndMeal(date: String, meal: String): List<MealEntry> {
            return storage.filter { it.date == date && it.meal == meal }
        }

        override suspend fun insertAll(entries: List<MealEntry>): List<Long> {
            return entries.map { insert(it) }
        }

        override suspend fun getByProduct(productId: Long): List<MealEntry> {
            return storage.filter { it.productId == productId }
        }

        override suspend fun getSince(sinceMillis: Long): List<MealEntry> {
            return storage.filter { it.loggedAt >= sinceMillis }
        }

        override suspend fun getLatestDateWithMealBefore(date: String, meal: String): String? {
            return storage.filter { it.date < date && it.meal == meal }.maxOfOrNull { it.date }
        }

        override suspend fun countByMeal(meal: String): Int {
            return storage.count { it.meal == meal }
        }
    }

    @Test
    fun getDayTotalsUseCaseAggregatesRepositoryEntries() = runSync {
        fakeRepo.storage.clear()
        fakeRepo.insert(
            MealEntry(
                id = 1L,
                date = "2026-10-06",
                meal = DefaultMealIds.BREAKFAST,
                productId = 10L,
                productName = "Avena",
                grams = 50.0,
                kcal = 185.0,
                protein = 6.5,
                carbs = 30.0,
                fat = 3.5,
                loggedAt = 100L,
            ),
        )
        fakeRepo.insert(
            MealEntry(
                id = 2L,
                date = "2026-10-06",
                meal = DefaultMealIds.LUNCH,
                productId = 20L,
                productName = "Arroz",
                grams = 100.0,
                kcal = 130.0,
                protein = 2.5,
                carbs = 28.0,
                fat = 0.3,
                loggedAt = 200L,
            ),
        )

        val useCase = GetDayTotalsUseCase(fakeRepo)
        val dayTotals = useCase("2026-10-06")

        assertEquals(315.0, dayTotals.totals.kcal, 0.001)
        assertEquals(9.0, dayTotals.totals.protein, 0.001)
        assertEquals(58.0, dayTotals.totals.carbs, 0.001)
        assertEquals(3.8, dayTotals.totals.fat, 0.001)
        assertEquals(185.0, dayTotals.byMeal[DefaultMealIds.BREAKFAST]?.totals?.kcal ?: 0.0, 0.001)
        assertEquals(130.0, dayTotals.byMeal[DefaultMealIds.LUNCH]?.totals?.kcal ?: 0.0, 0.001)
    }

    @Test
    fun calculateDayRemainingUseCaseComputesRemainingAccurately() {
        val useCase = CalculateDayRemainingUseCase()
        val totals = NutrientTotals(kcal = 1800.0, protein = 140.0, carbs = 180.0, fat = 60.0)
        val targets = MacroTargets(kcal = 2000.0, protein = 150.0, carbs = 200.0, fat = 70.0)

        val remaining = useCase(totals, targets)

        assertEquals(200.0, remaining.remainingKcal, 0.001)
        assertEquals(10.0, remaining.remainingProtein, 0.001)
        assertEquals(20.0, remaining.remainingCarbs, 0.001)
        assertEquals(10.0, remaining.remainingFat, 0.001)
        assertEquals(0.0, remaining.surplusKcal, 0.001)
    }

    @Test
    fun getDayTotalsUseCaseWithEntriesOverloadCalculatesDirectly() {
        val useCase = GetDayTotalsUseCase(fakeRepo)
        val entries = listOf(
            MealEntry(
                id = 1L,
                date = "2026-10-07",
                meal = DefaultMealIds.DINNER,
                productId = 10L,
                productName = "Salmón",
                grams = 100.0,
                kcal = 208.0,
                protein = 20.0,
                carbs = 0.0,
                fat = 13.0,
                loggedAt = 100L,
            )
        )
        val totals = useCase("2026-10-07", entries)
        assertEquals(208.0, totals.totals.kcal, 0.001)
        assertEquals(20.0, totals.totals.protein, 0.001)
    }

    @Test
    fun getActiveGoalProfileUseCaseReturnsRepositoryProfileOrDefaultFallback() = runSync {
        val storedTraining = GoalProfile(
            id = 5L,
            dayType = DayType.TRAINING,
            kcal = 2650.0,
            proteinG = 180.0,
            carbsG = 300.0,
            fatG = 80.0,
            validFrom = "2026-10-01",
            origin = GoalOrigin.MANUAL,
        )
        val fakeGoalRepo = object : GoalProfileRepository {
            override suspend fun insert(profile: GoalProfile): Long = 1L
            override suspend fun getProfileForDayType(dayType: DayType, date: String): GoalProfile? {
                return if (dayType == DayType.TRAINING) storedTraining else null
            }
            override suspend fun getProfilesForDayTypeUpTo(dayType: DayType, date: String): List<GoalProfile> = emptyList()
        }

        val useCase = GetActiveGoalProfileUseCase(fakeGoalRepo)
        val activeTraining = useCase(DayType.TRAINING, "2026-10-14")
        assertEquals(storedTraining, activeTraining)

        val fallbackRest = useCase(DayType.REST, "2026-10-14")
        assertEquals(DefaultGoalProfiles.defaultFor(DayType.REST), fallbackRest)
        assertEquals(2000.0, fallbackRest.kcal, 0.01)
        assertEquals(2400.0, DefaultGoalProfiles.defaultFor(DayType.TRAINING).kcal, 0.01)
    }

    @Test
    fun mealEntryRepositoryDefaultMethodsSaveMergeAndUpdateLoggedAt() = runSync {
        fakeRepo.storage.clear()
        val first = MealEntry(
            id = 0L,
            date = "2026-10-10",
            meal = DefaultMealIds.BREAKFAST,
            productId = 10L,
            productName = "Avena",
            grams = 40.0,
            kcal = 148.0,
            protein = 5.4,
            carbs = 24.0,
            fat = 2.8,
            loggedAt = 1000L,
        )
        val r1 = fakeRepo.save(first)
        assertTrue(r1 is MealEntrySaveResult.Created)

        val second = first.copy(grams = 20.0, kcal = 74.0, protein = 2.7, carbs = 12.0, fat = 1.4, loggedAt = 2000L)
        val r2 = fakeRepo.save(second)
        assertTrue(r2 is MealEntrySaveResult.Merged)
        assertEquals(60.0, fakeRepo.getByDate("2026-10-10").single().grams, 0.01)

        fakeRepo.updateLoggedAtForMeal("2026-10-10", DefaultMealIds.BREAKFAST, 5000L)
        assertEquals(5000L, fakeRepo.getByDate("2026-10-10").single().loggedAt)
    }
}
