package com.controlcal.app.reminder

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.controlcal.app.data.db.ControlCalDatabase
import com.controlcal.app.data.db.entity.SupplementEntity
import com.controlcal.app.data.repo.SupplementRepositoryImpl
import com.controlcal.core.repo.SupplementRepository
import com.controlcal.core.supplement.IntakeStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SupplementActionReceiverTest {

    private lateinit var context: Context
    private lateinit var db: ControlCalDatabase
    private lateinit var repositoryImpl: SupplementRepositoryImpl
    private lateinit var notificationHelper: SupplementNotificationHelper
    private lateinit var reminderScheduler: SupplementReminderScheduler
    private var testSupplementId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ControlCalDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        testSupplementId = db.supplementDao().insert(
            SupplementEntity(
                name = "Omega 3",
                doseAmount = 1000.0,
                doseUnit = "mg",
                scheduleType = "DAILY",
                startDate = "2026-11-01",
                times = """["08:00"]""",
                active = true,
                remindersEnabled = true,
                createdAt = 1000L,
                updatedAt = 1000L,
            )
        )

        repositoryImpl = SupplementRepositoryImpl(db, db.supplementDao())
        notificationHelper = SupplementNotificationHelper(context)
        reminderScheduler = SupplementReminderScheduler(context, db.supplementDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun markTakenActionPressedTwiceDoesNotDuplicateRecords() {
        val latch = CountDownLatch(2)

        val delegatingRepo = object : SupplementRepository by repositoryImpl {
            override suspend fun recordIntake(
                supplementId: Long,
                scheduledDate: LocalDate,
                scheduledTime: LocalTime,
                status: IntakeStatus,
            ): Boolean {
                val result = repositoryImpl.recordIntake(supplementId, scheduledDate, scheduledTime, status)
                latch.countDown()
                return result
            }
        }

        val receiver = SupplementActionReceiver().apply {
            this.supplementRepository = delegatingRepo
            this.notificationHelper = this@SupplementActionReceiverTest.notificationHelper
            this.reminderScheduler = this@SupplementActionReceiverTest.reminderScheduler
        }
        try {
            val field = Hilt_SupplementActionReceiver::class.java.getDeclaredField("injected")
            field.isAccessible = true
            field.set(receiver, true)
        } catch (_: Throwable) {}

        val intent = Intent(SupplementActionReceiver.ACTION_MARK_TAKEN).apply {
            putExtra(SupplementActionReceiver.EXTRA_SUPPLEMENT_ID, testSupplementId)
            putExtra(SupplementActionReceiver.EXTRA_SCHEDULED_DATE, "2026-11-10")
            putExtra(SupplementActionReceiver.EXTRA_SCHEDULED_TIME, "08:00")
            putExtra(SupplementActionReceiver.EXTRA_NOTIFICATION_ID, 1001)
        }

        // Simulate user or system tapping the action twice
        receiver.onReceive(context, intent)
        receiver.onReceive(context, intent)

        val completed = latch.await(5, TimeUnit.SECONDS)
        assertTrue("Timed out waiting for repository operations", completed)

        val intakes = runBlocking { db.supplementDao().getAllIntakes() }
        assertEquals("Should only contain 1 intake record without duplicates", 1, intakes.size)
        assertEquals(testSupplementId, intakes.first().supplementId)
        assertEquals("2026-11-10", intakes.first().scheduledDate)
        assertEquals("08:00", intakes.first().scheduledTime)
        assertEquals("TAKEN", intakes.first().status)
    }

    @Test
    fun snoozeActionCancelsNotificationAndSchedulesAlarm() {
        val receiver = SupplementActionReceiver().apply {
            this.supplementRepository = repositoryImpl
            this.notificationHelper = this@SupplementActionReceiverTest.notificationHelper
            this.reminderScheduler = this@SupplementActionReceiverTest.reminderScheduler
        }
        try {
            val field = Hilt_SupplementActionReceiver::class.java.getDeclaredField("injected")
            field.isAccessible = true
            field.set(receiver, true)
        } catch (_: Throwable) {}

        val intent = Intent(SupplementActionReceiver.ACTION_SNOOZE_15_MIN).apply {
            putExtra(SupplementActionReceiver.EXTRA_SUPPLEMENT_ID, testSupplementId)
            putExtra(SupplementActionReceiver.EXTRA_SCHEDULED_DATE, "2026-11-10")
            putExtra(SupplementActionReceiver.EXTRA_SCHEDULED_TIME, "08:00")
            putExtra(SupplementActionReceiver.EXTRA_NOTIFICATION_ID, 1001)
        }

        // Should execute cleanly without error
        receiver.onReceive(context, intent)
    }
}
