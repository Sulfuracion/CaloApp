package com.controlcal.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.controlcal.core.supplement.ScheduleType
import com.controlcal.core.supplement.Supplement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "supplements")
data class SupplementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "dose_amount")
    val doseAmount: Double,
    @ColumnInfo(name = "dose_unit")
    val doseUnit: String,
    @ColumnInfo(name = "schedule_type")
    val scheduleType: String,
    val weekdays: Int? = null,
    @ColumnInfo(name = "interval_days")
    val intervalDays: Int? = null,
    @ColumnInfo(name = "start_date")
    val startDate: String,
    val times: String,
    @ColumnInfo(defaultValue = "1")
    val active: Boolean = true,
    @ColumnInfo(name = "reminders_enabled", defaultValue = "1")
    val remindersEnabled: Boolean = true,
    val notes: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    fun toDomain(): Supplement {
        val parsedTimes = try {
            val list = Json.decodeFromString<List<String>>(times)
            list.map { LocalTime.parse(it) }
        } catch (_: Exception) {
            emptyList()
        }

        return Supplement(
            id = id,
            name = name,
            doseAmount = doseAmount,
            doseUnit = doseUnit,
            scheduleType = ScheduleType.valueOf(scheduleType),
            weekdays = weekdays,
            intervalDays = intervalDays,
            startDate = LocalDate.parse(startDate),
            times = parsedTimes,
            active = active,
            remindersEnabled = remindersEnabled,
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    companion object {
        fun fromDomain(domain: Supplement): SupplementEntity {
            val timesJson = Json.encodeToString(domain.times.map { it.toString() })
            return SupplementEntity(
                id = domain.id,
                name = domain.name,
                doseAmount = domain.doseAmount,
                doseUnit = domain.doseUnit,
                scheduleType = domain.scheduleType.name,
                weekdays = domain.weekdays,
                intervalDays = domain.intervalDays,
                startDate = domain.startDate.toString(),
                times = timesJson,
                active = domain.active,
                remindersEnabled = domain.remindersEnabled,
                notes = domain.notes,
                createdAt = domain.createdAt,
                updatedAt = domain.updatedAt,
            )
        }
    }
}
