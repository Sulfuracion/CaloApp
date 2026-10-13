package com.controlcal.app.ui.debug

import android.app.Application
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.concurrent.Executors

sealed interface OcrCollectorUiState {
    data class Ready(
        val savedCount: Int,
        val nextBaseName: String,
        val isTorchOn: Boolean = false,
        val isTorchAvailable: Boolean = false
    ) : OcrCollectorUiState

    data class Capturing(
        val baseName: String,
        val savedCount: Int
    ) : OcrCollectorUiState

    data class CapturedSuccess(
        val baseName: String,
        val photoFile: File,
        val jsonFile: File,
        val linesCount: Int,
        val savedCount: Int,
        val directoryPath: String,
        val adbCommand: String
    ) : OcrCollectorUiState

    data class Error(
        val message: String,
        val savedCount: Int
    ) : OcrCollectorUiState
}

class OcrCollectorViewModel(application: Application) : AndroidViewModel(application) {

    private val storageHelper = OcrLabelStorageHelper(application)
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val _uiState = MutableStateFlow<OcrCollectorUiState>(createReadyState())
    val uiState: StateFlow<OcrCollectorUiState> = _uiState.asStateFlow()

    private fun createReadyState(
        isTorchOn: Boolean = false,
        isTorchAvailable: Boolean = false
    ): OcrCollectorUiState.Ready {
        val count = storageHelper.countSavedLabels()
        val nextName = storageHelper.getNextBaseName()
        return OcrCollectorUiState.Ready(
            savedCount = count,
            nextBaseName = nextName,
            isTorchOn = isTorchOn,
            isTorchAvailable = isTorchAvailable
        )
    }

    fun onTorchAvailabilityChanged(available: Boolean) {
        _uiState.update { current ->
            if (current is OcrCollectorUiState.Ready) {
                current.copy(isTorchAvailable = available)
            } else {
                current
            }
        }
    }

    fun onToggleTorch() {
        _uiState.update { current ->
            if (current is OcrCollectorUiState.Ready) {
                current.copy(isTorchOn = !current.isTorchOn)
            } else {
                current
            }
        }
    }

    fun onCaptureRequested(cameraController: OcrCameraController) {
        val currentState = _uiState.value
        val (baseName, count) = when (currentState) {
            is OcrCollectorUiState.Ready -> currentState.nextBaseName to currentState.savedCount
            else -> storageHelper.getNextBaseName() to storageHelper.countSavedLabels()
        }

        _uiState.value = OcrCollectorUiState.Capturing(baseName = baseName, savedCount = count)

        val targetPhotoFile = storageHelper.getPhotoTargetFile(baseName)

        cameraController.takePicture(
            outputFile = targetPhotoFile,
            executor = cameraExecutor,
            onSuccess = { savedFile ->
                processCapturedImage(savedFile, baseName)
            },
            onError = { error ->
                _uiState.value = OcrCollectorUiState.Error(
                    message = error.localizedMessage ?: "Error al capturar la imagen",
                    savedCount = storageHelper.countSavedLabels()
                )
            }
        )
    }

    private fun processCapturedImage(photoFile: File, baseName: String) {
        viewModelScope.launch {
            try {
                val (imageWidth, imageHeight) = withContext(Dispatchers.IO) {
                    getImageDimensionsUpright(photoFile)
                }

                val inputImage = withContext(Dispatchers.IO) {
                    InputImage.fromFilePath(getApplication(), Uri.fromFile(photoFile))
                }

                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        viewModelScope.launch(Dispatchers.IO) {
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

                            val metadata = OcrLabelMetadata(
                                imageFileName = photoFile.name,
                                timestamp = Instant.now().toString(),
                                imageWidth = imageWidth,
                                imageHeight = imageHeight,
                                lines = lines
                            )

                            val jsonFile = storageHelper.saveJsonMetadata(baseName, metadata)
                            val newCount = storageHelper.countSavedLabels()

                            _uiState.value = OcrCollectorUiState.CapturedSuccess(
                                baseName = baseName,
                                photoFile = photoFile,
                                jsonFile = jsonFile,
                                linesCount = lines.size,
                                savedCount = newCount,
                                directoryPath = storageHelper.getLabelsDirectory().absolutePath,
                                adbCommand = storageHelper.getAdbPullCommand()
                            )
                        }
                    }
                    .addOnFailureListener { error ->
                        _uiState.value = OcrCollectorUiState.Error(
                            message = error.localizedMessage ?: "Error en el reconocimiento de texto",
                            savedCount = storageHelper.countSavedLabels()
                        )
                    }
            } catch (t: Throwable) {
                _uiState.value = OcrCollectorUiState.Error(
                    message = t.localizedMessage ?: "Error al procesar la captura",
                    savedCount = storageHelper.countSavedLabels()
                )
            }
        }
    }

    private fun getImageDimensionsUpright(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        var width = options.outWidth
        var height = options.outHeight

        try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE
            ) {
                val temp = width
                width = height
                height = temp
            }
        } catch (_: Exception) {
            // Keep bounds from BitmapFactory if EXIF read fails
        }
        return width to height
    }

    fun onNextCaptured() {
        val isTorchOn = (_uiState.value as? OcrCollectorUiState.Ready)?.isTorchOn ?: false
        val isTorchAvail = (_uiState.value as? OcrCollectorUiState.Ready)?.isTorchAvailable ?: false
        _uiState.value = createReadyState(isTorchOn, isTorchAvail)
    }

    fun onDiscardCaptured(baseName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            storageHelper.deleteLabel(baseName)
            _uiState.value = createReadyState()
        }
    }

    fun onRetryReady() {
        _uiState.value = createReadyState()
    }

    override fun onCleared() {
        super.onCleared()
        cameraExecutor.shutdown()
        recognizer.close()
    }
}
