package com.controlcal.app.ui.scanner

import com.controlcal.app.data.off.OffClient
import com.controlcal.app.data.off.OffProduct
import com.controlcal.app.data.off.OffSearchResult
import com.controlcal.core.model.Meal
import com.controlcal.core.model.MealEntry
import com.controlcal.core.model.Product
import com.controlcal.core.model.ProductSource
import com.controlcal.core.repo.MealEntryRepository
import com.controlcal.core.repo.ProductRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeProductRepository: FakeProductRepository
    private lateinit var fakeOffClient: FakeOffClient
    private lateinit var fakeMealEntryRepository: FakeMealEntryRepository
    private lateinit var viewModel: ScannerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeProductRepository = FakeProductRepository()
        fakeOffClient = FakeOffClient()
        fakeMealEntryRepository = FakeMealEntryRepository()
        viewModel = ScannerViewModel(
            productRepository = fakeProductRepository,
            offClient = fakeOffClient,
            mealEntryRepository = fakeMealEntryRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isCheckingWithTorchOff() {
        val state = viewModel.uiState.value
        assertEquals(CameraPermissionState.CHECKING, state.permissionState)
        assertFalse(state.isTorchOn)
        assertFalse(state.isTorchAvailable)
        assertFalse(state.showRationaleDialog)
        assertNull(state.scannedBarcode)
        assertEquals(BarcodeLookupState.Idle, state.lookupState)
    }

    @Test
    fun onPermissionChecked_whenGranted_setsGrantedState() {
        viewModel.onPermissionChecked(isGranted = true)

        val state = viewModel.uiState.value
        assertEquals(CameraPermissionState.GRANTED, state.permissionState)
        assertFalse(state.showRationaleDialog)
    }

    @Test
    fun onPermissionChecked_whenNotGranted_showsRationale() {
        viewModel.onPermissionChecked(isGranted = false)

        val state = viewModel.uiState.value
        assertEquals(CameraPermissionState.RATIONALE, state.permissionState)
        assertTrue(state.showRationaleDialog)
    }

    @Test
    fun onRationaleDismissed_setsDeniedState() {
        viewModel.onPermissionChecked(isGranted = false)
        viewModel.onRationaleDismissed()

        val state = viewModel.uiState.value
        assertEquals(CameraPermissionState.DENIED, state.permissionState)
        assertFalse(state.showRationaleDialog)
    }

    @Test
    fun onRationaleAccepted_dismissesDialog() {
        viewModel.onPermissionChecked(isGranted = false)
        viewModel.onRationaleAccepted()

        val state = viewModel.uiState.value
        assertFalse(state.showRationaleDialog)
    }

    @Test
    fun onPermissionResult_whenGranted_setsGranted() {
        viewModel.onPermissionResult(isGranted = true)

        val state = viewModel.uiState.value
        assertEquals(CameraPermissionState.GRANTED, state.permissionState)
        assertFalse(state.showRationaleDialog)
    }

    @Test
    fun onPermissionResult_whenDenied_setsDeniedAndTurnsOffTorch() {
        viewModel.onTorchAvailabilityChanged(true)
        viewModel.onTorchToggled()
        assertTrue(viewModel.uiState.value.isTorchOn)

        viewModel.onPermissionResult(isGranted = false)

        val state = viewModel.uiState.value
        assertEquals(CameraPermissionState.DENIED, state.permissionState)
        assertFalse(state.isTorchOn)
        assertFalse(state.showRationaleDialog)
    }

    @Test
    fun torchToggling_onlyWorksWhenTorchAvailable() {
        assertFalse(viewModel.uiState.value.isTorchAvailable)
        viewModel.onTorchToggled()
        assertFalse(viewModel.uiState.value.isTorchOn)

        viewModel.onTorchAvailabilityChanged(true)
        assertTrue(viewModel.uiState.value.isTorchAvailable)

        viewModel.onTorchToggled()
        assertTrue(viewModel.uiState.value.isTorchOn)

        viewModel.onTorchToggled()
        assertFalse(viewModel.uiState.value.isTorchOn)
    }

    @Test
    fun torchTurnsOff_whenTorchBecomesUnavailable() {
        viewModel.onTorchAvailabilityChanged(true)
        viewModel.onTorchToggled()
        assertTrue(viewModel.uiState.value.isTorchOn)

        viewModel.onTorchAvailabilityChanged(false)
        assertFalse(viewModel.uiState.value.isTorchAvailable)
        assertFalse(viewModel.uiState.value.isTorchOn)
    }

    @Test
    fun onRetryPermission_reopensRationale() {
        viewModel.onPermissionResult(isGranted = false)
        assertEquals(CameraPermissionState.DENIED, viewModel.uiState.value.permissionState)

        viewModel.onRetryPermission()
        val state = viewModel.uiState.value
        assertEquals(CameraPermissionState.RATIONALE, state.permissionState)
        assertTrue(state.showRationaleDialog)
    }

    @Test
    fun onBarcodeScanned_whenProductExistsLocally_returnsLocalWithoutNetwork() = runTest {
        val existing = Product(
            id = 42L,
            barcode = "8410000000001",
            name = "Leche entera",
            kcal100g = 63.0,
            protein100g = 3.1,
            carbs100g = 4.8,
            fat100g = 3.6,
            source = ProductSource.OFF
        )
        fakeProductRepository.products.add(existing)

        viewModel.onBarcodeScanned("8410000000001")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("8410000000001", state.scannedBarcode)
        assertTrue(state.lookupState is BarcodeLookupState.LocalProductFound)
        val found = (state.lookupState as BarcodeLookupState.LocalProductFound).product
        assertEquals(42L, found.id)
        assertEquals("Leche entera", found.name)
        assertFalse(fakeOffClient.wasCalled)
    }

    @Test
    fun onBarcodeScanned_whenNotInLocalDb_andOffSucceeds_returnsOffProductFound() = runTest {
        val offProd = OffProduct(
            barcode = "8410000000002",
            name = "Copos de avena",
            brand = "Marca",
            kcal100g = 370.0,
            protein100g = 13.5,
            carbs100g = 60.0,
            fat100g = 7.0
        )
        fakeOffClient.resultToReturn = OffSearchResult.Success(offProd)

        viewModel.onBarcodeScanned("8410000000002")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.lookupState is BarcodeLookupState.OffProductFound)
        val found = (state.lookupState as BarcodeLookupState.OffProductFound).offProduct
        assertEquals("Copos de avena", found.name)
        assertTrue(fakeOffClient.wasCalled)
    }

    @Test
    fun onBarcodeScanned_whenNotInLocalDb_andOffOffline_returnsOfflineState() = runTest {
        fakeOffClient.resultToReturn = OffSearchResult.Offline

        viewModel.onBarcodeScanned("8410000000003")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.lookupState is BarcodeLookupState.Offline)
        assertEquals("8410000000003", (state.lookupState as BarcodeLookupState.Offline).barcode)
    }

    @Test
    fun onBarcodeScanned_whenNotInLocalDb_andOffNotFound_returnsNotFoundState() = runTest {
        fakeOffClient.resultToReturn = OffSearchResult.NotFound

        viewModel.onBarcodeScanned("8410000000004")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.lookupState is BarcodeLookupState.NotFound)
        assertEquals("8410000000004", (state.lookupState as BarcodeLookupState.NotFound).barcode)
    }

    @Test
    fun onBarcodeScanned_whenNotInLocalDb_andOffError_returnsErrorState() = runTest {
        fakeOffClient.resultToReturn = OffSearchResult.Error("Error HTTP 500")

        viewModel.onBarcodeScanned("8410000000005")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.lookupState is BarcodeLookupState.Error)
        assertEquals("Error HTTP 500", (state.lookupState as BarcodeLookupState.Error).message)
    }

    @Test
    fun onAddLocalProductToDiary_insertsMealEntryAndResetsState() = runTest {
        val existing = Product(
            id = 10L,
            barcode = "8410000000001",
            name = "Avena",
            kcal100g = 370.0,
            protein100g = 13.5,
            carbs100g = 60.0,
            fat100g = 7.0
        )
        fakeProductRepository.products.add(existing)

        viewModel.onAddLocalProductToDiary(existing, Meal.BREAKFAST, 50.0)
        advanceUntilIdle()

        assertEquals(1, fakeMealEntryRepository.entries.size)
        val entry = fakeMealEntryRepository.entries.first()
        assertEquals(10L, entry.productId)
        assertEquals("Avena", entry.productName)
        assertEquals(Meal.BREAKFAST, entry.meal)
        assertEquals(50.0, entry.grams, 0.001)
        assertEquals(185.0, entry.kcal, 0.001) // 370 * 0.5

        val state = viewModel.uiState.value
        assertNull(state.scannedBarcode)
        assertEquals(BarcodeLookupState.Idle, state.lookupState)
    }

    @Test
    fun onScanAgain_clearsState() = runTest {
        viewModel.onBarcodeScanned("8410000000001")
        advanceUntilIdle()

        viewModel.onScanAgain()
        val state = viewModel.uiState.value
        assertNull(state.scannedBarcode)
        assertEquals(BarcodeLookupState.Idle, state.lookupState)
    }
}

private class FakeProductRepository : ProductRepository {
    val products = mutableListOf<Product>()

    override suspend fun getById(id: Long): Product? = products.find { it.id == id }
    override suspend fun getByBarcode(barcode: String): Product? = products.find { it.barcode == barcode }
    override suspend fun searchByName(prefix: String): List<Product> = products.filter { it.name.startsWith(prefix, ignoreCase = true) }
    override suspend fun getActiveProducts(): List<Product> = products.filter { !it.archived }
    override suspend fun insert(product: Product): Long {
        val id = if (product.id == 0L) (products.size + 1).toLong() else product.id
        products.add(product.copy(id = id))
        return id
    }
    override suspend fun update(product: Product) {
        val idx = products.indexOfFirst { it.id == product.id }
        if (idx >= 0) products[idx] = product
    }
    override suspend fun archive(id: Long) {
        val idx = products.indexOfFirst { it.id == id }
        if (idx >= 0) products[idx] = products[idx].copy(archived = true)
    }
    override suspend fun hasEntries(productId: Long): Boolean = false
}

private class FakeOffClient : OffClient {
    var resultToReturn: OffSearchResult = OffSearchResult.NotFound
    var wasCalled = false

    override suspend fun getProductByBarcode(barcode: String): OffSearchResult {
        wasCalled = true
        return resultToReturn
    }
}

private class FakeMealEntryRepository : MealEntryRepository {
    val entries = mutableListOf<MealEntry>()

    override suspend fun insert(entry: MealEntry): Long {
        val id = if (entry.id == 0L) (entries.size + 1).toLong() else entry.id
        entries.add(entry.copy(id = id))
        return id
    }
    override suspend fun update(entry: MealEntry) {}
    override suspend fun delete(entry: MealEntry) {}
    override suspend fun getByDate(date: String): List<MealEntry> = entries.filter { it.date == date }
    override suspend fun getByDateAndMeal(date: String, meal: Meal): List<MealEntry> = entries.filter { it.date == date && it.meal == meal }
    override suspend fun getByProduct(productId: Long): List<MealEntry> = entries.filter { it.productId == productId }
}
