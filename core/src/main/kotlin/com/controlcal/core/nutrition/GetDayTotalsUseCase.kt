package com.controlcal.core.nutrition

import com.controlcal.core.model.DayTotals
import com.controlcal.core.model.MealEntry
import com.controlcal.core.repo.MealEntryRepository

class GetDayTotalsUseCase(
    private val mealEntryRepository: MealEntryRepository,
) {
    suspend operator fun invoke(date: String): DayTotals {
        val entries = mealEntryRepository.getByDate(date)
        return NutritionCalculator.calculateDayTotals(date, entries)
    }

    operator fun invoke(date: String, entries: List<MealEntry>): DayTotals {
        return NutritionCalculator.calculateDayTotals(date, entries)
    }
}
