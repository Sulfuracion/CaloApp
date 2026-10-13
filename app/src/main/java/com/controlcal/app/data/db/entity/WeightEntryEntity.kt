package com.controlcal.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One weight measurement per day. [date] is the primary key in local ISO
 * format (yyyy-MM-dd), as defined by section 4 of docs/PLAN.md.
 */
@Entity(tableName = "weight_entries")
data class WeightEntryEntity(
    @PrimaryKey
    val date: String,
    @ColumnInfo(name = "weight_kg")
    val weightKg: Double,
)
