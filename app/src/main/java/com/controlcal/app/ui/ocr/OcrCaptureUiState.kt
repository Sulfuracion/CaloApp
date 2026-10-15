package com.controlcal.app.ui.ocr

import com.controlcal.app.ui.scanner.CameraPermissionState
import com.controlcal.core.ocr.model.OcrParsedNutrients
import java.io.File

sealed interface OcrProcessingState {
    data object Idle : OcrProcessingState
    data object Capturing : OcrProcessingState
    data object Processing : OcrProcessingState
    data class Success(
        val photoFile: File,
        val parsedNutrients: OcrParsedNutrients,
        val barcode: String? = null
    ) : OcrProcessingState
    data class NoDataDetected(
        val photoFile: File,
        val barcode: String? = null
    ) : OcrProcessingState
    data class Error(val message: String) : OcrProcessingState
}

data class OcrCaptureUiState(
    val permissionState: CameraPermissionState = CameraPermissionState.CHECKING,
    val showRationaleDialog: Boolean = false,
    val isTorchOn: Boolean = false,
    val isTorchAvailable: Boolean = false,
    val targetBarcode: String? = null,
    val processingState: OcrProcessingState = OcrProcessingState.Idle
)
