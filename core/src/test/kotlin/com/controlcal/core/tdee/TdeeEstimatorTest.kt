package com.controlcal.core.tdee

import com.controlcal.core.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TdeeEstimatorTest {

    private val endDate = "2026-10-21"

    private fun generateDateRange(start: String, days: Int): List<String> {
        val startDate = LocalDate.parse(start)
        return (0 until days).map { startDate.plusDays(it.toLong()).toString() }
    }

    // 1. DATOS INSUFICIENTES
    @Test
    fun insufficientDataWhenFewerThan14FoodDaysOrFewerThan8Weights() {
        val dates = generateDateRange("2026-10-01", 21)

        // Caso A: 13 días de comida (< 14) y 10 pesajes (>= 8)
        val food13Days = dates.take(13).map { DailyIntake(date = it, kcal = 2000.0) }
        val weights10Days = dates.take(10).map { WeightEntry(date = it, weightKg = 75.0) }
        val resultA = TdeeEstimator.estimate(endDate, food13Days, weights10Days)
        assertTrue(resultA is TdeeCalculationResult.InsufficientData)
        val dataA = resultA as TdeeCalculationResult.InsufficientData
        assertEquals(13, dataA.loggedFoodDays)
        assertEquals(14, dataA.requiredFoodDays)

        // Caso B: 18 días de comida (>= 14) pero solo 7 pesajes (< 8)
        val food18Days = dates.take(18).map { DailyIntake(date = it, kcal = 2000.0) }
        val weights7Days = dates.take(7).map { WeightEntry(date = it, weightKg = 75.0) }
        val resultB = TdeeEstimator.estimate(endDate, food18Days, weights7Days)
        assertTrue(resultB is TdeeCalculationResult.InsufficientData)
        val dataB = resultB as TdeeCalculationResult.InsufficientData
        assertEquals(7, dataB.weighInDays)
        assertEquals(8, dataB.requiredWeighInDays)

        // Caso C: Ambos insuficientes
        val resultC = TdeeEstimator.estimate(endDate, dates.take(5).map { DailyIntake(it, 1800.0) }, dates.take(3).map { WeightEntry(it, 75.0) })
        assertTrue(resultC is TdeeCalculationResult.InsufficientData)
        val dataC = resultC as TdeeCalculationResult.InsufficientData
        assertEquals(5, dataC.loggedFoodDays)
        assertEquals(3, dataC.weighInDays)
    }

    // 2. PESO ESTABLE
    @Test
    fun stableWeightYieldsTdeeEqualToAverageIntake() {
        val dates = generateDateRange("2026-10-01", 21)
        val food = dates.map { DailyIntake(date = it, kcal = 2200.0) }
        val weights = dates.map { WeightEntry(date = it, weightKg = 75.0) }

        val result = TdeeEstimator.estimate(endDate, food, weights)
        assertTrue(result is TdeeCalculationResult.Success)
        val success = result as TdeeCalculationResult.Success

        assertEquals(2200.0, success.averageDailyIntakeKcal, 0.01)
        assertEquals(0.0, success.weightTrendChangeKg, 0.001)
        assertEquals(2200.0, success.tdeeKcal, 0.01)
        assertEquals(21, success.loggedFoodDays)
        assertEquals(21, success.weighInDays)
    }

    // 3. PÉRDIDA CONSTANTE
    @Test
    fun constantWeightLossCalculatesHigherTdeeThanIntake() {
        val dates = generateDateRange("2026-10-01", 21)
        val food = dates.map { DailyIntake(date = it, kcal = 2000.0) }

        // Pesajes simulados con descenso constante
        val weights = dates.mapIndexed { index, date ->
            WeightEntry(date = date, weightKg = 80.0 - (index * (1.0 / 20.0)))
        }

        val result = TdeeEstimator.estimate(endDate, food, weights)
        assertTrue(result is TdeeCalculationResult.Success)
        val success = result as TdeeCalculationResult.Success

        assertTrue(success.weightTrendChangeKg < 0.0)
        assertTrue(success.tdeeKcal > success.averageDailyIntakeKcal)

        // Comprobación analítica exacta de la fórmula: TDEE = media - (cambio * 7700 / 21)
        val expectedDailyEnergyDelta = (success.weightTrendChangeKg * 7700.0) / 21.0
        val expectedTdee = success.averageDailyIntakeKcal - expectedDailyEnergyDelta
        assertEquals(expectedTdee, success.tdeeKcal, 0.001)
    }

    // 4. GANANCIA DE PESO
    @Test
    fun weightGainCalculatesLowerTdeeThanIntake() {
        val dates = generateDateRange("2026-10-01", 21)
        val food = dates.map { DailyIntake(date = it, kcal = 2500.0) }

        // Pesajes simulados con aumento constante
        val weights = dates.mapIndexed { index, date ->
            WeightEntry(date = date, weightKg = 70.0 + (index * (1.0 / 20.0)))
        }

        val result = TdeeEstimator.estimate(endDate, food, weights)
        assertTrue(result is TdeeCalculationResult.Success)
        val success = result as TdeeCalculationResult.Success

        assertTrue(success.weightTrendChangeKg > 0.0)
        assertTrue(success.tdeeKcal < success.averageDailyIntakeKcal)

        val expectedDailyEnergyDelta = (success.weightTrendChangeKg * 7700.0) / 21.0
        val expectedTdee = success.averageDailyIntakeKcal - expectedDailyEnergyDelta
        assertEquals(expectedTdee, success.tdeeKcal, 0.001)
    }

    // 5. DÍAS SIN REGISTRAR
    @Test
    fun unrecordedDaysCalculatesAverageCorrectlyDividingOnlyByLoggedDays() {
        val dates = generateDateRange("2026-10-01", 21)
        // 15 días registrados con 2100 kcal y 6 días sin registrar (ausentes de la lista)
        val food15Days = dates.take(15).map { DailyIntake(date = it, kcal = 2100.0) }
        val weights = dates.take(10).map { WeightEntry(date = it, weightKg = 75.0) }

        val result = TdeeEstimator.estimate(endDate, food15Days, weights)
        assertTrue(result is TdeeCalculationResult.Success)
        val success = result as TdeeCalculationResult.Success

        // La media diaria debe ser 2100 kcal (dividido por 15 días registrados, NO por 21 días)
        assertEquals(2100.0, success.averageDailyIntakeKcal, 0.01)
        assertEquals(15, success.loggedFoodDays)
        assertEquals(10, success.weighInDays)
        assertEquals(2100.0, success.tdeeKcal, 0.01)
    }

    // 6. UN PESAJE ANÓMALO (OUTLIER RESISTENTE MEDIANTE EMA)
    @Test
    fun anomalousWeighInSpikeIsDampedByEmaTrend() {
        val dates = generateDateRange("2026-10-01", 21)
        val food = dates.map { DailyIntake(date = it, kcal = 2000.0) }

        // Pesos constantes en 75.0 kg, excepto un pico anómalo de retención de líquidos el día 10 (78.5 kg, +3.5 kg)
        val weights = dates.mapIndexed { index, date ->
            val w = if (index == 9) 78.5 else 75.0
            WeightEntry(date = date, weightKg = w)
        }

        val result = TdeeEstimator.estimate(endDate, food, weights)
        assertTrue(result is TdeeCalculationResult.Success)
        val success = result as TdeeCalculationResult.Success

        // Gracias al EMA con alpha 0.1 y a los 11 días posteriores de pesajes normales,
        // la alteración sobre la tendencia final es insignificante (< 0.15 kg)
        // amortiguando el artefacto del pico puntual de 3.5 kg
        assertTrue(Math.abs(success.weightTrendChangeKg) < 0.15)
        assertEquals(2000.0, success.tdeeKcal, 60.0)
    }

    @Test
    fun ignoresZeroOrNegativeFoodDaysAndOutOfWindowEntries() {
        val dates = generateDateRange("2026-10-01", 21)
        val validFood = dates.take(14).map { DailyIntake(date = it, kcal = 2000.0) }
        val zeroAndOutOfWindowFood = listOf(
            DailyIntake(date = dates[14], kcal = 0.0),
            DailyIntake(date = dates[15], kcal = -100.0),
            DailyIntake(date = "2026-09-15", kcal = 3000.0), // before window
            DailyIntake(date = "2026-10-25", kcal = 3000.0), // after endDate
        )
        val weights = dates.take(8).map { WeightEntry(date = it, weightKg = 75.0) } +
            listOf(WeightEntry(date = "2026-09-10", weightKg = 90.0))

        val result = TdeeEstimator.estimate(endDate, validFood + zeroAndOutOfWindowFood, weights)
        assertTrue(result is TdeeCalculationResult.Success)
        val success = result as TdeeCalculationResult.Success
        assertEquals(14, success.loggedFoodDays)
        assertEquals(8, success.weighInDays)
        assertEquals(2000.0, success.averageDailyIntakeKcal, 0.01)
    }
}
