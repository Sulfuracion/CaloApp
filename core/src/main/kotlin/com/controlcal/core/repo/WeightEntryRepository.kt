package com.controlcal.core.repo

import com.controlcal.core.model.WeightEntry

interface WeightEntryRepository {
    suspend fun upsert(entry: WeightEntry)
    suspend fun getByDate(date: String): WeightEntry?
    suspend fun getBetween(from: String, to: String): List<WeightEntry>
    suspend fun getAll(): List<WeightEntry>
    suspend fun deleteByDate(date: String)
    suspend fun deleteAll() {}
    suspend fun moveEntry(oldDate: String, newEntry: WeightEntry)
}
