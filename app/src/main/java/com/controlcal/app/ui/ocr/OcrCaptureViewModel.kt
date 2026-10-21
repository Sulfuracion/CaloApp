package com.controlcal.app.ui.ocr

import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controlcal.app.ui.scanner.CameraPermissionState
import com.controlcal.core.ocr.NutritionLabelParser
import com.controlcal.core.ocr.model.OcrBox
import com.controlcal.core.ocr.model.OcrLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

@HiltViewModel
class OcrCaptureViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(OcrCaptureUiState())
    val uiState: StateFlow<OcrCaptureUiState> = _uiState.asStateFlow()

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private var _cameraExecutor: ExecutorService? = null
    internal var cameraExecutor: Executor
        get() {
            val current = _cameraExecutor
            if (current != null && !current.isShutdown) return current
            return Executors.newSingleThreadExecutor().also { _cameraExecutor = it }
        }
        set(value) {
            _cameraExecutor?.shutdown()
            _cameraExecutor = value as? ExecutorService
        }

    private var _recognizer: TextRecognizer? = null

    internal fun getRecognizer(): TextRecognizer {
        return _recognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also {
            _recognizer = it
        }
    }

    internal fun setRecognizer(recognizer: TextRecognizer) {
        _recognizer = recognizer
    }

    fun releaseResources() {
        try {
            _recognizer?.close()
        } catch (_: Exception) {}
        _recognizer = null
        _cameraExecutor?.shutdown()
        _cameraExecutor = null
    }

    fun setTargetBarcode(barcode: String?) {
        _uiState.update { it.copy(targetBarcode = barcode) }
    }

    fun onPermissionChecked(isGranted: Boolean) {
        _uiState.update { current ->
            if (isGranted) {
                current.copy(permissionState = CameraPermissionState.GRANTED)
            } else if (current.permissionState == CameraPermissionState.CHECKING) {
                current.copy(
                    permissionState = CameraPermissionState.RATIONALE,
                    showRationaleDialog = true
                )
            } else {
                current
            }
        }
    }

    fun onPermissionResult(isGranted: Boolean) {
        _uiState.update { current ->
            if (isGranted) {
                current.copy(
                    permissionState = CameraPermissionState.GRANTED,
                    showRationaleDialog = false
                )
            } else {
                current.copy(
                    permissionState = CameraPermissionState.DENIED,
                    showRationaleDialog = false
                )
            }
        }
    }

    fun onRationaleAccepted() {
        _uiState.update { it.copy(showRationaleDialog = false) }
    }

    fun onRationaleDismissed() {
        _uiState.update {
            it.copy(
                showRationaleDialog = false,
                permissionState = CameraPermissionState.DENIED
            )
        }
    }

    fun onTorchToggled() {
        _uiState.update { it.copy(isTorchOn = !it.isTorchOn) }
    }

    fun onTorchAvailabilityChanged(available: Boolean) {
        _uiState.update { it.copy(isTorchAvailable = available) }
    }

    private fun deleteFileOffMainThread(file: File) {
        val isAndroidMainThread = try {
            android.os.Looper.getMainLooper()?.thread == Thread.currentThread()
        } catch (_: Throwable) {
            false
        }
        if (isAndroidMainThread) {
            viewModelScope.launch(ioDispatcher) {
                if (file.exists()) file.delete()
            }
        } else {
            if (file.exists()) file.delete()
        }
    }

    fun onRetryCapture(photoFileToDelete: File? = null) {
        photoFileToDelete?.let { file ->
            deleteFileOffMainThread(file)
        }
        _uiState.update { it.copy(processingState = OcrProcessingState.Idle) }
    }

    fun onCancelCapture() {
        val currentProcessing = _uiState.value.processingState
        if (currentProcessing is OcrProcessingState.NoDataDetected) {
            deleteFileOffMainThread(currentProcessing.photoFile)
        }
        releaseResources()
        _uiState.update { it.copy(processingState = OcrProcessingState.Idle) }
    }

    fun onDismissError() {
        _uiState.update { it.copy(processingState = OcrProcessingState.Idle) }
    }

    fun takePhoto(imageCapture: ImageCapture, context: Context) {
        val targetFile = File(context.cacheDir, "ocr_temp_${System.currentTimeMillis()}.jpg")
        _uiState.update { it.copy(processingState = OcrProcessingState.Capturing) }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(targetFile).build()
        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    processImageFile(targetFile, context)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    _uiState.update {
                        it.copy(
                            processingState = OcrProcessingState.Error(
                                exception.localizedMessage ?: "Error al tomar la fotografía"
                            )
                        )
                    }
                }
            }
        )
    }

    internal fun processImageFile(photoFile: File, context: Context) {
        _uiState.update { it.copy(processingState = OcrProcessingState.Processing) }

        viewModelScope.launch {
            try {
                val inputImage = withContext(ioDispatcher) {
                    InputImage.fromFilePath(context, Uri.fromFile(photoFile))
                }

                getRecognizer().process(inputImage)
                    .addOnSuccessListener { visionText ->
                        viewModelScope.launch(ioDispatcher) {
                            val lines = visionText.textBlocks.flatMap { block ->
                                block.lines.map { line ->
                                    val rect = line.boundingBox ?: android.graphics.Rect(0, 0, 0, 0)
                                    OcrLine(
                                        text = line.text,
                                        box = OcrBox(
                                            left = rect.left,
                                            top = rect.top,
                                            right = rect.right,
                                            bottom = rect.bottom
                                        )
                                    )
                                }
                            }

                            val parsedNutrients = NutritionLabelParser.parse(lines)
                            val barcode = _uiState.value.targetBarcode

                            _uiState.update { current ->
                                if (parsedNutrients != null) {
                                    current.copy(
                                        processingState = OcrProcessingState.Success(
                                            photoFile = photoFile,
                                            parsedNutrients = parsedNutrients,
                                            barcode = barcode
                                        )
                                    )
                                } else {
                                    current.copy(
                                        processingState = OcrProcessingState.NoDataDetected(
                                            photoFile = photoFile,
                                            barcode = barcode
                                        )
                                    )
                                }
                            }
                        }
                    }
                    .addOnFailureListener { error ->
                        deleteFileOffMainThread(photoFile)
                        _uiState.update {
                            it.copy(
                                processingState = OcrProcessingState.Error(
                                    error.localizedMessage ?: "Error en el reconocimiento de texto"
                                )
                            )
                        }
                    }
            } catch (e: Exception) {
                deleteFileOffMainThread(photoFile)
                _uiState.update {
                    it.copy(
                        processingState = OcrProcessingState.Error(
                            e.localizedMessage ?: "Error al procesar la imagen"
                        )
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        releaseResources()
    }

    companion object {
        fun cleanupOrphanedTempPhotos(cacheDir: File) {
            cacheDir.listFiles { _, name ->
                name.startsWith("ocr_temp_") && name.endsWith(".jpg")
            }?.forEach { file ->
                file.delete()
            }
        }
    }
}
