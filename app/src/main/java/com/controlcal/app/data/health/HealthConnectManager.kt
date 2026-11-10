package com.controlcal.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface HealthConnectManager {
    fun isSupported(): Boolean
    suspend fun hasStepsPermission(): Boolean
    fun getPermissions(): Set<String>
    suspend fun readStepsForDate(date: LocalDate): Long?
    suspend fun readYesterdaySteps(): Long?
}

@Singleton
class HealthConnectManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : HealthConnectManager {

    private fun getClient(): HealthConnectClient? {
        return try {
            if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun isSupported(): Boolean {
        return try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (_: Exception) {
            false
        }
    }

    override fun getPermissions(): Set<String> {
        return setOf(HealthPermission.getReadPermission(StepsRecord::class))
    }

    override suspend fun hasStepsPermission(): Boolean {
        return try {
            val client = getClient() ?: return false
            val granted = client.permissionController.getGrantedPermissions()
            granted.contains(HealthPermission.getReadPermission(StepsRecord::class))
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun readStepsForDate(date: LocalDate): Long? {
        return try {
            val client = getClient() ?: return null
            if (!hasStepsPermission()) return null

            val zoneId = clock.zone
            val startOfDay = date.atStartOfDay(zoneId).toInstant()
            val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant()

            val request = AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
            )
            val response = client.aggregate(request)
            response[StepsRecord.COUNT_TOTAL] ?: 0L
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun readYesterdaySteps(): Long? {
        val yesterday = LocalDate.now(clock.zone).minusDays(1)
        return readStepsForDate(yesterday)
    }
}
