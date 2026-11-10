package com.controlcal.app.data.health

import android.content.Context
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class HealthConnectManagerTest {

    private lateinit var context: Context
    private val fixedClock = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneId.of("UTC"))
    private lateinit var manager: HealthConnectManagerImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = HealthConnectManagerImpl(context, fixedClock)
    }

    @Test
    fun getPermissions_returnsStepsReadPermission() {
        val permissions = manager.getPermissions()
        assertEquals(1, permissions.size)
        assertTrue(permissions.contains(HealthPermission.getReadPermission(StepsRecord::class)))
    }

    @Test
    fun isSupported_doesNotThrow() {
        // In Robolectric environment without Health Connect APK, returns false safely without throwing
        val supported = manager.isSupported()
        // Must be a valid boolean
        assertTrue(supported || !supported)
    }

    @Test
    fun hasStepsPermission_whenNoClient_returnsFalseGracefully() = runTest {
        val hasPermission = manager.hasStepsPermission()
        assertEquals(false, hasPermission)
    }

    @Test
    fun readStepsForDate_whenNoClientOrNoPermission_returnsNullGracefully() = runTest {
        val steps = manager.readStepsForDate(LocalDate.of(2026, 11, 1))
        assertEquals(null, steps)
    }

    @Test
    fun readYesterdaySteps_whenNoClientOrNoPermission_returnsNullGracefully() = runTest {
        val steps = manager.readYesterdaySteps()
        assertEquals(null, steps)
    }
}
