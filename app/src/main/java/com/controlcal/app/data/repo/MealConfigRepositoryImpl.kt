package com.controlcal.app.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.controlcal.app.R
import com.controlcal.core.model.ConfiguredMeal
import com.controlcal.core.model.DefaultMeals
import com.controlcal.core.repo.MealConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealConfigRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) : MealConfigRepository {

    private val mealConfigsKey = stringPreferencesKey("configured_meals_json")

    override suspend fun getMealConfigs(): List<ConfiguredMeal> {
        val prefs = dataStore.data.first()
        val raw = prefs[mealConfigsKey]
        val defaults = getDefaultMeals(context)
        if (raw.isNullOrBlank()) {
            return defaults
        }
        return deserializeMeals(raw, defaults)
    }

    override suspend fun saveMealConfigs(configs: List<ConfiguredMeal>) {
        val jsonString = serializeMeals(configs)
        dataStore.edit { prefs ->
            prefs[mealConfigsKey] = jsonString
        }
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun getDefaultMeals(context: Context): List<ConfiguredMeal> {
            return DefaultMeals.defaults(
                breakfastName = context.getString(R.string.default_meal_breakfast),
                lunchName = context.getString(R.string.default_meal_lunch),
                snackName = context.getString(R.string.default_meal_snack),
                dinnerName = context.getString(R.string.default_meal_dinner),
            )
        }

        fun serializeMeals(meals: List<ConfiguredMeal>): String {
            val dtos = meals.map { MealConfigDto.fromDomain(it) }
            return json.encodeToString(dtos)
        }

        fun deserializeMeals(rawJson: String, defaultMeals: List<ConfiguredMeal>): List<ConfiguredMeal> {
            if (rawJson.isBlank()) return defaultMeals
            return try {
                val dtos = json.decodeFromString<List<MealConfigDto>>(rawJson)
                if (dtos.isEmpty()) {
                    defaultMeals
                } else {
                    val domainMeals = dtos.map { it.toDomain() }
                    // If no meal is active, fall back to default meals for safety
                    if (domainMeals.none { it.isActive }) {
                        defaultMeals
                    } else {
                        domainMeals
                    }
                }
            } catch (_: Exception) {
                defaultMeals
            }
        }
    }
}
