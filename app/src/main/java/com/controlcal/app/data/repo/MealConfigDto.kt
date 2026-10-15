package com.controlcal.app.data.repo

import com.controlcal.core.model.ConfiguredMeal
import kotlinx.serialization.Serializable
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Serializable DTO for persisting configured meals in DataStore as JSON.
 * [startTime] is stored in standard "HH:mm" 24h format.
 */
@Serializable
data class MealConfigDto(
    val id: String,
    val name: String,
    val startTime: String,
    val isActive: Boolean = true,
) {
    fun toDomain(): ConfiguredMeal {
        val parsedTime = try {
            LocalTime.parse(startTime, TIME_FORMATTER)
        } catch (_: Exception) {
            LocalTime.of(0, 0)
        }
        return ConfiguredMeal(
            id = id,
            name = name,
            startTime = parsedTime,
            isActive = isActive,
        )
    }

    companion object {
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun fromDomain(domain: ConfiguredMeal): MealConfigDto {
            return MealConfigDto(
                id = domain.id,
                name = domain.name,
                startTime = domain.startTime.format(TIME_FORMATTER),
                isActive = domain.isActive,
            )
        }
    }
}
