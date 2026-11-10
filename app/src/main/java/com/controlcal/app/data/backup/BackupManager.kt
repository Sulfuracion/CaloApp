package com.controlcal.app.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import com.controlcal.app.data.db.ControlCalDatabase
import com.controlcal.app.data.db.dao.DailyLogDao
import com.controlcal.app.data.db.dao.GoalProfileDao
import com.controlcal.app.data.db.dao.MealEntryDao
import com.controlcal.app.data.db.dao.ProductDao
import com.controlcal.app.data.db.dao.RecipeDao
import com.controlcal.app.data.db.dao.SavedMealDao
import com.controlcal.app.data.db.dao.SupplementDao
import com.controlcal.app.data.db.dao.WeightEntryDao
import com.controlcal.app.data.db.entity.DailyLogEntity
import com.controlcal.app.data.db.entity.GoalProfileEntity
import com.controlcal.app.data.db.entity.MealEntryEntity
import com.controlcal.app.data.db.entity.ProductEntity
import com.controlcal.app.data.db.entity.RecipeEntity
import com.controlcal.app.data.db.entity.RecipeIngredientEntity
import com.controlcal.app.data.db.entity.SavedMealEntity
import com.controlcal.app.data.db.entity.SavedMealItemEntity
import com.controlcal.app.data.db.entity.SupplementEntity
import com.controlcal.app.data.db.entity.SupplementIntakeEntity
import com.controlcal.app.data.db.entity.WeightEntryEntity
import com.controlcal.app.reminder.SupplementReminderScheduler
import com.controlcal.core.backup.BackupCodec
import com.controlcal.core.backup.BackupDailyLogDto
import com.controlcal.core.backup.BackupGoalProfileDto
import com.controlcal.core.backup.BackupMealEntryDto
import com.controlcal.core.backup.BackupPayload
import com.controlcal.core.backup.BackupPreferencesDto
import com.controlcal.core.backup.BackupProductDto
import com.controlcal.core.backup.BackupRecipeDto
import com.controlcal.core.backup.BackupRecipeIngredientDto
import com.controlcal.core.backup.BackupSavedMealDto
import com.controlcal.core.backup.BackupSavedMealItemDto
import com.controlcal.core.backup.BackupSupplementDto
import com.controlcal.core.backup.BackupSupplementIntakeDto
import com.controlcal.core.backup.BackupSummary
import com.controlcal.core.backup.BackupWeightEntryDto
import com.controlcal.core.backup.NewerSchemaVersionException
import com.controlcal.core.model.DayType
import com.controlcal.core.model.GoalOrigin
import com.controlcal.core.model.ProductSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    private val database: ControlCalDatabase,
    private val productDao: ProductDao,
    private val dailyLogDao: DailyLogDao,
    private val mealEntryDao: MealEntryDao,
    private val weightEntryDao: WeightEntryDao,
    private val goalProfileDao: GoalProfileDao,
    private val savedMealDao: SavedMealDao,
    private val supplementDao: SupplementDao,
    private val recipeDao: RecipeDao,
    private val reminderScheduler: SupplementReminderScheduler,
    private val dataStore: DataStore<Preferences>,
) {

    private val weeklyPatternKey = stringPreferencesKey("default_weekly_pattern")
    private val minKcalLimitKey = doublePreferencesKey("min_kcal_limit")
    private val lastTdeeProposalReviewedDateKey = stringPreferencesKey("last_tdee_proposal_reviewed_date")
    private val configuredMealsKey = stringPreferencesKey("configured_meals_json")
    private val lastBackupTimestampKey = longPreferencesKey("last_backup_timestamp")
    private val userSexKey = stringPreferencesKey("user_sex")
    private val userBirthDateKey = stringPreferencesKey("user_birth_date")
    private val userHeightCmKey = doublePreferencesKey("user_height_cm")
    private val userWorkoutKcalKey = doublePreferencesKey("user_workout_kcal_per_session")

    /**
     * Reads all tables and user preferences and exports them encrypted into [outputStream].
     * Never exports `controlcal_key_store` or device-specific state.
     */
    suspend fun exportBackup(password: CharArray, outputStream: OutputStream, appVersion: String = "1.0") {
        withContext(Dispatchers.IO) {
            val payload = buildBackupPayload(appVersion)
            val encryptedBytes = BackupCodec.encrypt(payload, password)
            outputStream.use { it.write(encryptedBytes) }

            val now = System.currentTimeMillis()
            dataStore.edit { prefs ->
                prefs[lastBackupTimestampKey] = now
            }
        }
    }

    /**
     * Reads and decrypts the backup from [inputStream], validates schema version,
     * and returns both the preview summary and the parsed [BackupPayload] for confirmation.
     * Does NOT touch the database.
     */
    suspend fun inspectBackup(password: CharArray, inputStream: InputStream): Pair<BackupSummary, BackupPayload> {
        return withContext(Dispatchers.IO) {
            val encryptedBytes = inputStream.use { it.readBytes() }
            val payload = BackupCodec.decrypt(encryptedBytes, password)

            if (payload.schemaVersion > CURRENT_ROOM_SCHEMA_VERSION) {
                throw NewerSchemaVersionException(payload.schemaVersion, CURRENT_ROOM_SCHEMA_VERSION)
            }

            val summary = BackupCodec.extractSummary(payload)
            Pair(summary, payload)
        }
    }

    /**
     * Restores [payload] into Room in a single atomic transaction.
     * Foreign keys are strictly respected: dependent tables are cleared first and inserted last.
     * If an error occurs, full rollback is performed and existing data remains intact.
     * Preferences in DataStore are updated only after the Room transaction commits successfully.
     */
    suspend fun restoreBackup(payload: BackupPayload) {
        withContext(Dispatchers.IO) {
            if (payload.schemaVersion > CURRENT_ROOM_SCHEMA_VERSION) {
                throw NewerSchemaVersionException(payload.schemaVersion, CURRENT_ROOM_SCHEMA_VERSION)
            }

            val productEntities = payload.products.map { it.toEntity() }
            val dailyLogEntities = payload.dailyLogs.map { it.toEntity(payload.schemaVersion) }
            val mealEntryEntities = payload.mealEntries.map { it.toEntity() }
            val weightEntryEntities = payload.weightEntries.map { it.toEntity() }
            val goalProfileEntities = payload.goalProfiles.map { it.toEntity() }
            val savedMealEntities = payload.savedMeals.map { it.toEntity() }
            val savedMealItemEntities = payload.savedMealItems.map { it.toEntity() }
            val supplementEntities = payload.supplements.map { it.toEntity() }
            val supplementIntakeEntities = payload.supplementIntakes.map { it.toEntity() }
            val recipeEntities = payload.recipes.map { it.toEntity() }
            val recipeIngredientEntities = payload.recipeIngredients.map { it.toEntity() }

            database.withTransaction {
                // Delete dependent tables first (reverse FK order)
                supplementDao.deleteAllIntakes()
                supplementDao.deleteAll()
                recipeDao.deleteAllIngredients()
                recipeDao.deleteAll()
                savedMealDao.deleteAllItems()
                savedMealDao.deleteAllMeals()
                mealEntryDao.deleteAll()
                dailyLogDao.deleteAll()
                productDao.deleteAll()
                weightEntryDao.deleteAll()
                goalProfileDao.deleteAll()

                // Insert parent tables before child tables
                if (productEntities.isNotEmpty()) {
                    productDao.insertAll(productEntities)
                }
                if (recipeEntities.isNotEmpty()) {
                    for (recipe in recipeEntities) {
                        recipeDao.insertRecipe(recipe)
                    }
                }
                if (recipeIngredientEntities.isNotEmpty()) {
                    recipeDao.insertIngredients(recipeIngredientEntities)
                }
                if (dailyLogEntities.isNotEmpty()) {
                    dailyLogDao.insertAll(dailyLogEntities)
                }
                if (mealEntryEntities.isNotEmpty()) {
                    mealEntryDao.insertAll(mealEntryEntities)
                }
                if (weightEntryEntities.isNotEmpty()) {
                    weightEntryDao.insertAll(weightEntryEntities)
                }
                if (goalProfileEntities.isNotEmpty()) {
                    goalProfileDao.insertAll(goalProfileEntities)
                }
                if (savedMealEntities.isNotEmpty()) {
                    for (meal in savedMealEntities) {
                        savedMealDao.insertMeal(meal)
                    }
                }
                if (savedMealItemEntities.isNotEmpty()) {
                    savedMealDao.insertItems(savedMealItemEntities)
                }
                if (supplementEntities.isNotEmpty()) {
                    supplementDao.insertAll(supplementEntities)
                }
                if (supplementIntakeEntities.isNotEmpty()) {
                    supplementDao.insertAllIntakes(supplementIntakeEntities)
                }
            }

            // Restore preferences only after Room transaction commits successfully
            dataStore.edit { prefs ->
                payload.preferences.weeklyPattern?.let { prefs[weeklyPatternKey] = it }
                payload.preferences.minKcalLimit?.let { prefs[minKcalLimitKey] = it }
                payload.preferences.lastTdeeProposalReviewedDate?.let { prefs[lastTdeeProposalReviewedDateKey] = it }
                payload.preferences.configuredMealsJson?.let { prefs[configuredMealsKey] = it }
                payload.preferences.userSex?.let { prefs[userSexKey] = it }
                payload.preferences.birthDate?.let { prefs[userBirthDateKey] = it }
                payload.preferences.heightCm?.let { prefs[userHeightCmKey] = it }
                payload.preferences.workoutKcalPerSession?.let { prefs[userWorkoutKcalKey] = it }
            }

            // Reschedule all reminders after restoring backup
            reminderScheduler.rescheduleAll()
        }
    }

    suspend fun buildBackupPayload(appVersion: String = "1.0"): BackupPayload {
        val products = productDao.allIncludingArchived().map { it.toBackupDto() }
        val dailyLogs = dailyLogDao.getAll().map { it.toBackupDto() }
        val mealEntries = mealEntryDao.getAll().map { it.toBackupDto() }
        val weightEntries = weightEntryDao.getAllAsc().map { it.toBackupDto() }
        val goalProfiles = goalProfileDao.getAll().map { it.toBackupDto() }

        val savedMealsWithItems = savedMealDao.getAllWithItems()
        val backupSavedMeals = savedMealsWithItems.map { it.meal.toBackupDto() }
        val backupSavedMealItems = savedMealsWithItems.flatMap { it.items.map { item -> item.toBackupDto() } }

        val supplements = supplementDao.getAll().map { it.toBackupDto() }
        val supplementIntakes = supplementDao.getAllIntakes().map { it.toBackupDto() }

        val recipesWithIngredients = recipeDao.getAllWithIngredients(includeArchived = true)
        val backupRecipes = recipesWithIngredients.map { it.recipe.toBackupDto() }
        val backupRecipeIngredients = recipesWithIngredients.flatMap { it.ingredients.map { item -> item.toBackupDto() } }

        val prefs = dataStore.data.first()
        val preferences = BackupPreferencesDto(
            weeklyPattern = prefs[weeklyPatternKey],
            minKcalLimit = prefs[minKcalLimitKey],
            lastTdeeProposalReviewedDate = prefs[lastTdeeProposalReviewedDateKey],
            configuredMealsJson = prefs[configuredMealsKey],
            userSex = prefs[userSexKey],
            birthDate = prefs[userBirthDateKey],
            heightCm = prefs[userHeightCmKey],
            workoutKcalPerSession = prefs[userWorkoutKcalKey],
        )

        return BackupPayload(
            backupFormatVersion = BackupCodec.CURRENT_FORMAT_VERSION,
            schemaVersion = CURRENT_ROOM_SCHEMA_VERSION,
            createdAt = System.currentTimeMillis(),
            appVersion = appVersion,
            products = products,
            dailyLogs = dailyLogs,
            mealEntries = mealEntries,
            weightEntries = weightEntries,
            goalProfiles = goalProfiles,
            preferences = preferences,
            savedMeals = backupSavedMeals,
            savedMealItems = backupSavedMealItems,
            supplements = supplements,
            supplementIntakes = supplementIntakes,
            recipes = backupRecipes,
            recipeIngredients = backupRecipeIngredients,
        )
    }

    companion object {
        const val CURRENT_ROOM_SCHEMA_VERSION = 6

        private fun ProductEntity.toBackupDto(): BackupProductDto = BackupProductDto(
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
            source = source.name,
            archived = archived,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

        private fun BackupProductDto.toEntity(): ProductEntity = ProductEntity(
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
            source = try { ProductSource.valueOf(source) } catch (_: Exception) { ProductSource.MANUAL },
            archived = archived,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

        private fun DailyLogEntity.toBackupDto(): BackupDailyLogDto = BackupDailyLogDto(
            date = date,
            dayType = dayType.name,
            waterMl = waterMl,
            notes = notes,
            steps = steps,
        )

        private fun BackupDailyLogDto.toEntity(backupSchemaVersion: Int): DailyLogEntity = DailyLogEntity(
            date = date,
            dayType = try { DayType.valueOf(dayType) } catch (_: Exception) { DayType.REST },
            waterMl = waterMl,
            notes = notes,
            steps = if (backupSchemaVersion < 3) 0 else steps,
        )

        private fun MealEntryEntity.toBackupDto(): BackupMealEntryDto = BackupMealEntryDto(
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

        private fun BackupMealEntryDto.toEntity(): MealEntryEntity = MealEntryEntity(
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

        private fun WeightEntryEntity.toBackupDto(): BackupWeightEntryDto = BackupWeightEntryDto(
            date = date,
            weightKg = weightKg,
        )

        private fun BackupWeightEntryDto.toEntity(): WeightEntryEntity = WeightEntryEntity(
            date = date,
            weightKg = weightKg,
        )

        private fun GoalProfileEntity.toBackupDto(): BackupGoalProfileDto = BackupGoalProfileDto(
            id = id,
            dayType = dayType.name,
            kcal = kcal,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            validFrom = validFrom,
            origin = origin.name,
        )

        private fun BackupGoalProfileDto.toEntity(): GoalProfileEntity = GoalProfileEntity(
            id = id,
            dayType = try { DayType.valueOf(dayType) } catch (_: Exception) { DayType.REST },
            kcal = kcal,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            validFrom = validFrom,
            origin = try { GoalOrigin.valueOf(origin) } catch (_: Exception) { GoalOrigin.MANUAL },
        )

        private fun SavedMealEntity.toBackupDto(): BackupSavedMealDto = BackupSavedMealDto(
            id = id,
            name = name,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastUsedAt = lastUsedAt,
            useCount = useCount,
        )

        private fun BackupSavedMealDto.toEntity(): SavedMealEntity = SavedMealEntity(
            id = id,
            name = name,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastUsedAt = lastUsedAt,
            useCount = useCount,
        )

        private fun SavedMealItemEntity.toBackupDto(): BackupSavedMealItemDto = BackupSavedMealItemDto(
            id = id,
            savedMealId = savedMealId,
            position = position,
            productId = productId,
            productName = productName,
            grams = grams,
            kcal = kcal,
            protein = protein,
            carbs = carbs,
            fat = fat,
        )

        private fun BackupSavedMealItemDto.toEntity(): SavedMealItemEntity = SavedMealItemEntity(
            id = id,
            savedMealId = savedMealId,
            position = position,
            productId = productId,
            productName = productName,
            grams = grams,
            kcal = kcal,
            protein = protein,
            carbs = carbs,
            fat = fat,
        )

        private fun SupplementEntity.toBackupDto(): BackupSupplementDto = BackupSupplementDto(
            id = id,
            name = name,
            doseAmount = doseAmount,
            doseUnit = doseUnit,
            scheduleType = scheduleType,
            weekdays = weekdays,
            intervalDays = intervalDays,
            startDate = startDate,
            times = times,
            active = active,
            remindersEnabled = remindersEnabled,
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

        private fun BackupSupplementDto.toEntity(): SupplementEntity = SupplementEntity(
            id = id,
            name = name,
            doseAmount = doseAmount,
            doseUnit = doseUnit,
            scheduleType = scheduleType,
            weekdays = weekdays,
            intervalDays = intervalDays,
            startDate = startDate,
            times = times,
            active = active,
            remindersEnabled = remindersEnabled,
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

        private fun SupplementIntakeEntity.toBackupDto(): BackupSupplementIntakeDto = BackupSupplementIntakeDto(
            id = id,
            supplementId = supplementId,
            scheduledDate = scheduledDate,
            scheduledTime = scheduledTime,
            status = status,
            recordedAt = recordedAt,
        )

        private fun BackupSupplementIntakeDto.toEntity(): SupplementIntakeEntity = SupplementIntakeEntity(
            id = id,
            supplementId = supplementId,
            scheduledDate = scheduledDate,
            scheduledTime = scheduledTime,
            status = status,
            recordedAt = recordedAt,
        )

        private fun RecipeEntity.toBackupDto(): BackupRecipeDto = BackupRecipeDto(
            id = id,
            productId = productId,
            name = name,
            cookedWeightG = cookedWeightG,
            servings = servings,
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt,
            archived = archived,
        )

        private fun BackupRecipeDto.toEntity(): RecipeEntity = RecipeEntity(
            id = id,
            productId = productId,
            name = name,
            cookedWeightG = cookedWeightG,
            servings = servings,
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt,
            archived = archived,
        )

        private fun RecipeIngredientEntity.toBackupDto(): BackupRecipeIngredientDto = BackupRecipeIngredientDto(
            id = id,
            recipeId = recipeId,
            position = position,
            productId = productId,
            grams = grams,
        )

        private fun BackupRecipeIngredientDto.toEntity(): RecipeIngredientEntity = RecipeIngredientEntity(
            id = id,
            recipeId = recipeId,
            position = position,
            productId = productId,
            grams = grams,
        )
    }
}
