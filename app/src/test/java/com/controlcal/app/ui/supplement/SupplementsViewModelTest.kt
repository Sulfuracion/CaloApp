package com.controlcal.app.ui.supplement

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.controlcal.app.reminder.SupplementNotificationHelper
import com.controlcal.app.reminder.SupplementReminderScheduler
import com.controlcal.app.test.FakeSupplementRepository
import com.controlcal.core.supplement.IntakeStatus
import com.controlcal.core.supplement.ScheduleType
import com.controlcal.core.supplement.Supplement
import com.controlcal.core.supplement.SupplementIntake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SupplementsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var fakeRepository: FakeSupplementRepository
    private lateinit var reminderScheduler: SupplementReminderScheduler
    private lateinit var notificationHelper: SupplementNotificationHelper
    private lateinit var viewModel: SupplementsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        fakeRepository = FakeSupplementRepository()
        notificationHelper = SupplementNotificationHelper(context)
        reminderScheduler = SupplementReminderScheduler(context, object : com.controlcal.app.data.db.dao.SupplementDao {
            override fun getAllFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.controlcal.app.data.db.entity.SupplementEntity>>()
            override suspend fun getAll() = emptyList<com.controlcal.app.data.db.entity.SupplementEntity>()
            override fun getActiveFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.controlcal.app.data.db.entity.SupplementEntity>>()
            override suspend fun getActive() = emptyList<com.controlcal.app.data.db.entity.SupplementEntity>()
            override suspend fun getById(id: Long) = null
            override suspend fun insert(supplement: com.controlcal.app.data.db.entity.SupplementEntity) = 1L
            override suspend fun insertAll(supplements: List<com.controlcal.app.data.db.entity.SupplementEntity>) {}
            override suspend fun update(supplement: com.controlcal.app.data.db.entity.SupplementEntity) {}
            override suspend fun setActive(id: Long, active: Boolean, updatedAt: Long) {}
            override suspend fun deleteById(id: Long) {}
            override suspend fun deleteAll() {}
            override fun getIntakesForDateFlow(date: String) = kotlinx.coroutines.flow.emptyFlow<List<com.controlcal.app.data.db.entity.SupplementIntakeEntity>>()
            override suspend fun getIntakesForDate(date: String) = emptyList<com.controlcal.app.data.db.entity.SupplementIntakeEntity>()
            override suspend fun getIntakesBetween(startDate: String, endDate: String) = emptyList<com.controlcal.app.data.db.entity.SupplementIntakeEntity>()
            override suspend fun getIntake(supplementId: Long, date: String, time: String) = null
            override suspend fun insertIntake(intake: com.controlcal.app.data.db.entity.SupplementIntakeEntity) = 1L
            override suspend fun insertIntakeIgnore(intake: com.controlcal.app.data.db.entity.SupplementIntakeEntity) = 1L
            override suspend fun insertAllIntakes(intakes: List<com.controlcal.app.data.db.entity.SupplementIntakeEntity>) {}
            override suspend fun deleteIntake(supplementId: Long, date: String, time: String) {}
            override suspend fun deleteAllIntakes() {}
            override suspend fun getAllIntakes() = emptyList<com.controlcal.app.data.db.entity.SupplementIntakeEntity>()
        }).apply {
            this.ioDispatcher = testDispatcher
        }

        viewModel = SupplementsViewModel(
            supplementRepository = fakeRepository,
            reminderScheduler = reminderScheduler,
            notificationHelper = notificationHelper,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadSupplementsAndCalculateAdherence() = runTest(testDispatcher) {
        val today = LocalDate.now()
        val supplement = Supplement(
            id = 1L,
            name = "Vitamina D3",
            doseAmount = 2000.0,
            doseUnit = "UI",
            scheduleType = ScheduleType.DAILY,
            startDate = today.minusDays(5),
            times = listOf(LocalTime.of(8, 0)),
            active = true,
            remindersEnabled = true,
        )
        fakeRepository.supplements.add(supplement)

        // 4 taken, 1 skipped
        for (i in 1..4) {
            fakeRepository.intakes.add(
                SupplementIntake(
                    id = i.toLong(),
                    supplementId = 1L,
                    scheduledDate = today.minusDays(i.toLong()),
                    scheduledTime = LocalTime.of(8, 0),
                    status = IntakeStatus.TAKEN,
                    recordedAt = 1000L + i,
                )
            )
        }
        fakeRepository.intakes.add(
            SupplementIntake(
                id = 5L,
                supplementId = 1L,
                scheduledDate = today.minusDays(5),
                scheduledTime = LocalTime.of(8, 0),
                status = IntakeStatus.SKIPPED,
                recordedAt = 1005L,
            )
        )

        viewModel.loadSupplements()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.activeSupplements.size)
        assertEquals("Vitamina D3", state.activeSupplements.first().name)
        assertTrue(state.inactiveSupplements.isEmpty())

        val adherence = checkNotNull(state.adherence)
        assertTrue(adherence.totalScheduledDosesCount > 0)
        assertEquals(4, adherence.totalTakenCount)
        assertEquals(1, adherence.totalSkippedCount)
    }

    @Test
    fun toggleActiveMovesSupplementToInactiveWithoutDeletingHistory() = runTest(testDispatcher) {
        val today = LocalDate.now()
        val supplement = Supplement(
            id = 2L,
            name = "Creatina",
            doseAmount = 5.0,
            doseUnit = "g",
            scheduleType = ScheduleType.DAILY,
            startDate = today.minusDays(2),
            times = listOf(LocalTime.of(9, 0)),
            active = true,
            remindersEnabled = true,
        )
        fakeRepository.supplements.add(supplement)

        viewModel.loadSupplements()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.activeSupplements.size)
        val activeSupplement = viewModel.uiState.value.activeSupplements.first()

        // Deactivate
        viewModel.onToggleActive(activeSupplement)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.activeSupplements.isEmpty())
        assertEquals(1, state.inactiveSupplements.size)
        assertEquals("Creatina", state.inactiveSupplements.first().name)
        assertFalse(state.inactiveSupplements.first().active)
    }

    @Test
    fun addAndEditSupplement() = runTest(testDispatcher) {
        viewModel.loadSupplements()
        advanceUntilIdle()

        // 1. Add supplement
        viewModel.onAddSupplementClick()
        viewModel.onNameChanged("Magnesio")
        viewModel.onDoseAmountChanged("400")
        viewModel.onDoseUnitChanged("mg")
        viewModel.onScheduleTypeChanged(ScheduleType.DAILY)
        viewModel.onNotesChanged("Antes de dormir")
        viewModel.onSaveSupplement()
        advanceUntilIdle()

        val stateAfterAdd = viewModel.uiState.value
        assertEquals(1, stateAfterAdd.activeSupplements.size)
        val added = stateAfterAdd.activeSupplements.first()
        assertEquals("Magnesio", added.name)
        assertEquals(400.0, added.doseAmount, 0.001)

        // 2. Edit supplement
        viewModel.onEditSupplementClick(added)
        viewModel.onNameChanged("Magnesio Bisglicinato")
        viewModel.onDoseAmountChanged("500")
        viewModel.onAddTime(LocalTime.of(22, 30))
        viewModel.onRemoveTime(LocalTime.of(8, 0))
        viewModel.onToggleReminders(false)
        viewModel.onNotesChanged("Cambiado a bisglicinato")
        viewModel.onSaveSupplement()
        advanceUntilIdle()

        val stateAfterEdit = viewModel.uiState.value
        assertEquals(1, stateAfterEdit.activeSupplements.size)
        val edited = stateAfterEdit.activeSupplements.first()
        assertEquals("Magnesio Bisglicinato", edited.name)
        assertEquals(500.0, edited.doseAmount, 0.001)
        assertEquals(listOf(LocalTime.of(22, 30)), edited.times)
        assertFalse(edited.remindersEnabled)
    }
}
