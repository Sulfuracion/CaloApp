package com.controlcal.app.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * CameraX ImageAnalysis.Analyzer that detects 1D barcodes (EAN-13, EAN-8, UPC-A, UPC-E)
 * using the bundled ML Kit model and enforces consecutive identical readings.
 */
class BarcodeAnalyzer(
    private val gate: BarcodeConsecutiveGate = BarcodeConsecutiveGate(),
    private val onBarcodeAccepted: (String) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {

    private val scanner: BarcodeScanner = run {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    @Volatile
    var isPaused: Boolean = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isPaused) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        try {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (!isPaused) {
                        val matching = barcodes.firstOrNull { barcode ->
                            !barcode.rawValue.isNullOrBlank()
                        }
                        if (matching != null) {
                            val raw = matching.rawValue!!
                            if (gate.onBarcodeRead(raw)) {
                                isPaused = true
                                onBarcodeAccepted(raw)
                            }
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } catch (_: Throwable) {
            imageProxy.close()
        }
    }

    /**
     * Clears candidate history and unpauses scanning.
     */
    fun resetGate() {
        gate.reset()
        isPaused = false
    }

    override fun close() {
        try {
            scanner.close()
        } catch (_: Throwable) {
            // Ignore error on close
        }
    }
}
