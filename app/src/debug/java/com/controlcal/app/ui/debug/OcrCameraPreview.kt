package com.controlcal.app.ui.debug

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class OcrCameraController {
    internal var imageCapture: ImageCapture? = null

    fun takePicture(
        outputFile: File,
        executor: Executor,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val capture = imageCapture ?: run {
            onError(IllegalStateException("Cámara no lista para capturar"))
            return
        }

        val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        capture.takePicture(
            options,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onSuccess(outputFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            }
        )
    }
}

@Composable
fun OcrCameraPreview(
    controller: OcrCameraController,
    isTorchOn: Boolean,
    onTorchAvailabilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    onCameraError: (Throwable) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var camera by remember { mutableStateOf<Camera?>(null) }
    var activeProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(camera, isTorchOn) {
        val currentCamera = camera ?: return@LaunchedEffect
        if (currentCamera.cameraInfo.hasFlashUnit()) {
            try {
                currentCamera.cameraControl.enableTorch(isTorchOn)
            } catch (_: Throwable) {
                // Ignore torch error during state changes
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                activeProvider?.unbindAll()
            } catch (_: Throwable) {
                // Ignore error during unbind
            }
            activeProvider = null
            camera = null
            controller.imageCapture = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener(
                {
                    try {
                        val provider = cameraProviderFuture.get()
                        activeProvider = provider

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()

                        provider.unbindAll()
                        val boundCamera = provider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )

                        camera = boundCamera
                        controller.imageCapture = imageCapture
                        onTorchAvailabilityChanged(boundCamera.cameraInfo.hasFlashUnit())
                    } catch (t: Throwable) {
                        onCameraError(t)
                    }
                },
                ContextCompat.getMainExecutor(ctx)
            )

            previewView
        }
    )
}
