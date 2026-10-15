package com.controlcal.app.camera

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
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
import java.util.concurrent.Executors

/**
 * Camera preview composable using CameraX with optional image analysis and capture.
 *
 * Binds the CameraX preview, analysis and capture lifecycles to the current LifecycleOwner,
 * controls the flashlight/torch state, and unbinds the camera completely
 * when leaving the composition.
 */
@Composable
fun CameraPreview(
    isTorchOn: Boolean,
    onTorchAvailabilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    imageAnalyzer: ImageAnalysis.Analyzer? = null,
    imageCapture: ImageCapture? = null,
    onCameraError: (Throwable) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var activeProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(camera, isTorchOn) {
        val currentCamera = camera ?: return@LaunchedEffect
        if (currentCamera.cameraInfo.hasFlashUnit()) {
            try {
                currentCamera.cameraControl.enableTorch(isTorchOn)
            } catch (_: Throwable) {
                // Ignore torch toggle error during transition/shutdown
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                activeProvider?.unbindAll()
            } catch (_: Throwable) {
                // Ignore errors during provider unbind
            }
            activeProvider = null
            camera = null
            analysisExecutor.shutdown()
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

                        val analysisUseCase = imageAnalyzer?.let { analyzer ->
                            ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build().also { useCase ->
                                    useCase.setAnalyzer(analysisExecutor, analyzer)
                                }
                        }

                        val useCases = mutableListOf<UseCase>(preview)
                        if (analysisUseCase != null) {
                            useCases.add(analysisUseCase)
                        }
                        if (imageCapture != null) {
                            useCases.add(imageCapture)
                        }

                        provider.unbindAll()
                        val boundCamera = provider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            *useCases.toTypedArray()
                        )
                        camera = boundCamera
                        val hasFlash = boundCamera.cameraInfo.hasFlashUnit()
                        onTorchAvailabilityChanged(hasFlash)
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
