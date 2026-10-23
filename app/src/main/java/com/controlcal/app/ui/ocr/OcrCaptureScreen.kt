package com.controlcal.app.ui.ocr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlcal.app.R
import com.controlcal.app.camera.CameraPreview
import com.controlcal.app.ui.scanner.CameraPermissionState
import com.controlcal.core.ocr.model.OcrParsedNutrients
import java.io.File

@Composable
fun OcrCaptureRoute(
    viewModel: OcrCaptureViewModel,
    targetBarcode: String?,
    onBack: () -> Unit,
    onNavigateToConfirmation: (File, OcrParsedNutrients, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(targetBarcode) {
        viewModel.setTargetBarcode(targetBarcode)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.releaseResources()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onPermissionResult(isGranted)
    }

    LaunchedEffect(Unit) {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionChecked(isGranted)
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    LaunchedEffect(uiState.processingState) {
        val state = uiState.processingState
        if (state is OcrProcessingState.Success) {
            onNavigateToConfirmation(state.photoFile, state.parsedNutrients, state.barcode)
            viewModel.onRetryCapture()
        }
    }

    OcrCaptureScreen(
        uiState = uiState,
        imageCapture = imageCapture,
        onRequestPermission = {
            viewModel.onRationaleAccepted()
            permissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onDismissRationale = viewModel::onRationaleDismissed,
        onRetryPermission = {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onOpenSettings = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        },
        onToggleTorch = viewModel::onTorchToggled,
        onTorchAvailabilityChanged = viewModel::onTorchAvailabilityChanged,
        onCapture = {
            viewModel.takePhoto(imageCapture, context)
        },
        onRetryNoData = { photoFile ->
            viewModel.onRetryCapture(photoFile)
        },
        onContinueManual = { photoFile, barcode ->
            onNavigateToConfirmation(photoFile, OcrParsedNutrients(), barcode)
            viewModel.onRetryCapture()
        },
        onDismissError = viewModel::onDismissError,
        onBack = {
            viewModel.onCancelCapture()
            onBack()
        },
        modifier = modifier
    )
}

@Composable
fun OcrCaptureScreen(
    uiState: OcrCaptureUiState,
    imageCapture: ImageCapture,
    onRequestPermission: () -> Unit,
    onDismissRationale: () -> Unit,
    onRetryPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTorch: () -> Unit,
    onTorchAvailabilityChanged: (Boolean) -> Unit,
    onCapture: () -> Unit,
    onRetryNoData: (File) -> Unit,
    onContinueManual: (File, String?) -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.showRationaleDialog) {
        AlertDialog(
            onDismissRequest = onDismissRationale,
            title = {
                Text(text = stringResource(R.string.ocr_capture_permission_rationale_title))
            },
            text = {
                Text(text = stringResource(R.string.ocr_capture_permission_rationale_message))
            },
            confirmButton = {
                Button(onClick = onRequestPermission) {
                    Text(text = stringResource(R.string.scanner_permission_grant_button))
                }
            },
            dismissButton = {
                TextButton(onClick = onBack) {
                    Text(text = stringResource(R.string.scanner_back))
                }
            }
        )
    }

    when (uiState.permissionState) {
        CameraPermissionState.CHECKING,
        CameraPermissionState.RATIONALE -> {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {}
        }

        CameraPermissionState.DENIED -> {
            OcrPermissionDeniedContent(
                onBack = onBack,
                onRetryPermission = onRetryPermission,
                onOpenSettings = onOpenSettings,
                modifier = modifier
            )
        }

        CameraPermissionState.GRANTED -> {
            OcrCameraContent(
                uiState = uiState,
                imageCapture = imageCapture,
                onBack = onBack,
                onToggleTorch = onToggleTorch,
                onTorchAvailabilityChanged = onTorchAvailabilityChanged,
                onCapture = onCapture,
                onRetryNoData = onRetryNoData,
                onContinueManual = onContinueManual,
                onDismissError = onDismissError,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun OcrCameraContent(
    uiState: OcrCaptureUiState,
    imageCapture: ImageCapture,
    onBack: () -> Unit,
    onToggleTorch: () -> Unit,
    onTorchAvailabilityChanged: (Boolean) -> Unit,
    onCapture: () -> Unit,
    onRetryNoData: (File) -> Unit,
    onContinueManual: (File, String?) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBusy = uiState.processingState is OcrProcessingState.Capturing ||
        uiState.processingState is OcrProcessingState.Processing

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera preview streaming layer with image capture use-case
        CameraPreview(
            isTorchOn = uiState.isTorchOn,
            onTorchAvailabilityChanged = onTorchAvailabilityChanged,
            imageCapture = imageCapture,
            modifier = Modifier.fillMaxSize()
        )

        // Viewfinder reticle overlay for nutrition table framing
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 300.dp, height = 360.dp)
                    .border(
                        width = 2.dp,
                        color = Color.White.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(16.dp)
                    )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.65f)
            ) {
                Text(
                    text = stringResource(R.string.ocr_capture_viewfinder_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Top bar with back and torch controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onBack,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.scanner_back)
                )
            }

            if (uiState.isTorchAvailable) {
                FilledIconButton(
                    onClick = onToggleTorch,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (uiState.isTorchOn) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Black.copy(alpha = 0.55f)
                        },
                        contentColor = if (uiState.isTorchOn) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            Color.White
                        }
                    )
                ) {
                    Icon(
                        painter = painterResource(
                            if (uiState.isTorchOn) R.drawable.ic_flashlight_on
                            else R.drawable.ic_flashlight_off
                        ),
                        contentDescription = stringResource(
                            if (uiState.isTorchOn) R.string.scanner_torch_turn_off
                            else R.string.scanner_torch_turn_on
                        )
                    )
                }
            }
        }

        // Bottom capture control bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            FilledIconButton(
                onClick = onCapture,
                enabled = !isBusy,
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_document_scanner),
                    contentDescription = stringResource(R.string.ocr_capture_take_photo),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Loading overlay when processing OCR
        if (isBusy) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.ocr_capture_processing),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // Dialog if no nutrition data recognized
        val processingState = uiState.processingState
        if (processingState is OcrProcessingState.NoDataDetected) {
            AlertDialog(
                onDismissRequest = { onRetryNoData(processingState.photoFile) },
                title = { Text(text = stringResource(R.string.ocr_capture_no_data_title)) },
                text = { Text(text = stringResource(R.string.ocr_capture_no_data_message)) },
                confirmButton = {
                    Button(onClick = { onContinueManual(processingState.photoFile, processingState.barcode) }) {
                        Text(text = stringResource(R.string.ocr_capture_continue_manual_action))
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { onRetryNoData(processingState.photoFile) }) {
                        Text(text = stringResource(R.string.ocr_capture_retry_action))
                    }
                }
            )
        }

        // Dialog on error
        if (processingState is OcrProcessingState.Error) {
            AlertDialog(
                onDismissRequest = onDismissError,
                title = { Text(text = stringResource(R.string.scanner_product_error_title)) },
                text = { Text(text = processingState.message.asString()) },
                confirmButton = {
                    Button(onClick = onDismissError) {
                        Text(text = stringResource(R.string.confirm_product_discard_button))
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrPermissionDeniedContent(
    onBack: () -> Unit,
    onRetryPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.ocr_capture_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.scanner_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_camera_disabled),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.scanner_permission_denied_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ocr_capture_permission_rationale_message),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetryPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.scanner_permission_denied_retry_button))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.scanner_permission_denied_settings_button))
            }
        }
    }
}
