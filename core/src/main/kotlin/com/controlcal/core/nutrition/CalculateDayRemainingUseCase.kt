package com.controlcal.core.nutrition

import com.controlcal.core.model.DayRemaining
import com.controlcal.core.model.MacroTargets
import com.controlcal.core.model.NutrientTotals

class CalculateDayRemainingUseCase {

    operator fun invoke(totals: NutrientTotals, targets: MacroTargets): DayRemaining {
        return NutritionCalculator.calculateRemaining(totals, targets)
    }
}
