package com.controlcal.app.ui.scanner

import com.controlcal.app.data.off.OffProduct
import com.controlcal.core.model.Product

enum class CameraPermissionState {
    CHECKING,
    RATIONALE,
    GRANTED,
    DENIED
}

sealed interface BarcodeLookupState {
    data object Idle : BarcodeLookupState
    data class Searching(val barcode: String) : BarcodeLookupState
    data class LocalProductFound(val product: Product) : BarcodeLookupState
    data class OffProductFound(val offProduct: OffProduct) : BarcodeLookupState
    data class Offline(val barcode: String) : BarcodeLookupState
    data class NotFound(val barcode: String) : BarcodeLookupState
    data class Error(val barcode: String, val message: String) : BarcodeLookupState
}

data class ScannerUiState(
    val permissionState: CameraPermissionState = CameraPermissionState.CHECKING,
    val isTorchOn: Boolean = false,
    val isTorchAvailable: Boolean = false,
    val showRationaleDialog: Boolean = false,
    val scannedBarcode: String? = null,
    val lookupState: BarcodeLookupState = BarcodeLookupState.Idle,
)
