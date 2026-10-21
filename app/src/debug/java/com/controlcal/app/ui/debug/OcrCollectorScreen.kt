package com.controlcal.app.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controlcal.app.R

@Composable
fun OcrCollectorRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel = remember(context) {
        val owner = context as? ViewModelStoreOwner
            ?: throw IllegalStateException("Context is not a ViewModelStoreOwner")
        ViewModelProvider(
            owner,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return OcrCollectorViewModel(context.applicationContext as android.app.Application) as T
                }
            }
        )[OcrCollectorViewModel::class.java]
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraController = remember { OcrCameraController() }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.releaseResources()
        }
    }

    OcrCollectorScreen(
        uiState = uiState,
        cameraController = cameraController,
        onBack = onBack,
        onCaptureRequested = { viewModel.onCaptureRequested(cameraController) },
        onNextCaptured = viewModel::onNextCaptured,
        onDiscardCaptured = viewModel::onDiscardCaptured,
        onRetryReady = viewModel::onRetryReady,
        onToggleTorch = viewModel::onToggleTorch,
        onTorchAvailabilityChanged = viewModel::onTorchAvailabilityChanged,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrCollectorScreen(
    uiState: OcrCollectorUiState,
    cameraController: OcrCameraController,
    onBack: () -> Unit,
    onCaptureRequested: () -> Unit,
    onNextCaptured: () -> Unit,
    onDiscardCaptured: (String) -> Unit,
    onRetryReady: () -> Unit,
    onToggleTorch: () -> Unit,
    onTorchAvailabilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeleteConfirmDialog by remember { mutableStateOf<String?>(null) }
    var showFolderInfoDialog by remember { mutableStateOf(false) }

    val savedCount = when (uiState) {
        is OcrCollectorUiState.Ready -> uiState.savedCount
        is OcrCollectorUiState.Capturing -> uiState.savedCount
        is OcrCollectorUiState.CapturedSuccess -> uiState.savedCount
        is OcrCollectorUiState.Error -> uiState.savedCount
    }

    val isTorchOn = (uiState as? OcrCollectorUiState.Ready)?.isTorchOn ?: false

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera preview layer
        OcrCameraPreview(
            controller = cameraController,
            isTorchOn = isTorchOn,
            onTorchAvailabilityChanged = onTorchAvailabilityChanged,
            modifier = Modifier.fillMaxSize()
        )

        // Viewfinder reticle overlay (centered framing guide for nutrition tables)
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
                        color = Color.White.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(16.dp)
                    )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = stringResource(R.string.debug_ocr_viewfinder_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Top bar overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.scanner_back),
                    tint = Color.White
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.debug_ocr_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.debug_ocr_saved_count, savedCount),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            IconButton(onClick = { showFolderInfoDialog = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = stringResource(R.string.debug_ocr_folder_title),
                    tint = Color.White
                )
            }
        }

        // Bottom control overlay
        if (uiState is OcrCollectorUiState.Ready) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    Text(
                        text = "Siguiente: ${uiState.nextBaseName}.jpg",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Torch button
                    if (uiState.isTorchAvailable) {
                        FilledIconButton(
                            onClick = onToggleTorch,
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (uiState.isTorchOn) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Black.copy(alpha = 0.6f)
                                },
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (uiState.isTorchOn) {
                                        R.drawable.ic_flashlight_on
                                    } else {
                                        R.drawable.ic_flashlight_off
                                    }
                                ),
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(56.dp))
                    }

                    // Large Capture button
                    Button(
                        onClick = onCaptureRequested,
                        modifier = Modifier.size(76.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = "FOTO",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.size(56.dp))
                }
            }
        }

        // Overlay for capturing progress
        if (uiState is OcrCollectorUiState.Capturing) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.75f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.debug_ocr_capturing),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${uiState.baseName}.jpg",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Overlay for capture success result
        if (uiState is OcrCollectorUiState.CapturedSuccess) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 10.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.debug_ocr_success_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(
                            R.string.debug_ocr_success_message,
                            "${uiState.baseName}.jpg",
                            "${uiState.baseName}.json",
                            uiState.linesCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Comando ADB para descargar:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.adbCommand,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onNextCaptured,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.debug_ocr_next_button))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = uiState.baseName },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.debug_ocr_delete_last_button))
                    }
                }
            }
        }

        // Overlay for Error
        if (uiState is OcrCollectorUiState.Error) {
            AlertDialog(
                onDismissRequest = onRetryReady,
                title = { Text(text = "Error de captura") },
                text = { Text(text = uiState.message) },
                confirmButton = {
                    Button(onClick = onRetryReady) {
                        Text(text = "Reintentar")
                    }
                }
            )
        }

        // Confirmation dialog for deleting captured photo
        if (showDeleteConfirmDialog != null) {
            val baseName = showDeleteConfirmDialog!!
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = null },
                title = { Text(text = stringResource(R.string.debug_ocr_confirm_delete_title)) },
                text = {
                    Text(
                        text = stringResource(
                            R.string.debug_ocr_confirm_delete_message,
                            "$baseName.jpg",
                            "$baseName.json"
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = null
                            onDiscardCaptured(baseName)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(text = stringResource(R.string.debug_ocr_delete_confirm_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = null }) {
                        Text(text = stringResource(R.string.debug_ocr_cancel_action))
                    }
                }
            )
        }

        // Information dialog about storage folder and ADB
        if (showFolderInfoDialog) {
            val storageHelper = remember { OcrLabelStorageHelper(context) }
            val folderPath = storageHelper.getLabelsDirectory().absolutePath
            val adbCommand = storageHelper.getAdbPullCommand()

            val adbCopiedMessage = stringResource(R.string.debug_ocr_adb_copied)

            AlertDialog(
                onDismissRequest = { showFolderInfoDialog = false },
                title = { Text(text = stringResource(R.string.debug_ocr_folder_title)) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = "Ruta en el móvil:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = folderPath,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Copiar al PC mediante ADB:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = adbCommand,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("ADB command", adbCommand))
                            Toast.makeText(
                                context,
                                adbCopiedMessage,
                                Toast.LENGTH_SHORT
                            ).show()
                            showFolderInfoDialog = false
                        }
                    ) {
                        Text(text = stringResource(R.string.debug_ocr_copy_adb_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFolderInfoDialog = false }) {
                        Text(text = "Cerrar")
                    }
                }
            )
        }
    }
}
