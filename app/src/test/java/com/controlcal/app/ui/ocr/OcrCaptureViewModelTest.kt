package com.controlcal.app.ui.ocr

import com.controlcal.app.ui.scanner.CameraPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class OcrCaptureViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: OcrCaptureViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = OcrCaptureViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_defaultsToCheckingAndIdle() {
        val state = viewModel.uiState.value
        assertEquals(CameraPermissionState.CHECKING, state.permissionState)
        assertFalse(state.showRationaleDialog)
        assertFalse(state.isTorchOn)
        assertFalse(state.isTorchAvailable)
        assertNull(state.targetBarcode)
        assertTrue(state.processingState is OcrProcessingState.Idle)
    }

    @Test
    fun permissionChecked_whenFalse_showsRationale() {
        viewModel.onPermissionChecked(false)
        val state = viewModel.uiState.value
        assertEquals(CameraPermissionState.RATIONALE, state.permissionState)
        assertTrue(state.showRationaleDialog)
    }

    @Test
    fun permissionChecked_whenTrue_setsGranted() {
        viewModel.onPermissionChecked(true)
        val state = viewModel.uiState.value
        assertEquals(CameraPermissionState.GRANTED, state.permissionState)
        assertFalse(state.showRationaleDialog)
    }

    @Test
    fun permissionResult_updatesStateCorrectly() {
        viewModel.onPermissionResult(false)
        assertEquals(CameraPermissionState.DENIED, viewModel.uiState.value.permissionState)

        viewModel.onPermissionResult(true)
        assertEquals(CameraPermissionState.GRANTED, viewModel.uiState.value.permissionState)
    }

    @Test
    fun rationaleAcceptedAndDismissed() {
        viewModel.onPermissionChecked(false)
        assertTrue(viewModel.uiState.value.showRationaleDialog)

        viewModel.onRationaleAccepted()
        assertFalse(viewModel.uiState.value.showRationaleDialog)

        viewModel.onRationaleDismissed()
        assertFalse(viewModel.uiState.value.showRationaleDialog)
        assertEquals(CameraPermissionState.DENIED, viewModel.uiState.value.permissionState)
    }

    @Test
    fun torchTogglingAndAvailability() {
        viewModel.onTorchAvailabilityChanged(true)
        assertTrue(viewModel.uiState.value.isTorchAvailable)

        viewModel.onTorchToggled()
        assertTrue(viewModel.uiState.value.isTorchOn)

        viewModel.onTorchToggled()
        assertFalse(viewModel.uiState.value.isTorchOn)
    }

    @Test
    fun setTargetBarcode_updatesState() {
        viewModel.setTargetBarcode("8412345678901")
        assertEquals("8412345678901", viewModel.uiState.value.targetBarcode)
    }

    @Test
    fun onRetryCapture_deletesPhotoFileAndResetsState() {
        val tempFile = File.createTempFile("test_retry", ".jpg")
        assertTrue(tempFile.exists())

        viewModel.onRetryCapture(tempFile)
        assertFalse(tempFile.exists())
        assertTrue(viewModel.uiState.value.processingState is OcrProcessingState.Idle)
    }

    @Test
    fun onDismissError_resetsToIdle() {
        viewModel.onDismissError()
        assertTrue(viewModel.uiState.value.processingState is OcrProcessingState.Idle)
    }
}
