package com.controlcal.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.controlcal.core.model.DayType
import com.controlcal.core.model.GoalOrigin

/**
 * A macro goal profile for a [dayType]. The profile in force for a day is
 * the latest one whose [validFrom] (local ISO date, yyyy-MM-dd) is not
 * after that day (section 5.1 of docs/PLAN.md).
 */
@Entity(tableName = "goal_profiles")
data class GoalProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "day_type")
    val dayType: DayType,
    val kcal: Double,
    @ColumnInfo(name = "protein_g")
    val proteinG: Double,
    @ColumnInfo(name = "carbs_g")
    val carbsG: Double,
    @ColumnInfo(name = "fat_g")
    val fatG: Double,
    @ColumnInfo(name = "valid_from")
    val validFrom: String,
    val origin: GoalOrigin,
)
