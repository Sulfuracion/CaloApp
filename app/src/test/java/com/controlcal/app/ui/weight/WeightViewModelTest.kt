package com.controlcal.app.ui.weight

import com.controlcal.core.model.WeightEntry
import com.controlcal.core.model.WeeklyDayPattern
import com.controlcal.core.repo.UserSettingsRepository
import com.controlcal.core.repo.WeightEntryRepository
import com.controlcal.core.weight.WeightRange
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WeightViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeWeightEntryRepository
    private lateinit var fakeUserSettingsRepository: FakeUserSettingsRepository
    private lateinit var viewModel: WeightViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeWeightEntryRepository()
        fakeUserSettingsRepository = FakeUserSettingsRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initLoadsEntriesAndCalculatesTrend() = runTest {
        val today = LocalDate.now().toString()
        val yesterday = LocalDate.now().minusDays(1).toString()
        fakeRepository.upsert(WeightEntry(date = yesterday, weightKg = 80.0))
        fakeRepository.upsert(WeightEntry(date = today, weightKg = 81.0))

        viewModel = WeightViewModel(fakeRepository, fakeUserSettingsRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.entries.size)
        // Descending order for entries
        assertEquals(today, state.entries[0].date)
        assertEquals(yesterday, state.entries[1].date)

        // Today weight is present
        assertEquals(81.0, state.todayWeightKg!!, 0.001)

        // Trend is calculated
        assertEquals(2, state.trendPoints.size)
        assertEquals(yesterday, state.trendPoints[0].date)
        assertEquals(80.0, state.trendPoints[0].trendKg, 0.001)
        assertEquals(today, state.trendPoints[1].date)
        // 0.1 * 81.0 + 0.9 * 80.0 = 80.1
        assertEquals(80.1, state.trendPoints[1].trendKg, 0.001)
    }

    @Test
    fun onQuickAddDeltaModifiesInputText() = runTest {
        viewModel = WeightViewModel(fakeRepository, fakeUserSettingsRepository)
        advanceUntilIdle()

        viewModel.onTodayInputChanged("78,0")
        viewModel.onQuickAddDelta(0.5)

        assertEquals("78,5", viewModel.uiState.value.todayInputText)

        viewModel.onQuickAddDelta(-0.1)
        assertEquals("78,4", viewModel.uiState.value.todayInputText)
    }

    @Test
    fun onSaveTodayWeightUpsertsAndUpdatesState() = runTest {
        val today = LocalDate.now().toString()
        viewModel = WeightViewModel(fakeRepository, fakeUserSettingsRepository)
        advanceUntilIdle()

        viewModel.onTodayInputChanged("79,2")
        viewModel.onSaveTodayWeight()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(79.2, state.todayWeightKg!!, 0.001)
        assertNotNull(fakeRepository.getByDate(today))
        assertEquals(79.2, fakeRepository.getByDate(today)!!.weightKg, 0.001)
        assertEquals("Peso guardado correctamente", state.message)
    }

    @Test
    fun onSaveTodayWeightInvalidInputSetsError() = runTest {
        viewModel = WeightViewModel(fakeRepository, fakeUserSettingsRepository)
        advanceUntilIdle()

        viewModel.onTodayInputChanged("abc")
        viewModel.onSaveTodayWeight()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.todayInputError)
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun onRangeSelectedUpdatesSelectedRangeAndChartPoints() = runTest {
        val today = LocalDate.now()
        val d5 = today.minusDays(5).toString()
        val d12 = today.minusDays(12).toString()
        val d25 = today.minusDays(25).toString()
        val d100 = today.minusDays(100).toString()

        fakeRepository.upsert(WeightEntry(date = d100, weightKg = 85.0))
        fakeRepository.upsert(WeightEntry(date = d25, weightKg = 83.0))
        fakeRepository.upsert(WeightEntry(date = d12, weightKg = 81.0))
        fakeRepository.upsert(WeightEntry(date = d5, weightKg = 80.0))

        viewModel = WeightViewModel(fakeRepository, fakeUserSettingsRepository)
        advanceUntilIdle()

        // Default range is 1M (LAST_1_MONTH)
        assertEquals(WeightRange.LAST_1_MONTH, viewModel.uiState.value.selectedRange)
        // In 1M: d25, d12, d5 are included (3 points)
        assertEquals(3, viewModel.uiState.value.chartPoints.size)
        assertNotNull(viewModel.uiState.value.periodSummary)
        assertTrue(viewModel.uiState.value.periodSummary!!.hasSufficientData)

        // Select 7D
        viewModel.onRangeSelected(WeightRange.LAST_7_DAYS)
        advanceUntilIdle()
        assertEquals(WeightRange.LAST_7_DAYS, viewModel.uiState.value.selectedRange)
        assertEquals(1, viewModel.uiState.value.chartPoints.size)
        assertEquals(d5, viewModel.uiState.value.chartPoints[0].date)
        assertEquals(WeightRange.LAST_7_DAYS, fakeUserSettingsRepository.getWeightChartRange())

        // Select 15D
        viewModel.onRangeSelected(WeightRange.LAST_15_DAYS)
        advanceUntilIdle()
        assertEquals(WeightRange.LAST_15_DAYS, viewModel.uiState.value.selectedRange)
        assertEquals(2, viewModel.uiState.value.chartPoints.size)

        // Select ALL
        viewModel.onRangeSelected(WeightRange.ALL)
        advanceUntilIdle()
        assertEquals(WeightRange.ALL, viewModel.uiState.value.selectedRange)
        assertEquals(4, viewModel.uiState.value.chartPoints.size)
        assertEquals(WeightRange.ALL, fakeUserSettingsRepository.getWeightChartRange())
    }

    @Test
    fun onSaveEditEntryUpdatesDateAndWeight() = runTest {
        val originalDate = "2026-10-01"
        fakeRepository.upsert(WeightEntry(date = originalDate, weightKg = 80.0))

        viewModel = WeightViewModel(fakeRepository, fakeUserSettingsRepository)
        advanceUntilIdle()

        val entry = fakeRepository.getByDate(originalDate)!!
        viewModel.onStartEditEntry(entry)
        viewModel.onEditingDateChanged("2026-10-02")
        viewModel.onEditingWeightChanged("80,5")
        viewModel.onSaveEditEntry()
        advanceUntilIdle()

        assertNull(fakeRepository.getByDate("2026-10-01"))
        val updated = fakeRepository.getByDate("2026-10-02")
        assertNotNull(updated)
        assertEquals(80.5, updated!!.weightKg, 0.001)
        assertEquals("Pesaje actualizado", viewModel.uiState.value.message)
    }

    @Test
    fun onConfirmDeleteEntryRemovesWeight() = runTest {
        val date = "2026-10-01"
        fakeRepository.upsert(WeightEntry(date = date, weightKg = 80.0))

        viewModel = WeightViewModel(fakeRepository, fakeUserSettingsRepository)
        advanceUntilIdle()

        val entry = fakeRepository.getByDate(date)!!
        viewModel.onStartDeleteEntry(entry)
        viewModel.onConfirmDeleteEntry()
        advanceUntilIdle()

        assertNull(fakeRepository.getByDate(date))
        assertTrue(viewModel.uiState.value.entries.isEmpty())
        assertEquals("Pesaje eliminado", viewModel.uiState.value.message)
    }

    @Test
    fun onSavePastEntryAddsHistoricalMeasurement() = runTest {
        viewModel = WeightViewModel(fakeRepository, fakeUserSettingsRepository)
        advanceUntilIdle()

        viewModel.onStartAddPastEntry()
        viewModel.onPastEntryDateChanged("2026-09-15")
        viewModel.onPastEntryWeightChanged("82,3")
        viewModel.onSavePastEntry()
        advanceUntilIdle()

        val saved = fakeRepository.getByDate("2026-09-15")
        assertNotNull(saved)
        assertEquals(82.3, saved!!.weightKg, 0.001)
        assertEquals(1, viewModel.uiState.value.entries.size)
    }

    @Test
    fun onSaveEditEntry_whenTargetDateHasExistingEntry_triggersConflictConfirmation() = runTest {
        fakeRepository.upsert(WeightEntry(date = "2026-10-01", weightKg = 80.0))
        fakeRepository.upsert(WeightEntry(date = "2026-10-02", weightKg = 75.0))

        viewModel = WeightViewModel(fakeRepository, fakeUserSettingsRepository)
        advanceUntilIdle()

        val entry = fakeRepository.getByDate("2026-10-01")!!
        viewModel.onStartEditEntry(entry)
        viewModel.onEditingDateChanged("2026-10-02")
        viewModel.onEditingWeightChanged("81,0")
        viewModel.onSaveEditEntry()
        advanceUntilIdle()

        val conflict = viewModel.uiState.value.conflictState
        assertNotNull(conflict)
        assertTrue(conflict!!.isFromEdit)
        assertEquals("2026-10-01", conflict.oldDate)
        assertEquals("2026-10-02", conflict.targetDate)
        assertEquals(81.0, conflict.newWeightKg, 0.001)
        assertEquals(75.0, conflict.existingWeightKg, 0.001)

        // Confirming the conflict moves the entry atomically
        viewModel.onConfirmConflict()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.conflictState)
        assertNull(fakeRepository.getByDate("2026-10-01"))
        val replaced = fakeRepository.getByDate("2026-10-02")
        assertNotNull(replaced)
        assertEquals(81.0, replaced!!.weightKg, 0.001)
    }

    @Test
    fun onDismissConflict_clearsConflictWithoutModifyingEntries() = runTest {
        fakeRepository.upsert(WeightEntry(date = "2026-10-01", weightKg = 80.0))
        fakeRepository.upsert(WeightEntry(date = "2026-10-02", weightKg = 75.0))

        viewModel = WeightViewModel(fakeRepository, fakeUserSettingsRepository)
        advanceUntilIdle()

        val entry = fakeRepository.getByDate("2026-10-01")!!
        viewModel.onStartEditEntry(entry)
        viewModel.onEditingDateChanged("2026-10-02")
        viewModel.onEditingWeightChanged("81,0")
        viewModel.onSaveEditEntry()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.conflictState)
        viewModel.onDismissConflict()

        assertNull(viewModel.uiState.value.conflictState)
        // Both original entries must remain untouched
        assertEquals(80.0, fakeRepository.getByDate("2026-10-01")!!.weightKg, 0.001)
        assertEquals(75.0, fakeRepository.getByDate("2026-10-02")!!.weightKg, 0.001)
    }

    @Test
    fun onSavePastEntry_whenTargetDateHasExistingEntry_triggersConflictConfirmation() = runTest {
        fakeRepository.upsert(WeightEntry(date = "2026-10-05", weightKg = 78.0))

        viewModel = WeightViewModel(fakeRepository, fakeUserSettingsRepository)
        advanceUntilIdle()

        viewModel.onStartAddPastEntry()
        viewModel.onPastEntryDateChanged("2026-10-05")
        viewModel.onPastEntryWeightChanged("79,0")
        viewModel.onSavePastEntry()
        advanceUntilIdle()

        val conflict = viewModel.uiState.value.conflictState
        assertNotNull(conflict)
        assertFalse(conflict!!.isFromEdit)
        assertEquals("2026-10-05", conflict.targetDate)
        assertEquals(79.0, conflict.newWeightKg, 0.001)
        assertEquals(78.0, conflict.existingWeightKg, 0.001)

        // Confirming the conflict replaces the existing entry
        viewModel.onConfirmConflict()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.conflictState)
        val replaced = fakeRepository.getByDate("2026-10-05")
        assertNotNull(replaced)
        assertEquals(79.0, replaced!!.weightKg, 0.001)
    }
}

private class FakeWeightEntryRepository : WeightEntryRepository {
    val entries = mutableMapOf<String, WeightEntry>()

    override suspend fun upsert(entry: WeightEntry) {
        entries[entry.date] = entry
    }

    override suspend fun getByDate(date: String): WeightEntry? = entries[date]

    override suspend fun getBetween(from: String, to: String): List<WeightEntry> =
        entries.values.filter { it.date in from..to }.sortedBy { it.date }

    override suspend fun getAll(): List<WeightEntry> =
        entries.values.sortedBy { it.date }

    override suspend fun deleteByDate(date: String) {
        entries.remove(date)
    }

    override suspend fun moveEntry(oldDate: String, newEntry: WeightEntry) {
        if (oldDate != newEntry.date) {
            entries.remove(oldDate)
        }
        entries[newEntry.date] = newEntry
    }
}

private class FakeUserSettingsRepository : UserSettingsRepository {
    private var range: WeightRange = WeightRange.LAST_1_MONTH
    private var pattern: WeeklyDayPattern = WeeklyDayPattern()

    override suspend fun getWeeklyPattern(): WeeklyDayPattern = pattern
    override suspend fun setWeeklyPattern(pattern: WeeklyDayPattern) {
        this.pattern = pattern
    }

    override suspend fun getMinKcalLimit(): Double = 1200.0
    override suspend fun setMinKcalLimit(minKcal: Double) {}

    override suspend fun getLastTdeeProposalReviewedDate(): String? = null
    override suspend fun setLastTdeeProposalReviewedDate(date: String) {}

    override suspend fun getWeightChartRange(): WeightRange = range
    override suspend fun setWeightChartRange(range: WeightRange) {
        this.range = range
    }
}
