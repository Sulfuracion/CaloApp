package com.controlcal.app.data.repo

import com.controlcal.app.data.db.entity.DailyLogEntity
import com.controlcal.app.data.db.entity.GoalProfileEntity
import com.controlcal.app.data.db.entity.MealEntryEntity
import com.controlcal.app.data.db.entity.ProductEntity
import com.controlcal.app.data.db.entity.WeightEntryEntity
import com.controlcal.core.model.DailyLog
import com.controlcal.core.model.GoalProfile
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.Product
import com.controlcal.core.model.WeightEntry

fun ProductEntity.toDomain(): Product =
    Product(
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

fun Product.toEntity(): ProductEntity =
    ProductEntity(
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

fun MealEntryEntity.toDomain(): MealEntry =
    MealEntry(
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

fun MealEntry.toEntity(): MealEntryEntity =
    MealEntryEntity(
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

fun DailyLogEntity.toDomain(): DailyLog =
    DailyLog(
        date = date,
        dayType = dayType,
        waterMl = waterMl,
        notes = notes,
    )

fun DailyLog.toEntity(): DailyLogEntity =
    DailyLogEntity(
        date = date,
        dayType = dayType,
        waterMl = waterMl,
        notes = notes,
    )

fun GoalProfileEntity.toDomain(): GoalProfile =
    GoalProfile(
        id = id,
        dayType = dayType,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        validFrom = validFrom,
        origin = origin,
    )

fun GoalProfile.toEntity(): GoalProfileEntity =
    GoalProfileEntity(
        id = id,
        dayType = dayType,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        validFrom = validFrom,
        origin = origin,
    )

fun WeightEntryEntity.toDomain(): WeightEntry =
    WeightEntry(
        date = date,
        weightKg = weightKg,
    )

fun WeightEntry.toEntity(): WeightEntryEntity =
    WeightEntryEntity(
        date = date,
        weightKg = weightKg,
    )

