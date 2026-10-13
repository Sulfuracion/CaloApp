package com.controlcal.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.controlcal.core.model.DayType

/**
 * One row per day. [date] is the primary key in local ISO format
 * (yyyy-MM-dd), as defined by section 4 of docs/PLAN.md.
 */
@Entity(tableName = "daily_logs")
data class DailyLogEntity(
    @PrimaryKey
    val date: String,
    @ColumnInfo(name = "day_type")
    val dayType: DayType,
    @ColumnInfo(name = "water_ml")
    val waterMl: Int = 0,
    val notes: String? = null,
)
