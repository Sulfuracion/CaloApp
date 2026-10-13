package com.controlcal.core.repo

import com.controlcal.core.model.DailyLog
import com.controlcal.core.model.DayType

interface DailyLogRepository {
    suspend fun getByDate(date: String): DailyLog?
    suspend fun insert(log: DailyLog)
    suspend fun setDayType(date: String, dayType: DayType)
    suspend fun setWaterMl(date: String, waterMl: Int)
    suspend fun setNotes(date: String, notes: String?)
}
