package com.controlcal.app.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.controlcal.core.model.ConfiguredMeal
import com.controlcal.core.model.DefaultMealIds
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class MealConfigRepositoryImplTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val mealConfigsKey = stringPreferencesKey("configured_meals_json")

    @After
    fun cleanUp() = runTest {
        dataStore.edit { it.clear() }
    }

    @Test
    fun returnsDefaultMealsWhenEmpty() = runTest {
        val repo = MealConfigRepositoryImpl(context, dataStore)
        val meals = repo.getMealConfigs()

        assertEquals(4, meals.size)
        assertEquals(DefaultMealIds.BREAKFAST, meals[0].id)
        assertEquals(LocalTime.of(6, 0), meals[0].startTime)
        assertTrue(meals[0].isActive)

        assertEquals(DefaultMealIds.LUNCH, meals[1].id)
        assertEquals(LocalTime.of(12, 0), meals[1].startTime)
        assertTrue(meals[1].isActive)

        assertEquals(DefaultMealIds.SNACK, meals[2].id)
        assertEquals(LocalTime.of(17, 0), meals[2].startTime)
        assertTrue(meals[2].isActive)

        assertEquals(DefaultMealIds.DINNER, meals[3].id)
        assertEquals(LocalTime.of(21, 0), meals[3].startTime)
        assertTrue(meals[3].isActive)
    }

    @Test
    fun roundTrip_serializesAndDeserializesCorrectly() = runTest {
        val repo = MealConfigRepositoryImpl(context, dataStore)
        val customMeals = listOf(
            ConfiguredMeal("BREAKFAST", "Desayuno", LocalTime.of(7, 30), isActive = true),
            ConfiguredMeal("LUNCH", "Comida", LocalTime.of(14, 0), isActive = true),
            ConfiguredMeal("DINNER", "Cena Ligera", LocalTime.of(21, 30), isActive = true),
            ConfiguredMeal("meal_custom_1", "Pre-entreno", LocalTime.of(18, 0), isActive = false),
        )

        repo.saveMealConfigs(customMeals)
        val retrieved = repo.getMealConfigs()

        assertEquals(4, retrieved.size)
        assertEquals("BREAKFAST", retrieved[0].id)
        assertEquals("Desayuno", retrieved[0].name)
        assertEquals(LocalTime.of(7, 30), retrieved[0].startTime)
        assertTrue(retrieved[0].isActive)

        assertEquals("LUNCH", retrieved[1].id)
        assertEquals("Comida", retrieved[1].name)
        assertEquals(LocalTime.of(14, 0), retrieved[1].startTime)
        assertTrue(retrieved[1].isActive)

        assertEquals("DINNER", retrieved[2].id)
        assertEquals("Cena Ligera", retrieved[2].name)
        assertEquals(LocalTime.of(21, 30), retrieved[2].startTime)
        assertTrue(retrieved[2].isActive)

        assertEquals("meal_custom_1", retrieved[3].id)
        assertEquals("Pre-entreno", retrieved[3].name)
        assertEquals(LocalTime.of(18, 0), retrieved[3].startTime)
        assertEquals(false, retrieved[3].isActive)
    }

    @Test
    fun corruptedJson_fallsBackToDefaults() = runTest {
        val repo = MealConfigRepositoryImpl(context, dataStore)
        dataStore.edit { prefs ->
            prefs[mealConfigsKey] = "{ corrupt: [broken json "
        }

        val meals = repo.getMealConfigs()
        val defaults = MealConfigRepositoryImpl.getDefaultMeals(context)

        assertEquals(defaults.size, meals.size)
        assertEquals(defaults.map { it.id }, meals.map { it.id })
    }

    @Test
    fun jsonWithUnknownKeys_deserializesSafely() = runTest {
        val repo = MealConfigRepositoryImpl(context, dataStore)
        val jsonWithUnknownFields = """
            [
                {
                    "id": "BREAKFAST",
                    "name": "Desayuno Rico",
                    "startTime": "07:00",
                    "isActive": true,
                    "futureField": "unexpected_value",
                    "nestedUnknown": { "version": 2 }
                },
                {
                    "id": "DINNER",
                    "name": "Cena",
                    "startTime": "20:30",
                    "isActive": true,
                    "extraInteger": 42
                }
            ]
        """.trimIndent()

        dataStore.edit { prefs ->
            prefs[mealConfigsKey] = jsonWithUnknownFields
        }

        val meals = repo.getMealConfigs()
        assertEquals(2, meals.size)
        assertEquals("BREAKFAST", meals[0].id)
        assertEquals("Desayuno Rico", meals[0].name)
        assertEquals(LocalTime.of(7, 0), meals[0].startTime)
        assertTrue(meals[0].isActive)

        assertEquals("DINNER", meals[1].id)
        assertEquals("Cena", meals[1].name)
        assertEquals(LocalTime.of(20, 30), meals[1].startTime)
        assertTrue(meals[1].isActive)
    }

    @Test
    fun jsonWithAllInactiveMeals_fallsBackToDefaults() = runTest {
        val repo = MealConfigRepositoryImpl(context, dataStore)
        val jsonAllInactive = """
            [
                {
                    "id": "BREAKFAST",
                    "name": "Desayuno",
                    "startTime": "08:00",
                    "isActive": false
                }
            ]
        """.trimIndent()

        dataStore.edit { prefs ->
            prefs[mealConfigsKey] = jsonAllInactive
        }

        val meals = repo.getMealConfigs()
        val defaults = MealConfigRepositoryImpl.getDefaultMeals(context)

        assertEquals(defaults.size, meals.size)
        assertTrue(meals.any { it.isActive })
    }

    companion object {
        private lateinit var dataStore: DataStore<Preferences>

        @BeforeClass
        @JvmStatic
        fun createStore() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val file = File(context.filesDir, "test_meal_configs.preferences_pb").apply { delete() }
            dataStore = PreferenceDataStoreFactory.create { file }
        }
    }
}
