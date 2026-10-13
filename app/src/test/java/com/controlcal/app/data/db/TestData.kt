package com.controlcal.app.data.db

import com.controlcal.app.data.db.entity.DailyLogEntity
import com.controlcal.app.data.db.entity.GoalProfileEntity
import com.controlcal.app.data.db.entity.MealEntryEntity
import com.controlcal.app.data.db.entity.ProductEntity
import com.controlcal.app.data.db.entity.WeightEntryEntity
import com.controlcal.core.model.DayType
import com.controlcal.core.model.GoalOrigin
import com.controlcal.core.model.Meal
import com.controlcal.core.model.ProductSource

/** Shared builders for DAO tests. */
object TestData {

    const val DATE_A = "2026-10-06"
    const val DATE_B = "2026-10-07"

    fun product(
        id: Long = 0,
        barcode: String? = "8412345678901",
        name: String = "Arroz integral",
        brand: String? = null,
        kcal100g: Double = 350.0,
        protein100g: Double = 7.5,
        carbs100g: Double = 77.0,
        fat100g: Double = 1.2,
        fiber100g: Double? = 2.8,
        sugars100g: Double? = 0.9,
        satFat100g: Double? = 0.3,
        salt100g: Double? = 0.01,
        isLiquid: Boolean = false,
        unitName: String? = null,
        unitG: Double? = null,
        servingG: Double? = null,
        minG: Double? = null,
        maxG: Double? = null,
        inPantry: Boolean = false,
        source: ProductSource = ProductSource.MANUAL,
        archived: Boolean = false,
        createdAt: Long = 1_000L,
        updatedAt: Long = 1_000L,
    ) = ProductEntity(
        id = id,
        barcode = barcode,
        name = name,
        brand = brand,
        kcal100g = kcal100g,
        protein100g = protein100g,
        carbs100g = carbs100g,
        fat100g = fat100g,
        fiber100g = fiber100g,
        sugars100g = sugars100g,
        satFat100g = satFat100g,
        salt100g = salt100g,
        isLiquid = isLiquid,
        unitName = unitName,
        unitG = unitG,
        servingG = servingG,
        minG = minG,
        maxG = maxG,
        inPantry = inPantry,
        source = source,
        archived = archived,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun dailyLog(
        date: String = DATE_A,
        dayType: DayType = DayType.REST,
        waterMl: Int = 0,
        notes: String? = null,
    ) = DailyLogEntity(date = date, dayType = dayType, waterMl = waterMl, notes = notes)

    fun mealEntry(
        id: Long = 0,
        date: String = DATE_A,
        meal: Meal = Meal.LUNCH,
        productId: Long,
        productName: String,
        grams: Double = 100.0,
        kcal: Double = 350.0,
        protein: Double = 7.5,
        carbs: Double = 77.0,
        fat: Double = 1.2,
        loggedAt: Long = 1_000L,
    ) = MealEntryEntity(
        id = id,
        date = date,
        meal = meal,
        productId = productId,
        productName = productName,
        grams = grams,
        kcal = kcal,
        protein = protein,
        carbs = carbs,
        fat = fat,
        loggedAt = loggedAt,
    )

    fun weightEntry(date: String = DATE_A, weightKg: Double) =
        WeightEntryEntity(date = date, weightKg = weightKg)

    fun goalProfile(
        id: Long = 0,
        dayType: DayType = DayType.REST,
        kcal: Double = 2200.0,
        proteinG: Double = 140.0,
        carbsG: Double = 220.0,
        fatG: Double = 75.0,
        validFrom: String = DATE_A,
        origin: GoalOrigin = GoalOrigin.MANUAL,
    ) = GoalProfileEntity(
        id = id,
        dayType = dayType,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        validFrom = validFrom,
        origin = origin,
    )
}
