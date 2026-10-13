package com.controlcal.core.model

/**
 * Domain model for daily log metadata.
 * [date] is in local ISO-8601 format (yyyy-MM-dd).
 */
data class DailyLog(
    val date: String,
    val dayType: DayType = DayType.REST,
    val waterMl: Int = 0,
    val notes: String? = null,
)
