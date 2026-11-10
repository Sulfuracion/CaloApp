package com.controlcal.core.backup

import kotlinx.serialization.Serializable

/**
 * Encrypted backup payload structure.
 *
 * HOW TO ADD FUTURE TABLES (e.g. Recipes, Supplements, Custom Meals):
 * 1. Declare a new `@Serializable data class Backup<Entity>Dto(...)`.
 * 2. Add an optional property with a default empty list to [BackupPayload], e.g.:
 *    `val recipes: List<BackupRecipeDto> = emptyList()`
 * 3. Update the export and restore logic in `BackupManager` to read from / insert into the new Room table.
 * Because JSON deserialization is configured with `ignoreUnknownKeys = true` and default values,
 * older backups without the new property will parse cleanly, and newer backups loaded on an older
 * app version (with compatible format version) will ignore the unrecognized array.
 */
@Serializable
data class BackupPayload(
    val backupFormatVersion: Int = 1,
    val schemaVersion: Int,
    val createdAt: Long,
    val appVersion: String,
    val products: List<BackupProductDto> = emptyList(),
    val dailyLogs: List<BackupDailyLogDto> = emptyList(),
    val mealEntries: List<BackupMealEntryDto> = emptyList(),
    val weightEntries: List<BackupWeightEntryDto> = emptyList(),
    val goalProfiles: List<BackupGoalProfileDto> = emptyList(),
    val preferences: BackupPreferencesDto = BackupPreferencesDto(),
    val savedMeals: List<BackupSavedMealDto> = emptyList(),
    val savedMealItems: List<BackupSavedMealItemDto> = emptyList(),
    val supplements: List<BackupSupplementDto> = emptyList(),
    val supplementIntakes: List<BackupSupplementIntakeDto> = emptyList(),
    val recipes: List<BackupRecipeDto> = emptyList(),
    val recipeIngredients: List<BackupRecipeIngredientDto> = emptyList(),
    // Extension points for future features:
    val customMeals: List<String> = emptyList(),
)

@Serializable
data class BackupProductDto(
    val id: Long,
    val barcode: String? = null,
    val name: String,
    val brand: String? = null,
    val kcal100g: Double,
    val protein100g: Double,
    val carbs100g: Double,
    val fat100g: Double,
    val fiber100g: Double? = null,
    val sugars100g: Double? = null,
    val satFat100g: Double? = null,
    val salt100g: Double? = null,
    val isLiquid: Boolean = false,
    val unitName: String? = null,
    val unitG: Double? = null,
    val servingG: Double? = null,
    val minG: Double? = null,
    val maxG: Double? = null,
    val inPantry: Boolean = false,
    val source: String,
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupDailyLogDto(
    val date: String,
    val dayType: String,
    val waterMl: Int = 0,
    val notes: String? = null,
    val steps: Int = 0,
)

@Serializable
data class BackupMealEntryDto(
    val id: Long,
    val date: String,
    val meal: String,
    val productId: Long? = null,
    val productName: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val loggedAt: Long,
)

@Serializable
data class BackupWeightEntryDto(
    val date: String,
    val weightKg: Double,
)

@Serializable
data class BackupGoalProfileDto(
    val id: Long,
    val dayType: String,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val validFrom: String,
    val origin: String,
)

@Serializable
data class BackupPreferencesDto(
    val weeklyPattern: String? = null,
    val minKcalLimit: Double? = null,
    val lastTdeeProposalReviewedDate: String? = null,
    val configuredMealsJson: String? = null,
)

@Serializable
data class BackupSavedMealDto(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long? = null,
    val useCount: Int = 0,
)

@Serializable
data class BackupSavedMealItemDto(
    val id: Long,
    val savedMealId: Long,
    val position: Int,
    val productId: Long? = null,
    val productName: String,
    val grams: Double,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
)

@Serializable
data class BackupSupplementDto(
    val id: Long,
    val name: String,
    val doseAmount: Double,
    val doseUnit: String,
    val scheduleType: String,
    val weekdays: Int? = null,
    val intervalDays: Int? = null,
    val startDate: String,
    val times: String,
    val active: Boolean = true,
    val remindersEnabled: Boolean = true,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupSupplementIntakeDto(
    val id: Long,
    val supplementId: Long,
    val scheduledDate: String,
    val scheduledTime: String,
    val status: String,
    val recordedAt: Long,
)

@Serializable
data class BackupRecipeDto(
    val id: Long,
    val productId: Long,
    val name: String,
    val cookedWeightG: Double? = null,
    val servings: Double? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean = false,
)

@Serializable
data class BackupRecipeIngredientDto(
    val id: Long,
    val recipeId: Long,
    val position: Int,
    val productId: Long,
    val grams: Double,
)
